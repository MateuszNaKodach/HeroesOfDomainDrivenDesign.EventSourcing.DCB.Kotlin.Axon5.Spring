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

// ── Markdown + image extraction per item ──────────────────────────────────────

const MARKDOWN_FIELDS = ['description', 'details'];

// For a single element or slice: extracts non-empty markdown fields into
// {itemDir}/description.md and {itemDir}/details.md, downloads referenced images
// into {itemDir}/images/, rewrites storage: refs to ./images/... in the .md files,
// and returns the updated item object with field values replaced by relative paths
// (relative to chapterDir, e.g. "./elements/{id}/description.md").
async function extractItem(item, itemDir, relBase) {
  const updated = { ...item };
  let hasContent = false;

  for (const field of MARKDOWN_FIELDS) {
    if (typeof item[field] === 'string' && item[field].trim()) {
      hasContent = true;
      const mdContent = item[field].replace(STORAGE_REF_RE, (_, p) => `./images/${p}`);
      await ensureDir(itemDir);
      await fs.writeFile(path.join(itemDir, `${field}.md`), mdContent, 'utf8');
      updated[field] = `./${relBase}/${item.id}/${field}.md`;
    }
  }

  if (hasContent) {
    // Download images referenced in this item's markdown fields
    const storageRefs = new Set();
    for (const field of MARKDOWN_FIELDS) {
      if (typeof item[field] === 'string') collectStorageRefs(item[field], storageRefs);
    }
    if (storageRefs.size > 0) {
      const imagesDir = path.join(itemDir, 'images');
      await Promise.all(
        [...storageRefs].map(async (storagePath) => {
          try {
            const buf = await apiBuffer(`/storage/images/${storagePath}`);
            await writeBuffer(path.join(imagesDir, storagePath), buf);
          } catch (err) {
            console.error(`  ✗ Image ${storagePath}: ${err.message}`);
          }
        })
      );
    }
  }

  return updated;
}

// Extracts markdown + images for all elements and slices in a chapter.
// Returns updated chapter data with field values replaced by relative file refs.
async function extractChapterDocs(data, chapterDir) {
  const updatedElements = await Promise.all(
    (data.elements ?? []).map((el) =>
      extractItem(el, path.join(chapterDir, 'elements', el.id), 'elements')
    )
  );

  const updatedSlices = await Promise.all(
    (data.slices ?? []).map((sl) =>
      extractItem(sl, path.join(chapterDir, 'slices', sl.id), 'slices')
    )
  );

  return { data: { ...data, elements: updatedElements, slices: updatedSlices } };
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

  const chapterResults = await Promise.all(
    chapters.map(async (ch) => {
      const slug = toSlug(ch.name);
      const chapterDir = path.join(CHAPTERS_DIR, slug);

      try {
        const raw = await apiJson(`/chapters/${ch.id}`);

        // Extract markdown fields (description/details) into elements/{id}/ and slices/{id}/,
        // download per-item images, rewrite storage: refs to ./images/... in .md files.
        const { data } = await extractChapterDocs(raw, chapterDir);

        await writeJson(path.join(chapterDir, 'index.json'), data);
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
  });

  // 4. Summary
  console.log('');
  console.log('Backup complete!');
  console.log(`  Workspace : ${workspace_name}`);
  console.log(`  Chapters  : ${successCount}/${chapters.length}`);
  chapterResults.forEach((r) => console.log(`    ${r.ok ? '✓' : '✗'} ${r.slug}`));
  console.log(`  Timestamp : ${backedUpAt}`);
  console.log(`  Output    : ${BACKUP_DIR}`);
}

main().catch((err) => {
  console.error('Fatal error:', err.message);
  process.exit(1);
});
