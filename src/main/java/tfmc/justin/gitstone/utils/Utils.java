package tfmc.justin.gitstone.utils;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");

    private Utils() {
    }

    /**
     * Translates '&' color codes (and #rrggbb hex codes) into legacy color codes.
     */
    public static String color(String msg) {
        if (msg == null) {
            return "";
        }
        Matcher match = HEX_PATTERN.matcher(msg);
        while (match.find()) {
            String hex = msg.substring(match.start(), match.end());
            msg = msg.replace(hex, String.valueOf(ChatColor.of(hex)));
            match = HEX_PATTERN.matcher(msg);
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    /**
     * Sends a color-translated message to a sender.
     */
    public static void msg(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(color(message));
    }

    /**
     * Runs a task asynchronously (off the main thread). Use for file IO / JGit work.
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    /**
     * Runs a task on the main server thread. Use for Bukkit block/world access.
     */
    public static void runSync(Plugin plugin, Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }
}
