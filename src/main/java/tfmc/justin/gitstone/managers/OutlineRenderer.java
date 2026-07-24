package tfmc.justin.gitstone.managers;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import tfmc.justin.gitstone.GitStonePlugin;

/**
 * Repeating task that draws a live, see-through particle outline of each
 * online player's current selection (WorldEdit/litematica style), redrawn
 * every {@code outline.refresh-ticks} and visible only to that player.
 */
public class OutlineRenderer extends BukkitRunnable {

    private final GitStonePlugin plugin;

    public OutlineRenderer(GitStonePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Schedules this renderer to run repeatedly, per outline.refresh-ticks in config.
     */
    public BukkitTask start() {
        long period = Math.max(1, plugin.getConfig().getLong("outline.refresh-ticks", 10));
        return runTaskTimer(plugin, period, period);
    }

    @Override
    public void run() {
        SelectionManager selectionManager = plugin.getSelectionManager();
        Particle.DustOptions dust = buildDustOptions();
        int pointsPerEdge = Math.max(2, plugin.getConfig().getInt("outline.points-per-edge", 12));

        for (Player player : Bukkit.getOnlinePlayers()) {
            SelectionManager.Selection sel = selectionManager.getSelection(player);
            if (sel == null || !sel.hasBothCorners()) {
                continue;
            }
            World world = player.getWorld();
            if (!world.getName().equals(sel.getWorld())) {
                continue;
            }
            drawOutline(player, world, sel, dust, pointsPerEdge);
        }
    }

    private Particle.DustOptions buildDustOptions() {
        String raw = plugin.getConfig().getString("outline.particle-color", "0,255,120");
        String[] parts = raw.split(",");
        int r = 0, g = 255, b = 120;
        try {
            r = Integer.parseInt(parts[0].trim());
            g = Integer.parseInt(parts[1].trim());
            b = Integer.parseInt(parts[2].trim());
        } catch (Exception ignored) {
            // fall back to defaults above on any parse issue
        }
        return new Particle.DustOptions(Color.fromRGB(
            clamp(r), clamp(g), clamp(b)), 1.0f);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private void drawOutline(Player player, World world, SelectionManager.Selection sel,
                              Particle.DustOptions dust, int pointsPerEdge) {
        int[] min = sel.min();
        int[] max = sel.max();
        // Cuboid corners span the outer faces of the blocks (min inclusive, max+1 exclusive edge)
        double minX = min[0], minY = min[1], minZ = min[2];
        double maxX = max[0] + 1.0, maxY = max[1] + 1.0, maxZ = max[2] + 1.0;

        double[][] corners = {
            {minX, minY, minZ}, {maxX, minY, minZ}, {maxX, minY, maxZ}, {minX, minY, maxZ},
            {minX, maxY, minZ}, {maxX, maxY, minZ}, {maxX, maxY, maxZ}, {minX, maxY, maxZ}
        };

        int[][] edges = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0}, // bottom face
            {4, 5}, {5, 6}, {6, 7}, {7, 4}, // top face
            {0, 4}, {1, 5}, {2, 6}, {3, 7}  // verticals
        };

        for (int[] edge : edges) {
            double[] a = corners[edge[0]];
            double[] b = corners[edge[1]];
            for (int i = 0; i <= pointsPerEdge; i++) {
                double t = (double) i / pointsPerEdge;
                double x = a[0] + (b[0] - a[0]) * t;
                double y = a[1] + (b[1] - a[1]) * t;
                double z = a[2] + (b[2] - a[2]) * t;
                player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
            }
        }
    }
}
