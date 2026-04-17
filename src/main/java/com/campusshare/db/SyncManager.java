package com.campusshare.db;

import com.campusshare.remote.SupabaseClient;
import com.campusshare.remote.SupabaseConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline → Online sync.
 *
 * Pushes local SQLite records that are missing from Supabase.
 * Handles the local-id → Supabase-id mapping for FK columns (created_by, posted_by, uploaded_by).
 *
 * Strategy:
 *   1. Build a map: local_user_id → supabase_user_id  (by matching email)
 *   2. For each local record not yet in Supabase, translate FK columns then INSERT
 *   3. File uploads go to Supabase Storage
 *   4. If FK translation fails, insert with NULL (schema allows it)
 */
public class SyncManager {

    public record SyncResult(int notes, int events, int announcements, int messages, int errors) {
        public boolean anyChanged() { return notes + events + announcements + messages > 0; }
        public String summary() {
            if (!anyChanged() && errors == 0) return "All data is already in sync with Supabase.";
            StringBuilder sb = new StringBuilder("Sync complete:\n");
            if (notes         > 0) sb.append("  • ").append(notes).append(" note(s) uploaded\n");
            if (events        > 0) sb.append("  • ").append(events).append(" event(s) uploaded\n");
            if (announcements > 0) sb.append("  • ").append(announcements).append(" announcement(s) uploaded\n");
            if (messages      > 0) sb.append("  • ").append(messages).append(" chat message(s) uploaded\n");
            if (errors        > 0) sb.append("  ⚠ ").append(errors).append(" error(s) — see console\n");
            return sb.toString().trim();
        }
    }

    /** Call after a confirmed Supabase session exists. */
    public static SyncResult syncLocalToCloud() {
        if (!SupabaseConfig.isConfigured() || SupabaseConfig.FORCE_OFFLINE) {
            return new SyncResult(0, 0, 0, 0, 0);
        }
        if (!SupabaseClient.hasSession()) {
            System.out.println("[Sync] Skipped — no active Supabase session.");
            return new SyncResult(0, 0, 0, 0, 0);
        }

        int notes = 0, events = 0, announcements = 0, messages = 0, errors = 0;
        try {
            // Build local→cloud user ID mapping first (needed for FK columns)
            Map<Integer, Integer> userIdMap = buildUserIdMap();
            System.out.println("[Sync] User ID map: " + userIdMap);

            notes         = syncNotes(userIdMap);
            events        = syncEvents(userIdMap);
            announcements = syncAnnouncements(userIdMap);
            messages      = syncMessages(userIdMap);
        } catch (Exception e) {
            e.printStackTrace();
            errors++;
        }
        return new SyncResult(notes, events, announcements, messages, errors);
    }

    /**
     * Build a map: local SQLite user_id  →  Supabase user_id
     * Matched by email address (unique in both databases).
     */
    private static Map<Integer, Integer> buildUserIdMap() throws SQLException {
        Map<Integer, Integer> map = new HashMap<>();

        // Fetch all users from Supabase (email → supabase_user_id)
        Map<String, Integer> cloudByEmail = new HashMap<>();
        JSONArray cloudUsers = SupabaseClient.select("users", "?select=user_id,email");
        for (int i = 0; i < cloudUsers.length(); i++) {
            JSONObject u = cloudUsers.getJSONObject(i);
            cloudByEmail.put(u.optString("email", "").toLowerCase(), u.optInt("user_id", 0));
        }

        // Match against local users
        ResultSet rs = Database.get().createStatement()
            .executeQuery("SELECT user_id, email FROM users");
        while (rs.next()) {
            int    localId = rs.getInt("user_id");
            String email   = rs.getString("email").toLowerCase();
            Integer cloudId = cloudByEmail.get(email);
            if (cloudId != null && cloudId > 0) {
                map.put(localId, cloudId);
            }
        }
        return map;
    }

    /** Translate a local user_id to Supabase user_id. Returns null if not mappable. */
    private static Integer mapUserId(int localId, Map<Integer, Integer> userIdMap) {
        return userIdMap.get(localId);
    }

    // ── Notes ─────────────────────────────────────────────────────────────────
    private static int syncNotes(Map<Integer, Integer> userIdMap) throws SQLException {
        int pushed = 0;

        // Fetch cloud keys to skip duplicates
        JSONArray cloudRows = SupabaseClient.select("resources", "?select=file_name,subject_id");
        List<String> cloudKeys = new ArrayList<>();
        for (int i = 0; i < cloudRows.length(); i++) {
            JSONObject r = cloudRows.getJSONObject(i);
            cloudKeys.add(r.optString("file_name") + "|" + r.optInt("subject_id"));
        }

        ResultSet rs = Database.get().createStatement().executeQuery(
            "SELECT r.*, s.name as subject_name FROM resources r " +
            "LEFT JOIN subjects s ON r.subject_id = s.subject_id");

        while (rs.next()) {
            String fname  = rs.getString("file_name");
            int    subId  = rs.getInt("subject_id");
            int    localUploaderId = rs.getInt("uploaded_by");
            if (cloudKeys.contains(fname + "|" + subId)) continue;

            try {
                // Translate uploaded_by to Supabase user_id
                Integer cloudUploaderId = mapUserId(localUploaderId, userIdMap);

                // Try to upload the actual file to Supabase Storage
                String filePath    = rs.getString("file_path");
                String storagePath = filePath;
                File   f           = new File(filePath);
                if (f.exists()) {
                    byte[] bytes  = Files.readAllBytes(f.toPath());
                    String mime   = mimeForExt(rs.getString("file_type"));
                    String remote = (cloudUploaderId != null ? cloudUploaderId : "0")
                        + "/" + System.currentTimeMillis() + "_" + fname;
                    String url = SupabaseClient.uploadFile("notes", remote, bytes, mime);
                    if (url != null) storagePath = url;
                }

                JSONObject row = new JSONObject()
                    .put("subject_id", subId)
                    .put("file_name",  fname)
                    .put("file_type",  rs.getString("file_type"))
                    .put("file_size",  rs.getString("file_size"))
                    .put("file_path",  storagePath)
                    .put("approved",   rs.getInt("approved") == 1);
                if (cloudUploaderId != null) row.put("uploaded_by", cloudUploaderId);

                JSONObject created = SupabaseClient.insert("resources", row);
                if (created != null) pushed++;
                else System.err.println("[Sync] Failed to push note: " + fname);
            } catch (Exception e) {
                System.err.println("[Sync] Error pushing note " + fname + ": " + e.getMessage());
            }
        }
        return pushed;
    }

    // ── Events ────────────────────────────────────────────────────────────────
    private static int syncEvents(Map<Integer, Integer> userIdMap) throws SQLException {
        int pushed = 0;

        JSONArray cloudRows = SupabaseClient.select("events", "?select=title,event_date");
        List<String> cloudKeys = new ArrayList<>();
        for (int i = 0; i < cloudRows.length(); i++) {
            JSONObject r = cloudRows.getJSONObject(i);
            cloudKeys.add(r.optString("title") + "|" + r.optString("event_date"));
        }

        ResultSet rs = Database.get().createStatement().executeQuery("SELECT * FROM events");
        while (rs.next()) {
            String title       = rs.getString("title");
            String date        = rs.getString("event_date");
            int    localCreator = rs.getInt("created_by");
            if (cloudKeys.contains(title + "|" + date)) continue;

            try {
                Integer cloudCreator = mapUserId(localCreator, userIdMap);
                JSONObject row = new JSONObject()
                    .put("title",      title)
                    .put("event_date", date)
                    .put("event_time", rs.getString("event_time"))
                    .put("category",   rs.getString("category"))
                    .put("location",   rs.getString("location"));
                if (cloudCreator != null) row.put("created_by", cloudCreator);

                JSONObject created = SupabaseClient.insert("events", row);
                if (created != null) pushed++;
                else System.err.println("[Sync] Failed to push event: " + title);
            } catch (Exception e) {
                System.err.println("[Sync] Error pushing event " + title + ": " + e.getMessage());
            }
        }
        return pushed;
    }

    // ── Announcements ─────────────────────────────────────────────────────────
    private static int syncAnnouncements(Map<Integer, Integer> userIdMap) throws SQLException {
        int pushed = 0;

        JSONArray cloudRows = SupabaseClient.select("announcements", "?select=title");
        List<String> cloudKeys = new ArrayList<>();
        for (int i = 0; i < cloudRows.length(); i++) {
            cloudKeys.add(cloudRows.getJSONObject(i).optString("title"));
        }

        ResultSet rs = Database.get().createStatement().executeQuery("SELECT * FROM announcements");
        while (rs.next()) {
            String title      = rs.getString("title");
            int    localPoster = rs.getInt("posted_by");
            if (cloudKeys.contains(title)) continue;

            try {
                Integer cloudPoster = mapUserId(localPoster, userIdMap);
                JSONObject row = new JSONObject()
                    .put("title", title)
                    .put("body",  rs.getString("body"))
                    .put("tag",   rs.getString("tag"));
                if (cloudPoster != null) row.put("posted_by", cloudPoster);

                JSONObject created = SupabaseClient.insert("announcements", row);
                if (created != null) pushed++;
                else System.err.println("[Sync] Failed to push announcement: " + title);
            } catch (Exception e) {
                System.err.println("[Sync] Error pushing announcement " + title + ": " + e.getMessage());
            }
        }
        return pushed;
    }

    // ── Messages (chat) ───────────────────────────────────────────────────────
    private static int syncMessages(Map<Integer, Integer> userIdMap) throws SQLException {
        int pushed = 0;

        // Build a lightweight "fingerprint" set from recent cloud messages to avoid duplicates.
        // We cap to 1000 to keep it fast; chat is append-only so this is good enough.
        JSONArray cloudRows = SupabaseClient.select("messages",
            "?select=type,channel,sender_id,recipient_id,content,sent_at&order=sent_at.desc&limit=1000");
        java.util.HashSet<String> cloudKeys = new java.util.HashSet<>();
        for (int i = 0; i < cloudRows.length(); i++) {
            JSONObject m = cloudRows.getJSONObject(i);
            cloudKeys.add(fingerprint(
                m.optString("type", "channel"),
                m.optString("channel", ""),
                m.optInt("sender_id", 0),
                m.optInt("recipient_id", 0),
                m.optString("content", ""),
                m.optString("sent_at", "")
            ));
        }

        // Only push messages that have not been pushed yet.
        ResultSet rs = Database.get().createStatement().executeQuery(
            "SELECT * FROM messages WHERE IFNULL(synced,0)=0 ORDER BY sent_at ASC");
        while (rs.next()) {
            int localMessageId = rs.getInt("message_id");
            String type = rs.getString("type");
            String channel = rs.getString("channel");
            int localSender = rs.getInt("sender_id");
            int localRecip  = rs.getInt("recipient_id");
            String content  = rs.getString("content");
            String sentAt   = rs.getString("sent_at");

            Integer cloudSender = mapUserId(localSender, userIdMap);
            Integer cloudRecip  = (localRecip > 0) ? mapUserId(localRecip, userIdMap) : null;

            // If we can't map the sender, we can't safely push this message.
            if (cloudSender == null || cloudSender <= 0) continue;

            String key = fingerprint(
                type != null ? type : "channel",
                channel != null ? channel : "",
                cloudSender,
                cloudRecip != null ? cloudRecip : 0,
                content != null ? content : "",
                sentAt != null ? sentAt : ""
            );
            if (cloudKeys.contains(key)) continue;

            try {
                JSONObject row = new JSONObject()
                    .put("type", type != null ? type : "channel")
                    .put("content", content != null ? content : "");
                if ("channel".equalsIgnoreCase(type)) row.put("channel", channel);
                row.put("sender_id", cloudSender);
                if ("dm".equalsIgnoreCase(type) && cloudRecip != null) row.put("recipient_id", cloudRecip);

                JSONObject created = SupabaseClient.insert("messages", row);
                if (created != null) {
                    pushed++;
                    cloudKeys.add(key);
                    try (PreparedStatement up = Database.get().prepareStatement(
                        "UPDATE messages SET synced=1 WHERE message_id=?")) {
                        up.setInt(1, localMessageId);
                        up.executeUpdate();
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                System.err.println("[Sync] Error pushing message: " + e.getMessage());
            }
        }
        return pushed;
    }

    private static String fingerprint(String type, String channel, int senderId, int recipientId, String content, String sentAt) {
        // Normalize timestamps (SQLite uses "YYYY-MM-DD HH:MM:SS", Supabase commonly returns ISO-8601).
        // Goal: reduce format-only differences so we don't re-upload the same logical row.
        String t = normalizeSentAt(sentAt);
        return (type == null ? "" : type) + "|" +
            (channel == null ? "" : channel) + "|" +
            senderId + "|" + recipientId + "|" +
            (content == null ? "" : content) + "|" +
            t;
    }

    private static String normalizeSentAt(String sentAt) {
        if (sentAt == null) return "";
        String s = sentAt.trim();
        if (s.isEmpty()) return "";
        // Convert 'T' separator to space.
        s = s.replace('T', ' ');
        // Drop trailing 'Z' if present.
        if (s.endsWith("Z")) s = s.substring(0, s.length() - 1);
        // Drop timezone offsets like "+00:00" or "-06:00" if present.
        // Supabase may return "YYYY-MM-DD HH:MM:SS+00:00" or "...:SS.SSS+00:00".
        int plus = s.lastIndexOf('+');
        int minus = s.lastIndexOf('-');
        int tzIdx = Math.max(plus, minus);
        if (tzIdx > 9) { // after date part
            // Only treat as timezone if there is a ':' after it (e.g. +06:00)
            int colon = s.indexOf(':', tzIdx);
            if (colon > tzIdx) s = s.substring(0, tzIdx);
        }
        // Drop fractional seconds, keep up to "YYYY-MM-DD HH:MM:SS" (19 chars).
        if (s.length() > 19) s = s.substring(0, 19);
        return s;
    }

    private static String mimeForExt(String ext) {
        if (ext == null) return "application/octet-stream";
        return switch (ext.toUpperCase()) {
            case "PDF"  -> "application/pdf";
            case "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "DOC"  -> "application/msword";
            case "PPTX" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "PPT"  -> "application/vnd.ms-powerpoint";
            case "XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "PNG"  -> "image/png";
            case "JPG", "JPEG" -> "image/jpeg";
            case "GIF"  -> "image/gif";
            case "TXT"  -> "text/plain";
            default     -> "application/octet-stream";
        };
    }
}
