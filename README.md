# CampusShare — Java Swing Desktop App

A campus resource-sharing platform built in Java Swing with:
- **Offline mode** (SQLite, zero config)
- **Online mode** (Supabase cloud, optional)

---

## Prerequisites

| Tool | Version | Download |
|------|---------|----------|
| JDK  | 17 or 21 LTS | https://adoptium.net |
| Maven | 3.8+ | https://maven.apache.org/download.cgi |

After installing, verify:
```
java -version
mvn -version
```

---

## Offline Mode (SQLite) — Default, no config needed

### Build
```bash
cd CampusShareFinal
mvn clean package
```
Maven downloads all dependencies on first build (~30 MB, internet required once).

### Run
```bash
java -jar target\CampusShare.jar       # Windows
java -jar target/CampusShare.jar       # Mac/Linux
```

On first launch the app creates `~/CampusShare/campusshare.db` with demo data.

### Demo accounts (all passwords: `pass123`)

| Email | Role |
|-------|------|
| grace@campus.edu | Student |
| admin@campus.edu | Admin |
| bob@campus.edu   | Student |
| mary@campus.edu  | Faculty |

### Reset the database
Delete `~/CampusShare/campusshare.db` and relaunch — fresh seed data is created.

---

## Online Mode (Supabase Cloud)

### Step 1 — Create a Supabase project
1. Go to https://supabase.com → New Project
2. Choose a region, set a database password, wait for provisioning

### Step 2 — Run the schema SQL
In Supabase Dashboard → SQL Editor, paste and run:

```sql
CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY, full_name TEXT NOT NULL,
  email TEXT NOT NULL UNIQUE, password TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'STUDENT',
  department TEXT NOT NULL DEFAULT 'CSE', semester INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS subjects (
  id SERIAL PRIMARY KEY, name TEXT NOT NULL, code TEXT NOT NULL, department TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS resources (
  id SERIAL PRIMARY KEY, subject_id INTEGER REFERENCES subjects(id),
  uploader_id INTEGER REFERENCES users(id), file_name TEXT NOT NULL,
  file_type TEXT NOT NULL DEFAULT 'PDF', file_size TEXT NOT NULL DEFAULT '—',
  file_path TEXT NOT NULL DEFAULT '', approved BOOLEAN NOT NULL DEFAULT false,
  rating REAL NOT NULL DEFAULT 0, uploaded_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS events (
  id SERIAL PRIMARY KEY, title TEXT NOT NULL, date TEXT NOT NULL,
  time TEXT NOT NULL DEFAULT '00:00', category TEXT NOT NULL DEFAULT 'SEMINAR',
  location TEXT NOT NULL DEFAULT 'TBA', organizer_id INTEGER REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS announcements (
  id SERIAL PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL,
  category TEXT NOT NULL DEFAULT 'GENERAL', author_id INTEGER REFERENCES users(id),
  posted_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS messages (
  id SERIAL PRIMARY KEY, channel TEXT NOT NULL,
  sender_id INTEGER REFERENCES users(id), body TEXT NOT NULL,
  sent_at TIMESTAMPTZ DEFAULT NOW()
);
```

### Step 3 — Add your credentials
Open `src/main/java/com/campusshare/remote/SupabaseConfig.java` and fill in:

```java
public static final String SUPABASE_URL      = "https://YOUR-PROJECT-ID.supabase.co";
public static final String SUPABASE_ANON_KEY = "YOUR-ANON-PUBLIC-KEY";
```

Find these in: Supabase Dashboard → Settings → API

### Step 4 — Rebuild and run
```bash
mvn clean package
java -jar target/CampusShare.jar
```

The app now authenticates via Supabase and falls back to SQLite if offline.

---

## Project Structure

```
CampusShareFinal/
├── pom.xml
└── src/main/java/com/campusshare/
    ├── CampusShareApp.java              ← Entry point
    ├── data/
    │   └── DataStore.java               ← All model classes + DAO-backed lists
    ├── db/
    │   ├── Database.java                ← SQLite connection + schema creation
    │   ├── DAO.java                     ← All DB operations (dual-mode)
    │   └── SeedData.java                ← Demo data seeded on first launch
    ├── remote/
    │   ├── SupabaseConfig.java          ← ★ EDIT THIS for cloud mode
    │   ├── SupabaseClient.java          ← Supabase REST API calls
    │   └── RealtimeClient.java          ← Realtime stub
    ├── chat/
    │   ├── ChatServer.java
    │   ├── ChatClient.java
    │   └── LocalBroadcast.java          ← In-process message events
    └── ui/
        ├── LoginWindow.java             ← Space-themed animated login
        ├── MainWindow.java              ← Sidebar + panel switcher
        ├── Theme.java                   ← Colors, fonts, buttons
        ├── RoundBorder.java
        └── panels/
            ├── DashboardPanel.java      ← Stats + recent activity
            ├── NotesPanel.java          ← Browse/upload/approve notes
            ├── EventsPanel.java         ← Events grid, admin add/delete
            ├── AnnouncementsPanel.java  ← Post/delete announcements
            ├── ForumPanel.java          ← Persisted chat by channel
            └── ProfilePanel.java        ← Edit profile + change password
```

## Role capabilities

| Feature | Student | Faculty | Admin |
|---------|---------|---------|-------|
| View notes | ✅ (approved only) | ✅ (all) | ✅ (all) |
| Upload notes | ✅ (pending approval) | ✅ (auto-approved) | ✅ |
| Approve notes | ❌ | ❌ | ✅ |
| View events | ✅ | ✅ | ✅ |
| Add/delete events | ❌ | ❌ | ✅ |
| Post announcements | ❌ | ❌ | ✅ |
| Chat | ✅ | ✅ | ✅ |
| Edit own profile | ✅ | ✅ | ✅ |
