-- Trackr demo dataset — screenshot/dev fixture.
--
-- Builds a complete, Room-openable trackr.db from scratch with `sqlite3`.
-- See SKILL.md for how to build and load it onto an emulator.
--
-- SCHEMA IS COPIED FROM Room's exported schema:
--   app/schemas/net.clahey.trackr.data.local.TrackrDatabase/3.json
-- If you bump the DB version, re-copy the CREATE statements, the
-- `PRAGMA user_version`, and the room_master_table identity hash below from the
-- new schema JSON. Room verifies both the user_version and the identity hash on
-- open and will fail loudly if either is stale — so drift can't slip through.

PRAGMA foreign_keys = OFF;

-- Idempotent: rebuild cleanly even if run against an existing file.
DROP TABLE IF EXISTS events;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS room_master_table;
DROP TABLE IF EXISTS android_metadata;

-- Room checks the SQLite user_version matches @Database(version = 3).
PRAGMA user_version = 3;

-- Platform metadata table normally created by the Android SQLite framework.
CREATE TABLE android_metadata (locale TEXT);
INSERT INTO android_metadata VALUES ('en_US');

-- Room identity check (id 42 + hash from the exported schema JSON, version 3).
CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);
INSERT OR REPLACE INTO room_master_table (id, identity_hash)
  VALUES (42, '2b2ff98272a06e56d196b0542221d015');

-- ---------------------------------------------------------------------------
-- Schema (verbatim from 3.json, ${TABLE_NAME} expanded to real names)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `categories` (
  `id` TEXT NOT NULL, `name` TEXT NOT NULL, `emoji` TEXT, `color` INTEGER,
  `valueType` TEXT, `defaultValue` TEXT, `allowEmptyText` INTEGER NOT NULL,
  `sortOrder` INTEGER NOT NULL, `parentId` TEXT, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS `index_categories_sortOrder` ON `categories` (`sortOrder`);
CREATE INDEX IF NOT EXISTS `index_categories_parentId` ON `categories` (`parentId`);

CREATE TABLE IF NOT EXISTS `events` (
  `id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL,
  `value` TEXT, `notes` TEXT, `imagePaths` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
  PRIMARY KEY(`id`),
  FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE);
CREATE INDEX IF NOT EXISTS `index_events_categoryId` ON `events` (`categoryId`);
CREATE INDEX IF NOT EXISTS `index_events_timestamp` ON `events` (`timestamp`);
CREATE INDEX IF NOT EXISTS `index_events_createdAt` ON `events` (`createdAt`);

-- ---------------------------------------------------------------------------
-- Categories — one per value type, so screenshots show the full range.
-- color is an ARGB Long (SQLite accepts 0x hex literals); palette from
-- ui/theme/CategoryColors.kt. valueType strings from ValueTypeConverter
-- (Occurrence == "none"). allowEmptyText 0/1. defaultValue NULL.
-- ---------------------------------------------------------------------------
INSERT INTO categories (id, name, emoji, color, valueType, defaultValue, allowEmptyText, sortOrder, parentId) VALUES
  ('cat-water',      'Water',      '💧', 0xFF1E88E5, 'number',   NULL, 0, 0, NULL),
  ('cat-mood',       'Mood',       '😊', 0xFFFFB300, 'scale',    NULL, 0, 1, NULL),
  ('cat-coffee',     'Coffee',     '☕', 0xFF6D4C41, 'none',     NULL, 0, 2, NULL),
  ('cat-workout',    'Workout',    '🏋️', 0xFF43A047, 'exercise', NULL, 0, 3, NULL),
  ('cat-meditation', 'Meditation', '🧘', 0xFF00897B, 'duration', NULL, 0, 4, NULL),
  ('cat-vitamins',   'Vitamins',   '💊', 0xFFE53935, 'boolean',  NULL, 0, 5, NULL),
  ('cat-journal',    'Journal',    '📝', 0xFF8E24AA, 'text',     NULL, 0, 6, NULL);

-- ---------------------------------------------------------------------------
-- Events — a fresh-looking timeline across today / yesterday / earlier.
-- timestamps are relative to build time so screenshots always read as recent.
-- `value` is kotlinx JSON with discriminator "type" (EventValueConverter);
-- Duration is whole seconds; imagePaths is a JSON array (empty here).
-- ---------------------------------------------------------------------------

-- Today
INSERT INTO events (id, categoryId, timestamp, value, notes, imagePaths, createdAt) VALUES
  ('evt-coffee-1', 'cat-coffee', (strftime('%s','now','-1 hours')*1000), NULL, NULL, '[]', (strftime('%s','now','-1 hours')*1000)),
  ('evt-water-1',  'cat-water',  (strftime('%s','now','-2 hours')*1000), '{"type":"NumberValue","value":2.0,"unit":"glasses"}', NULL, '[]', (strftime('%s','now','-2 hours')*1000)),
  ('evt-mood-1',   'cat-mood',   (strftime('%s','now','-3 hours')*1000), '{"type":"Scale","value":8}', 'Productive morning', '[]', (strftime('%s','now','-3 hours')*1000)),
  ('evt-med-1',    'cat-meditation', (strftime('%s','now','-4 hours')*1000), '{"type":"DurationValue","duration":600}', NULL, '[]', (strftime('%s','now','-4 hours')*1000)),
  ('evt-vit-1',    'cat-vitamins', (strftime('%s','now','-5 hours')*1000), '{"type":"BooleanValue","value":true}', NULL, '[]', (strftime('%s','now','-5 hours')*1000));

-- Yesterday
INSERT INTO events (id, categoryId, timestamp, value, notes, imagePaths, createdAt) VALUES
  ('evt-workout-1', 'cat-workout', (strftime('%s','now','-1 days','-1 hours')*1000), '{"type":"ExerciseValue","sets":4,"reps":12}', 'Upper body', '[]', (strftime('%s','now','-1 days','-1 hours')*1000)),
  ('evt-coffee-2',  'cat-coffee',  (strftime('%s','now','-1 days','-3 hours')*1000), NULL, NULL, '[]', (strftime('%s','now','-1 days','-3 hours')*1000)),
  ('evt-water-2',   'cat-water',   (strftime('%s','now','-1 days','-5 hours')*1000), '{"type":"NumberValue","value":3.0,"unit":"glasses"}', NULL, '[]', (strftime('%s','now','-1 days','-5 hours')*1000)),
  ('evt-journal-1', 'cat-journal', (strftime('%s','now','-1 days','-7 hours')*1000), '{"type":"TextValue","text":"Shipped the first release build to Play. Big milestone."}', NULL, '[]', (strftime('%s','now','-1 days','-7 hours')*1000)),
  ('evt-mood-2',    'cat-mood',    (strftime('%s','now','-1 days','-9 hours')*1000), '{"type":"Scale","value":6}', 'A bit tired', '[]', (strftime('%s','now','-1 days','-9 hours')*1000));

-- Two days ago
INSERT INTO events (id, categoryId, timestamp, value, notes, imagePaths, createdAt) VALUES
  ('evt-coffee-3', 'cat-coffee',     (strftime('%s','now','-2 days','-2 hours')*1000), NULL, NULL, '[]', (strftime('%s','now','-2 days','-2 hours')*1000)),
  ('evt-med-2',    'cat-meditation', (strftime('%s','now','-2 days','-5 hours')*1000), '{"type":"DurationValue","duration":900}', NULL, '[]', (strftime('%s','now','-2 days','-5 hours')*1000)),
  ('evt-vit-2',    'cat-vitamins',   (strftime('%s','now','-2 days','-6 hours')*1000), '{"type":"BooleanValue","value":true}', NULL, '[]', (strftime('%s','now','-2 days','-6 hours')*1000));

-- Three days ago
INSERT INTO events (id, categoryId, timestamp, value, notes, imagePaths, createdAt) VALUES
  ('evt-workout-2', 'cat-workout', (strftime('%s','now','-3 days','-2 hours')*1000), '{"type":"ExerciseValue","sets":3,"reps":15}', 'Leg day', '[]', (strftime('%s','now','-3 days','-2 hours')*1000)),
  ('evt-water-3',   'cat-water',   (strftime('%s','now','-3 days','-4 hours')*1000), '{"type":"NumberValue","value":2.0,"unit":"glasses"}', NULL, '[]', (strftime('%s','now','-3 days','-4 hours')*1000));
