package com.campusshare.db;

import java.io.File;
import java.sql.*;
import java.util.Properties;

/**
 * Manages the SQLite connection.
 * DB file: ~/CampusShare/campusshare.db
 *
 * Column names in this schema are kept identical to what DAO.java expects,
 * so there is no mismatch between the CREATE TABLE statements and the queries.
 */
public class Database {

    private static Connection conn;

    public static synchronized Connection get() {
        if (conn != null) return conn;

        File dir = new File(System.getProperty("user.home"), "CampusShare");
        dir.mkdirs();
        System.setProperty("org.sqlite.tmpdir", dir.getAbsolutePath());

        try { Class.forName("org.sqlite.JDBC"); }
        catch (ClassNotFoundException e) { throw new RuntimeException("SQLite driver missing", e); }

        File dbFile = new File(dir, "campusshare.db");

        conn = openConnection(dbFile);

        if (conn == null) {
            System.out.println("[CampusShare] DB locked — wiping and retrying...");
            for (String suffix : new String[]{"", "-wal", "-shm", "-journal"}) {
                new File(dir, "campusshare.db" + suffix).delete();
            }
            conn = openConnection(dbFile);
        }

        if (conn == null) throw new RuntimeException(
            "Cannot open database even after reset.\nDelete this folder and try again:\n" + dir.getAbsolutePath());

        try {
            // ── Schema version guard ─────────────────────────────────────────
            // If the DB was created with the old schema (column 'id' not 'user_id')
            // we drop everything and start fresh so DAO queries work correctly.
            if (hasOldSchema(conn)) {
                System.out.println("[CampusShare] Old schema detected — dropping all tables for fresh start.");
                dropAllTables(conn);
            }
            createSchema(conn);
            migrateSchema(conn);
            SeedData.seed(conn);
            System.out.println("[CampusShare] DB ready: " + dbFile.getAbsolutePath());
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Schema/seed error: " + e.getMessage(), e);
        }

        return conn;
    }

    /** Called from the shutdown hook in CampusShareApp. */
    public static synchronized void close() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
            conn = null;
        }
    }

    // ── Check if this is the old schema (uses 'id' instead of 'user_id') ────
    private static boolean hasOldSchema(Connection c) {
        try {
            ResultSet rs = c.getMetaData().getColumns(null, null, "users", "user_id");
            boolean hasNew = rs.next();
            rs.close();
            // If 'user_id' column doesn't exist but 'users' table does → old schema
            ResultSet tbl = c.getMetaData().getTables(null, null, "users", null);
            boolean hasTable = tbl.next();
            tbl.close();
            return hasTable && !hasNew;
        } catch (SQLException e) { return false; }
    }

    private static void dropAllTables(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys=OFF");
            for (String t : new String[]{
                "messages","announcements","events","resources","subjects","users"
            }) {
                st.execute("DROP TABLE IF EXISTS " + t);
            }
            st.execute("PRAGMA foreign_keys=ON");
        }
    }

    private static Connection openConnection(File dbFile) {
        try {
            String path = dbFile.getAbsolutePath().replace('\\', '/');
            Properties props = new Properties();
            props.setProperty("open_mode",    "6");
            props.setProperty("shared_cache", "false");
            props.setProperty("busy_timeout", "3000");
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path, props);
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA journal_mode=DELETE");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA foreign_keys=ON");
            }
            return c;
        } catch (SQLException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("SQLITE_BUSY") || msg.contains("locked")) return null;
            throw new RuntimeException("Failed to open SQLite DB: " + msg, e);
        }
    }

    /**
     * All column names here EXACTLY match what DAO.java reads/writes.
     * Do not rename columns without updating DAO as well.
     */
    /** Adds columns that did not exist in older schema versions. */
    private static void migrateSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            // v5 — profile photo path
            try { st.execute("ALTER TABLE users ADD COLUMN avatar_path TEXT NOT NULL DEFAULT ''"); }
            catch (SQLException ignored) {}
            // v6 — real online tracking
            try { st.execute("ALTER TABLE users ADD COLUMN last_seen TEXT NOT NULL DEFAULT ''"); }
            catch (SQLException ignored) {}
            // v12 — unified messages table: add type and recipient_id to messages
            try { st.execute("ALTER TABLE messages ADD COLUMN type TEXT NOT NULL DEFAULT 'channel'"); }
            catch (SQLException ignored) {}
            try { st.execute("ALTER TABLE messages ADD COLUMN recipient_id INTEGER"); }
            catch (SQLException ignored) {}
            // v13 — mark whether a row has been pushed to Supabase yet
            try { st.execute("ALTER TABLE messages ADD COLUMN synced INTEGER NOT NULL DEFAULT 0"); }
            catch (SQLException ignored) {}
            // Migrate old DM data if direct_messages table still exists
            try {
                ResultSet chk = c.createStatement().executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='direct_messages'");
                if (chk.next()) {
                    st.execute("INSERT INTO messages (type,sender_id,recipient_id,content,sent_at) " +
                        "SELECT 'dm',sender_id,recipient_id,content,sent_at FROM direct_messages");
                    st.execute("DROP TABLE IF EXISTS direct_messages");
                }
            } catch (SQLException ignored) {}
        }
    }

    private static void createSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {

            // ── users ────────────────────────────────────────────────────────
            st.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                "user_id       INTEGER PRIMARY KEY AUTOINCREMENT," +
                "full_name     TEXT    NOT NULL," +
                "email         TEXT    NOT NULL UNIQUE," +
                "password_hash TEXT    NOT NULL," +
                "role          TEXT    NOT NULL DEFAULT 'STUDENT'," +
                "avatar_path    TEXT    NOT NULL DEFAULT ''," +
                "last_seen      TEXT    NOT NULL DEFAULT ''," +
                "department    TEXT    NOT NULL DEFAULT 'CSE'," +
                "semester      INTEGER NOT NULL DEFAULT 1)");

            // ── subjects ─────────────────────────────────────────────────────
            st.execute(
                "CREATE TABLE IF NOT EXISTS subjects (" +
                "subject_id   INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name         TEXT    NOT NULL," +
                "code         TEXT    NOT NULL," +
                "department   TEXT    NOT NULL," +
                "credit_hours INTEGER NOT NULL DEFAULT 3)");

            // ── resources (notes) ─────────────────────────────────────────────
            st.execute(
                "CREATE TABLE IF NOT EXISTS resources (" +
                "resource_id  INTEGER PRIMARY KEY AUTOINCREMENT," +
                "subject_id   INTEGER REFERENCES subjects(subject_id)," +
                "uploaded_by  INTEGER REFERENCES users(user_id)," +
                "file_name    TEXT    NOT NULL," +
                "file_type    TEXT    NOT NULL DEFAULT 'PDF'," +
                "file_size    TEXT    NOT NULL DEFAULT '-'," +
                "file_path    TEXT    NOT NULL DEFAULT ''," +
                "approved     INTEGER NOT NULL DEFAULT 0," +
                "uploaded_at  TEXT    NOT NULL DEFAULT (datetime('now')))");

            // ── events ────────────────────────────────────────────────────────
            st.execute(
                "CREATE TABLE IF NOT EXISTS events (" +
                "event_id   INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title      TEXT    NOT NULL," +
                "event_date TEXT    NOT NULL," +
                "event_time TEXT    NOT NULL DEFAULT '00:00'," +
                "category   TEXT    NOT NULL DEFAULT 'SEMINAR'," +
                "location   TEXT    NOT NULL DEFAULT 'TBA'," +
                "created_by INTEGER REFERENCES users(user_id))");

            // ── announcements ─────────────────────────────────────────────────
            st.execute(
                "CREATE TABLE IF NOT EXISTS announcements (" +
                "ann_id     INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title      TEXT    NOT NULL," +
                "body       TEXT    NOT NULL," +
                "posted_by  INTEGER REFERENCES users(user_id)," +
                "tag        TEXT    NOT NULL DEFAULT 'GENERAL'," +
                "created_at TEXT    NOT NULL DEFAULT (datetime('now')))");

            // ── messages (channel + DM unified) ──────────────────────────────
            // type='channel' → channel field set, recipient_id NULL
            // type='dm'      → recipient_id set, channel NULL
            st.execute(
                "CREATE TABLE IF NOT EXISTS messages (" +
                "message_id   INTEGER PRIMARY KEY AUTOINCREMENT," +
                "type         TEXT    NOT NULL DEFAULT 'channel'," +
                "channel      TEXT," +
                "sender_id    INTEGER REFERENCES users(user_id)," +
                "recipient_id INTEGER REFERENCES users(user_id)," +
                "content      TEXT    NOT NULL," +
                "sent_at      TEXT    NOT NULL DEFAULT (datetime('now'))," +
                "synced       INTEGER NOT NULL DEFAULT 0)");
        }
    }
}
