package tfmc.justin.gitstone.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Tracks each player's in-progress region selection (two corners in a world)
 * plus which repo they currently have "active" for commands like /gs commit.
 * In-memory during normal operation; optionally persisted to a YAML file via
 * {@link #save(File)} / {@link #load(File)} so selections survive a restart.
 */
public class SelectionManager {

    /**
     * A two-corner cuboid selection within a single world.
     */
    public static class Selection {
        private final String world;
        private int x1, y1, z1;
        private int x2, y2, z2;
        private boolean pos1Set;
        private boolean pos2Set;

        public Selection(String world) {
            this.world = world;
        }

        public String getWorld() {
            return world;
        }

        public void setPos1(int x, int y, int z) {
            this.x1 = x;
            this.y1 = y;
            this.z1 = z;
            this.pos1Set = true;
        }

        public void setPos2(int x, int y, int z) {
            this.x2 = x;
            this.y2 = y;
            this.z2 = z;
            this.pos2Set = true;
        }

        public boolean hasPos1() {
            return pos1Set;
        }

        public boolean hasPos2() {
            return pos2Set;
        }

        public boolean hasBothCorners() {
            return pos1Set && pos2Set;
        }

        public int[] getPos1() {
            return new int[]{x1, y1, z1};
        }

        public int[] getPos2() {
            return new int[]{x2, y2, z2};
        }

        /**
         * Minimum corner (x,y,z) of the cuboid. Requires both corners set.
         */
        public int[] min() {
            return new int[]{
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2)
            };
        }

        /**
         * Maximum corner (x,y,z) of the cuboid. Requires both corners set.
         */
        public int[] max() {
            return new int[]{
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2)
            };
        }

        /**
         * Total block volume of the cuboid (inclusive of both corners).
         */
        public long volume() {
            if (!hasBothCorners()) {
                return 0;
            }
            int[] min = min();
            int[] max = max();
            long dx = (long) (max[0] - min[0]) + 1;
            long dy = (long) (max[1] - min[1]) + 1;
            long dz = (long) (max[2] - min[2]) + 1;
            return dx * dy * dz;
        }
    }

    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, String> activeRepos = new HashMap<>();

    public void setPos1(Player player, String world, int x, int y, int z) {
        Selection sel = getOrCreate(player, world);
        sel.setPos1(x, y, z);
    }

    public void setPos2(Player player, String world, int x, int y, int z) {
        Selection sel = getOrCreate(player, world);
        sel.setPos2(x, y, z);
    }

    private Selection getOrCreate(Player player, String world) {
        Selection sel = selections.get(player.getUniqueId());
        // If the player switched worlds, the old selection no longer makes sense - reset it.
        if (sel == null || !sel.getWorld().equals(world)) {
            sel = new Selection(world);
            selections.put(player.getUniqueId(), sel);
        }
        return sel;
    }

    public Selection getSelection(Player player) {
        return selections.get(player.getUniqueId());
    }

    public boolean hasBothCorners(Player player) {
        Selection sel = getSelection(player);
        return sel != null && sel.hasBothCorners();
    }

    public void clear(Player player) {
        selections.remove(player.getUniqueId());
    }

    public void setActiveRepo(Player player, String repo) {
        activeRepos.put(player.getUniqueId(), repo);
    }

    public String getActiveRepo(Player player) {
        return activeRepos.get(player.getUniqueId());
    }

    public void save(File file) {
        save(file, null);
    }

    public void load(File file) {
        load(file, null);
    }

    /**
     * Writes all current selections + active repos to {@code file} as YAML,
     * keyed by player UUID string. Best-effort: logs and swallows IO errors
     * rather than crashing plugin disable.
     */
    public void save(File file, Logger logger) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Selection> entry : selections.entrySet()) {
            String key = entry.getKey().toString();
            Selection sel = entry.getValue();
            ConfigurationSection section = yaml.createSection(key);
            section.set("world", sel.getWorld());
            if (sel.hasPos1()) {
                int[] p = sel.getPos1();
                section.set("pos1", p[0] + "," + p[1] + "," + p[2]);
            }
            if (sel.hasPos2()) {
                int[] p = sel.getPos2();
                section.set("pos2", p[0] + "," + p[1] + "," + p[2]);
            }
            section.set("pos1set", sel.hasPos1());
            section.set("pos2set", sel.hasPos2());
        }
        for (Map.Entry<UUID, String> entry : activeRepos.entrySet()) {
            String key = entry.getKey().toString();
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) {
                section = yaml.createSection(key);
            }
            section.set("active-repo", entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            if (logger != null) {
                logger.warning("Failed to save selections to " + file.getAbsolutePath() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Loads selections + active repos previously written by {@link #save}.
     * Robust to a missing/empty/corrupt file: leaves the maps empty and logs
     * a warning rather than throwing.
     */
    public void load(File file, Logger logger) {
        selections.clear();
        activeRepos.clear();
        if (file == null || !file.isFile()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Failed to load selections from " + file.getAbsolutePath() + ": " + e.getMessage());
            }
            return;
        }
        for (String key : yaml.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                String world = section.getString("world");
                if (world != null) {
                    Selection sel = new Selection(world);
                    if (section.getBoolean("pos1set", false)) {
                        int[] p = parseTriple(section.getString("pos1"));
                        if (p != null) {
                            sel.setPos1(p[0], p[1], p[2]);
                        }
                    }
                    if (section.getBoolean("pos2set", false)) {
                        int[] p = parseTriple(section.getString("pos2"));
                        if (p != null) {
                            sel.setPos2(p[0], p[1], p[2]);
                        }
                    }
                    if (sel.hasPos1() || sel.hasPos2()) {
                        selections.put(uuid, sel);
                    }
                }
                String activeRepo = section.getString("active-repo");
                if (activeRepo != null) {
                    activeRepos.put(uuid, activeRepo);
                }
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("Skipping corrupt selection entry '" + key + "': " + e.getMessage());
                }
            }
        }
    }

    private int[] parseTriple(String s) {
        if (s == null) {
            return null;
        }
        String[] parts = s.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new int[]{
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim())
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
