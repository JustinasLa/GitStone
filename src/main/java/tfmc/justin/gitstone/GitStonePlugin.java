package tfmc.justin.gitstone;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import tfmc.justin.gitstone.commands.GitStoneCommand;
import tfmc.justin.gitstone.listeners.WandListener;
import tfmc.justin.gitstone.managers.OutlineRenderer;
import tfmc.justin.gitstone.managers.RepoManager;
import tfmc.justin.gitstone.managers.SelectionManager;
import tfmc.justin.gitstone.managers.SnapshotService;

import java.io.File;

public class GitStonePlugin extends JavaPlugin {

    private static GitStonePlugin instance;

    private File reposRoot;
    private File selectionsFile;
    private SelectionManager selectionManager;
    private RepoManager repoManager;
    private SnapshotService snapshotService;
    private BukkitTask outlineTask;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        reposRoot = resolveReposRoot();
        if (!reposRoot.exists() && !reposRoot.mkdirs()) {
            getLogger().warning("Could not create repos directory at " + reposRoot.getAbsolutePath());
        }

        selectionManager = new SelectionManager();
        selectionsFile = new File(getDataFolder(), "selections.yml");
        selectionManager.load(selectionsFile, getLogger());
        repoManager = new RepoManager(reposRoot);
        snapshotService = new SnapshotService(this);

        GitStoneCommand command = new GitStoneCommand(this);
        var pluginCommand = getCommand("gs");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        } else {
            getLogger().warning("Command 'gs' is not registered in plugin.yml!");
        }

        getServer().getPluginManager().registerEvents(new WandListener(this), this);

        outlineTask = new OutlineRenderer(this).start();

        getLogger().info("GitStone has been enabled! Repos: " + reposRoot.getAbsolutePath());
    }

    @Override
    public void onDisable() {
        if (outlineTask != null) {
            outlineTask.cancel();
            outlineTask = null;
        }
        if (selectionManager != null && selectionsFile != null) {
            selectionManager.save(selectionsFile, getLogger());
        }
        getLogger().info("GitStone has been disabled!");
    }

    private File resolveReposRoot() {
        String configured = getConfig().getString("repos-path", "");
        if (configured != null && !configured.isBlank()) {
            return new File(configured);
        }
        return new File(getDataFolder(), "repos");
    }

    public static GitStonePlugin getInstance() {
        return instance;
    }

    public File getReposRoot() {
        return reposRoot;
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    public RepoManager getRepoManager() {
        return repoManager;
    }

    public SnapshotService getSnapshotService() {
        return snapshotService;
    }
}
