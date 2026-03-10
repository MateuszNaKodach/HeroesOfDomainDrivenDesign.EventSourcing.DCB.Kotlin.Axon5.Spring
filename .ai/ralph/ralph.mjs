#!/usr/bin/env node

// Ralph Loop - Autonomous AI agent orchestrator
//
// Usage (from repo root):
//   node .ai/ralph/ralph.mjs                                    # 10 iterations, sequential
//   node .ai/ralph/ralph.mjs --iterations 5                     # 5 iterations, sequential
//   node .ai/ralph/ralph.mjs --max-worktrees 3 --max-iterations 4  # parallel (3 worktrees)
//   node .ai/ralph/ralph.mjs --max-worktrees 2 --stream         # parallel with streaming
//   node .ai/ralph/ralph.mjs --fresh --max-worktrees 3          # wipe state, then parallel
//
// Sequential mode (--max-worktrees 1, default): agent commits on parent branch, orchestrator pushes.
// Parallel mode (--max-worktrees > 1): per-slice git worktrees, concurrent Claude agents.
//   Each worktree gets ./mvnw install -DskipTests before Claude starts.
//   Agents only commit+push. Orchestrator owns a finalization queue that processes done slices
//   one by one (squash → rebase → ff-merge/PR/none).
//
// Flags: --max-iterations, --max-worktrees, --stream, --finalize (pr|merge|none),
//        --discover (every|once), --fresh, --conflict-commit (separate|squash)

import {execSync, spawn} from "node:child_process";
import {
    appendFileSync,
    existsSync,
    mkdirSync,
    readdirSync,
    readFileSync,
    rmSync,
    unlinkSync,
    writeFileSync
} from "node:fs";
import {dirname, join, resolve} from "node:path";
import {fileURLToPath} from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(scriptDir, "..", "..");

// ── CLI arg parsing ──────────────────────────────────────────

function parseFlag(flag, fallback) {
    const idx = process.argv.indexOf(flag);
    if (idx === -1) return fallback;
    return parseInt(process.argv[idx + 1], 10) || fallback;
}

function parseStringFlag(flag, fallback) {
    const idx = process.argv.indexOf(flag);
    if (idx === -1) return fallback;
    return process.argv[idx + 1] || fallback;
}

function hasFlag(flag) {
    return process.argv.includes(flag);
}

const maxIterations = parseFlag("--max-iterations", parseFlag("--iterations", 10));
const maxWorktrees = parseFlag("--max-worktrees", 1);
const streamOutput = hasFlag("--stream");
const finalizeMode = parseStringFlag("--finalize", "pr");
const discoverMode = parseStringFlag("--discover", "every");
const freshStart = hasFlag("--fresh");
const conflictCommitMode = parseStringFlag("--conflict-commit", "separate");

const PROMPT_FILE = join(scriptDir, "prompt.md");
const STATE_FILE = join(repoRoot, ".ai", "temp", "ralph-state.json");
const REGISTRY_FILE = join(repoRoot, ".ai", "temp", "ralph-registry.json");
const TEMP_DIR = join(repoRoot, ".ai", "temp");
const WORKTREES_DIR = join(repoRoot, ".claude", "worktrees");

const now = () => new Date().toISOString().replace("T", " ").replace(/\.\d+Z$/, "Z");
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function elapsed(startTime) {
    const diff = Date.now() - startTime;
    const mins = Math.floor(diff / 60000);
    const secs = Math.floor((diff % 60000) / 1000);
    return mins > 0 ? `${mins}m ${secs}s` : `${secs}s`;
}

function toKebab(label) {
    return label.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

function formatTokens(n) {
    if (!n) return "";
    if (n >= 1000) return `${Math.round(n / 1000)}k`;
    return `${n}`;
}

// ── Logging ─────────────────────────────────────────────────

const log = {
    banner: (msg) => console.log(`\n${"═".repeat(60)}\n  ${msg}\n${"═".repeat(60)}`),
    info: (msg) => console.log(`  ℹ️  ${msg}`),
    start: (msg) => console.log(`  🚀 ${msg}`),
    done: (msg) => console.log(`  ✅ ${msg}`),
    warn: (msg) => console.log(`  ⚠️  ${msg}`),
    error: (msg) => console.log(`  ❌ ${msg}`),
    wait: (msg) => console.log(`  ⏳ ${msg}`),
    skip: (msg) => console.log(`  ⏭️  ${msg}`),
    complete: (msg) => console.log(`  🎉 ${msg}`),
    iteration: (msg) => console.log(`  🔄 ${msg}`),
    stall: (msg) => console.log(`  ❓ ${msg}`),
    finalize: (msg) => console.log(`  🔀 ${msg}`),
    queue: (msg) => console.log(`  📦 ${msg}`),
};

// ── State persistence (crash recovery — sequential mode) ──────

function saveState(iteration, status) {
    mkdirSync(dirname(STATE_FILE), { recursive: true });
    writeFileSync(STATE_FILE, JSON.stringify({
        iteration,
        maxIterations,
        status,
        startedAt: state?.startedAt ?? now(),
        updatedAt: now(),
    }, null, 2));
}

function loadState() {
    if (!existsSync(STATE_FILE)) return null;
    try {
        return JSON.parse(readFileSync(STATE_FILE, "utf8"));
    } catch {
        return null;
    }
}

function clearState() {
    if (existsSync(STATE_FILE)) unlinkSync(STATE_FILE);
}

let state = loadState();

// ── Registry (parallel mode) ────────────────────────────────

function loadRegistry() {
    if (!existsSync(REGISTRY_FILE)) return null;
    try {
        const reg = JSON.parse(readFileSync(REGISTRY_FILE, "utf8"));
        // Migrate old format: add readyQueue and finalizingSlice if missing
        if (!reg.readyQueue) reg.readyQueue = [];
        if (!("finalizingSlice" in reg)) reg.finalizingSlice = null;
        return reg;
    } catch {
        return null;
    }
}

function saveRegistry(registry) {
    mkdirSync(dirname(REGISTRY_FILE), { recursive: true });
    writeFileSync(REGISTRY_FILE, JSON.stringify(registry, null, 2));
}

function clearRegistry() {
    if (existsSync(REGISTRY_FILE)) unlinkSync(REGISTRY_FILE);
}

// ── Git helpers ─────────────────────────────────────────────

function getParentBranch() {
    try {
        return execSync("git branch --show-current", { cwd: repoRoot, encoding: "utf8" }).trim();
    } catch {
        return "main";
    }
}

function gitExec(cmd, opts = {}) {
    return execSync(cmd, {
        cwd: opts.cwd || repoRoot,
        encoding: "utf8",
        stdio: "pipe",
        ...opts,
    }).trim();
}

function pruneWorktrees() {
    try { gitExec("git worktree prune"); } catch { /* ignore */ }
}

function createWorktree(worktreePath, parentBranch, branchName) {
    const absPath = resolve(repoRoot, worktreePath);
    mkdirSync(dirname(absPath), { recursive: true });
    gitExec(`git worktree add "${absPath}" -b "${branchName}" "${parentBranch}"`);
}

function removeWorktree(worktreePath) {
    const absPath = resolve(repoRoot, worktreePath);
    try {
        gitExec(`git worktree remove --force "${absPath}"`);
    } catch (e) {
        log.warn(`git worktree remove failed: ${e.message?.slice(0, 200)} — cleaning up manually`);
        if (existsSync(absPath)) rmSync(absPath, { recursive: true, force: true });
        pruneWorktrees();
    }
}

function deleteBranch(branchName) {
    try {
        execSync(`git branch -D "${branchName}"`, { cwd: repoRoot, encoding: "utf8", stdio: "pipe" });
    } catch { /* ignore if branch doesn't exist */ }
}

function isProcessAlive(pid) {
    try {
        process.kill(pid, 0);
        return true;
    } catch {
        return false;
    }
}

// ── Fresh start ─────────────────────────────────────────────

function wipePreviousState() {
    log.info("--fresh: wiping all previous run state...");

    // Delete registry
    if (existsSync(REGISTRY_FILE)) {
        unlinkSync(REGISTRY_FILE);
        log.info("  Deleted ralph-registry.json");
    }

    // Delete state file
    if (existsSync(STATE_FILE)) {
        unlinkSync(STATE_FILE);
        log.info("  Deleted ralph-state.json");
    }

    // Delete ralph log files
    if (existsSync(TEMP_DIR)) {
        for (const f of readdirSync(TEMP_DIR)) {
            if (f.startsWith("ralph-") && f.endsWith(".log")) {
                unlinkSync(join(TEMP_DIR, f));
                log.info(`  Deleted ${f}`);
            }
        }
    }

    // Remove ralph worktrees
    if (existsSync(WORKTREES_DIR)) {
        for (const d of readdirSync(WORKTREES_DIR)) {
            if (d.startsWith("ralph-")) {
                removeWorktree(join(".claude", "worktrees", d));
                log.info(`  Removed worktree ${d}`);
            }
        }
    }

    // Delete progress files
    if (existsSync(TEMP_DIR)) {
        for (const d of readdirSync(TEMP_DIR)) {
            const progressPath = join(TEMP_DIR, d, "progress.md");
            if (d.startsWith("feature-") && existsSync(progressPath)) {
                rmSync(join(TEMP_DIR, d), { recursive: true, force: true });
                log.info(`  Deleted progress for ${d}`);
            }
        }
    }

    state = null;
    log.done("Previous state wiped.");
}

// ── Stream-JSON parsing ─────────────────────────────────────

function extractTextFromStreamJson(chunk) {
    const lines = chunk.split("\n").filter(Boolean);
    let text = "";
    for (const line of lines) {
        try {
            const obj = JSON.parse(line);
            if (obj.type === "assistant" && obj.message?.content) {
                for (const block of obj.message.content) {
                    if (block.type === "text") text += block.text;
                }
            }
            if (obj.type === "content_block_delta" && obj.delta?.text) {
                text += obj.delta.text;
            }
            if (obj.type === "result" && obj.result) {
                text += obj.result;
            }
        } catch {
            text += line;
        }
    }
    return text;
}

function extractTokensFromStreamJson(rawOutput) {
    const lines = rawOutput.split("\n").filter(Boolean);
    for (const line of lines) {
        try {
            const obj = JSON.parse(line);
            if (obj.type === "result" && obj.usage) {
                return {
                    input: obj.usage.input_tokens || 0,
                    output: obj.usage.output_tokens || 0,
                    total: (obj.usage.input_tokens || 0) + (obj.usage.output_tokens || 0),
                };
            }
        } catch { /* ignore */ }
    }
    return { input: 0, output: 0, total: 0 };
}

function detectAskUserQuestion(rawOutput) {
    const lines = rawOutput.split("\n").filter(Boolean);
    for (const line of lines) {
        try {
            const obj = JSON.parse(line);
            if (obj.type === "assistant" && obj.message?.content) {
                for (const block of obj.message.content) {
                    if (block.type === "tool_use" && block.name === "AskUserQuestion") {
                        return block.input?.question || block.input?.text || "unknown question";
                    }
                }
            }
        } catch { /* ignore */ }
    }
    return null;
}

// ── Shared Claude CLI spawner ────────────────────────────────

function spawnClaude({ prompt, cwd = repoRoot, logFiles = [], streamPrefix, passStderr = false }) {
    const child = spawn("claude", [
        "--print", "--dangerously-skip-permissions",
        "--output-format", "stream-json", "--verbose",
    ], { cwd, stdio: ["pipe", "pipe", "pipe"] });

    let output = "";
    let rawOutput = "";

    child.stdin.write(prompt);
    child.stdin.end();

    for (const f of logFiles) {
        mkdirSync(dirname(f), { recursive: true });
    }

    const appendLogs = logFiles.length > 0
        ? (text) => { for (const f of logFiles) try { appendFileSync(f, text); } catch { /* ignore */ } }
        : () => {};

    child.stdout.on("data", (chunk) => {
        const raw = chunk.toString();
        rawOutput += raw;
        const text = extractTextFromStreamJson(raw);
        if (text) {
            output += text;
            appendLogs(text);
            if (streamPrefix && streamOutput) {
                const prefixed = text.split("\n").map(l => l ? `  [${streamPrefix}] ${l}` : "").join("\n");
                process.stdout.write(prefixed);
            } else if (passStderr) {
                process.stdout.write(text);
            }
        }
    });

    child.stderr.on("data", (chunk) => {
        const text = chunk.toString();
        appendLogs(text);
        if (passStderr) {
            process.stderr.write(text);
            output += text;
        }
    });

    const promise = new Promise((resolve) => {
        child.on("close", (code) => resolve({ code: code ?? 1, output, rawOutput }));
    });

    return { child, promise };
}

// ══════════════════════════════════════════════════════════════
// ██ SEQUENTIAL MODE (legacy, maxWorktrees === 1)
// ══════════════════════════════════════════════════════════════

function runClaudeSequential(iteration) {
    const basePrompt = readFileSync(PROMPT_FILE, "utf8");
    const prompt = `${basePrompt}\n\n---\n🔄 Ralph iteration ${iteration} of ${maxIterations} | Started: ${state?.startedAt ?? now()}`;
    return spawnClaude({ prompt, passStderr: true }).promise;
}

async function runSequentialMode() {
    const startIteration = state?.status === "running" ? state.iteration : 1;
    const loopStartTime = Date.now();
    const parentBranch = getParentBranch();

    if (startIteration > 1) {
        log.banner(`🔁 Resuming Ralph — iteration ${startIteration} of ${maxIterations}`);
        log.info(`Previous run interrupted at ${state?.updatedAt ?? "unknown"}`);
        log.info(`Original start: ${state?.startedAt ?? "unknown"}`);
    } else {
        log.banner(`🤖 Ralph Loop — ${maxIterations} iterations max (sequential)`);
        log.info(`Prompt: ${PROMPT_FILE}`);
        log.info(`State:  ${STATE_FILE}`);
        log.info(`Finalize: ${finalizeMode} | Parent: ${parentBranch}`);
        log.info(`Time:   ${now()}`);
    }

    for (let i = startIteration; i <= maxIterations; i++) {
        const iterStartTime = Date.now();

        log.banner(`🔄 Iteration ${i} of ${maxIterations}`);
        log.start(`Spawning Claude at ${now()}`);
        log.info(`Progress: ${i - 1} iterations done, ${maxIterations - i + 1} remaining`);

        saveState(i, "running");

        let claudeSkip = false;

        while (true) {
            const { code, output } = await runClaudeSequential(i);

            if (code === 0) {
                if (output.includes("<promise>COMPLETE</promise>")) {
                    console.log();
                    log.complete(`All slices implemented!`);
                    log.done(`Finished at iteration ${i} of ${maxIterations}`);
                    log.info(`Total time: ${elapsed(loopStartTime)}`);
                    clearState();
                    process.exit(0);
                }

                if (output.includes("<promise>NO_TASKS</promise>")) {
                    console.log();
                    log.wait(`No planned slices available — waiting 30s before retry...`);
                    await sleep(30 * 1000);
                }

                // Sequential finalization: push to remote after agent commits (skip only for "none")
                if (finalizeMode !== "none") {
                    try {
                        log.finalize(`Pushing ${parentBranch} to remote...`);
                        gitExec(`git push origin ${parentBranch}`);
                        log.done(`Pushed ${parentBranch} to remote`);
                    } catch (e) {
                        log.warn(`Push failed (non-fatal): ${e.message?.slice(0, 200)}`);
                    }
                }

                break;
            }

            if (output.includes("No messages returned")) {
                console.log();
                log.skip(`Claude returned no messages (transient) — skipping iteration`);
                claudeSkip = true;
                break;
            }

            console.log();
            log.error(`Claude exited with code ${code}`);
            log.wait(`Possibly rate-limited — waiting 5 minutes before retry...`);
            await sleep(5 * 60 * 1000);
            log.iteration(`Retrying iteration ${i}...`);
        }

        if (claudeSkip) continue;

        console.log();
        log.done(`Iteration ${i} complete (${elapsed(iterStartTime)})`);
        log.info(`Total elapsed: ${elapsed(loopStartTime)}`);
        log.wait(`Pausing 2s before next iteration...`);
        await sleep(2000);
    }

    console.log();
    log.banner(`⛔ Ralph reached max iterations (${maxIterations})`);
    log.warn(`Not all slices completed — check proophboard for status`);
    log.info(`Total time: ${elapsed(loopStartTime)}`);
    clearState();
    process.exit(1);
}

// ══════════════════════════════════════════════════════════════
// ██ PARALLEL MODE (maxWorktrees > 1)
// ══════════════════════════════════════════════════════════════

// ── Slice Discovery ─────────────────────────────────────────

async function discoverSlices() {
    const prompt = `Read proophboard via MCP. Read .proophboard/workspace.json to get workspace_id, then call mcp__proophboard__list_chapters, then for each chapter call mcp__proophboard__get_chapter.

IMPORTANT: Only include slices whose status is EXACTLY "planned". Do NOT include slices with status "ready", "deployed", "in-progress", or "blocked" — those are already done or not available.

Output ONLY a JSON array of planned slices wrapped in <slices>...</slices> tags. Format:
[{"id":"slice-id","label":"Slice Label","chapter":"Chapter Name","type":"write|read|automation","priority":1}]

Priority rules:
- Write slices that unblock other slices (automations, read models) rank highest (lowest priority number)
- Write slices that produce events consumed by many downstream slices rank higher
- Automations and read slices that depend on already-implemented events rank next
- Within equal priority, prefer lower chapter+slice index

If no slices have status "planned", output: <slices>[]</slices>`;

    const { code, output } = await spawnClaude({ prompt }).promise;

    if (code !== 0) {
        log.error(`Discovery Claude exited with code ${code}`);
        return [];
    }

    const match = output.match(/<slices>([\s\S]*?)<\/slices>/);
    if (!match) {
        log.warn("Discovery returned no <slices> block");
        return [];
    }

    try {
        return JSON.parse(match[1].trim());
    } catch (e) {
        log.error(`Failed to parse discovered slices: ${e.message}`);
        return [];
    }
}

// ── Status Table ────────────────────────────────────────────

function printStatusTable(registry, loopStartTime, queueSize = 0) {
    const active = Object.keys(registry.activeSlices).length;
    const completed = registry.completedIterations;
    const elapsedStr = elapsed(loopStartTime);
    const totalTokens = registry.history.reduce((sum, h) => sum + (h.tokens?.total || 0), 0);
    const stalledCount = registry.history.filter(h => h.result === "stalled").length;
    const readyQueueSize = registry.readyQueue.length;

    console.log();
    console.log(`  🤖 Ralph │ ${completed}/${registry.maxIterations} done │ ${active}/${maxWorktrees} active │ ${elapsedStr} │ finalize: ${finalizeMode} │ parent: ${registry.parentBranch}`);

    // Build table rows
    const tableRows = [];

    for (const [, info] of Object.entries(registry.activeSlices)) {
        const dur = elapsed(new Date(info.startedAt).getTime());
        tableRows.push({
            "": info.status === "stalled" ? "❓" : "🔨",
            Slice: info.sliceLabel,
            Branch: info.branch,
            Time: dur,
            Status: info.status === "stalled" ? "STALLED" : "implementing",
            Tokens: "",
        });
    }

    // Show ready queue entries
    for (const item of registry.readyQueue) {
        tableRows.push({
            "": "📦",
            Slice: item.label,
            Branch: item.branch,
            Time: item.duration || "?",
            Status: "queued-for-finalization",
            Tokens: item.tokens?.total ? formatTokens(item.tokens.total) : "",
        });
    }

    // Show currently finalizing slice
    if (registry.finalizingSlice) {
        tableRows.push({
            "": "🔀",
            Slice: registry.finalizingSlice.label,
            Branch: registry.finalizingSlice.branch,
            Time: registry.finalizingSlice.duration || "?",
            Status: "finalizing",
            Tokens: registry.finalizingSlice.tokens?.total ? formatTokens(registry.finalizingSlice.tokens.total) : "",
        });
    }

    for (const h of registry.history.slice(-10)) {
        let icon, status;
        if (h.result === "completed") {
            icon = "✅";
            if (finalizeMode === "pr" && h.prNumber) {
                status = `→ PR #${h.prNumber}`;
            } else if (finalizeMode === "merge") {
                status = "→ merged";
            } else {
                status = "→ on branch";
            }
        } else if (h.result === "blocked") {
            icon = "🚫";
            status = "BLOCKED";
        } else if (h.result === "stalled") {
            icon = "❓";
            status = "STALLED";
        } else {
            icon = "⚠️";
            status = h.result;
        }
        tableRows.push({
            "": icon,
            Slice: h.label,
            Branch: h.branch || "",
            Time: h.duration || "?",
            Status: status,
            Tokens: h.tokens?.total ? formatTokens(h.tokens.total) : "",
        });
    }

    if (tableRows.length > 0) {
        console.table(tableRows);
    } else {
        console.log("  (no slices yet)");
    }

    const parts = [`Queue: ${queueSize}`];
    if (readyQueueSize > 0) parts.push(`Ready: ${readyQueueSize}`);
    if (registry.finalizingSlice) parts.push(`Finalizing: 1`);
    if (stalledCount > 0) parts.push(`Stalled: ${stalledCount}`);
    if (totalTokens > 0) parts.push(`Total tokens: ${formatTokens(totalTokens)}`);
    console.log(`  ${parts.join(" │ ")}`);
    console.log();
}

// ── Worker: spawn Claude in a worktree ──────────────────────

function runMvnInstall(cwd, sliceLabel) {
    log.info(`Running ./mvnw install -DskipTests in worktree for "${sliceLabel}"...`);
    const startTime = Date.now();
    try {
        execSync("./mvnw install -DskipTests -q", {
            cwd,
            encoding: "utf8",
            stdio: "pipe",
            timeout: 5 * 60 * 1000, // 5 min timeout
        });
        log.done(`Maven install for "${sliceLabel}" done (${elapsed(startTime)})`);
        return true;
    } catch (e) {
        log.error(`Maven install failed for "${sliceLabel}": ${e.message?.slice(0, 200)}`);
        return false;
    }
}

function spawnWorker(slice, registry, parentBranch) {
    const kebab = toKebab(slice.label);
    const worktreePath = join(".claude", "worktrees", `ralph-${kebab}`);
    const branchName = `feature/${kebab}`;
    const absWorktreePath = resolve(repoRoot, worktreePath);
    const logFile = join(TEMP_DIR, `ralph-${kebab}.log`);

    // Create worktree (retry once if stale branch exists)
    try {
        createWorktree(worktreePath, parentBranch, branchName);
    } catch (e) {
        if (!e.message?.includes("already exists")) {
            log.error(`Failed to create worktree for "${slice.label}": ${e.message}`);
            return null;
        }
        log.warn(`Branch "${branchName}" already exists — deleting stale branch and retrying`);
        removeWorktree(worktreePath);
        deleteBranch(branchName);
        try {
            createWorktree(worktreePath, parentBranch, branchName);
        } catch (e2) {
            log.error(`Failed to create worktree for "${slice.label}": ${e2.message}`);
            return null;
        }
    }

    // Run Maven install to compile dependencies in the fresh worktree
    if (!runMvnInstall(absWorktreePath, slice.label)) {
        removeWorktree(worktreePath);
        deleteBranch(branchName);
        return null;
    }

    // Initialize log files — main log (truncated) + progress file (with header)
    const progressFile = join(absWorktreePath, ".ai", "temp", "claude-output.md");
    mkdirSync(dirname(logFile), { recursive: true });
    mkdirSync(dirname(progressFile), { recursive: true });
    writeFileSync(logFile, "");
    writeFileSync(progressFile, `# Claude Output — ${slice.label}\n\nStarted: ${now()}\n\n---\n\n`);

    // Build prompt with assignment
    const basePrompt = readFileSync(PROMPT_FILE, "utf8");
    const lockedSlices = Object.entries(registry.activeSlices)
        .filter(([id]) => id !== slice.id)
        .map(([id, info]) => `- "${info.sliceLabel}" (${id}) — another worktree`)
        .join("\n");

    const fullPrompt = `${basePrompt}\n
---
## Worktree Assignment (Ralph Orchestrator)

Your working directory is a dedicated git worktree for this slice.
There are ${Object.keys(registry.activeSlices).length} other worktrees running in parallel on different slices.

### Assigned Slice
- **Slice ID**: ${slice.id}
- **Slice Label**: ${slice.label}
- **Chapter**: ${slice.chapter}
- **Type**: ${slice.type}

Pick THIS slice in \`/em2code-slice\` — pass the slice ID as argument. Do not scan for others.

### Locked Slices (DO NOT TOUCH)
${lockedSlices || "- (none)"}

### Finalization
- **Commit and push only.** After quality gate and commit: \`git push -u origin ${branchName}\`
- Do NOT merge, rebase, or create PRs. The orchestrator handles that.
- After successful push: \`<promise>SLICE_DONE:${slice.id}</promise>\`
- If blocked: \`<promise>SLICE_BLOCKED:${slice.id}</promise>\`
- Do NOT output \`<promise>COMPLETE</promise>\` — only the orchestrator determines that.
`;

    // Spawn Claude — log to both the main log file and the worktree progress file
    const { child, promise: claudePromise } = spawnClaude({
        prompt: fullPrompt,
        cwd: absWorktreePath,
        logFiles: [logFile, progressFile],
        streamPrefix: kebab,
    });

    const promise = claudePromise.then(result => ({
        ...result, slice, worktreePath, branchName,
    }));

    return { pid: child.pid, promise, worktreePath, branchName };
}

// ── Finalization Pipeline ────────────────────────────────────

function squashBranch(branch, parentBranch) {
    const commitCount = parseInt(gitExec(`git rev-list --count ${parentBranch}..${branch}`), 10);
    if (commitCount <= 1) {
        log.info(`Branch "${branch}" has ${commitCount} commit(s) — no squash needed`);
        return;
    }

    log.info(`Squashing ${commitCount} commits on "${branch}" into 1...`);
    gitExec(`git checkout ${branch}`);
    const mergeBase = gitExec(`git merge-base ${parentBranch} ${branch}`);

    // Get first commit message from the feature branch
    const allMsgs = gitExec(`git log --format=%s ${mergeBase}..${branch} --reverse`);
    const firstMsg = allMsgs.split("\n")[0]?.trim();
    const commitMsg = firstMsg || `feat: ${branch}`;

    gitExec(`git reset --soft ${mergeBase}`);
    gitExec(`git commit -m "${commitMsg.replace(/"/g, '\\"')}"`);
    log.done(`Squashed to 1 commit: "${commitMsg}"`);
}

function attemptRebase(branch, parentBranch) {
    gitExec(`git checkout ${branch}`);
    try {
        gitExec(`git rebase ${parentBranch}`);
        return { success: true, conflicts: false };
    } catch (e) {
        // Check if there are actual merge conflicts (unmerged files)
        let hasConflicts = false;
        try {
            const status = gitExec(`git status --porcelain`);
            // Unmerged entries start with U, or have both sides modified (AA, DD, UU, etc.)
            hasConflicts = status.split("\n").some(line => /^(U.|.U|AA|DD)/.test(line));
        } catch { /* ignore */ }

        if (hasConflicts) {
            log.info(`Rebase paused with conflicts — aborting for Claude resolution`);
            try { gitExec(`git rebase --abort`); } catch { /* ignore */ }
            return { success: false, conflicts: true };
        }

        // Not a conflict — some other rebase error
        log.warn(`Rebase failed (non-conflict): ${e.message?.slice(0, 300)}`);
        try { gitExec(`git rebase --abort`); } catch { /* ignore */ }
        return { success: false, conflicts: false };
    }
}

async function resolveRebaseWithClaude(item, registry, rebaseResult) {
    const isConflict = rebaseResult.conflicts;
    const logLabel = isConflict ? "conflict resolution" : "rebase failure resolution";
    log.finalize(`Spawning Claude for ${logLabel} on "${item.label}"...`);

    // Ensure we're on the feature branch
    try { gitExec(`git checkout ${item.branch}`); } catch { /* may already be on branch */ }

    const conflictInstructions = `
## Your Task: Resolve rebase conflicts

1. Start the rebase: \`git rebase ${registry.parentBranch}\`
2. Check \`git status\` to see conflicting files.
3. Resolve each conflict following these rules:

### Conflict Resolution Rules
- **Event/command classes** — two slices introduced the same event or command. Keep the version that matches the proophboard event definition (source of truth). If both are identical, accept either.
- **Sealed interface files** — e.g., \`DwellingEvent.kt\` got new events from both branches. Merge both additions — the sealed interface should list all events.
- **Exhaustive \`when\` blocks** — after merging new events into a sealed interface, any \`when\` expression over that interface needs the missing branch added.
- **EventTags.kt** — both slices added tag constants. Keep both.
- **Feature flag configs** — both slices added entries. Keep all from both sides.

The proophboard Event Model is the source of truth for property names, types, and structure.

4. After resolving each file: \`git add <file>\`
5. Continue: \`git rebase --continue\`
   - The rebase continue creates the commit with the resolved conflicts automatically.
6. If more conflicts appear, repeat steps 2-5.`;

    const failureInstructions = `
## Your Task: Diagnose and fix rebase failure

The rebase of \`${item.branch}\` onto \`${registry.parentBranch}\` failed with a non-conflict error.
The rebase was already aborted.

1. Investigate what went wrong:
   - \`git status\` — check repo state
   - \`git log --oneline -5 ${item.branch}\` and \`git log --oneline -5 ${registry.parentBranch}\` — compare histories
   - Look for lock files, dirty state, or other issues
2. Fix the issue (clean state, remove locks, etc.)
3. Retry the rebase: \`git rebase ${registry.parentBranch}\`
4. If the rebase produces conflicts, resolve them using the same rules as above.`;

    const postRebaseInstructions = `
## After Rebase Completes

7. Run \`./mvnw test\` to verify everything works.
8. If tests FAIL after rebase (e.g., missing \`when\` branches, compilation errors from merged sealed interfaces):
   - Fix the issues (add missing branches, fix imports, etc.)
   - Stage fixes: \`git add <fixed-files>\`
${conflictCommitMode === "separate" ? `   - Create a separate fix commit:
     \`git commit -m "🚫 git(rebase): slice '${item.label}' — post-rebase fixes

     Fixed compilation/test issues after rebasing onto ${registry.parentBranch}."\`
` : `   - Amend the slice commit: \`git commit --amend --no-edit\`
`}   - Re-run \`./mvnw test\` to confirm fix.
9. If tests PASS after rebase — great, no extra commit needed.

## Signals

- Output \`<promise>REBASE_RESOLVED</promise>\` if successful (rebase done + tests pass).
- If you cannot resolve: \`git rebase --abort\` and output \`<promise>REBASE_FAILED</promise>\`.`;

    const prompt = `You are fixing a git rebase issue for slice "${item.label}" (${item.type}).

${isConflict ? conflictInstructions : failureInstructions}
${postRebaseInstructions}`;

    const kebab = toKebab(item.label);
    const rebaseLogFile = join(TEMP_DIR, `ralph-rebase-${kebab}.log`);

    const { code, output } = await spawnClaude({
        prompt,
        logFiles: [rebaseLogFile],
        streamPrefix: `${isConflict ? "conflict" : "rebase"}-${kebab}`,
    }).promise;

    if (code === 0 && output.includes("<promise>REBASE_RESOLVED</promise>")) {
        log.done(`Rebase ${logLabel} succeeded for "${item.label}"`);
        return { success: true };
    }

    log.error(`Rebase ${logLabel} failed for "${item.label}" (exit code: ${code})`);
    try { gitExec(`git rebase --abort`); } catch { /* ignore */ }
    return { success: false };
}

async function finalizeSlice(item, registry) {
    const { branch, worktreePath, label } = item;
    const parentBranch = registry.parentBranch;

    log.finalize(`Finalizing "${label}" (branch: ${branch})...`);

    // Set as currently finalizing
    registry.finalizingSlice = item;
    saveRegistry(registry);

    try {
        // 1. Remove worktree FIRST — it locks the branch and prevents checkout
        if (worktreePath) {
            log.info(`Removing worktree for "${label}" before finalization...`);
            removeWorktree(worktreePath);
        }

        // 2. Ensure main repo is on parent branch and up to date
        gitExec(`git checkout ${parentBranch}`);
        try {
            gitExec(`git pull --ff-only origin ${parentBranch}`);
        } catch {
            log.warn(`Pull --ff-only failed (non-fatal) — continuing with local state`);
        }

        // 3. Squash feature branch to single commit
        squashBranch(branch, parentBranch);

        // 4. Rebase feature branch onto parent
        const rebaseResult = attemptRebase(branch, parentBranch);
        if (!rebaseResult.success) {
            const reason = rebaseResult.conflicts ? "conflicts" : "non-conflict error";
            log.warn(`Rebase of "${branch}" onto "${parentBranch}" failed (${reason}) — invoking Claude...`);
            const resolveResult = await resolveRebaseWithClaude(item, registry, rebaseResult);
            if (!resolveResult.success) {
                throw new Error(`Rebase resolution failed for "${label}" (${reason})`);
            }
            log.done(`Rebase resolved for "${label}"`);
        }

        // 5. Finalize based on mode
        let prNumber = null;
        if (finalizeMode === "merge") {
            gitExec(`git checkout ${parentBranch}`);
            gitExec(`git merge --ff-only ${branch}`);
            log.done(`Fast-forward merged "${branch}" into "${parentBranch}"`);
            try {
                gitExec(`git push origin ${parentBranch}`);
                log.done(`Pushed "${parentBranch}" to remote`);
            } catch (e) {
                log.warn(`Push failed: ${e.message?.slice(0, 200)}`);
            }
        } else if (finalizeMode === "pr") {
            try {
                gitExec(`git push -u origin ${branch} --force-with-lease`);
            } catch {
                gitExec(`git push -u origin ${branch} --force`);
            }
            try {
                const prOutput = gitExec(`gh pr create --title "feat: ${label}" --body "Implemented by Ralph orchestrator." --base ${parentBranch} --head ${branch}`);
                const prMatch = prOutput.match(/pull\/(\d+)/);
                if (prMatch) prNumber = parseInt(prMatch[1], 10);
                log.done(`Created PR for "${label}"${prNumber ? ` (#${prNumber})` : ""}`);
            } catch (e) {
                log.warn(`PR creation failed: ${e.message?.slice(0, 200)}`);
            }
        } else {
            // none — leave branch as-is
            log.info(`"${label}" left on branch "${branch}"`);
        }

        // 6. Cleanup (worktree already removed in step 1)
        if (finalizeMode === "merge") {
            deleteBranch(branch);
        }
        // Ensure we're back on parent branch
        try {
            gitExec(`git checkout ${parentBranch}`);
        } catch { /* ignore */ }

        // 7. Move to history
        registry.history.push({
            sliceId: item.sliceId,
            label: item.label,
            branch: item.branch,
            result: "completed",
            prNumber,
            duration: item.duration,
            tokens: item.tokens,
        });
        registry.completedIterations++;
        registry.finalizingSlice = null;
        saveRegistry(registry);

        log.done(`Finalized "${label}" (${registry.completedIterations}/${registry.maxIterations})`);
        return { success: true, prNumber };

    } catch (e) {
        log.error(`Finalization failed for "${label}": ${e.message}`);
        // Ensure we're back on parent branch
        try {
            gitExec(`git rebase --abort`);
        } catch { /* ignore */ }
        try {
            gitExec(`git checkout ${parentBranch}`);
        } catch { /* ignore */ }

        registry.history.push({
            sliceId: item.sliceId,
            label: item.label,
            branch: item.branch,
            result: "finalization-failed",
            error: e.message,
            duration: item.duration,
            tokens: item.tokens,
        });
        registry.completedIterations++;
        registry.finalizingSlice = null;
        saveRegistry(registry);

        return { success: false };
    }
}

async function processReadyQueueLoop(registry, loopStartTime, doneSignal) {
    while (true) {
        if (registry.readyQueue.length > 0) {
            const item = registry.readyQueue[0];

            // Set finalizingSlice BEFORE removing from queue so item is always visible in status table
            registry.finalizingSlice = item;
            registry.readyQueue.shift();
            saveRegistry(registry);

            log.queue(`Processing ready queue: "${item.label}" (${registry.readyQueue.length} remaining)`);
            printStatusTable(registry, loopStartTime, 0);

            await finalizeSlice(item, registry);

            printStatusTable(registry, loopStartTime, 0);
        } else if (doneSignal.value && !registry.finalizingSlice) {
            // All workers done, queue empty, nothing finalizing — exit
            break;
        } else {
            // Wait before polling again
            await sleep(2000);
        }
    }
}

// ── Crash Recovery (parallel mode) ──────────────────────────

function recoverFromCrash(registry) {
    const recovered = [];
    const toRemove = [];

    // Handle interrupted finalization
    if (registry.finalizingSlice) {
        log.warn(`Found interrupted finalization for "${registry.finalizingSlice.label}" — recovering...`);
        try {
            gitExec(`git rebase --abort`);
        } catch { /* ignore */ }
        try {
            gitExec(`git checkout ${registry.parentBranch}`);
        } catch { /* ignore */ }
        // Move back to front of ready queue
        registry.readyQueue.unshift(registry.finalizingSlice);
        registry.finalizingSlice = null;
        saveRegistry(registry);
        log.done(`Moved interrupted slice back to ready queue`);
    }

    for (const [id, info] of Object.entries(registry.activeSlices)) {
        if (isProcessAlive(info.pid)) {
            log.info(`Slice "${info.sliceLabel}" (PID ${info.pid}) is still running — leaving it`);
            continue;
        }

        log.warn(`Slice "${info.sliceLabel}" (PID ${info.pid}) — process dead, re-queuing`);
        cleanupWorkerBranch(
            existsSync(resolve(repoRoot, info.worktreePath)) ? info.worktreePath : null,
            info.branch
        );
        recovered.push({ id, label: info.sliceLabel, chapter: info.chapter || "unknown", type: info.type || "write", priority: 0 });
        toRemove.push(id);
    }

    for (const id of toRemove) {
        delete registry.activeSlices[id];
    }

    return recovered;
}

// ── Main Parallel Loop ──────────────────────────────────────

function sliceBranch(slice) {
    return `feature/${toKebab(slice.label)}`;
}

function collectOccupiedBranches(registry) {
    const branches = new Set();
    for (const info of Object.values(registry.activeSlices)) branches.add(info.branch);
    for (const item of registry.readyQueue) branches.add(item.branch);
    if (registry.finalizingSlice) branches.add(registry.finalizingSlice.branch);
    return branches;
}

function filterNewSlices(slices, registry, completedIds, completedBranches) {
    const activeIds = new Set(Object.keys(registry.activeSlices));
    const occupiedBranches = collectOccupiedBranches(registry);

    return deduplicateByBranch(slices.filter(s => {
        const branch = sliceBranch(s);
        return !completedIds.has(s.id)
            && !activeIds.has(s.id)
            && !completedBranches.has(branch)
            && !occupiedBranches.has(branch);
    }));
}

function deduplicateByBranch(slices) {
    const seen = new Set();
    return slices.filter(s => {
        const branch = sliceBranch(s);
        if (seen.has(branch)) {
            log.skip(`"${s.label}" (id=${s.id}) deduped — same branch as another queued slice`);
            return false;
        }
        seen.add(branch);
        return true;
    });
}

async function rediscoverSlices(registry, completedIds, completedBranches) {
    log.info("Re-discovering slices from proophboard...");
    const newSlices = await discoverSlices();
    const fresh = filterNewSlices(newSlices, registry, completedIds, completedBranches);
    if (fresh.length > 0) {
        log.info(`Discovered ${fresh.length} new planned slices:`);
        for (const s of fresh) {
            log.info(`  → "${s.label}" (${s.type}) [${s.chapter}] id=${s.id}`);
        }
    } else {
        log.info("No new planned slices found");
    }
    return fresh;
}

function isSliceAvailable(slice, registry, completedBranches, occupiedBranches) {
    const branch = sliceBranch(slice);
    if (registry.activeSlices[slice.id]) {
        log.skip(`"${slice.label}" already active — skipping`);
        return false;
    }
    if (completedBranches.has(branch)) {
        log.skip(`"${slice.label}" branch already completed — skipping`);
        return false;
    }
    if (occupiedBranches.has(branch)) {
        log.skip(`"${slice.label}" branch already occupied — skipping`);
        return false;
    }
    return true;
}

function enqueueForFinalization(registry, { sliceId, slice, branchName, worktreePath, tokens, duration }) {
    registry.readyQueue.push({
        sliceId,
        label: slice.label,
        chapter: slice.chapter,
        type: slice.type,
        branch: branchName,
        worktreePath,
        tokens,
        duration,
        completedAt: now(),
    });
}

function retireWorker(activeWorkers, registry, sliceId) {
    activeWorkers.delete(sliceId);
    delete registry.activeSlices[sliceId];
}

function cleanupWorkerBranch(worktreePath, branchName) {
    if (worktreePath) removeWorktree(worktreePath);
    if (branchName) deleteBranch(branchName);
}

function handleWorkerResult(result, registry, activeWorkers, queue, completedIds, completedBranches) {
    const { id: sliceId, code, output, rawOutput, slice, worktreePath, branchName } = result;
    const tokens = extractTokensFromStreamJson(rawOutput);
    const duration = elapsed(new Date(registry.activeSlices[sliceId]?.startedAt || Date.now()).getTime());

    // Stall detection (AskUserQuestion)
    const askQuestion = detectAskUserQuestion(rawOutput);
    if (askQuestion) {
        log.stall(`"${slice.label}" STALLED — Claude asked: "${askQuestion}"`);
        retireWorker(activeWorkers, registry, sliceId);
        cleanupWorkerBranch(worktreePath, branchName);
        registry.history.push({ sliceId, label: slice.label, branch: branchName, result: "stalled", reason: askQuestion, duration, tokens });
        registry.completedIterations++;
        return { needsSleep: false };
    }

    if (code === 0) {
        if (output.includes(`<promise>SLICE_DONE:${sliceId}</promise>`)) {
            log.done(`"${slice.label}" committed+pushed! (${duration}) — queued for finalization`);
            enqueueForFinalization(registry, { sliceId, slice, branchName, worktreePath, tokens, duration });
            retireWorker(activeWorkers, registry, sliceId);
            // completedIterations incremented after finalization

        } else if (output.includes(`<promise>SLICE_BLOCKED:${sliceId}</promise>`)) {
            log.warn(`"${slice.label}" is BLOCKED`);
            retireWorker(activeWorkers, registry, sliceId);
            cleanupWorkerBranch(worktreePath, branchName);
            completedBranches.add(branchName);
            registry.history.push({ sliceId, label: slice.label, branch: branchName, result: "blocked", duration, tokens });
            registry.completedIterations++;

        } else {
            log.warn(`"${slice.label}" finished without SLICE_DONE signal — enqueuing for finalization`);
            enqueueForFinalization(registry, { sliceId, slice, branchName, worktreePath, tokens, duration });
            retireWorker(activeWorkers, registry, sliceId);
        }
        return { needsSleep: false };
    }

    // Non-zero exit
    retireWorker(activeWorkers, registry, sliceId);
    cleanupWorkerBranch(worktreePath, branchName);

    if (output.includes("No messages returned")) {
        log.skip(`"${slice.label}" — no messages returned, re-queuing`);
        queue.unshift(slice);
        return { needsSleep: false };
    }

    log.error(`"${slice.label}" exited with code ${code} — waiting 5min then re-queuing`);
    queue.unshift(slice);
    return { needsSleep: true };
}

async function runParallelMode() {
    const loopStartTime = Date.now();
    const parentBranch = getParentBranch();
    const sessionId = `ralph-${new Date().toISOString().replace(/[:.]/g, "-").replace("T", "T").slice(0, 19)}`;

    log.banner(`🤖 Ralph Loop — ${maxIterations} iterations max | ${maxWorktrees} parallel worktrees`);
    log.info(`Finalize: ${finalizeMode} | Discover: ${discoverMode} | Stream: ${streamOutput}`);
    log.info(`Conflict commit: ${conflictCommitMode}`);
    log.info(`Parent branch: ${parentBranch}`);
    log.info(`Time: ${now()}`);

    // Prune stale worktree entries once at startup (e.g., after crash)
    pruneWorktrees();

    // Load or create registry
    let registry = loadRegistry();
    let recoveredSlices = [];

    if (registry && (Object.keys(registry.activeSlices).length > 0 || registry.finalizingSlice || registry.readyQueue.length > 0)) {
        log.info("Found existing registry — checking for crash recovery...");
        recoveredSlices = recoverFromCrash(registry);
        registry.maxIterations = maxIterations;
        registry.parentBranch = parentBranch;
        saveRegistry(registry);
    } else {
        registry = {
            sessionId,
            startedAt: now(),
            parentBranch,
            maxIterations,
            completedIterations: 0,
            activeSlices: {},
            readyQueue: [],
            finalizingSlice: null,
            history: [],
        };
        saveRegistry(registry);
    }

    // Discovery phase
    log.start("Discovering planned slices from proophboard...");
    const completedIds = new Set(registry.history.map(h => h.sliceId));
    const completedBranches = new Set(registry.history.map(h => h.branch));
    const discoveredSlices = await discoverSlices();

    let queue = filterNewSlices(
        recoveredSlices.length > 0
            ? [...recoveredSlices, ...discoveredSlices.filter(s => !recoveredSlices.find(r => r.id === s.id))]
            : discoveredSlices,
        registry, completedIds, completedBranches
    );

    if (recoveredSlices.length > 0) {
        log.info(`Recovered ${recoveredSlices.length} slices from crash`);
    }

    log.info(`Found ${queue.length} planned slices:`);
    for (const s of queue) {
        log.info(`  → "${s.label}" (${s.type}) [${s.chapter}] id=${s.id}`);
    }

    if (queue.length === 0 && registry.readyQueue.length === 0) {
        log.complete("No planned slices found — nothing to do!");
        clearRegistry();
        process.exit(0);
    }

    // Active workers map: sliceId → { child, promise, ... }
    const activeWorkers = new Map();
    const doneSignal = { value: false };
    const finalizationPromise = processReadyQueueLoop(registry, loopStartTime, doneSignal);

    const heartbeatInterval = setInterval(() => {
        if (activeWorkers.size > 0 || registry.readyQueue.length > 0 || registry.finalizingSlice) {
            printStatusTable(registry, loopStartTime, queue.length);
        }
    }, 60000);

    try {
        while (registry.completedIterations < maxIterations && (queue.length > 0 || activeWorkers.size > 0)) {

            // Re-discover when a slot is free and queue is empty
            if (discoverMode === "every" && activeWorkers.size < maxWorktrees && queue.length === 0) {
                const fresh = await rediscoverSlices(registry, completedIds, completedBranches);
                queue.push(...fresh);
            }

            // Spawn workers up to maxWorktrees
            const occupied = collectOccupiedBranches(registry);
            while (activeWorkers.size < maxWorktrees && queue.length > 0 && registry.completedIterations + activeWorkers.size < maxIterations) {
                const slice = queue.shift();
                if (!isSliceAvailable(slice, registry, completedBranches, occupied)) continue;

                log.start(`Spawning worktree for "${slice.label}" (${slice.type})`);
                const worker = spawnWorker(slice, registry, parentBranch);
                if (!worker) {
                    log.error(`Failed to spawn worker for "${slice.label}" — skipping`);
                    continue;
                }

                registry.activeSlices[slice.id] = {
                    sliceLabel: slice.label,
                    chapter: slice.chapter,
                    type: slice.type,
                    worktreePath: worker.worktreePath,
                    branch: worker.branchName,
                    pid: worker.pid,
                    status: "implementing",
                    startedAt: now(),
                };
                saveRegistry(registry);
                activeWorkers.set(slice.id, worker);
                printStatusTable(registry, loopStartTime, queue.length);
            }

            if (activeWorkers.size === 0) {
                if (discoverMode === "every") {
                    log.info("All workers done, queue empty — re-discovering...");
                    const fresh = await rediscoverSlices(registry, completedIds, completedBranches);
                    if (fresh.length > 0) {
                        queue.push(...fresh);
                        continue;
                    }
                }
                break;
            }

            // Wait for ANY worker to finish
            const racePromises = [...activeWorkers.entries()].map(([id, w]) =>
                w.promise.then(result => ({ id, ...result }))
            );
            const result = await Promise.race(racePromises);
            const { needsSleep } = handleWorkerResult(result, registry, activeWorkers, queue, completedIds, completedBranches);

            if (needsSleep) await sleep(5 * 60 * 1000);

            saveRegistry(registry);
            printStatusTable(registry, loopStartTime, queue.length);
        }
    } finally {
        clearInterval(heartbeatInterval);
    }

    // Signal finalization loop to drain and exit
    doneSignal.value = true;
    log.info("Waiting for finalization queue to drain...");
    await finalizationPromise;

    // Final summary
    console.log();
    printStatusTable(registry, loopStartTime, queue.length);

    const allCompleted = registry.history.every(h => h.result === "completed");
    if (allCompleted && registry.history.length > 0) {
        log.complete("All slices implemented!");
    } else if (registry.completedIterations >= maxIterations) {
        log.banner(`⛔ Ralph reached max iterations (${maxIterations})`);
        log.warn("Not all slices may be completed — check proophboard for status");
    } else {
        log.done("No more slices to process");
    }

    log.info(`Total time: ${elapsed(loopStartTime)}`);

    // Keep registry for disaster recovery — do NOT clearRegistry()
    process.exit(allCompleted ? 0 : 1);
}

// ══════════════════════════════════════════════════════════════
// ██ MAIN
// ══════════════════════════════════════════════════════════════

if (freshStart) {
    wipePreviousState();
}

if (maxWorktrees <= 1) {
    await runSequentialMode();
} else {
    await runParallelMode();
}
