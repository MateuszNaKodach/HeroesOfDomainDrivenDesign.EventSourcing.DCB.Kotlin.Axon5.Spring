#!/usr/bin/env node

// Ralph Loop - Autonomous AI agent orchestrator
//
// Usage:
//   node ralph.mjs                      # single run (step 1 test)
//
// Spawns Claude Code CLI with prompt.md piped to stdin.
// Claude runs in the project directory with full permissions.

import {spawn} from "node:child_process";
import {readFileSync} from "node:fs";
import {dirname, join} from "node:path";
import {fileURLToPath} from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));

const PROMPT_FILE = join(scriptDir, "prompt.md");

function runClaude() {
    return new Promise((resolve) => {
        const prompt = readFileSync(PROMPT_FILE, "utf8");
        const child = spawn("claude", ["--dangerously-skip-permissions"], {
            cwd: scriptDir,
            stdio: ["pipe", "pipe", "pipe"],
        });

        let output = "";

        const onData = (chunk) => {
            const text = chunk.toString();
            process.stdout.write(text);
            output += text;
        };

        child.stdin.write(prompt);
        child.stdin.end();

        child.stdout.on("data", onData);
        child.stderr.on("data", onData);

        child.on("close", (code) => resolve({code: code ?? 1, output}));
    });
}

const {code} = await runClaude();
process.exit(code);
