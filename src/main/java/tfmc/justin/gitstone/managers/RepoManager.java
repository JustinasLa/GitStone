package tfmc.justin.gitstone.managers;

import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Owns the on-disk repos directory and all JGit plumbing: init, staging +
 * committing, log/branch/checkout. Repo layout matches what web/server.js
 * expects: {@code <reposRoot>/<name>/} containing a standard git repo plus a
 * {@code .gitstone-description} file.
 * <p>
 * Split of responsibilities: {@link SnapshotService} is responsible for
 * turning a world region into files on disk (build.gsbuild / info.yml) and
 * back; {@link RepoManager} is responsible for everything git - creating the
 * repo, staging whatever files are on disk, committing, and reading history.
 * {@code commit()} therefore calls into {@code SnapshotService#capture} (via
 * the caller / command layer) before it stages+commits, or callers may pass
 * already-written files - the exact wiring is decided in Phase 2.
 * <p>
 * Phase 1: only directory/name bookkeeping is implemented. All git-backed
 * methods are stubs that throw {@link UnsupportedOperationException} until
 * Phase 2 wires in JGit.
 */
public class RepoManager {

    /**
     * A single commit's metadata, as shown by /gs log and the web viewer.
     */
    public record CommitInfo(String hash, String shortHash, String author, String email,
                              long epochSeconds, String subject) {
    }

    private final File reposRoot;

    public RepoManager(File reposRoot) {
        this.reposRoot = reposRoot;
    }

    /**
     * Directory a given repo lives (or would live) in, under the repos root.
     */
    public File repoDir(String name) {
        return new File(reposRoot, name);
    }

    /**
     * Names of all repos currently present under the repos root.
     */
    public List<String> listRepos() {
        File[] dirs = reposRoot.listFiles(File::isDirectory);
        if (dirs == null) {
            return List.of();
        }
        return java.util.Arrays.stream(dirs)
            .map(File::getName)
            .sorted()
            .toList();
    }

    /**
     * Whether a repo with this name already exists on disk.
     */
    public boolean repoExists(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return repoDir(name).isDirectory();
    }

    /**
     * Creates a new repo directory, JGit-inits it, and writes the
     * .gitstone-description file. Phase 2.
     */
    public void init(String name, String description, Player author) throws IOException {
        throw new UnsupportedOperationException("phase 2");
    }

    /**
     * Stages whatever SnapshotService wrote into the repo directory and
     * creates a commit authored by the given player. Phase 2.
     */
    public void commit(String repo, Player author, String message) throws IOException {
        throw new UnsupportedOperationException("phase 2");
    }

    /**
     * Most recent {@code n} commits on the current branch (newest first). Phase 2.
     */
    public List<CommitInfo> log(String repo, int n) throws IOException {
        throw new UnsupportedOperationException("phase 2");
    }

    /**
     * All local branch names. Phase 2.
     */
    public List<String> branches(String repo) throws IOException {
        throw new UnsupportedOperationException("phase 2");
    }

    /**
     * Name of the currently checked-out branch. Phase 2.
     */
    public String currentBranch(String repo) throws IOException {
        throw new UnsupportedOperationException("phase 2");
    }

    /**
     * Creates a new branch (pointing at HEAD) without switching to it. Phase 2.
     */
    public void createBranch(String repo, String name) throws IOException {
        throw new UnsupportedOperationException("phase 2");
    }

    /**
     * Checks out a branch/commit ref on disk (JGit-level; does NOT rebuild
     * blocks in the world - that is SnapshotService#restore, called
     * afterwards by the command layer). Phase 2.
     */
    public void checkout(String repo, String ref) throws IOException {
        throw new UnsupportedOperationException("phase 2");
    }
}
