package com.campusshare.db;

import com.campusshare.data.DataStore;
import com.campusshare.db.SyncManager;
import com.campusshare.remote.ConnectionMonitor;
import com.campusshare.remote.SupabaseClient;
import com.campusshare.remote.SupabaseConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt;

import java.io.File;
import java.nio.file.Files;
import java.sql.*;
import java.net.URLEncoder;
import java.util.*;

/**
 * Data Access Object  — routes all calls to:
 *   • Supabase (when online & configured)   → real multi-PC sync
 *   • SQLite local cache (when offline)     → graceful fallback
 *
 * All public methods return DataStore model objects so UI code never changes.
 */
public class DAO {

    // Cache local-id → cloud-id lookups to keep chat snappy
    private static final Map<Integer, Integer> CLOUD_ID_CACHE = new HashMap<>();

    private static boolean useSupabase() {
        // ConnectionMonitor manages FORCE_OFFLINE based on real connectivity.
        // We just check: not force-offline + credentials configured + session active.
        return !SupabaseConfig.FORCE_OFFLINE
            && SupabaseConfig.isConfigured()
            && SupabaseClient.hasSession();
    }

    /** True if we have internet and a cloud session — for quick UI checks. */
    public static boolean isCloudAvailable() {
        return ConnectionMonitor.isOnline() && SupabaseClient.hasSession();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AUTH
    // ══════════════════════════════════════════════════════════════════════════

    public static DataStore.User login(String email, String password) {
        if (SupabaseConfig.isConfigured() && !SupabaseConfig.FORCE_OFFLINE) {
            // Step 1: Try to sign in to Supabase
            DataStore.User cloudUser = loginSupabase(email, password);
            if (cloudUser != null) return cloudUser;

            // Step 2: Supabase sign-in failed — check if local account exists
            DataStore.User localUser = loginLocal(email, password);
            if (localUser != null) {
                // Local account exists but not in Supabase Auth yet.
                // Auto-register them in Supabase so cloud sync works from now on.
                System.out.println("[Auth] Auto-registering " + email + " in Supabase...");
                boolean registered = registerSupabase(
                    localUser.fullName, email, password,
                    localUser.role, localUser.department, localUser.semester);
                if (registered) {
                    System.out.println("[Auth] Supabase registration OK — signing in...");
                    DataStore.User cloudUser2 = loginSupabase(email, password);
                    if (cloudUser2 != null) return cloudUser2;
                }
                // Auto-register failed or email confirmation still needed
                // — return local user so app still works offline
                System.out.println("[Auth] Could not create Supabase account — running offline for this session.");
                return localUser;
            }
        }
        return loginLocal(email, password);
    }

    private static DataStore.User loginSupabase(String email, String password) {
        JSONObject session = SupabaseClient.signIn(email, password);
        if (session == null || !session.has("access_token")) return null;
        // Trigger background sync of any offline data after successful cloud login
        new Thread(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            SyncManager.SyncResult r = SyncManager.syncLocalToCloud();
            if (r.anyChanged()) System.out.println("[Sync] " + r.summary());
        }, "CampusShare-PostLogin-Sync").start();

        String token   = session.getString("access_token");
        String refresh = session.optString("refresh_token", null);
        int expiresIn  = session.optInt("expires_in", 3600);
        JSONObject su  = session.getJSONObject("user");
        String uid     = su.getString("id");
        // Store the refresh token so the session can auto-renew after 1 hour
        SupabaseClient.setSession(token, refresh, uid, expiresIn);
        // Ensure storage bucket is ready for file uploads
        new Thread(() -> {
            SupabaseClient.ensureBucketExists("notes");
            SupabaseClient.ensureBucketExists("avatars");
        }, "BucketInit").start();

        // Fetch profile row from users table
        JSONObject profile = SupabaseClient.selectOne("users",
            "?select=*&user_uuid=eq." + uid);
        if (profile == null) {
            // User exists in Supabase Auth but not in our users table.
            // This happens on first cloud login — create the row now.
            String userEmail = su.optString("email", "");
            JSONObject row = new JSONObject()
                .put("user_uuid", uid)
                .put("email",     userEmail)
                .put("full_name", su.optJSONObject("user_metadata") != null
                    ? su.getJSONObject("user_metadata").optString("full_name", userEmail)
                    : userEmail)
                .put("role",       "STUDENT")
                .put("department", "")
                .put("semester",   0);
            SupabaseClient.insert("users", row);
            profile = SupabaseClient.selectOne("users", "?select=*&user_uuid=eq." + uid);
            if (profile == null) return null;
        }
        DataStore.User u = mapUserFromJson(profile);
        DataStore.cloudUserId = u.id;
        return u;
    }

    private static DataStore.User loginLocal(String email, String password) {
        try {
            PreparedStatement ps = Database.get().prepareStatement(
                "SELECT * FROM users WHERE email=?");
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && BCrypt.checkpw(password, rs.getString("password_hash"))) {
                return mapUserFromRs(rs);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public static boolean registerUser(String fullName, String email, String password,
                                       String role, String department, int semester) {
        // Always register locally so the account works immediately,
        // regardless of Supabase email-confirmation settings.
        boolean localOk = registerLocal(fullName, email, password, role, department, semester);
        if (!localOk) return false; // duplicate email
        // Also attempt Supabase registration in the background (best-effort).
        if (SupabaseConfig.isConfigured()) {
            registerSupabase(fullName, email, password, role, department, semester);
        }
        return true;
    }

    private static boolean registerSupabase(String fullName, String email, String password,
                                             String role, String dept, int semester) {
        // Step 1: Create the Supabase Auth account
        JSONObject meta = new JSONObject()
            .put("full_name", fullName).put("role", role)
            .put("department", dept).put("semester", semester);
        JSONObject result = SupabaseClient.signUp(email, password, meta);
        if (result == null || !result.has("user")) {
            System.err.println("[Auth] signUp failed for " + email);
            return false;
        }
        String uid = result.getJSONObject("user").getString("id");
        System.out.println("[Auth] Supabase Auth account created: " + uid);

        // Step 2: Get an authenticated session so the RLS INSERT is permitted.
        // When email confirmation is disabled, Supabase returns access_token
        // directly in the signUp response — use that first (fastest path).
        // Fall back to an explicit signIn if the token is not in the signup response.
        boolean hasSession = false;
        if (result.has("access_token")) {
            String token   = result.getString("access_token");
            String refresh = result.optString("refresh_token", null);
            int    exp     = result.optInt("expires_in", 3600);
            SupabaseClient.setSession(token, refresh, uid, exp);
            System.out.println("[Auth] Session from signUp response — OK.");
            hasSession = true;
        } else {
            // Email confirmation might be ON, or signUp returned user-only.
            // Try an explicit signIn.
            JSONObject session = SupabaseClient.signIn(email, password);
            if (session != null && session.has("access_token")) {
                String token   = session.getString("access_token");
                String refresh = session.optString("refresh_token", null);
                int    exp     = session.optInt("expires_in", 3600);
                SupabaseClient.setSession(token, refresh, uid, exp);
                System.out.println("[Auth] Session from explicit signIn — OK.");
                hasSession = true;
            } else {
                System.err.println("[Auth] No session after signUp. Check email confirmation is OFF in Supabase.");
            }
        }

        // Step 3: Insert the profile row in the public users table
        JSONObject row = new JSONObject()
            .put("user_uuid",   uid)
            .put("full_name",   fullName)
            .put("email",       email)
            .put("role",        role)
            .put("department",  dept)
            .put("semester",    semester);
        JSONObject inserted = SupabaseClient.insert("users", row);
        if (inserted == null) {
            // Row might already exist — update the uuid link
            SupabaseClient.update("users", "email=eq." + email,
                new JSONObject().put("user_uuid", uid));
            System.out.println("[Auth] Profile row updated with user_uuid.");
        } else {
            System.out.println("[Auth] Profile row inserted OK.");
        }
        return true;
    }

    private static boolean registerLocal(String fullName, String email, String password,
                                          String role, String dept, int semester) {
        try {
            String hash = BCrypt.hashpw(password, BCrypt.gensalt());
            PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO users (full_name,email,password_hash,role,department,semester) VALUES(?,?,?,?,?,?)");
            ps.setString(1, fullName); ps.setString(2, email);
            ps.setString(3, hash);    ps.setString(4, role);
            ps.setString(5, dept);    ps.setInt(6, semester);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    public static boolean updatePassword(int userId, String currentPassword, String newPassword) {
        if (useSupabase()) {
            DataStore.User u = DataStore.getCurrentUser();
            if (u == null) return false;
            // Step 1: Re-authenticate to verify the current password is correct
            JSONObject verify = SupabaseClient.signIn(u.email, currentPassword);
            if (verify == null || !verify.has("access_token")) return false;
            // Step 2: Update the password in Supabase Auth (PUT /auth/v1/user)
            // This is the ONLY correct way — writing to the "users" table does nothing
            // because passwords are hashed inside Supabase Auth, not our users table.
            return SupabaseClient.updateAuthPassword(newPassword);
        }
        try {
            PreparedStatement ps = Database.get().prepareStatement(
                "SELECT password_hash FROM users WHERE user_id=?");
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && BCrypt.checkpw(currentPassword, rs.getString(1))) {
                PreparedStatement up = Database.get().prepareStatement(
                    "UPDATE users SET password_hash=? WHERE user_id=?");
                up.setString(1, BCrypt.hashpw(newPassword, BCrypt.gensalt()));
                up.setInt(2, userId); up.executeUpdate();
                return true;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public static boolean updateProfile(int userId, String fullName, String department, int semester) {
        if (useSupabase()) {
            // SECURITY: filter on user_uuid (the auth UUID), not user_id (integer PK).
            // The RLS policy "users_update_own" uses:  USING (user_uuid = auth.uid())
            // If we filter by user_id, any logged-in user could patch any other user's row
            // because Postgres runs the filter first and RLS second on PATCH.
            // Filtering by user_uuid ensures only the matching auth identity can update.
            String myUuid = SupabaseClient.getUserId(); // UUID from auth.uid()
            if (myUuid == null) return false;
            JSONObject patch = new JSONObject()
                .put("full_name", fullName).put("department", department).put("semester", semester);
            return SupabaseClient.update("users", "user_uuid=eq." + myUuid, patch) != null;
        }
        try {
            PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE users SET full_name=?,department=?,semester=? WHERE user_id=?");
            ps.setString(1, fullName); ps.setString(2, department);
            ps.setInt(3, semester);   ps.setInt(4, userId);
            ps.executeUpdate(); return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // USERS
    // ══════════════════════════════════════════════════════════════════════════

    public static List<DataStore.User> getAllUsers() {
        List<DataStore.User> list = new ArrayList<>();
        if (useSupabase()) {
            JSONArray rows = SupabaseClient.select("users", "?select=*&order=full_name.asc");
            for (int i = 0; i < rows.length(); i++) list.add(mapUserFromJson(rows.getJSONObject(i)));
            return list;
        }
        try {
            ResultSet rs = Database.get().createStatement()
                .executeQuery("SELECT * FROM users ORDER BY full_name");
            while (rs.next()) list.add(mapUserFromRs(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SUBJECTS
    // ══════════════════════════════════════════════════════════════════════════

    public static List<DataStore.Subject> getAllSubjects() {
        List<DataStore.Subject> list = new ArrayList<>();
        if (useSupabase()) {
            JSONArray rows = SupabaseClient.select("subjects", "?select=*&order=name.asc");
            for (int i = 0; i < rows.length(); i++) list.add(mapSubjectFromJson(rows.getJSONObject(i)));
            return list;
        }
        try {
            ResultSet rs = Database.get().createStatement()
                .executeQuery("SELECT * FROM subjects ORDER BY name");
            while (rs.next()) list.add(mapSubjectFromRs(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /** Create a new subject and return its ID. Returns -1 on failure. */
    public static int createSubject(String name, String code, String department, int creditHours) {
        if (useSupabase()) {
            JSONObject row = new JSONObject()
                .put("name", name)
                .put("code", code)
                .put("department", department)
                .put("credit_hours", creditHours);
            JSONObject created = SupabaseClient.insert("subjects", row);
            if (created != null && created.has("subject_id")) {
                return created.getInt("subject_id");
            }
            return -1;
        }
        try {
            PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO subjects (name, code, department, credit_hours) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, code);
            ps.setString(3, department);
            ps.setInt(4, creditHours);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NOTES / RESOURCES
    // ══════════════════════════════════════════════════════════════════════════

    public static List<DataStore.Note> getAllNotes() {
        List<DataStore.Note> list = new ArrayList<>();
        if (useSupabase()) {
            // Join via select with embedded relation
            JSONArray rows = SupabaseClient.select("resources",
                "?select=*,uploader:users(full_name)&order=uploaded_at.desc");
            for (int i = 0; i < rows.length(); i++) list.add(mapNoteFromJson(rows.getJSONObject(i)));
            return list;
        }
        try {
            ResultSet rs = Database.get().createStatement().executeQuery("""
                SELECT r.*, u.full_name AS uploader_name FROM resources r
                JOIN users u ON r.uploaded_by=u.user_id ORDER BY r.uploaded_at DESC""");
            while (rs.next()) list.add(mapNoteFromRs(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /**
     * Add a note. Uploads the actual file to Supabase Storage (or copies locally).
     * Returns new resource_id or -1 on error.
     */

    /**
     * Returns the Supabase integer user_id for a given local user_id.
     * Looks up by email to handle cases where IDs diverge between SQLite and Supabase.
     * Returns null if the user has no Supabase account yet.
     */
    private static Integer getCloudUserId(int localUserId) {
        if (!useSupabase()) return null;
        try {
            // Get the email from local DB
            PreparedStatement ps = Database.get().prepareStatement(
                "SELECT email FROM users WHERE user_id=?");
            ps.setInt(1, localUserId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            String email = rs.getString("email");

            // Look up in Supabase by email
            JSONArray rows = SupabaseClient.select("users",
                "?select=user_id&email=eq." + java.net.URLEncoder.encode(email, "UTF-8"));
            if (rows.length() > 0) return rows.getJSONObject(0).optInt("user_id", -1);
        } catch (Exception e) {
            System.err.println("[DAO] getCloudUserId error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Normalize a possibly-local user_id into the correct Supabase users.user_id.
     * - If the id already exists in Supabase, returns it.
     * - Else tries to map via local SQLite email → Supabase user_id.
     * - Else returns null (caller may omit FK column).
     */
    private static Integer normalizeToCloudUserId(int maybeLocalId) {
        if (!useSupabase() || maybeLocalId <= 0) return null;

        // Fast path for current logged-in user
        DataStore.User cu = DataStore.getCurrentUser();
        if (cu != null && cu.id == maybeLocalId && DataStore.cloudUserId > 0) {
            return DataStore.cloudUserId;
        }

        Integer cached = CLOUD_ID_CACHE.get(maybeLocalId);
        if (cached != null && cached > 0) return cached;

        // If this id is already a Supabase user_id, it will exist in cloud.
        try {
            JSONArray exists = SupabaseClient.select("users", "?select=user_id&user_id=eq." + maybeLocalId + "&limit=1");
            if (exists.length() > 0) {
                CLOUD_ID_CACHE.put(maybeLocalId, maybeLocalId);
                return maybeLocalId;
            }
        } catch (Exception ignored) {}

        // Else: try local-id → email → cloud-id mapping
        Integer mapped = getCloudUserId(maybeLocalId);
        if (mapped != null && mapped > 0) {
            CLOUD_ID_CACHE.put(maybeLocalId, mapped);
            return mapped;
        }
        return null;
    }

    public static int addNote(int subjectId, int uploadedBy, String fileName,
                               String fileType, String fileSize, String localFilePath, boolean approved) {
        // Step 1: ALWAYS write to local SQLite first (works offline & online)
        String storagePath = localFilePath;
        int localId = -1;
        try {
            PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO resources (subject_id,uploaded_by,file_name,file_type,file_size,file_path,approved) VALUES(?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, subjectId); ps.setInt(2, uploadedBy);
            ps.setString(3, fileName); ps.setString(4, fileType);
            ps.setString(5, fileSize); ps.setString(6, storagePath);
            ps.setInt(7, approved ? 1 : 0);
            ps.executeUpdate();
            ResultSet k = ps.getGeneratedKeys();
            if (k.next()) localId = k.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }

        // Step 2: If online, also push to Supabase with correct cloud user_id
        if (useSupabase()) {
            Integer cloudUploaderId = getCloudUserId(uploadedBy);
            try {
                byte[] bytes = Files.readAllBytes(new File(localFilePath).toPath());
                String mime = mimeForType(fileType);
                String remotePath = (cloudUploaderId != null ? cloudUploaderId : uploadedBy)
                    + "/" + System.currentTimeMillis() + "_" + fileName;
                String url = SupabaseClient.uploadFile("notes", remotePath, bytes, mime);
                if (url != null) storagePath = url;
            } catch (Exception e) { e.printStackTrace(); }
            JSONObject row = new JSONObject()
                .put("subject_id", subjectId)
                .put("file_name", fileName).put("file_type", fileType)
                .put("file_size", fileSize).put("file_path", storagePath)
                .put("approved", approved);
            if (cloudUploaderId != null) row.put("uploaded_by", cloudUploaderId);
            SupabaseClient.insert("resources", row);
        }
        return localId;
    }

    public static boolean approveNote(int resourceId) {
        if (useSupabase()) {
            return SupabaseClient.update("resources", "resource_id=eq." + resourceId,
                new JSONObject().put("approved", true)) != null;
        }
        try {
            PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE resources SET approved=1 WHERE resource_id=?");
            ps.setInt(1, resourceId); return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public static boolean deleteNote(int resourceId) {
        if (useSupabase()) return SupabaseClient.delete("resources", "resource_id=eq." + resourceId);
        try {
            PreparedStatement ps = Database.get().prepareStatement("DELETE FROM resources WHERE resource_id=?");
            ps.setInt(1, resourceId); return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }



    // ══════════════════════════════════════════════════════════════════════════
    // EVENTS
    // ══════════════════════════════════════════════════════════════════════════

    public static List<DataStore.Event> getAllEvents() {
        List<DataStore.Event> list = new ArrayList<>();
        if (useSupabase()) {
            JSONArray rows = SupabaseClient.select("events", "?select=*&order=event_date.asc,event_time.asc");
            for (int i = 0; i < rows.length(); i++) list.add(mapEventFromJson(rows.getJSONObject(i)));
            return list;
        }
        try {
            ResultSet rs = Database.get().createStatement()
                .executeQuery("SELECT * FROM events ORDER BY event_date,event_time");
            while (rs.next()) list.add(mapEventFromRs(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public static int addEvent(String title, String date, String time,
                                String category, String location, int createdBy) {
        // Always write local first
        int localId = -1;
        try {
            PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO events (title,event_date,event_time,category,location,created_by) VALUES(?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,title); ps.setString(2,date); ps.setString(3,time);
            ps.setString(4,category); ps.setString(5,location); ps.setInt(6,createdBy);
            ps.executeUpdate();
            ResultSet k = ps.getGeneratedKeys(); if (k.next()) localId = k.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        // Also push to cloud if online (translate local user_id → Supabase user_id)
        if (useSupabase()) {
            JSONObject row = new JSONObject()
                .put("title", title).put("event_date", date).put("event_time", time)
                .put("category", category).put("location", location);
            Integer cloudCreator = getCloudUserId(createdBy);
            if (cloudCreator != null) row.put("created_by", cloudCreator);
            SupabaseClient.insert("events", row);
        }
        return localId;
    }

    public static boolean deleteEvent(int eventId) {
        // Always delete locally
        boolean localOk = false;
        try {
            PreparedStatement ps = Database.get().prepareStatement("DELETE FROM events WHERE event_id=?");
            ps.setInt(1, eventId); localOk = ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); }
        // Also delete from cloud if online
        if (useSupabase()) SupabaseClient.delete("events", "event_id=eq." + eventId);
        return localOk;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ANNOUNCEMENTS
    // ══════════════════════════════════════════════════════════════════════════

    public static List<DataStore.Announcement> getAllAnnouncements() {
        List<DataStore.Announcement> list = new ArrayList<>();
        if (useSupabase()) {
            JSONArray rows = SupabaseClient.select("announcements",
                "?select=*,poster:users(full_name)&order=created_at.desc");
            for (int i = 0; i < rows.length(); i++) list.add(mapAnnouncementFromJson(rows.getJSONObject(i)));
            return list;
        }
        try {
            ResultSet rs = Database.get().createStatement().executeQuery("""
                SELECT a.*,u.full_name AS poster_name FROM announcements a
                JOIN users u ON a.posted_by=u.user_id ORDER BY a.created_at DESC""");
            while (rs.next()) list.add(mapAnnouncementFromRs(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public static int addAnnouncement(String title, String body, int postedBy, String tag) {
        // Always write local first
        int localId = -1;
        try {
            PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO announcements (title,body,posted_by,tag) VALUES(?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,title); ps.setString(2,body); ps.setInt(3,postedBy); ps.setString(4,tag);
            ps.executeUpdate();
            ResultSet k = ps.getGeneratedKeys(); if (k.next()) localId = k.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        // Also push to cloud if online (translate local user_id → Supabase user_id)
        if (useSupabase()) {
            JSONObject row = new JSONObject()
                .put("title", title).put("body", body)
                .put("tag", tag);
            Integer cloudPoster = getCloudUserId(postedBy);
            if (cloudPoster != null) row.put("posted_by", cloudPoster);
            SupabaseClient.insert("announcements", row);
        }
        return localId;
    }

    public static boolean deleteAnnouncement(int annId) {
        boolean localOk = false;
        try {
            PreparedStatement ps = Database.get().prepareStatement("DELETE FROM announcements WHERE ann_id=?");
            ps.setInt(1, annId); localOk = ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); }
        if (useSupabase()) SupabaseClient.delete("announcements", "ann_id=eq." + annId);
        return localOk;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MESSAGES  (group channels + private DMs)
    // ══════════════════════════════════════════════════════════════════════════

    public static List<DataStore.Message> getMessages(String channel) {
        List<DataStore.Message> list = new ArrayList<>();
        if (useSupabase()) {
            JSONArray rows = SupabaseClient.select("messages",
                "?select=*,sender:users(full_name,role)&type=eq.channel&channel=eq."
                + channel + "&order=sent_at.asc&limit=200");
            for (int i = 0; i < rows.length(); i++) list.add(mapMessageFromJson(rows.getJSONObject(i)));
            return list;
        }
        try {
            PreparedStatement ps = Database.get().prepareStatement("""
                SELECT m.message_id,m.sender_id,u.full_name,u.role,m.content,
                       strftime('%H:%M',m.sent_at) AS sent_time
                FROM messages m JOIN users u ON m.sender_id=u.user_id
                WHERE m.type='channel' AND m.channel=? ORDER BY m.sent_at ASC LIMIT 200""");
            ps.setString(1, channel);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new DataStore.Message(rs.getInt("message_id"), rs.getInt("sender_id"),
                    rs.getString("full_name"), rs.getString("content"), rs.getString("sent_time"),
                    rs.getString("role").equals("FACULTY")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /** Get DM history between two users — from unified messages table (type='dm'). */
    public static List<DataStore.Message> getDirectMessages(int userA, int userB) {
        List<DataStore.Message> list = new ArrayList<>();
        if (useSupabase()) {
            Integer cloudA = normalizeToCloudUserId(userA);
            Integer cloudB = normalizeToCloudUserId(userB);
            if (cloudA == null || cloudB == null) return list;
            String filter = "?select=*,sender:users(full_name,role)&type=eq.dm" +
                "&or=(and(sender_id.eq." + cloudA + ",recipient_id.eq." + cloudB + ")," +
                "and(sender_id.eq." + cloudB + ",recipient_id.eq." + cloudA + "))" +
                "&order=sent_at.asc&limit=200";
            JSONArray rows = SupabaseClient.select("messages", filter);
            for (int i = 0; i < rows.length(); i++) list.add(mapMessageFromJson(rows.getJSONObject(i)));
            return list;
        }
        try {
            PreparedStatement ps = Database.get().prepareStatement("""
                SELECT m.message_id, m.sender_id, u.full_name, u.role, m.content,
                       strftime('%H:%M', m.sent_at) AS sent_time
                FROM messages m JOIN users u ON m.sender_id=u.user_id
                WHERE m.type='dm'
                  AND ((m.sender_id=? AND m.recipient_id=?)
                    OR (m.sender_id=? AND m.recipient_id=?))
                ORDER BY m.sent_at ASC LIMIT 200""");
            ps.setInt(1, userA); ps.setInt(2, userB);
            ps.setInt(3, userB); ps.setInt(4, userA);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new DataStore.Message(rs.getInt("message_id"), rs.getInt("sender_id"),
                    rs.getString("full_name"), rs.getString("content"), rs.getString("sent_time"),
                    rs.getString("role").equals("FACULTY")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public static int saveMessage(int senderId, String channel, String content) {
        // If we're online, write to Supabase first, then store locally as "synced".
        // This prevents SyncManager from re-uploading the same message later.
        if (useSupabase()) {
            Integer cloudSender = normalizeToCloudUserId(senderId);
            JSONObject row = new JSONObject()
                .put("type", "channel")
                .put("channel", channel)
                .put("content", content);
            if (cloudSender != null) row.put("sender_id", cloudSender);
            JSONObject created = SupabaseClient.insert("messages", row);
            int cloudId = created != null ? created.optInt("message_id", -1) : -1;
            if (cloudId > 0) {
                try {
                    PreparedStatement ps2 = Database.get().prepareStatement(
                        "INSERT OR IGNORE INTO messages (message_id,type,sender_id,channel,content,sent_at,synced) " +
                        "VALUES(?, 'channel', ?, ?, ?, datetime('now'), 1)");
                    ps2.setInt(1, cloudId);
                    ps2.setInt(2, senderId);
                    ps2.setString(3, channel);
                    ps2.setString(4, content);
                    ps2.executeUpdate();
                } catch (SQLException ignored) {}
                return cloudId;
            }
        }
        // Offline (or cloud insert failed): store locally as "not yet synced".
        int localId = -1;
        try {
            PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO messages (type,sender_id,channel,content,synced) VALUES('channel',?,?,?,0)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, senderId); ps.setString(2, channel); ps.setString(3, content);
            ps.executeUpdate();
            ResultSet k = ps.getGeneratedKeys(); localId = k.next() ? k.getInt(1) : -1;
        } catch (SQLException e) { e.printStackTrace(); }
        return localId;
    }

    public static int saveDirectMessage(int senderId, int recipientId, String content) {
        // Online: write to Supabase first, then store locally as synced.
        if (useSupabase()) {
            Integer cloudSender = normalizeToCloudUserId(senderId);
            Integer cloudRecip  = normalizeToCloudUserId(recipientId);
            JSONObject row = new JSONObject()
                .put("type", "dm")
                .put("content", content);
            if (cloudSender != null) row.put("sender_id", cloudSender);
            if (cloudRecip  != null) row.put("recipient_id", cloudRecip);
            JSONObject created = SupabaseClient.insert("messages", row);
            int cloudId = created != null ? created.optInt("message_id", -1) : -1;
            if (cloudId > 0) {
                try {
                    PreparedStatement ps2 = Database.get().prepareStatement(
                        "INSERT OR IGNORE INTO messages (message_id,type,sender_id,recipient_id,content,sent_at,synced) " +
                        "VALUES(?, 'dm', ?, ?, ?, datetime('now'), 1)");
                    ps2.setInt(1, cloudId);
                    ps2.setInt(2, senderId);
                    ps2.setInt(3, recipientId);
                    ps2.setString(4, content);
                    ps2.executeUpdate();
                } catch (SQLException ignored) {}
                return cloudId;
            }
        }
        // Offline (or cloud insert failed): store locally as not yet synced.
        int localId = -1;
        try {
            PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO messages (type,sender_id,recipient_id,content,synced) VALUES('dm',?,?,?,0)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, senderId); ps.setInt(2, recipientId); ps.setString(3, content);
            ps.executeUpdate();
            ResultSet k = ps.getGeneratedKeys(); localId = k.next() ? k.getInt(1) : -1;
        } catch (SQLException e) { e.printStackTrace(); }
        return localId;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAPPERS — Supabase JSON  ↔  DataStore model
    // ══════════════════════════════════════════════════════════════════════════

    private static DataStore.User mapUserFromJson(JSONObject o) {
        return new DataStore.User(
            o.optInt("user_id", o.optInt("id", 0)),
            o.optString("full_name"),
            o.optString("email"),
            o.optString("role", "STUDENT"),
            o.optString("department", ""),
            o.optInt("semester", 0));
    }

    private static DataStore.Subject mapSubjectFromJson(JSONObject o) {
        return new DataStore.Subject(
            o.optInt("subject_id"), o.optString("name"), o.optString("code"),
            o.optString("department"), o.optInt("credit_hours", 3));
    }

    private static DataStore.Note mapNoteFromJson(JSONObject o) {
        String uploaderName = "Unknown";
        if (o.has("uploader") && !o.isNull("uploader")) {
            Object up = o.get("uploader");
            if (up instanceof JSONObject) uploaderName = ((JSONObject)up).optString("full_name", "Unknown");
            else if (up instanceof JSONArray && ((JSONArray)up).length() > 0)
                uploaderName = ((JSONArray)up).getJSONObject(0).optString("full_name", "Unknown");
        }
        return new DataStore.Note(
            o.optInt("resource_id"), o.optInt("subject_id"),
            o.optString("file_name"), o.optString("file_type"),
            o.optString("file_size", "—"), uploaderName,
            o.optString("uploaded_at", "").substring(0, Math.min(10, o.optString("uploaded_at","").length())),
            o.optBoolean("approved", false),
            o.optString("file_path", ""));
    }

    private static DataStore.Event mapEventFromJson(JSONObject o) {
        return new DataStore.Event(
            o.optInt("event_id"), o.optString("title"),
            o.optString("event_date"), o.optString("event_time"),
            o.optString("category"), o.optString("location"),
            o.optInt("created_by"));
    }

    private static DataStore.Announcement mapAnnouncementFromJson(JSONObject o) {
        String posterName = "Admin";
        if (o.has("poster") && !o.isNull("poster")) {
            Object p = o.get("poster");
            if (p instanceof JSONObject) posterName = ((JSONObject)p).optString("full_name","Admin");
            else if (p instanceof JSONArray && ((JSONArray)p).length() > 0)
                posterName = ((JSONArray)p).getJSONObject(0).optString("full_name","Admin");
        }
        String date = o.optString("created_at", "");
        if (date.length() > 10) date = date.substring(0, 10);
        return new DataStore.Announcement(
            o.optInt("ann_id"), o.optString("title"), o.optString("body"),
            posterName, date, o.optString("tag", "GENERAL"));
    }


    /** Delete a single channel message by ID. Only the sender (or admin) can delete. */
    public static boolean deleteMessage(int messageId) {
        if (useSupabase()) SupabaseClient.delete("messages", "message_id=eq." + messageId);
        try {
            PreparedStatement ps = Database.get().prepareStatement(
                "DELETE FROM messages WHERE message_id=?");
            ps.setInt(1, messageId); return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    /** Delete a single DM by ID. */
    public static boolean deleteDM(int dmId) {
        if (useSupabase()) SupabaseClient.delete("messages", "message_id=eq." + dmId + "&type=eq.dm");
        try {
            PreparedStatement ps = Database.get().prepareStatement(
                "DELETE FROM messages WHERE message_id=? AND type='dm'");
            ps.setInt(1, dmId); return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    /** Delete all messages from a channel that were sent by the given user (local id + cloud id). */
    public static void deleteMyMessagesInChannel(String channel, int localUserId, int cloudUserId) {
        try {
            PreparedStatement ps = Database.get().prepareStatement(
                "DELETE FROM messages WHERE type='channel' AND channel=? AND sender_id IN (?,?)");
            ps.setString(1, channel); ps.setInt(2, localUserId); ps.setInt(3, cloudUserId);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
        if (useSupabase() && cloudUserId > 0) {
            SupabaseClient.delete("messages",
                "type=eq.channel&channel=eq." + channel + "&sender_id=eq." + cloudUserId);
        }
    }

    /** Delete ALL messages in a channel (admin only). */
    public static void clearChannel(String channel) {
        try {
            PreparedStatement ps = Database.get().prepareStatement(
                "DELETE FROM messages WHERE type='channel' AND channel=?");
            ps.setString(1, channel); ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
        if (useSupabase()) {
            SupabaseClient.delete("messages", "type=eq.channel&channel=eq." + channel);
        }
    }

    /** Delete all DMs between two users in both directions. */
    public static void clearDMHistory(int userA, int userB, int cloudA, int cloudB) {
        // Local — unified messages table
        try {
            PreparedStatement ps = Database.get().prepareStatement(
                "DELETE FROM messages WHERE type='dm' AND " +
                "((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?))");
            ps.setInt(1, userA); ps.setInt(2, userB);
            ps.setInt(3, userB); ps.setInt(4, userA);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
        // Cloud
        if (useSupabase() && cloudA > 0 && cloudB > 0) {
            SupabaseClient.delete("messages",
                "type=eq.dm&or=(and(sender_id.eq." + cloudA + ",recipient_id.eq." + cloudB + ")," +
                "and(sender_id.eq." + cloudB + ",recipient_id.eq." + cloudA + "))");
        }
    }

    /**
     * SQLite-only message fetch — used as a fallback when Supabase returns empty
     * (e.g. messages exist locally but haven't been synced to cloud yet).
     * Pass channel != null for channel messages; otherwise uses userA/userB for DM.
     */
    public static List<DataStore.Message> getMessagesLocal(String channel, int userA, int userB) {
        List<DataStore.Message> list = new ArrayList<>();
        try {
            PreparedStatement ps;
            if (channel != null) {
                ps = Database.get().prepareStatement("""
                    SELECT m.message_id,m.sender_id,u.full_name,u.role,m.content,
                           strftime('%H:%M',m.sent_at) AS sent_time
                    FROM messages m JOIN users u ON m.sender_id=u.user_id
                    WHERE m.type='channel' AND m.channel=? ORDER BY m.sent_at ASC LIMIT 200""");
                ps.setString(1, channel);
            } else {
                ps = Database.get().prepareStatement("""
                    SELECT m.message_id, m.sender_id, u.full_name, u.role, m.content,
                           strftime('%H:%M', m.sent_at) AS sent_time
                    FROM messages m JOIN users u ON m.sender_id=u.user_id
                    WHERE m.type='dm'
                      AND ((m.sender_id=? AND m.recipient_id=?)
                        OR (m.sender_id=? AND m.recipient_id=?))
                    ORDER BY m.sent_at ASC LIMIT 200""");
                ps.setInt(1, userA); ps.setInt(2, userB);
                ps.setInt(3, userB); ps.setInt(4, userA);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new DataStore.Message(rs.getInt("message_id"), rs.getInt("sender_id"),
                    rs.getString("full_name"), rs.getString("content"), rs.getString("sent_time"),
                    rs.getString("role").equals("FACULTY")));
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

        private static DataStore.Message mapMessageFromJson(JSONObject o) {
        String senderName = "Unknown";
        String role = "STUDENT";
        if (o.has("sender") && !o.isNull("sender")) {
            Object s = o.get("sender");
            if (s instanceof JSONObject) {
                senderName = ((JSONObject)s).optString("full_name","Unknown");
                role = ((JSONObject)s).optString("role","STUDENT");
            } else if (s instanceof JSONArray && ((JSONArray)s).length() > 0) {
                senderName = ((JSONArray)s).getJSONObject(0).optString("full_name","Unknown");
                role = ((JSONArray)s).getJSONObject(0).optString("role","STUDENT");
            }
        }
        String sentAt = o.optString("sent_at", "");
        if (sentAt.length() > 16) sentAt = sentAt.substring(11, 16); // extract HH:MM
        return new DataStore.Message(
            o.optInt("message_id", o.optInt("dm_id", 0)),
            o.optInt("sender_id"), senderName,
            o.optString("content"), sentAt,
            "FACULTY".equals(role));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAPPERS — SQLite ResultSet  ↔  DataStore model
    // ══════════════════════════════════════════════════════════════════════════

    private static DataStore.User mapUserFromRs(ResultSet rs) throws SQLException {
        DataStore.User u = new DataStore.User(rs.getInt("user_id"), rs.getString("full_name"),
            rs.getString("email"), rs.getString("role"),
            rs.getString("department"), rs.getInt("semester"));
        try { u.avatarPath = rs.getString("avatar_path"); } catch (SQLException ignored) {}
        try { u.lastSeen  = rs.getString("last_seen");   } catch (SQLException ignored) {}
        return u;
    }

    /** Call on login and periodically (heartbeat) so other users see accurate online status. */
    public static void updateLastSeen(int userId) {
        String now = java.time.LocalDateTime.now()
            .truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
        if (useSupabase()) {
            String myUuid = SupabaseClient.getUserId();
            if (myUuid != null)
                SupabaseClient.update("users", "user_uuid=eq." + myUuid,
                    new JSONObject().put("last_seen", now));
        }
        // Always update local SQLite regardless of mode
        try {
            PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE users SET last_seen=? WHERE user_id=?");
            ps.setString(1, now); ps.setInt(2, userId); ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static boolean updateAvatar(int userId, String avatarPath) {
        if (useSupabase()) {
            String myUuid = SupabaseClient.getUserId();
            if (myUuid == null) return false;
            return SupabaseClient.update("users", "user_uuid=eq." + myUuid,
                new JSONObject().put("avatar_path", avatarPath)) != null;
        }
        try {
            PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE users SET avatar_path=? WHERE user_id=?");
            ps.setString(1, avatarPath); ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    private static DataStore.Subject mapSubjectFromRs(ResultSet rs) throws SQLException {
        return new DataStore.Subject(rs.getInt("subject_id"), rs.getString("name"),
            rs.getString("code"), rs.getString("department"), rs.getInt("credit_hours"));
    }

    private static DataStore.Note mapNoteFromRs(ResultSet rs) throws SQLException {
        return new DataStore.Note(rs.getInt("resource_id"), rs.getInt("subject_id"),
            rs.getString("file_name"), rs.getString("file_type"), rs.getString("file_size"),
            rs.getString("uploader_name"), rs.getString("uploaded_at"),
            rs.getInt("approved")==1, rs.getString("file_path"));
    }

    private static DataStore.Event mapEventFromRs(ResultSet rs) throws SQLException {
        return new DataStore.Event(rs.getInt("event_id"), rs.getString("title"),
            rs.getString("event_date"), rs.getString("event_time"),
            rs.getString("category"), rs.getString("location"), rs.getInt("created_by"));
    }

    private static DataStore.Announcement mapAnnouncementFromRs(ResultSet rs) throws SQLException {
        String date = rs.getString("created_at"); if (date!=null&&date.length()>10) date=date.substring(0,10);
        return new DataStore.Announcement(rs.getInt("ann_id"), rs.getString("title"),
            rs.getString("body"), rs.getString("poster_name"), date, rs.getString("tag"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private static String mimeForType(String type) {
        return switch (type.toUpperCase()) {
            case "PDF"  -> "application/pdf";
            case "PPTX" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default     -> "application/octet-stream";
        };
    }
}