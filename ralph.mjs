#!/usr/bin/env node

// Ralph Loop - Autonomous AI agent orchestrator
//
// Usage:
//   node ralph.mjs                          # 10 iterations (default)
//   node ralph.mjs --iterations 5           # 5 iterations
//
// Spawns Claude Code CLI in a loop, piping prompt.md to stdin each iteration.
// Output is streamed in real-time via --output-format stream-json.
// Checks output for signal strings to decide: stop / continue / wait.

import {spawn} from "node:child_process";
import {readFileSync} from "node:fs";
import {dirname, join} from "node:path";
import {fileURLToPath} from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));

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

const now = () => new Date().toISOString().replace("T", " ").replace(/\.\d+Z$/, "");
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

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

function runClaude() {
    return new Promise((resolve) => {
        const prompt = readFileSync(PROMPT_FILE, "utf8");
        const child = spawn("claude", [
            "--print",
            "--dangerously-skip-permissions",
            "--output-format", "stream-json",
        ], {
            cwd: scriptDir,
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

console.log(`Starting Ralph - Max iterations: ${maxIterations}`);

for (let i = 1; i <= maxIterations; i++) {
    console.log();
    console.log("=".repeat(60));
    console.log(`  Ralph Iteration ${i} of ${maxIterations}`);
    console.log("=".repeat(60));
    console.log();
    console.log(`>>> Running Claude at ${now()}`);
    // ── Run Claude safely ────────────────────────────────────
    let claudeSkip = false;

    while (true) {
        const {code, output} = await runClaude();

        if (code === 0) {
            // ── Check signals ────────────────────────────────
            if (output.includes("<promise>COMPLETE</promise>")) {
                console.log();
                console.log("Ralph completed all tasks!");
                console.log(`Completed at iteration ${i} of ${maxIterations}`);
                process.exit(0);
            }

            if (output.includes("<promise>NO_TASKS</promise>")) {
                console.log();
                console.log("No tasks available. Waiting 30 seconds...");
                await sleep(30 * 1000);
            }

            break;
        }

        // Non-zero exit
        if (output.includes("No messages returned")) {
            console.log();
            console.log("Warning: Claude returned no messages (transient). Skipping iteration...");
            claudeSkip = true;
            break;
        }

        console.log();
        console.log("Warning: Claude exited with an error. Possibly spending limit reached.");
        console.log("Waiting 5 minutes before retry...");
        await sleep(5 * 60 * 1000);
    }

    if (claudeSkip) {
        continue;
    }

    console.log();
    console.log(`Iteration ${i} complete. Continuing...`);
    await sleep(2000);
}

console.log();
console.log(`Warning: Ralph reached max iterations (${maxIterations}) without completing all tasks.`);
process.exit(1);
