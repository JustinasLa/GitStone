package tfmc.justin.gitstone.commands;

import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tfmc.justin.gitstone.GitStonePlugin;
import tfmc.justin.gitstone.listeners.WandListener;
import tfmc.justin.gitstone.managers.RepoManager;
import tfmc.justin.gitstone.managers.SelectionManager;
import tfmc.justin.gitstone.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles /gs and its subcommands. Subcommands that need JGit/snapshot work
 * (init/use/list/commit/status/log/branch/checkout) call into RepoManager /
 * SnapshotService, which are Phase-1 stubs — argument parsing and permission
 * checks below are fully implemented regardless.
 */
public class GitStoneCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
        "wand", "init", "use", "list", "pos1", "pos2", "sel",
        "commit", "status", "log", "branch", "checkout", "help"
    );

    private final GitStonePlugin plugin;

    public GitStoneCommand(GitStonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gitstone.use")) {
            Utils.msg(sender, "&cYou do not have permission to use GitStone.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "wand" -> handleWand(sender);
            case "pos1" -> handlePos(sender, 1);
            case "pos2" -> handlePos(sender, 2);
            case "sel" -> handleSel(sender, args);
            case "init" -> handleInit(sender, args);
            case "use" -> handleUse(sender, args);
            case "list" -> handleList(sender);
            case "commit" -> handleCommit(sender, args);
            case "status" -> handleStatus(sender);
            case "log" -> handleLog(sender, args);
            case "branch" -> handleBranch(sender, args);
            case "checkout" -> handleCheckout(sender, args);
            case "help" -> sendHelp(sender);
            default -> {
                Utils.msg(sender, "&cUnknown subcommand: &f" + sub + " &7(try /gs help)");
            }
        }
        return true;
    }

    // --- wand / selection ------------------------------------------------

    private void handleWand(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        ItemStack wand = WandListener.createWand(plugin);
        player.getInventory().addItem(wand);
        Utils.msg(player, "&a[GitStone] &fYou received the GitStone wand. Left-click = pos1, right-click = pos2.");
    }

    private void handlePos(CommandSender sender, int which) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Block target = player.getTargetBlockExact(6);
        Block block = target != null ? target : player.getLocation().getBlock();

        SelectionManager sm = plugin.getSelectionManager();
        String world = block.getWorld().getName();
        if (which == 1) {
            sm.setPos1(player, world, block.getX(), block.getY(), block.getZ());
        } else {
            sm.setPos2(player, world, block.getX(), block.getY(), block.getZ());
        }
        Utils.msg(player, "&a[GitStone] &fPos" + which + " set to &e" + block.getX() + ", "
            + block.getY() + ", " + block.getZ());
    }

    private void handleSel(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
            plugin.getSelectionManager().clear(player);
            Utils.msg(player, "&a[GitStone] &fSelection cleared.");
            return;
        }
        SelectionManager.Selection sel = plugin.getSelectionManager().getSelection(player);
        if (sel == null || !(sel.hasPos1() || sel.hasPos2())) {
            Utils.msg(player, "&7[GitStone] No selection yet. Use the wand or /gs pos1 /gs pos2.");
            return;
        }
        Utils.msg(player, "&a[GitStone] &fWorld: &e" + sel.getWorld());
        if (sel.hasPos1()) {
            int[] p = sel.getPos1();
            Utils.msg(player, "&fPos1: &e" + p[0] + ", " + p[1] + ", " + p[2]);
        }
        if (sel.hasPos2()) {
            int[] p = sel.getPos2();
            Utils.msg(player, "&fPos2: &e" + p[0] + ", " + p[1] + ", " + p[2]);
        }
        if (sel.hasBothCorners()) {
            Utils.msg(player, "&fVolume: &e" + sel.volume() + " blocks");
        }
    }

    // --- repo management ---------------------------------------------------

    private void handleInit(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            Utils.msg(player, "&cUsage: /gs init <name> [description]");
            return;
        }
        String name = args[1];
        if (!name.matches("[A-Za-z0-9_-]+")) {
            Utils.msg(player, "&cRepo names may only contain letters, numbers, - and _.");
            return;
        }
        RepoManager repoManager = plugin.getRepoManager();
        if (repoManager.repoExists(name)) {
            Utils.msg(player, "&cA repo named '" + name + "' already exists.");
            return;
        }
        String description = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "";
        try {
            repoManager.init(name, description, player);
            plugin.getSelectionManager().setActiveRepo(player, name);
            Utils.msg(player, "&a[GitStone] &fCreated repo &e" + name + "&f and set it active.");
        } catch (UnsupportedOperationException e) {
            Utils.msg(player, "&7[GitStone] init is not implemented yet (Phase 2).");
        } catch (Exception e) {
            Utils.msg(player, "&cFailed to init repo: " + e.getMessage());
        }
    }

    private void handleUse(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            Utils.msg(player, "&cUsage: /gs use <repo>");
            return;
        }
        String name = args[1];
        if (!plugin.getRepoManager().repoExists(name)) {
            Utils.msg(player, "&cNo such repo: " + name);
            return;
        }
        plugin.getSelectionManager().setActiveRepo(player, name);
        Utils.msg(player, "&a[GitStone] &fActive repo set to &e" + name);
    }

    private void handleList(CommandSender sender) {
        List<String> repos = plugin.getRepoManager().listRepos();
        if (repos.isEmpty()) {
            Utils.msg(sender, "&7[GitStone] No repos yet. Create one with /gs init <name>.");
            return;
        }
        Utils.msg(sender, "&a[GitStone] &fRepos: &e" + String.join(", ", repos));
    }

    private void handleCommit(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        String repo = plugin.getSelectionManager().getActiveRepo(player);
        if (repo == null) {
            Utils.msg(player, "&cNo active repo. Use /gs use <repo> or /gs init <name> first.");
            return;
        }
        if (!plugin.getSelectionManager().hasBothCorners(player)) {
            Utils.msg(player, "&cSet both selection corners first (wand, or /gs pos1 /gs pos2).");
            return;
        }
        if (args.length < 2) {
            Utils.msg(player, "&cUsage: /gs commit <message>");
            return;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        try {
            plugin.getRepoManager().commit(repo, player, message);
            Utils.msg(player, "&a[GitStone] &fCommitted to &e" + repo);
        } catch (UnsupportedOperationException e) {
            Utils.msg(player, "&7[GitStone] commit is not implemented yet (Phase 2).");
        } catch (Exception e) {
            Utils.msg(player, "&cFailed to commit: " + e.getMessage());
        }
    }

    private void handleStatus(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        String repo = plugin.getSelectionManager().getActiveRepo(player);
        if (repo == null) {
            Utils.msg(player, "&7[GitStone] No active repo.");
            return;
        }
        Utils.msg(player, "&a[GitStone] &fActive repo: &e" + repo);
        try {
            String branch = plugin.getRepoManager().currentBranch(repo);
            Utils.msg(player, "&fBranch: &e" + branch);
        } catch (UnsupportedOperationException e) {
            Utils.msg(player, "&7(branch info not available yet - Phase 2)");
        } catch (Exception e) {
            Utils.msg(player, "&cFailed to read status: " + e.getMessage());
        }
    }

    private void handleLog(CommandSender sender, String[] args) {
        String repo = resolveRepoForRead(sender, args, 1);
        if (repo == null) {
            return;
        }
        int n = 10;
        if (args.length > 1 && !plugin.getRepoManager().repoExists(args[1])) {
            // args[1] wasn't a repo name, try parsing it as a count
            try {
                n = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                // leave default
            }
        } else if (args.length > 2) {
            try {
                n = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
                // leave default
            }
        }
        try {
            List<RepoManager.CommitInfo> log = plugin.getRepoManager().log(repo, n);
            if (log.isEmpty()) {
                Utils.msg(sender, "&7[GitStone] No commits yet.");
            }
            for (RepoManager.CommitInfo c : log) {
                Utils.msg(sender, "&e" + c.shortHash() + " &f" + c.subject() + " &7(" + c.author() + ")");
            }
        } catch (UnsupportedOperationException e) {
            Utils.msg(sender, "&7[GitStone] log is not implemented yet (Phase 2).");
        } catch (Exception e) {
            Utils.msg(sender, "&cFailed to read log: " + e.getMessage());
        }
    }

    private void handleBranch(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        String repo = plugin.getSelectionManager().getActiveRepo(player);
        if (repo == null) {
            Utils.msg(player, "&cNo active repo. Use /gs use <repo> first.");
            return;
        }
        try {
            if (args.length >= 2) {
                plugin.getRepoManager().createBranch(repo, args[1]);
                Utils.msg(player, "&a[GitStone] &fCreated branch &e" + args[1]);
            } else {
                List<String> branches = plugin.getRepoManager().branches(repo);
                Utils.msg(player, "&a[GitStone] &fBranches: &e" + String.join(", ", branches));
            }
        } catch (UnsupportedOperationException e) {
            Utils.msg(player, "&7[GitStone] branch is not implemented yet (Phase 2).");
        } catch (Exception e) {
            Utils.msg(player, "&cFailed: " + e.getMessage());
        }
    }

    private void handleCheckout(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        String repo = plugin.getSelectionManager().getActiveRepo(player);
        if (repo == null) {
            Utils.msg(player, "&cNo active repo. Use /gs use <repo> first.");
            return;
        }
        if (args.length < 2) {
            Utils.msg(player, "&cUsage: /gs checkout <ref> confirm &7(checkout rebuilds blocks in the world - destructive)");
            return;
        }
        String ref = args[1];
        boolean confirmed = args.length >= 3 && args[2].equalsIgnoreCase("confirm");
        if (!confirmed) {
            Utils.msg(player, "&cThis will overwrite live blocks. Re-run as: /gs checkout " + ref + " confirm");
            return;
        }
        try {
            plugin.getRepoManager().checkout(repo, ref);
            Utils.msg(player, "&a[GitStone] &fChecked out &e" + ref);
        } catch (UnsupportedOperationException e) {
            Utils.msg(player, "&7[GitStone] checkout is not implemented yet (Phase 2).");
        } catch (Exception e) {
            Utils.msg(player, "&cFailed to checkout: " + e.getMessage());
        }
    }

    private void sendHelp(CommandSender sender) {
        Utils.msg(sender, "&a=== GitStone ===");
        Utils.msg(sender, "&e/gs wand &7- get the selection wand");
        Utils.msg(sender, "&e/gs pos1&7/&epos2 &7- set corners at your target block");
        Utils.msg(sender, "&e/gs sel [clear] &7- show/clear your selection");
        Utils.msg(sender, "&e/gs init <name> [desc] &7- create a repo");
        Utils.msg(sender, "&e/gs use <repo> &7- set active repo");
        Utils.msg(sender, "&e/gs list &7- list repos");
        Utils.msg(sender, "&e/gs commit <message> &7- snapshot selection to active repo");
        Utils.msg(sender, "&e/gs status &7- active repo + branch");
        Utils.msg(sender, "&e/gs log [n] &7- recent commits");
        Utils.msg(sender, "&e/gs branch [name] &7- list/create branches");
        Utils.msg(sender, "&e/gs checkout <ref> confirm &7- rebuild a snapshot into the world");
    }

    // --- helpers -------------------------------------------------------

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        Utils.msg(sender, "&cThis command can only be used by players.");
        return null;
    }

    private String resolveRepoForRead(CommandSender sender, String[] args, int nameArgIndex) {
        if (args.length > nameArgIndex && plugin.getRepoManager().repoExists(args[nameArgIndex])) {
            return args[nameArgIndex];
        }
        if (sender instanceof Player player) {
            String active = plugin.getSelectionManager().getActiveRepo(player);
            if (active != null) {
                return active;
            }
        }
        Utils.msg(sender, "&cNo active repo. Use /gs use <repo> first, or /gs log <repo>.");
        return null;
    }

    // --- tab completion --------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> results = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBCOMMANDS) {
                if (s.startsWith(args[0].toLowerCase())) {
                    results.add(s);
                }
            }
            return results;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("use") || sub.equals("log") || sub.equals("checkout")) {
                for (String repo : plugin.getRepoManager().listRepos()) {
                    if (repo.toLowerCase().startsWith(args[1].toLowerCase())) {
                        results.add(repo);
                    }
                }
                return results;
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("checkout") && sender instanceof Player player) {
            String repo = plugin.getSelectionManager().getActiveRepo(player);
            if (repo != null) {
                try {
                    for (String branch : plugin.getRepoManager().branches(repo)) {
                        if (branch.toLowerCase().startsWith(args[2].toLowerCase())) {
                            results.add(branch);
                        }
                    }
                } catch (Exception ignored) {
                    // Phase 2 stub or IO failure - no suggestions
                }
            }
            return results;
        }

        return results;
    }
}
