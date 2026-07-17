const express = require("express");
const path = require("path");
const fs = require("fs");
const { execFile } = require("child_process");

const app = express();
const PORT = 3000;
const REPOS_DIR = path.resolve(__dirname, "..", "repos");

app.use(express.static(path.join(__dirname, "public")));

// Run a git command inside a repo, return stdout as a promise.
function git(repo, args) {
  return new Promise((resolve, reject) => {
    execFile(
      "git",
      ["-C", path.join(REPOS_DIR, repo), ...args],
      { maxBuffer: 10 * 1024 * 1024 },
      (err, stdout) => (err ? reject(err) : resolve(stdout))
    );
  });
}

// Repo names come from URLs — never allow path traversal outside REPOS_DIR.
function safeRepo(name) {
  if (!name || name.includes("..") || name.includes("/") || name.includes("\\")) return null;
  const full = path.join(REPOS_DIR, name);
  if (!fs.existsSync(path.join(full, ".git")) && !fs.existsSync(path.join(full, "HEAD"))) return null;
  return name;
}

const FIELD_SEP = "\x1f";
const LOG_FORMAT = ["%H", "%h", "%an", "%ae", "%at", "%s"].join(FIELD_SEP);

function parseLog(out) {
  return out
    .split("\n")
    .filter(Boolean)
    .map((line) => {
      const [hash, short, author, email, time, subject] = line.split(FIELD_SEP);
      return { hash, short, author, email, time: Number(time) * 1000, subject };
    });
}

app.get("/api/repos", async (_req, res) => {
  try {
    const names = fs
      .readdirSync(REPOS_DIR, { withFileTypes: true })
      .filter((d) => d.isDirectory())
      .map((d) => d.name)
      .filter((n) => safeRepo(n));

    const repos = await Promise.all(
      names.map(async (name) => {
        try {
          const [last] = parseLog(await git(name, ["log", "-1", `--format=${LOG_FORMAT}`]));
          const branches = (await git(name, ["branch", "--format=%(refname:short)"]))
            .split("\n")
            .filter(Boolean);
          const commitCount = Number((await git(name, ["rev-list", "--count", "HEAD"])).trim());
          let description = "";
          const descFile = path.join(REPOS_DIR, name, ".gitstone-description");
          if (fs.existsSync(descFile)) description = fs.readFileSync(descFile, "utf8").trim();
          return { name, description, lastCommit: last, branches, commitCount };
        } catch {
          return { name, description: "", lastCommit: null, branches: [], commitCount: 0 };
        }
      })
    );
    res.json(repos);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get("/api/repos/:repo/branches", async (req, res) => {
  const repo = safeRepo(req.params.repo);
  if (!repo) return res.status(404).json({ error: "repo not found" });
  try {
    const out = await git(repo, ["branch", "--format=%(refname:short)"]);
    const head = (await git(repo, ["rev-parse", "--abbrev-ref", "HEAD"])).trim();
    res.json({ head, branches: out.split("\n").filter(Boolean) });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get("/api/repos/:repo/commits", async (req, res) => {
  const repo = safeRepo(req.params.repo);
  if (!repo) return res.status(404).json({ error: "repo not found" });
  const ref = req.query.ref || "HEAD";
  try {
    const out = await git(repo, ["log", "-100", `--format=${LOG_FORMAT}`, ref, "--"]);
    res.json(parseLog(out));
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get("/api/repos/:repo/commit/:hash", async (req, res) => {
  const repo = safeRepo(req.params.repo);
  if (!repo) return res.status(404).json({ error: "repo not found" });
  if (!/^[0-9a-f]{4,40}$/i.test(req.params.hash)) return res.status(400).json({ error: "bad hash" });
  try {
    const [meta] = parseLog(await git(repo, ["log", "-1", `--format=${LOG_FORMAT}`, req.params.hash]));
    const diff = await git(repo, ["show", "--stat", "--patch", "--format=", req.params.hash]);
    res.json({ ...meta, diff });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get("/api/repos/:repo/tree", async (req, res) => {
  const repo = safeRepo(req.params.repo);
  if (!repo) return res.status(404).json({ error: "repo not found" });
  const ref = req.query.ref || "HEAD";
  const dir = req.query.path || "";
  try {
    const target = dir ? `${ref}:${dir}` : ref;
    const out = await git(repo, ["ls-tree", target]);
    const entries = out
      .split("\n")
      .filter(Boolean)
      .map((line) => {
        const [meta, name] = line.split("\t");
        const [, type] = meta.split(" ");
        return { name, type }; // type: blob | tree
      })
      .sort((a, b) => (a.type === b.type ? a.name.localeCompare(b.name) : a.type === "tree" ? -1 : 1));
    res.json(entries);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get("/api/repos/:repo/blob", async (req, res) => {
  const repo = safeRepo(req.params.repo);
  if (!repo) return res.status(404).json({ error: "repo not found" });
  const ref = req.query.ref || "HEAD";
  const file = req.query.path;
  if (!file) return res.status(400).json({ error: "path required" });
  try {
    const out = await git(repo, ["show", `${ref}:${file}`]);
    res.json({ content: out });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.listen(PORT, () => {
  console.log(`GitStone web running at http://localhost:${PORT}`);
  console.log(`Serving repos from ${REPOS_DIR}`);
});
