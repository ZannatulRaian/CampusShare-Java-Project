-- ============================================================
--  CampusShare — Complete Supabase Setup
--
--  Tables (6 total — clean):
--    users, subjects, resources, events, announcements, messages
--
--  messages stores BOTH channel messages AND direct messages:
--    type = 'channel'  →  channel field set,    recipient_id = NULL
--    type = 'dm'       →  recipient_id set,      channel = NULL
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ── users ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    user_id       SERIAL      PRIMARY KEY,
    user_uuid     UUID        UNIQUE DEFAULT NULL,
    full_name     TEXT        NOT NULL,
    email         TEXT        NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL DEFAULT '',
    role          TEXT        NOT NULL DEFAULT 'STUDENT',
    department    TEXT        NOT NULL DEFAULT '',
    semester      INT         NOT NULL DEFAULT 0,
    avatar_path   TEXT        NOT NULL DEFAULT '',
    last_seen     TEXT        NOT NULL DEFAULT '',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── subjects ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS subjects (
    subject_id   SERIAL PRIMARY KEY,
    name         TEXT   NOT NULL,
    code         TEXT   NOT NULL UNIQUE,
    department   TEXT   NOT NULL,
    credit_hours INT    NOT NULL DEFAULT 3
);

-- ── resources (notes / file uploads) ─────────────────────────
--  No rating columns — simplified
CREATE TABLE IF NOT EXISTS resources (
    resource_id  SERIAL      PRIMARY KEY,
    subject_id   INT         NOT NULL REFERENCES subjects(subject_id) ON DELETE CASCADE,
    uploaded_by  INT         NOT NULL REFERENCES users(user_id)       ON DELETE CASCADE,
    file_name    TEXT        NOT NULL,
    file_type    TEXT        NOT NULL DEFAULT 'PDF',
    file_size    TEXT        NOT NULL DEFAULT '-',
    file_path    TEXT        NOT NULL DEFAULT '',
    approved     BOOLEAN     NOT NULL DEFAULT FALSE,
    uploaded_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── events ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS events (
    event_id   SERIAL      PRIMARY KEY,
    title      TEXT        NOT NULL,
    event_date TEXT        NOT NULL,
    event_time TEXT        NOT NULL DEFAULT '00:00',
    category   TEXT        NOT NULL DEFAULT 'SEMINAR',
    location   TEXT        NOT NULL DEFAULT 'TBA',
    created_by INT         REFERENCES users(user_id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── announcements ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS announcements (
    ann_id     SERIAL      PRIMARY KEY,
    title      TEXT        NOT NULL,
    body       TEXT        NOT NULL,
    posted_by  INT         REFERENCES users(user_id) ON DELETE SET NULL,
    tag        TEXT        NOT NULL DEFAULT 'GENERAL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── messages (channel chat + direct messages combined) ────────
--  type = 'channel'  → channel is set, recipient_id is NULL
--  type = 'dm'       → recipient_id is set, channel is NULL
CREATE TABLE IF NOT EXISTS messages (
    message_id   SERIAL      PRIMARY KEY,
    type         TEXT        NOT NULL DEFAULT 'channel',
    channel      TEXT,
    sender_id    INT         REFERENCES users(user_id) ON DELETE SET NULL,
    recipient_id INT         REFERENCES users(user_id) ON DELETE SET NULL,
    content      TEXT        NOT NULL,
    sent_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Row Level Security ────────────────────────────────────────
--  Rule: any signed-in user can read and write everything.
--  Role restrictions (Admin/Faculty/Student) are in the Java app.

ALTER TABLE users         ENABLE ROW LEVEL SECURITY;
ALTER TABLE subjects      ENABLE ROW LEVEL SECURITY;
ALTER TABLE resources     ENABLE ROW LEVEL SECURITY;
ALTER TABLE events        ENABLE ROW LEVEL SECURITY;
ALTER TABLE announcements ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages      ENABLE ROW LEVEL SECURITY;

CREATE POLICY "users_all"         ON users         FOR ALL USING (auth.role() = 'authenticated') WITH CHECK (auth.role() = 'authenticated');
CREATE POLICY "subjects_all"      ON subjects      FOR ALL USING (auth.role() = 'authenticated') WITH CHECK (auth.role() = 'authenticated');
CREATE POLICY "resources_all"     ON resources     FOR ALL USING (auth.role() = 'authenticated') WITH CHECK (auth.role() = 'authenticated');
CREATE POLICY "events_all"        ON events        FOR ALL USING (auth.role() = 'authenticated') WITH CHECK (auth.role() = 'authenticated');
CREATE POLICY "announcements_all" ON announcements FOR ALL USING (auth.role() = 'authenticated') WITH CHECK (auth.role() = 'authenticated');
CREATE POLICY "messages_all"      ON messages      FOR ALL USING (auth.role() = 'authenticated') WITH CHECK (auth.role() = 'authenticated');

-- ── Storage policies ──────────────────────────────────────────
--  Run AFTER creating the buckets in Storage → New bucket
--  Bucket names: notes (Public) and avatars (Public)

CREATE POLICY "notes_insert" ON storage.objects FOR INSERT TO authenticated WITH CHECK (bucket_id = 'notes');
CREATE POLICY "notes_select" ON storage.objects FOR SELECT USING (bucket_id = 'notes');
CREATE POLICY "notes_delete" ON storage.objects FOR DELETE TO authenticated USING (bucket_id = 'notes');

CREATE POLICY "avatars_insert" ON storage.objects FOR INSERT TO authenticated WITH CHECK (bucket_id = 'avatars');
CREATE POLICY "avatars_select" ON storage.objects FOR SELECT USING (bucket_id = 'avatars');

-- ── Seed subjects ─────────────────────────────────────────────
INSERT INTO subjects (name, code, department, credit_hours) VALUES
    ('Data Structures',     'CSE201', 'CSE', 3),
    ('Algorithms',          'CSE301', 'CSE', 3),
    ('Operating Systems',   'CSE401', 'CSE', 3),
    ('Circuit Analysis',    'EEE201', 'EEE', 3),
    ('Digital Electronics', 'EEE301', 'EEE', 3)
ON CONFLICT (code) DO NOTHING;

-- ============================================================
--  AFTER RUNNING THIS FILE:
--
--  1. Authentication → Providers → Email → toggle OFF "Confirm email" → Save
--
--  2. Storage → New bucket → notes   (Public ON) → Save
--     Storage → New bucket → avatars (Public ON) → Save
--
--  3. Run the app, log in while online.
--     The app auto-registers your accounts in Supabase Auth.
--
--  4. Promote roles after first online login:
--     UPDATE users SET role = 'ADMIN'   WHERE email = 'your@email.com';
--     UPDATE users SET role = 'FACULTY' WHERE email = 'faculty@email.com';
-- ============================================================
