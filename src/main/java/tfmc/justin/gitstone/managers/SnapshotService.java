package tfmc.justin.gitstone.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import tfmc.justin.gitstone.GitStonePlugin;
import tfmc.justin.gitstone.utils.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a world region to/from the on-disk snapshot format described in
 * the design doc: {@code build.gsbuild} (palette + run-length-encoded block
 * data) and {@code info.yml} (metadata), written into a repo's working
 * directory. Reading/writing Bukkit blocks must happen on the main thread;
 * the actual git staging/commit is {@link RepoManager}'s job.
 */
public class SnapshotService {

    private static final int RUNS_PER_LINE = 20;

    private final GitStonePlugin plugin;

    public SnapshotService(GitStonePlugin plugin) {
        this.plugin = plugin;
    }

    private void writeBuildFile(File repoDir, String worldName, int[] origin, int dx, int dy, int dz,
                                 List<String> palette, List<Integer> indices) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("gitstone-build v1\n");
        sb.append("world: ").append(worldName).append('\n');
        sb.append("origin: ").append(origin[0]).append(' ').append(origin[1]).append(' ').append(origin[2]).append('\n');
        sb.append("size: ").append(dx).append(' ').append(dy).append(' ').append(dz).append('\n');
        sb.append("palette:\n");
        for (int i = 0; i < palette.size(); i++) {
            sb.append(i).append(' ').append(palette.get(i)).append('\n');
        }
        sb.append("blocks:\n");
        // RLE over indices, iteration order documented above (y outer, z, x inner).
        // Wrap ~RUNS_PER_LINE runs per line so diffs stay line-local.
        int runsOnLine = 0;
        int i = 0;
        int n = indices.size();
        while (i < n) {
            int idx = indices.get(i);
            int runLen = 1;
            while (i + runLen < n && indices.get(i + runLen) == idx) {
                runLen++;
            }
            if (runsOnLine > 0) {
                sb.append(' ');
            }
            sb.append(idx);
            if (runLen > 1) {
                sb.append('x').append(runLen);
            }
            runsOnLine++;
            if (runsOnLine >= RUNS_PER_LINE) {
                sb.append('\n');
                runsOnLine = 0;
            }
            i += runLen;
        }
        if (runsOnLine > 0) {
            sb.append('\n');
        }

        File buildFile = new File(repoDir, "build.gsbuild");
        Files.writeString(buildFile.toPath(), sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Reads the blocks within {@code sel} out of {@code world} and writes
     * build.gsbuild + info.yml into {@code repoDir}, recording the given
     * player's name/uuid as author metadata in info.yml. Must be called on
     * the main thread.
     */
    public void capture(SelectionManager.Selection sel, World world, File repoDir, org.bukkit.entity.Player author) throws IOException {
        if (world == null) {
            throw new IOException("World not loaded for selection.");
        }
        long volume = sel.volume();
        long maxVolume = plugin.getConfig().getLong("limits.max-region-volume", 500000);
        if (volume > maxVolume) {
            throw new IOException("Selection volume (" + volume + ") exceeds limits.max-region-volume (" + maxVolume + ")");
        }

        int[] min = sel.min();
        int[] max = sel.max();
        int dx = max[0] - min[0] + 1;
        int dy = max[1] - min[1] + 1;
        int dz = max[2] - min[2] + 1;

        Map<String, Integer> paletteIndex = new LinkedHashMap<>();
        List<String> palette = new ArrayList<>();
        List<Integer> indices = new ArrayList<>((int) volume);
        long nonAir = 0;

        for (int y = min[1]; y <= max[1]; y++) {
            for (int z = min[2]; z <= max[2]; z++) {
                for (int x = min[0]; x <= max[0]; x++) {
                    Block block = world.getBlockAt(x, y, z);
                    String data = block.getBlockData().getAsString();
                    Integer idx = paletteIndex.get(data);
                    if (idx == null) {
                        idx = palette.size();
                        paletteIndex.put(data, idx);
                        palette.add(data);
                    }
                    indices.add(idx);
                    if (block.getType() != Material.AIR) {
                        nonAir++;
                    }
                }
            }
        }

        writeBuildFile(repoDir, world.getName(), min, dx, dy, dz, palette, indices);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", repoDir.getName());
        yaml.set("author", author.getName());
        yaml.set("author-uuid", author.getUniqueId().toString());
        yaml.set("world", world.getName());
        yaml.set("size", dx + " x " + dy + " x " + dz);
        yaml.set("block-count", volume);
        yaml.set("non-air-count", nonAir);
        yaml.set("updated", Instant.now().toString());
        File infoFile = new File(repoDir, "info.yml");
        yaml.save(infoFile);
    }

    /**
     * Parses build.gsbuild out of {@code repoDir} and places its blocks back
     * into {@code world}, batched across ticks ({@code blocksPerTick} blocks
     * placed per tick) so a large restore doesn't freeze the server.
     * {@code onDone} is invoked (on the main thread) once every block has
     * been placed.
     */
    public void restore(File repoDir, World world, int blocksPerTick, Runnable onDone) throws IOException {
        File buildFile = new File(repoDir, "build.gsbuild");
        if (!buildFile.isFile()) {
            throw new IOException("No build.gsbuild found in " + repoDir.getAbsolutePath());
        }

        int[] origin;
        int dx, dy, dz;
        List<String> palette = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(buildFile.toPath(), StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || !header.startsWith("gitstone-build")) {
                throw new IOException("Malformed build.gsbuild: bad header");
            }
            String worldLine = reader.readLine(); // world: <name>
            String originLine = reader.readLine();
            if (originLine == null || !originLine.startsWith("origin:")) {
                throw new IOException("Malformed build.gsbuild: missing origin");
            }
            String[] originParts = originLine.substring("origin:".length()).trim().split("\\s+");
            origin = new int[]{
                Integer.parseInt(originParts[0]),
                Integer.parseInt(originParts[1]),
                Integer.parseInt(originParts[2])
            };
            String sizeLine = reader.readLine();
            if (sizeLine == null || !sizeLine.startsWith("size:")) {
                throw new IOException("Malformed build.gsbuild: missing size");
            }
            String[] sizeParts = sizeLine.substring("size:".length()).trim().split("\\s+");
            dx = Integer.parseInt(sizeParts[0]);
            dy = Integer.parseInt(sizeParts[1]);
            dz = Integer.parseInt(sizeParts[2]);

            String paletteHeader = reader.readLine(); // "palette:"
            if (paletteHeader == null || !paletteHeader.trim().equals("palette:")) {
                throw new IOException("Malformed build.gsbuild: missing palette section");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals("blocks:")) {
                    break;
                }
                if (line.isBlank()) {
                    continue;
                }
                int sp = line.indexOf(' ');
                if (sp < 0) {
                    continue;
                }
                // index is implicit == palette.size() (entries are sequential)
                String data = line.substring(sp + 1);
                palette.add(data);
            }
            // remaining lines are RLE run tokens
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                for (String token : line.trim().split("\\s+")) {
                    int xPos = token.indexOf('x');
                    int idx;
                    int runLen;
                    if (xPos > 0) {
                        idx = Integer.parseInt(token.substring(0, xPos));
                        runLen = Integer.parseInt(token.substring(xPos + 1));
                    } else {
                        idx = Integer.parseInt(token);
                        runLen = 1;
                    }
                    for (int r = 0; r < runLen; r++) {
                        indices.add(idx);
                    }
                }
            }
        }

        // Pre-resolve BlockData per palette entry once.
        BlockData[] resolved = new BlockData[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            try {
                resolved[i] = Bukkit.createBlockData(palette.get(i));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Failed to parse palette entry '" + palette.get(i) + "', falling back to AIR: " + e.getMessage());
                resolved[i] = Bukkit.createBlockData(Material.AIR);
            }
        }

        int expected = dx * dy * dz;
        if (indices.size() != expected) {
            throw new IOException("build.gsbuild block count mismatch: expected " + expected + " got " + indices.size());
        }

        final int fdx = dx, fdz = dz;
        final int[] forigin = origin;
        final List<Integer> findices = indices;
        final BlockData[] fresolved = resolved;

        Utils.runSync(plugin, () -> {
            int total = findices.size();
            int[] cursor = {0};
            org.bukkit.scheduler.BukkitTask[] taskHolder = new org.bukkit.scheduler.BukkitTask[1];
            taskHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                int placedThisTick = 0;
                while (cursor[0] < total && placedThisTick < blocksPerTick) {
                    int linear = cursor[0];
                    // linear index -> (x,y,z) matching write order: y outer, z, x inner
                    int y = linear / (fdx * fdz);
                    int rem = linear % (fdx * fdz);
                    int z = rem / fdx;
                    int x = rem % fdx;
                    int worldX = forigin[0] + x;
                    int worldY = forigin[1] + y;
                    int worldZ = forigin[2] + z;
                    BlockData data = fresolved[findices.get(linear)];
                    world.getBlockAt(worldX, worldY, worldZ).setBlockData(data, false);
                    cursor[0]++;
                    placedThisTick++;
                }
                if (cursor[0] >= total) {
                    taskHolder[0].cancel();
                    if (onDone != null) {
                        onDone.run();
                    }
                }
            }, 0L, 1L);
        });
    }
}
