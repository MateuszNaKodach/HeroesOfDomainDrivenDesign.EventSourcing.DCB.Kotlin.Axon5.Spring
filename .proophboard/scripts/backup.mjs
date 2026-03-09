#!/usr/bin/env node
// proophboard backup script
// Usage: node .proophboard/scripts/backup.mjs
// API key is read from .proophboard/.env.local or .proophboard/.env (PROOPHBOARD_API_KEY=...)
// or from the environment variable PROOPHBOARD_API_KEY directly.

import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROOPHBOARD_DIR = path.resolve(__dirname, '..');

// ── Load .env files ───────────────────────────────────────────────────────────

async function loadDotEnv(filePath) {
  try {
    const content = await fs.readFile(filePath, 'utf8');
    for (const line of content.split('\n')) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const eq = trimmed.indexOf('=');
      if (eq === -1) continue;
      const key = trimmed.slice(0, eq).trim();
      const val = trimmed.slice(eq + 1).trim().replace(/^["']|["']$/g, '');
      if (!(key in process.env)) process.env[key] = val;
    }
  } catch {
    // file doesn't exist — that's fine
  }
}

// .env.local takes precedence over .env (load .env first so .env.local can override)
await loadDotEnv(path.join(PROOPHBOARD_DIR, '.env'));
await loadDotEnv(path.join(PROOPHBOARD_DIR, '.env.local'));

// ── Config ────────────────────────────────────────────────────────────────────

const API_KEY = process.env.PROOPHBOARD_API_KEY;
if (!API_KEY) {
  console.error(
    'Error: PROOPHBOARD_API_KEY is not set.\n' +
    'Add it to .proophboard/.env.local or pass it as an environment variable.'
  );
  process.exit(1);
}

// API key is workspace-scoped — workspace_id is injected automatically by the server.
// Paths follow openapi.json: /chapters, /chapters/{chapter_id}, /storage/images/{storagePath}
const BASE_URL = 'https://flow.prooph-board.com/api';
const BACKUP_DIR = path.join(PROOPHBOARD_DIR, 'backup');
const CHAPTERS_DIR = path.join(BACKUP_DIR, 'chapters');

// ── Slug helper ───────────────────────────────────────────────────────────────

function toSlug(name) {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
}

// ── File system helpers ───────────────────────────────────────────────────────

async function ensureDir(dir) {
  await fs.mkdir(dir, { recursive: true });
}

async function writeJson(filePath, data) {
  await ensureDir(path.dirname(filePath));
  await fs.writeFile(filePath, JSON.stringify(data, null, 2), 'utf8');
}

async function writeBuffer(filePath, buf) {
  await ensureDir(path.dirname(filePath));
  await fs.writeFile(filePath, buf);
}

// ── API client ────────────────────────────────────────────────────────────────

async function apiFetch(urlPath, options = {}) {
  const url = `${BASE_URL}${urlPath}`;
  const res = await fetch(url, {
    ...options,
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      ...(options.headers ?? {}),
    },
  });

  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(`HTTP ${res.status} ${res.statusText} — ${url}\n${body}`);
  }

  return res;
}

async function apiJson(urlPath) {
  const res = await apiFetch(urlPath);
  return res.json();
}

async function apiBuffer(urlPath) {
  const res = await apiFetch(urlPath);
  const arrayBuf = await res.arrayBuffer();
  return Buffer.from(arrayBuf);
}

// ── Image ref extraction & rewriting ─────────────────────────────────────────

const STORAGE_REF_RE = /storage:([^\s)"']+)/g;

function collectStorageRefs(value, refs = new Set()) {
  if (typeof value === 'string') {
    for (const [, storagePath] of value.matchAll(STORAGE_REF_RE)) {
      refs.add(storagePath);
    }
  } else if (Array.isArray(value)) {
    for (const item of value) collectStorageRefs(item, refs);
  } else if (value !== null && typeof value === 'object') {
    for (const v of Object.values(value)) collectStorageRefs(v, refs);
  }
  return refs;
}

// Rewrites storage: refs to ./images/... (relative to the chapter's own directory)
function rewriteStorageRefs(value) {
  if (typeof value === 'string') {
    return value.replace(STORAGE_REF_RE, (_, p) => `./images/${p}`);
  }
  if (Array.isArray(value)) {
    return value.map(rewriteStorageRefs);
  }
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([k, v]) => [k, rewriteStorageRefs(v)])
    );
  }
  return value;
}

// ── Main ──────────────────────────────────────────────────────────────────────

async function main() {
  // Read workspace config (for name/id in backup metadata — id not needed for API calls)
  const workspaceJson = JSON.parse(
    await fs.readFile(path.join(PROOPHBOARD_DIR, 'workspace.json'), 'utf8')
  );
  const { workspace_id, workspace_name } = workspaceJson;

  await ensureDir(CHAPTERS_DIR);

  // 1. List chapters — GET /chapters
  console.log(`Fetching chapter list for workspace "${workspace_name}"…`);
  const chapters = await apiJson('/chapters');
  await writeJson(path.join(CHAPTERS_DIR, 'index.json'), chapters);

  // 2. Download each chapter in parallel — GET /chapters/{chapter_id}
  // Each chapter goes into chapters/{slug}/ with its own images/ subdirectory.
  console.log(`Downloading ${chapters.length} chapter(s)…`);
  let totalImagesCount = 0;

  const chapterResults = await Promise.all(
    chapters.map(async (ch) => {
      const slug = toSlug(ch.name);
      const chapterDir = path.join(CHAPTERS_DIR, slug);
      const imagesDir = path.join(chapterDir, 'images');

      try {
        const data = await apiJson(`/chapters/${ch.id}`);

        // Collect and download images for this chapter
        const storageRefs = collectStorageRefs(data);
        if (storageRefs.size > 0) {
          await Promise.all(
            [...storageRefs].map(async (storagePath) => {
              try {
                const buf = await apiBuffer(`/storage/images/${storagePath}`);
                await writeBuffer(path.join(imagesDir, storagePath), buf);
                totalImagesCount++;
              } catch (err) {
                console.error(`  ✗ Image ${storagePath} (${ch.name}): ${err.message}`);
              }
            })
          );
        }

        // Rewrite storage: refs to ./images/... and save
        await writeJson(path.join(chapterDir, 'index.json'), rewriteStorageRefs(data));
        return { ok: true, id: ch.id, slug, name: ch.name };
      } catch (err) {
        console.error(`  ✗ Chapter "${ch.name}" (${slug}): ${err.message}`);
        return { ok: false, id: ch.id, slug, name: ch.name };
      }
    })
  );

  // 3. Write backup metadata
  const backedUpAt = new Date().toISOString();
  const successCount = chapterResults.filter((r) => r.ok).length;
  await writeJson(path.join(BACKUP_DIR, 'index.json'), {
    backed_up_at: backedUpAt,
    workspace_id,
    workspace_name,
    chapters_count: successCount,
    images_count: totalImagesCount,
  });

  // 4. Summary
  console.log('');
  console.log('Backup complete!');
  console.log(`  Workspace : ${workspace_name}`);
  console.log(`  Chapters  : ${successCount}/${chapters.length}`);
  chapterResults.forEach((r) => console.log(`    ${r.ok ? '✓' : '✗'} ${r.slug}`));
  console.log(`  Images    : ${totalImagesCount}`);
  console.log(`  Timestamp : ${backedUpAt}`);
  console.log(`  Output    : ${BACKUP_DIR}`);
}

main().catch((err) => {
  console.error('Fatal error:', err.message);
  process.exit(1);
});
