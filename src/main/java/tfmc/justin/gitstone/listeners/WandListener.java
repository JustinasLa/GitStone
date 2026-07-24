package tfmc.justin.gitstone.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import tfmc.justin.gitstone.GitStonePlugin;
import tfmc.justin.gitstone.utils.Utils;

/**
 * Handles left/right clicks with the GitStone wand to set selection corners.
 * The wand is identified both by its configured material and by a
 * PersistentDataContainer tag, so a plain vanilla item of the same material
 * never gets mistaken for the wand.
 */
public class WandListener implements Listener {

    private static final String WAND_KEY = "gitstone_wand";

    private final GitStonePlugin plugin;

    public WandListener(GitStonePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Builds a fresh GitStone wand ItemStack using the material/name from config,
     * tagged with the PDC marker so {@link #isWand(GitStonePlugin, ItemStack)} recognizes it.
     */
    public static ItemStack createWand(GitStonePlugin plugin) {
        String materialName = plugin.getConfig().getString("wand-material", "BLAZE_ROD");
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.BLAZE_ROD;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = plugin.getConfig().getString("wand-name", "&aGitStone Wand");
            meta.setDisplayName(Utils.color(name));
            meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, WAND_KEY), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static boolean isWand(Plugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(
            new NamespacedKey(plugin, WAND_KEY), PersistentDataType.BYTE);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!isWand(plugin, item)) {
            return;
        }

        Action action = event.getAction();
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        Player player = event.getPlayer();
        Location loc = clicked.getLocation();

        if (action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            plugin.getSelectionManager().setPos1(player, loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            Utils.msg(player, "&a[GitStone] &fPos1 set to &e" + loc.getBlockX() + ", "
                + loc.getBlockY() + ", " + loc.getBlockZ());
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            plugin.getSelectionManager().setPos2(player, loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            Utils.msg(player, "&a[GitStone] &fPos2 set to &e" + loc.getBlockX() + ", "
                + loc.getBlockY() + ", " + loc.getBlockZ());
        }
    }
}
