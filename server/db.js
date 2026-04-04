const Database = require('better-sqlite3');
const path = require('path');

const DB_PATH = path.join(__dirname, 'utm_crowdsource.db');

function initDatabase() {
  const db = new Database(DB_PATH);

  // WAL mode for better concurrent read performance
  db.pragma('journal_mode = WAL');

  // Task completion submissions — one row per unique (struct_id, x, y, plane)
  // hits tracks how many players confirmed this location
  db.exec(`
    CREATE TABLE IF NOT EXISTS task_completions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      task_name TEXT NOT NULL,
      struct_id INTEGER NOT NULL,
      x INTEGER NOT NULL,
      y INTEGER NOT NULL,
      plane INTEGER NOT NULL DEFAULT 0,
      hits INTEGER NOT NULL DEFAULT 1,
      first_seen TEXT NOT NULL DEFAULT (datetime('now')),
      last_seen TEXT NOT NULL DEFAULT (datetime('now'))
    )
  `);

  // Index for fast lookups by struct_id
  db.exec(`
    CREATE INDEX IF NOT EXISTS idx_task_completions_struct
    ON task_completions(struct_id)
  `);

  // Unique constraint: one entry per (struct_id, x, y, plane)
  // If same location submitted again, we increment hits instead
  db.exec(`
    CREATE UNIQUE INDEX IF NOT EXISTS idx_task_completions_unique
    ON task_completions(struct_id, x, y, plane)
  `);

  // IP submission tracking — track who submitted what
  // Not for blocking (yet), but for audit/rollback if bad actor found
  db.exec(`
    CREATE TABLE IF NOT EXISTS ip_submissions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      ip_hash TEXT NOT NULL,
      struct_id INTEGER NOT NULL,
      x INTEGER NOT NULL,
      y INTEGER NOT NULL,
      plane INTEGER NOT NULL DEFAULT 0,
      submitted_at TEXT NOT NULL DEFAULT (datetime('now'))
    )
  `);

  // Index for finding all submissions by an IP (for bulk revoke)
  db.exec(`
    CREATE INDEX IF NOT EXISTS idx_ip_submissions_hash
    ON ip_submissions(ip_hash)
  `);

  // IP blacklist — manually populated if a bad actor is found
  db.exec(`
    CREATE TABLE IF NOT EXISTS ip_blacklist (
      ip_hash TEXT PRIMARY KEY,
      reason TEXT,
      blacklisted_at TEXT NOT NULL DEFAULT (datetime('now'))
    )
  `);

  return db;
}

module.exports = { initDatabase };
