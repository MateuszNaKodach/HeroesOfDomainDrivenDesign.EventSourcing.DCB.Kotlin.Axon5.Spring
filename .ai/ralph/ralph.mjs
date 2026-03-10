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
// Sequential mode (--max-worktrees 1, default): identical to legacy behavior.
// Parallel mode (--max-worktrees > 1): per-slice git worktrees, concurrent Claude agents.
//   Each worktree gets ./mvnw install -DskipTests before Claude starts.
//
// Flags: --max-iterations, --max-worktrees, --stream, --finalize (pr|merge|none),
//        --discover (every|once), --fresh

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
};

// ── State persistence (crash recovery — sequential mode) ──────

function saveState(iteration, status) {
    const dir = dirname(STATE_FILE);
    if (!existsSync(dir)) mkdirSync(dir, { recursive: true });
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
        return JSON.parse(readFileSync(REGISTRY_FILE, "utf8"));
    } catch {
        return null;
    }
}

function saveRegistry(registry) {
    const dir = dirname(REGISTRY_FILE);
    if (!existsSync(dir)) mkdirSync(dir, { recursive: true });
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

function createWorktree(worktreePath, parentBranch, branchName) {
    const absPath = resolve(repoRoot, worktreePath);
    if (!existsSync(dirname(absPath))) mkdirSync(dirname(absPath), { recursive: true });
    // Prune stale worktree entries (e.g., after crash left a registered but missing directory)
    try {
        execSync(`git worktree prune`, { cwd: repoRoot, encoding: "utf8", stdio: "pipe" });
    } catch { /* ignore */ }
    execSync(`git worktree add "${absPath}" -b "${branchName}" "${parentBranch}"`, {
        cwd: repoRoot,
        encoding: "utf8",
        stdio: "pipe",
    });
}

function removeWorktree(worktreePath) {
    const absPath = resolve(repoRoot, worktreePath);
    try {
        execSync(`git worktree remove --force "${absPath}"`, {
            cwd: repoRoot,
            encoding: "utf8",
            stdio: "pipe",
        });
    } catch {
        // If worktree removal fails, try to clean up manually
        if (existsSync(absPath)) {
            rmSync(absPath, { recursive: true, force: true });
        }
        try {
            execSync("git worktree prune", { cwd: repoRoot, encoding: "utf8", stdio: "pipe" });
        } catch { /* ignore */ }
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

// ══════════════════════════════════════════════════════════════
// ██ SEQUENTIAL MODE (legacy, maxWorktrees === 1)
// ══════════════════════════════════════════════════════════════

function runClaudeSequential(iteration) {
    return new Promise((resolve) => {
        const basePrompt = readFileSync(PROMPT_FILE, "utf8");
        const prompt = `${basePrompt}\n\n---\n🔄 Ralph iteration ${iteration} of ${maxIterations} | Started: ${state?.startedAt ?? now()}`;
        const child = spawn("claude", [
            "--print",
            "--dangerously-skip-permissions",
            "--output-format", "stream-json",
            "--verbose",
        ], {
            cwd: repoRoot,
            stdio: ["pipe", "pipe", "pipe"],
        });

        let output = "";
        let rawOutput = "";

        child.stdin.write(prompt);
        child.stdin.end();

        child.stdout.on("data", (chunk) => {
            const raw = chunk.toString();
            rawOutput += raw;
            const text = extractTextFromStreamJson(raw);
            if (text) {
                process.stdout.write(text);
                output += text;
            }
        });

        child.stderr.on("data", (chunk) => {
            const text = chunk.toString();
            process.stderr.write(text);
            output += text;
        });

        child.on("close", (code) => resolve({ code: code ?? 1, output, rawOutput }));
    });
}

async function runSequentialMode() {
    const startIteration = state?.status === "running" ? state.iteration : 1;
    const loopStartTime = Date.now();

    if (startIteration > 1) {
        log.banner(`🔁 Resuming Ralph — iteration ${startIteration} of ${maxIterations}`);
        log.info(`Previous run interrupted at ${state?.updatedAt ?? "unknown"}`);
        log.info(`Original start: ${state?.startedAt ?? "unknown"}`);
    } else {
        log.banner(`🤖 Ralph Loop — ${maxIterations} iterations max (sequential)`);
        log.info(`Prompt: ${PROMPT_FILE}`);
        log.info(`State:  ${STATE_FILE}`);
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

function discoverSlices() {
    return new Promise((resolve) => {
        const prompt = `Read proophboard via MCP. Read .proophboard/workspace.json to get workspace_id, then call mcp__proophboard__list_chapters, then for each chapter call mcp__proophboard__get_chapter.

Output ONLY a JSON array of planned slices (status = "planned") wrapped in <slices>...</slices> tags. Format:
[{"id":"slice-id","label":"Slice Label","chapter":"Chapter Name","type":"write|read|automation","priority":1}]

Priority rules:
- Write slices that unblock other slices (automations, read models) rank highest (lowest priority number)
- Write slices that produce events consumed by many downstream slices rank higher
- Automations and read slices that depend on already-implemented events rank next
- Within equal priority, prefer lower chapter+slice index

If no planned slices exist, output: <slices>[]</slices>`;

        const child = spawn("claude", [
            "--print",
            "--dangerously-skip-permissions",
            "--output-format", "stream-json",
            "--verbose",
        ], {
            cwd: repoRoot,
            stdio: ["pipe", "pipe", "pipe"],
        });

        let rawOutput = "";
        let output = "";

        child.stdin.write(prompt);
        child.stdin.end();

        child.stdout.on("data", (chunk) => {
            const raw = chunk.toString();
            rawOutput += raw;
            const text = extractTextFromStreamJson(raw);
            if (text) output += text;
        });

        child.stderr.on("data", () => { /* suppress */ });

        child.on("close", (code) => {
            if (code !== 0) {
                log.error(`Discovery Claude exited with code ${code}`);
                resolve([]);
                return;
            }

            const match = output.match(/<slices>([\s\S]*?)<\/slices>/);
            if (!match) {
                log.warn("Discovery returned no <slices> block");
                resolve([]);
                return;
            }

            try {
                const slices = JSON.parse(match[1].trim());
                resolve(slices);
            } catch (e) {
                log.error(`Failed to parse discovered slices: ${e.message}`);
                resolve([]);
            }
        });
    });
}

// ── Status Table ────────────────────────────────────────────

function printStatusTable(registry, loopStartTime, queueSize = 0) {
    const active = Object.keys(registry.activeSlices).length;
    const completed = registry.completedIterations;
    const elapsedStr = elapsed(loopStartTime);
    const totalTokens = registry.history.reduce((sum, h) => sum + (h.tokens?.total || 0), 0);
    const stalledCount = registry.history.filter(h => h.result === "stalled").length;

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

    // Create worktree
    try {
        createWorktree(worktreePath, parentBranch, branchName);
    } catch (e) {
        if (e.message?.includes("already exists")) {
            // Stale branch from a previous run — delete it and start fresh
            log.warn(`Branch "${branchName}" already exists — deleting stale branch and retrying`);
            removeWorktree(worktreePath);
            deleteBranch(branchName);
            try {
                createWorktree(worktreePath, parentBranch, branchName);
            } catch (e2) {
                log.error(`Failed to create worktree for "${slice.label}": ${e2.message}`);
                return null;
            }
        } else {
            log.error(`Failed to create worktree for "${slice.label}": ${e.message}`);
            return null;
        }
    }

    // Run Maven install to compile dependencies in the fresh worktree
    if (!runMvnInstall(absWorktreePath, slice.label)) {
        removeWorktree(worktreePath);
        deleteBranch(branchName);
        return null;
    }

    // Build prompt with assignment
    const basePrompt = readFileSync(PROMPT_FILE, "utf8");
    const lockedSlices = Object.entries(registry.activeSlices)
        .filter(([id]) => id !== slice.id)
        .map(([id, info]) => `- "${info.sliceLabel}" (${id}) — another worktree`)
        .join("\n");

    const assignment = `
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
- **Mode: ${finalizeMode}**
- **Parent branch**: ${parentBranch}
${finalizeMode === "pr" ? `- Create a PR via \`gh pr create\` targeting \`${parentBranch}\`.` : ""}
${finalizeMode === "merge" ? `- Rebase onto \`${parentBranch}\`, then fast-forward merge (\`git checkout ${parentBranch} && git merge --ff-only ${branchName}\`).` : ""}
${finalizeMode === "none" ? `- Leave changes on the feature branch. Do not merge or create a PR.` : ""}
- If rebase has conflicts, resolve them (em2code-slice conflict resolution rules).
- After successful finalization, output \`<promise>SLICE_DONE:${slice.id}</promise>\`.
- If blocked (cannot implement), output \`<promise>SLICE_BLOCKED:${slice.id}</promise>\`.
- Do NOT output \`<promise>COMPLETE</promise>\` — only the orchestrator determines that.
`;

    const fullPrompt = `${basePrompt}\n${assignment}`;

    // Spawn Claude
    const child = spawn("claude", [
        "--print",
        "--dangerously-skip-permissions",
        "--output-format", "stream-json",
        "--verbose",
    ], {
        cwd: absWorktreePath,
        stdio: ["pipe", "pipe", "pipe"],
    });

    child.stdin.write(fullPrompt);
    child.stdin.end();

    let output = "";
    let rawOutput = "";

    // Ensure log directory exists and truncate log file
    if (!existsSync(dirname(logFile))) mkdirSync(dirname(logFile), { recursive: true });
    writeFileSync(logFile, "");

    // Progress file inside the worktree — Claude output streamed here in real-time
    const progressFile = join(absWorktreePath, ".ai", "temp", "claude-output.md");
    const progressDir = dirname(progressFile);
    if (!existsSync(progressDir)) {
        mkdirSync(progressDir, {recursive: true});
    }
    writeFileSync(progressFile, `# Claude Output — ${slice.label}\n\nStarted: ${now()}\n\n---\n\n`);

    const appendToFiles = (text) => {
        try {
            appendFileSync(logFile, text);
            appendFileSync(progressFile, text);
        } catch { /* ignore */
        }
    };

    child.stdout.on("data", (chunk) => {
        const raw = chunk.toString();
        rawOutput += raw;
        const text = extractTextFromStreamJson(raw);
        if (text) {
            output += text;
            appendToFiles(text);
            if (streamOutput) {
                const prefixed = text.split("\n").map(l => l ? `  [${kebab}] ${l}` : "").join("\n");
                process.stdout.write(prefixed);
            }
        }
    });

    child.stderr.on("data", (chunk) => {
        const text = chunk.toString();
        output += text;
        appendToFiles(text);
    });

    const promise = new Promise((resolve) => {
        child.on("close", (code) => {
            resolve({
                code: code ?? 1,
                output,
                rawOutput,
                slice,
                worktreePath,
                branchName,
                kebab,
            });
        });
    });

    return {
        child,
        promise,
        slice,
        worktreePath,
        branchName,
        kebab,
        pid: child.pid,
    };
}

// ── Crash Recovery (parallel mode) ──────────────────────────

function recoverFromCrash(registry) {
    const recovered = [];
    const toRemove = [];

    for (const [id, info] of Object.entries(registry.activeSlices)) {
        if (isProcessAlive(info.pid)) {
            log.info(`Slice "${info.sliceLabel}" (PID ${info.pid}) is still running — leaving it`);
            continue;
        }

        log.warn(`Slice "${info.sliceLabel}" (PID ${info.pid}) — process dead`);
        const absPath = resolve(repoRoot, info.worktreePath);

        if (existsSync(absPath)) {
            // Worktree exists — re-queue for re-spawn
            log.info(`  Worktree exists — will re-spawn Claude in it`);
            removeWorktree(info.worktreePath);
            deleteBranch(info.branch);
            recovered.push({
                id,
                label: info.sliceLabel,
                chapter: info.chapter || "unknown",
                type: info.type || "write",
                priority: 0, // high priority for recovery
            });
        } else {
            log.info(`  No worktree — will re-queue slice`);
            recovered.push({
                id,
                label: info.sliceLabel,
                chapter: info.chapter || "unknown",
                type: info.type || "write",
                priority: 0,
            });
        }
        toRemove.push(id);
    }

    for (const id of toRemove) {
        delete registry.activeSlices[id];
    }

    return recovered;
}

// ── Main Parallel Loop ──────────────────────────────────────

async function runParallelMode() {
    const loopStartTime = Date.now();
    const parentBranch = getParentBranch();
    const sessionId = `ralph-${new Date().toISOString().replace(/[:.]/g, "-").replace("T", "T").slice(0, 19)}`;

    log.banner(`🤖 Ralph Loop — ${maxIterations} iterations max | ${maxWorktrees} parallel worktrees`);
    log.info(`Finalize: ${finalizeMode} | Discover: ${discoverMode} | Stream: ${streamOutput}`);
    log.info(`Parent branch: ${parentBranch}`);
    log.info(`Time: ${now()}`);

    // Load or create registry
    let registry = loadRegistry();
    let recoveredSlices = [];

    if (registry && Object.keys(registry.activeSlices).length > 0) {
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
            history: [],
        };
        saveRegistry(registry);
    }

    // Discovery phase
    log.start("Discovering planned slices from proophboard...");
    let queue = await discoverSlices();

    // Add recovered slices to front of queue
    if (recoveredSlices.length > 0) {
        queue = [...recoveredSlices, ...queue.filter(s => !recoveredSlices.find(r => r.id === s.id))];
        log.info(`Recovered ${recoveredSlices.length} slices from crash`);
    }

    // Filter out already-completed slices
    const completedIds = new Set(registry.history.map(h => h.sliceId));
    queue = queue.filter(s => !completedIds.has(s.id));

    log.info(`Found ${queue.length} planned slices:`);
    for (const s of queue) {
        log.info(`  → "${s.label}" (${s.type}) [${s.chapter}] id=${s.id}`);
    }

    if (queue.length === 0) {
        log.complete("No planned slices found — nothing to do!");
        clearRegistry();
        process.exit(0);
    }

    // Active workers map: sliceId → { child, promise, ... }
    const activeWorkers = new Map();

    // Heartbeat interval
    const heartbeatInterval = setInterval(() => {
        if (activeWorkers.size > 0) {
            printStatusTable(registry, loopStartTime, queue.length);
        }
    }, 60000);

    try {
        while (registry.completedIterations < maxIterations && (queue.length > 0 || activeWorkers.size > 0)) {

            // Re-discover if mode=every, a slot is free, and queue is empty
            if (discoverMode === "every" && activeWorkers.size < maxWorktrees && queue.length === 0) {
                log.info("Re-discovering slices from proophboard...");
                const newSlices = await discoverSlices();
                const activeIds = new Set(Object.keys(registry.activeSlices));
                const freshSlices = newSlices.filter(s => !completedIds.has(s.id) && !activeIds.has(s.id));
                if (freshSlices.length > 0) {
                    queue.push(...freshSlices);
                    log.info(`Discovered ${freshSlices.length} new planned slices:`);
                    for (const s of freshSlices) {
                        log.info(`  → "${s.label}" (${s.type}) [${s.chapter}] id=${s.id}`);
                    }
                } else {
                    log.info("No new planned slices found");
                }
            }

            // Spawn workers up to maxWorktrees
            while (activeWorkers.size < maxWorktrees && queue.length > 0 && registry.completedIterations + activeWorkers.size < maxIterations) {
                const slice = queue.shift();

                // Skip if already active
                if (registry.activeSlices[slice.id]) {
                    log.skip(`"${slice.label}" already active — skipping`);
                    continue;
                }

                log.start(`Spawning worktree for "${slice.label}" (${slice.type})`);

                const worker = spawnWorker(slice, registry, parentBranch);
                if (!worker) {
                    log.error(`Failed to spawn worker for "${slice.label}" — skipping`);
                    continue;
                }

                // Register in activeSlices (THIS IS THE LOCK)
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
                // No workers and no queue — check if we should rediscover or exit
                if (discoverMode === "every") {
                    log.info("All workers done, queue empty — re-discovering...");
                    const newSlices = await discoverSlices();
                    const activeIds = new Set(Object.keys(registry.activeSlices));
                    const freshSlices = newSlices.filter(s => !completedIds.has(s.id) && !activeIds.has(s.id));
                    if (freshSlices.length > 0) {
                        queue.push(...freshSlices);
                        continue;
                    }
                }
                break; // Nothing left to do
            }

            // Wait for ANY worker to finish
            const racePromises = [...activeWorkers.entries()].map(([id, w]) =>
                w.promise.then(result => ({ id, ...result }))
            );
            const result = await Promise.race(racePromises);

            const { id: sliceId, code, output, rawOutput, slice, worktreePath, branchName, kebab } = result;
            const tokens = extractTokensFromStreamJson(rawOutput);
            const duration = elapsed(new Date(registry.activeSlices[sliceId]?.startedAt || Date.now()).getTime());

            // Check for stall (AskUserQuestion detected)
            const askQuestion = detectAskUserQuestion(rawOutput);
            if (askQuestion) {
                log.stall(`"${slice.label}" STALLED — Claude asked: "${askQuestion}"`);
                registry.activeSlices[sliceId].status = "stalled";

                // Kill and clean up
                const worker = activeWorkers.get(sliceId);
                if (worker?.child && !worker.child.killed) {
                    worker.child.kill("SIGTERM");
                }
                activeWorkers.delete(sliceId);
                delete registry.activeSlices[sliceId];
                removeWorktree(worktreePath);
                deleteBranch(branchName);

                registry.history.push({
                    sliceId,
                    label: slice.label,
                    branch: branchName,
                    result: "stalled",
                    reason: askQuestion,
                    duration,
                    tokens,
                });
                registry.completedIterations++;
                saveRegistry(registry);
                printStatusTable(registry, loopStartTime, queue.length);
                continue;
            }

            if (code === 0) {
                // Check signals
                if (output.includes(`<promise>SLICE_DONE:${sliceId}</promise>`)) {
                    log.done(`"${slice.label}" completed! (${duration})`);

                    // Extract PR number if present
                    let prNumber = null;
                    const prMatch = output.match(/pull\/(\d+)/);
                    if (prMatch) prNumber = parseInt(prMatch[1], 10);

                    registry.history.push({
                        sliceId,
                        label: slice.label,
                        branch: branchName,
                        result: "completed",
                        prNumber,
                        duration,
                        tokens,
                    });

                    completedIds.add(sliceId);
                    activeWorkers.delete(sliceId);
                    delete registry.activeSlices[sliceId];
                    registry.completedIterations++;

                    // Cleanup worktree (unless finalize=none)
                    if (finalizeMode !== "none") {
                        removeWorktree(worktreePath);
                        if (finalizeMode === "merge") deleteBranch(branchName);
                    }

                } else if (output.includes(`<promise>SLICE_BLOCKED:${sliceId}</promise>`)) {
                    log.warn(`"${slice.label}" is BLOCKED`);

                    registry.history.push({
                        sliceId,
                        label: slice.label,
                        branch: branchName,
                        result: "blocked",
                        duration,
                        tokens,
                    });

                    activeWorkers.delete(sliceId);
                    delete registry.activeSlices[sliceId];
                    registry.completedIterations++;
                    removeWorktree(worktreePath);
                    deleteBranch(branchName);

                } else {
                    // Completed without signal — treat as done (best effort)
                    log.warn(`"${slice.label}" finished without SLICE_DONE signal — treating as completed`);

                    registry.history.push({
                        sliceId,
                        label: slice.label,
                        branch: branchName,
                        result: "completed",
                        duration,
                        tokens,
                    });

                    completedIds.add(sliceId);
                    activeWorkers.delete(sliceId);
                    delete registry.activeSlices[sliceId];
                    registry.completedIterations++;

                    if (finalizeMode !== "none") {
                        removeWorktree(worktreePath);
                    }
                }
            } else {
                // Non-zero exit
                if (output.includes("No messages returned")) {
                    log.skip(`"${slice.label}" — no messages returned, re-queuing`);
                    activeWorkers.delete(sliceId);
                    delete registry.activeSlices[sliceId];
                    removeWorktree(worktreePath);
                    deleteBranch(branchName);
                    queue.unshift(slice); // re-queue at front
                } else {
                    log.error(`"${slice.label}" exited with code ${code} — waiting 5min then re-queuing`);
                    activeWorkers.delete(sliceId);
                    delete registry.activeSlices[sliceId];
                    removeWorktree(worktreePath);
                    deleteBranch(branchName);
                    await sleep(5 * 60 * 1000);
                    queue.unshift(slice); // re-queue at front
                }
            }

            saveRegistry(registry);
            printStatusTable(registry, loopStartTime, queue.length);
        }
    } finally {
        clearInterval(heartbeatInterval);
    }

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

    // Cleanup
    clearRegistry();
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
