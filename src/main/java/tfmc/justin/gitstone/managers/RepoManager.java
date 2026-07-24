package tfmc.justin.gitstone.managers;

import org.bukkit.entity.Player;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
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
     * .gitstone-description file.
     */
    public void init(String name, String description, Player author) throws IOException {
        File dir = repoDir(name);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create repo directory: " + dir.getAbsolutePath());
        }
        try (Git git = Git.init().setDirectory(dir).call()) {
            // leave with no commits - viewer already handles empty repos
        } catch (GitAPIException e) {
            throw new IOException("Failed to init repo: " + e.getMessage(), e);
        }
        File descFile = new File(dir, ".gitstone-description");
        String text = description == null ? "" : description;
        Files.writeString(descFile.toPath(), text, StandardCharsets.UTF_8);
    }

    /**
     * Stages whatever SnapshotService wrote into the repo directory and
     * creates a commit authored by the given player.
     */
    public void commit(String repo, Player author, String message) throws IOException {
        File dir = repoDir(repo);
        try (Git git = Git.open(dir)) {
            git.add().addFilepattern(".").call();
            PersonIdent ident = new PersonIdent(author.getName(), author.getUniqueId() + "@gitstone.local");
            git.commit()
                .setMessage(message)
                .setAuthor(ident)
                .setCommitter(ident)
                .call();
        } catch (GitAPIException e) {
            throw new IOException("Failed to commit: " + e.getMessage(), e);
        }
    }

    /**
     * Most recent {@code n} commits on the current branch (newest first).
     */
    public List<CommitInfo> log(String repo, int n) throws IOException {
        File dir = repoDir(repo);
        List<CommitInfo> result = new ArrayList<>();
        try (Git git = Git.open(dir)) {
            Iterable<RevCommit> commits;
            try {
                commits = git.log().setMaxCount(n).call();
            } catch (org.eclipse.jgit.api.errors.NoHeadException e) {
                return List.of();
            }
            for (RevCommit c : commits) {
                String hash = c.getName();
                String shortHash = hash.length() > 7 ? hash.substring(0, 7) : hash;
                result.add(new CommitInfo(
                    hash,
                    shortHash,
                    c.getAuthorIdent().getName(),
                    c.getAuthorIdent().getEmailAddress(),
                    c.getCommitTime(),
                    c.getShortMessage()
                ));
            }
        } catch (GitAPIException e) {
            throw new IOException("Failed to read log: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * All local branch names.
     */
    public List<String> branches(String repo) throws IOException {
        File dir = repoDir(repo);
        List<String> result = new ArrayList<>();
        try (Git git = Git.open(dir)) {
            List<Ref> refs = git.branchList().call();
            for (Ref ref : refs) {
                String name = ref.getName();
                if (name.startsWith("refs/heads/")) {
                    name = name.substring("refs/heads/".length());
                }
                result.add(name);
            }
        } catch (GitAPIException e) {
            throw new IOException("Failed to list branches: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * Name of the currently checked-out branch.
     */
    public String currentBranch(String repo) throws IOException {
        File dir = repoDir(repo);
        try (Repository repository = new FileRepositoryBuilder()
            .setGitDir(new File(dir, ".git"))
            .build()) {
            return repository.getBranch();
        }
    }

    /**
     * Creates a new branch (pointing at HEAD) without switching to it.
     */
    public void createBranch(String repo, String name) throws IOException {
        File dir = repoDir(repo);
        try (Git git = Git.open(dir)) {
            git.branchCreate().setName(name).call();
        } catch (RefNotFoundException e) {
            throw new IOException("Commit something first before creating a branch.", e);
        } catch (GitAPIException e) {
            throw new IOException("Failed to create branch: " + e.getMessage(), e);
        }
    }

    /**
     * Checks out a branch/commit ref on disk (JGit-level; does NOT rebuild
     * blocks in the world - that is SnapshotService#restore, called
     * afterwards by the command layer).
     */
    public void checkout(String repo, String ref) throws IOException {
        File dir = repoDir(repo);
        try (Git git = Git.open(dir)) {
            git.checkout().setName(ref).call();
        } catch (GitAPIException e) {
            throw new IOException("Failed to checkout '" + ref + "': " + e.getMessage(), e);
        }
    }
}
