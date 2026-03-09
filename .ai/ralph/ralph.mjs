#!/usr/bin/env node

// Ralph Loop - Autonomous AI agent orchestrator
//
// Usage (from repo root):
//   node .ai/ralph/ralph.mjs                          # 10 iterations (default)
//   node .ai/ralph/ralph.mjs --iterations 5           # 5 iterations
//
// Spawns Claude Code CLI in a loop from the repository root,
// piping .ai/ralph/prompt.md to stdin each iteration.
// Output is streamed in real-time via --output-format stream-json.
// Checks output for signal strings to decide: stop / continue / wait.

import {spawn} from "node:child_process";
import {existsSync, mkdirSync, readFileSync, unlinkSync, writeFileSync} from "node:fs";
import {dirname, join} from "node:path";
import {fileURLToPath} from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(scriptDir, "..", "..");

// ── CLI arg parsing ──────────────────────────────────────────

function parseFlag(flag, fallback) {
    const idx = process.argv.indexOf(flag);
    if (idx === -1) {
        return fallback;
    }
    return parseInt(process.argv[idx + 1], 10) || fallback;
}

const maxIterations = parseFlag("--iterations", 10);

const PROMPT_FILE = join(scriptDir, "prompt.md");
const STATE_FILE = join(repoRoot, ".ai", "temp", "ralph-state.json");

const now = () => new Date().toISOString().replace("T", " ").replace(/\.\d+Z$/, "");
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function elapsed(startTime) {
    const diff = Date.now() - startTime;
    const mins = Math.floor(diff / 60000);
    const secs = Math.floor((diff % 60000) / 1000);
    return mins > 0 ? `${mins}m ${secs}s` : `${secs}s`;
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
};

// ── State persistence (crash recovery) ──────────────────────

function saveState(iteration, status) {
    const dir = dirname(STATE_FILE);
    if (!existsSync(dir)) mkdirSync(dir, {recursive: true});
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

// ── Run Claude ───────────────────────────────────────────────

function extractTextFromStreamJson(chunk) {
    // stream-json emits newline-delimited JSON objects
    // We extract text content from assistant messages
    const lines = chunk.split("\n").filter(Boolean);
    let text = "";
    for (const line of lines) {
        try {
            const obj = JSON.parse(line);
            // assistant message content
            if (obj.type === "assistant" && obj.message?.content) {
                for (const block of obj.message.content) {
                    if (block.type === "text") {
                        text += block.text;
                    }
                }
            }
            // content_block_delta for streaming chunks
            if (obj.type === "content_block_delta" && obj.delta?.text) {
                text += obj.delta.text;
            }
            // result message at the end
            if (obj.type === "result" && obj.result) {
                text += obj.result;
            }
        } catch {
            // Not JSON — pass through raw
            text += line;
        }
    }
    return text;
}

function runClaude(iteration) {
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

        const onStdout = (chunk) => {
            const raw = chunk.toString();
            rawOutput += raw;
            const text = extractTextFromStreamJson(raw);
            if (text) {
                process.stdout.write(text);
                output += text;
            }
        };

        const onStderr = (chunk) => {
            const text = chunk.toString();
            process.stderr.write(text);
            output += text;
        };

        child.stdin.write(prompt);
        child.stdin.end();

        child.stdout.on("data", onStdout);
        child.stderr.on("data", onStderr);

        child.on("close", (code) => resolve({code: code ?? 1, output, rawOutput}));
    });
}

// ── Main loop ────────────────────────────────────────────────

const startIteration = state?.status === "running" ? state.iteration : 1;
const loopStartTime = Date.now();

if (startIteration > 1) {
    log.banner(`🔁 Resuming Ralph — iteration ${startIteration} of ${maxIterations}`);
    log.info(`Previous run interrupted at ${state?.updatedAt ?? "unknown"}`);
    log.info(`Original start: ${state?.startedAt ?? "unknown"}`);
} else {
    log.banner(`🤖 Ralph Loop — ${maxIterations} iterations max`);
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

    // ── Run Claude safely ────────────────────────────────────
    let claudeSkip = false;

    while (true) {
        const {code, output} = await runClaude(i);

        if (code === 0) {
            // ── Check signals ────────────────────────────────
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

        // Non-zero exit
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

    if (claudeSkip) {
        continue;
    }

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
