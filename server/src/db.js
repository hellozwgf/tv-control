import Database from 'better-sqlite3';
import { existsSync, mkdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const DATA_DIR = join(__dirname, '..', 'data');

if (!existsSync(DATA_DIR)) mkdirSync(DATA_DIR, { recursive: true });

const db = new Database(join(DATA_DIR, 'tv-control.db'));
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

export function initDB() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS devices (
      id TEXT PRIMARY KEY,
      type TEXT NOT NULL CHECK(type IN ('phone', 'tv')),
      name TEXT DEFAULT '',
      auth_token TEXT NOT NULL UNIQUE,
      paired_with TEXT,
      created_at TEXT DEFAULT (datetime('now')),
      FOREIGN KEY (paired_with) REFERENCES devices(id)
    );
    CREATE TABLE IF NOT EXISTS states (
      device_id TEXT PRIMARY KEY,
      state TEXT NOT NULL DEFAULT 'open' CHECK(state IN ('open', 'closed')),
      updated_at TEXT DEFAULT (datetime('now')),
      FOREIGN KEY (device_id) REFERENCES devices(id)
    );
    CREATE TABLE IF NOT EXISTS unlocks (
      device_id TEXT PRIMARY KEY,
      expires_at TEXT NOT NULL,
      created_at TEXT DEFAULT (datetime('now')),
      FOREIGN KEY (device_id) REFERENCES devices(id)
    );
    CREATE INDEX IF NOT EXISTS idx_devices_token ON devices(auth_token);
    CREATE INDEX IF NOT EXISTS idx_devices_paired ON devices(paired_with);
  `);
  console.log('Database initialized');
}

if (process.argv.includes('--init')) {
  initDB();
  process.exit(0);
}

export default db;
