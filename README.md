# GitStone

**GitHub for Minecraft builds.** A Paper plugin that puts every build — a house, a
farm, a spawn — under real Git version control from inside the game, plus a
GitHub-style web viewer to browse the history in your browser.

One repo = one build. Snapshot a region, branch it, diff it, and roll it back —
the blocks physically rebuild in the world on checkout.

[![CI](https://github.com/JustinasLa/GitStone/actions/workflows/ci.yml/badge.svg)](https://github.com/JustinasLa/GitStone/actions/workflows/ci.yml)

---

## How it works

- Select a region with the **wand** (a live, see-through particle outline shows the box).
- `/gs commit` captures every block in the selection into a **standard Git repo** on disk.
- The snapshot is stored as diff-friendly text (`build.gsbuild`), so `git diff` between
  commits shows exactly which blocks changed.
- `/gs checkout` **rebuilds** any commit or branch back into the world, block by block.
- The **web viewer** (`web/`) serves those repos GitHub-style: repos list, commits, file
  tree, and per-commit diffs.

Git is powered by **JGit** (bundled, shaded into the jar) — no system `git` required on
the server host.

## Commands

All under `/gs` (alias `/gitstone`, permission `gitstone.use`, default op).

| Command | Description |
|---|---|
| `/gs wand` | Get the selection wand (left-click = pos1, right-click = pos2) |
| `/gs pos1` / `/gs pos2` | Set a corner at the block you're looking at |
| `/gs sel [clear]` | Show or clear your current selection |
| `/gs init <name> [desc]` | Create a repo for a build and make it active |
| `/gs use <repo>` / `/gs list` | Switch active repo / list repos |
| `/gs commit <message>` | Snapshot the selection into the active repo |
| `/gs diff` | Compare the selection against the committed HEAD snapshot |
| `/gs status` | Show active repo + current branch |
| `/gs log [n]` | Show recent commits |
| `/gs branch [name]` | List branches, or create one |
| `/gs checkout <ref> confirm` | Rebuild a commit/branch into the world (destructive) |

## Snapshot format

Each commit writes plain-text files into the repo so history stays diff-friendly:

- **`build.gsbuild`** — `world`, `origin`, `size`, a block **palette**, and a
  run-length-encoded body of palette indices (iteration order: y outer, z, x inner).
- **`info.yml`** — build metadata (name, author, world, size, block/non-air counts, timestamp).
- **`.gitstone-description`** — one-line build description (shown in the web viewer).

Example diff of adding a roof:

```diff
-size: 3 1 3
+size: 3 2 3
 palette:
 0 minecraft:air
 1 minecraft:oak_planks
+2 minecraft:cobblestone
 blocks:
-1x9
+1x9 2x9
```

## Build

Requires **JDK 21** and Maven.

```bash
mvn -DskipTests package
```

Produces `target/gitstone-1.0.0.jar` (JGit shaded in) and copies it to the repo root.

## Install (server)

1. Drop `gitstone-1.0.0.jar` into your **Paper 1.21.3** server's `plugins/` folder.
2. Start once, then edit `plugins/GitStone/config.yml`:
   - `repos-path` — where repos are stored. Point it at the web viewer's `repos/` dir to
     have in-game commits show up in the browser immediately.
   - `wand-material`, outline color, `limits.max-region-volume`, `restore.blocks-per-tick`.

## Web viewer

```bash
cd web
npm install
npm start        # http://localhost:3000
```

Reads every Git repo under `repos/` and renders repos, commits, trees, and diffs.

## Config

See `src/main/resources/config.yml` for all keys and defaults.

## Project layout

```
src/main/java/tfmc/justin/gitstone/
  GitStonePlugin.java            plugin entry / wiring
  commands/GitStoneCommand.java  /gs dispatch + tab-complete
  listeners/WandListener.java    wand corner selection
  managers/
    RepoManager.java             JGit: init/commit/log/branch/checkout
    SnapshotService.java         region <-> build.gsbuild + info.yml, restore
    SelectionManager.java        per-player selection + active repo (persisted)
    OutlineRenderer.java         live particle outline
web/                             Node/Express GitHub-style viewer
```

## License

MIT — see [LICENSE](LICENSE).
