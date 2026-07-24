package tfmc.justin.gitstone.managers;

import org.bukkit.World;

import java.io.File;
import java.io.IOException;

/**
 * Converts a world region to/from the on-disk snapshot format described in
 * the design doc: {@code build.gsbuild} (palette + run-length-encoded block
 * data) and {@code info.yml} (metadata), written into a repo's working
 * directory. Reading/writing Bukkit blocks must happen on the main thread;
 * the actual git staging/commit is {@link RepoManager}'s job.
 * <p>
 * Phase 1: signatures only - bodies stubbed for Phase 2.
 */
public class SnapshotService {

    public SnapshotService() {
    }

    /**
     * Reads the blocks within {@code sel} out of {@code world} and writes
     * build.gsbuild + info.yml into {@code repoDir}. Must be called (or have
     * its block-reading portion called) on the main thread. Phase 2.
     */
    public void capture(SelectionManager.Selection sel, World world, File repoDir) throws IOException {
        throw new UnsupportedOperationException("phase 2");
    }

    /**
     * Parses build.gsbuild out of {@code repoDir} and places its blocks back
     * into {@code world}, batched across ticks ({@code blocksPerTick} blocks
     * placed per tick) so a large restore doesn't freeze the server.
     * {@code onDone} is invoked (on the main thread) once every block has
     * been placed. Phase 2.
     */
    public void restore(File repoDir, World world, int blocksPerTick, Runnable onDone) throws IOException {
        throw new UnsupportedOperationException("phase 2");
    }
}
