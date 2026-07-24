package tfmc.justin.gitstone.managers;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks each player's in-progress region selection (two corners in a world)
 * plus which repo they currently have "active" for commands like /gs commit.
 * Purely in-memory - nothing here is persisted.
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
}
