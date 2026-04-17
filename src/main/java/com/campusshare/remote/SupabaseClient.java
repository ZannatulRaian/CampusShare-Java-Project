package com.campusshare.remote;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Low-level OkHttp wrapper for Supabase REST API.
 * All methods are synchronous (called from background threads in DAO).
 */
public class SupabaseClient {

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build();

    // Per-session JWT (set after login)
    private static volatile String accessToken   = null;
    private static volatile String refreshToken  = null;
    private static volatile String currentUserId = null;
    private static volatile long   tokenExpiresAt = 0; // epoch millis

    /** Full session set — called after login or token refresh. */
    public static void setSession(String jwt, String refresh, String userId, int expiresIn) {
        accessToken    = jwt;
        refreshToken   = refresh;
        currentUserId  = userId;
        // Refresh 60 s before actual expiry so calls never hit a stale token
        tokenExpiresAt = System.currentTimeMillis() + ((long)(expiresIn - 60)) * 1000L;
    }

    /** Backwards-compat overload (used by callers that don't have refresh token yet). */
    public static void setSession(String jwt, String userId) {
        setSession(jwt, null, userId, 3600);
    }

    public static void clearSession() {
        accessToken = null; refreshToken = null;
        currentUserId = null; tokenExpiresAt = 0;
    }
    public static String  getUserId()  { return currentUserId; }
    public static boolean hasSession() { return accessToken != null; }

    /**
     * Silently refreshes the JWT when it is expired or about to expire.
     * Called automatically before every authenticated API request.
     * Returns false if the session has truly expired and the user must re-login.
     */
    public static synchronized boolean ensureFreshToken() {
        if (accessToken == null) return false;
        if (System.currentTimeMillis() < tokenExpiresAt) return true; // still valid
        if (refreshToken == null) { clearSession(); return false; }

        JSONObject body = new JSONObject().put("refresh_token", refreshToken);
        Request req = new Request.Builder()
            .url(SupabaseConfig.AUTH_URL + "/token?grant_type=refresh_token")
            .addHeader("apikey",       SupabaseConfig.ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(body.toString(), JSON_TYPE))
            .build();
        try (Response r = HTTP.newCall(req).execute()) {
            if (r.isSuccessful() && r.body() != null) {
                JSONObject resp = new JSONObject(r.body().string());
                if (resp.has("access_token")) {
                    setSession(
                        resp.getString("access_token"),
                        resp.optString("refresh_token", refreshToken),
                        currentUserId,
                        resp.optInt("expires_in", 3600)
                    );
                    System.out.println("[Auth] Token refreshed.");
                    return true;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.err.println("[Auth] Token refresh failed — user must re-login.");
        clearSession();
        return false;
    }

    // ── Auth endpoints ────────────────────────────────────────────────────────

    /** Sign up with email/password. Returns the session JSON or null on error. */
    public static JSONObject signUp(String email, String password, JSONObject metadata) {
        JSONObject body = new JSONObject()
            .put("email", email)
            .put("password", password)
            .put("data", metadata);
        return postAuth("/signup", body);
    }

    /** Sign in. Returns session JSON (contains access_token, user) or null. */
    public static JSONObject signIn(String email, String password) {
        JSONObject body = new JSONObject()
            .put("email", email)
            .put("password", password);
        return postAuth("/token?grant_type=password", body);
    }

    /** Sign out (revokes token server-side). */
    public static void signOut() {
        if (accessToken == null) return;
        Request req = new Request.Builder()
            .url(SupabaseConfig.AUTH_URL + "/logout")
            .addHeader("Authorization", "Bearer " + accessToken)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .post(RequestBody.create("", JSON_TYPE))
            .build();
        try (Response r = HTTP.newCall(req).execute()) { /* ignore */ }
        catch (IOException ignored) {}
        clearSession();
    }

    private static JSONObject postAuth(String path, JSONObject body) {
        Request req = new Request.Builder()
            .url(SupabaseConfig.AUTH_URL + path)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(body.toString(), JSON_TYPE))
            .build();
        return executeJson(req);
    }

    /**
     * Changes the password in Supabase Auth (not the users table).
     * Requires a valid access token — re-authenticate first to verify the old password.
     * This is the correct way to change passwords with Supabase.
     */
    public static boolean updateAuthPassword(String newPassword) {
        if (!ensureFreshToken()) return false;
        JSONObject body = new JSONObject().put("password", newPassword);
        Request req = new Request.Builder()
            .url(SupabaseConfig.AUTH_URL + "/user")
            .addHeader("apikey",        SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer " + accessToken)
            .addHeader("Content-Type",  "application/json")
            .put(RequestBody.create(body.toString(), JSON_TYPE))
            .build();
        JSONObject resp = executeJson(req);
        return resp != null && resp.has("id");
    }

    // ── REST CRUD helpers ─────────────────────────────────────────────────────

    /** SELECT — returns JSONArray. query = "?select=*&order=created_at.desc" etc. */
    public static JSONArray select(String table, String query) {
        Request req = authRequest(
            new Request.Builder()
                .url(SupabaseConfig.REST_URL + "/" + table + (query != null ? query : ""))
                .addHeader("Accept", "application/json")
        ).get().build();
        try (Response r = HTTP.newCall(req).execute()) {
            if (r.isSuccessful() && r.body() != null) {
                String txt = r.body().string();
                return new JSONArray(txt);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return new JSONArray();
    }

    /** SELECT — returns first match as JSONObject or null. */
    public static JSONObject selectOne(String table, String query) {
        JSONArray arr = select(table, query + "&limit=1");
        return arr.length() > 0 ? arr.getJSONObject(0) : null;
    }

    /** INSERT — returns the created row as JSONObject or null. */
    public static JSONObject insert(String table, JSONObject row) {
        Request req = authRequest(
            new Request.Builder()
                .url(SupabaseConfig.REST_URL + "/" + table)
                .addHeader("Prefer", "return=representation")
        ).post(RequestBody.create(row.toString(), JSON_TYPE)).build();
        JSONArray arr = executeJsonArray(req);
        return arr != null && arr.length() > 0 ? arr.getJSONObject(0) : null;
    }

    /** UPDATE rows matching filter. Returns updated rows. */
    public static JSONArray update(String table, String filter, JSONObject patch) {
        Request req = authRequest(
            new Request.Builder()
                .url(SupabaseConfig.REST_URL + "/" + table + "?" + filter)
                .addHeader("Prefer", "return=representation")
        ).patch(RequestBody.create(patch.toString(), JSON_TYPE)).build();
        return executeJsonArray(req);
    }

    /** DELETE rows matching filter. */
    public static boolean delete(String table, String filter) {
        Request req = authRequest(
            new Request.Builder()
                .url(SupabaseConfig.REST_URL + "/" + table + "?" + filter)
        ).delete().build();
        try (Response r = HTTP.newCall(req).execute()) {
            return r.isSuccessful();
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ── Storage ───────────────────────────────────────────────────────────────

    /** Upload bytes to Supabase Storage. Returns public URL or null. */
    /** Ensures the storage bucket exists (creates it if not). Call once on login. */
    public static void ensureBucketExists(String bucket) {
        // Try to create the bucket — if it already exists, Supabase returns an error which we ignore
        try {
            org.json.JSONObject body = new org.json.JSONObject()
                .put("id", bucket)
                .put("name", bucket)
                .put("public", true);
            Request req = new Request.Builder()
                .url(SupabaseConfig.STORAGE_URL + "/bucket")
                .addHeader("apikey", SupabaseConfig.ANON_KEY)
                .addHeader("Authorization", "Bearer " + (accessToken != null ? accessToken : SupabaseConfig.ANON_KEY))
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .build();
            try (Response r = HTTP.newCall(req).execute()) {
                // 200 = created, 409 = already exists — both are fine
                System.out.println("[Storage] Bucket '" + bucket + "': " + r.code());
            }
        } catch (Exception e) { System.err.println("[Storage] Bucket check: " + e.getMessage()); }
    }

    public static String uploadFile(String bucket, String path, byte[] data, String mimeType) {
        RequestBody body = RequestBody.create(data, MediaType.get(mimeType));
        Request req = authRequest(
            new Request.Builder()
                .url(SupabaseConfig.STORAGE_URL + "/object/" + bucket + "/" + path)
                .addHeader("x-upsert", "true")
        ).post(body).build();
        JSONObject resp = executeJson(req);
        if (resp != null && resp.has("Key")) {
            return SupabaseConfig.STORAGE_URL + "/object/public/" + bucket + "/" + path;
        }
        return null;
    }

    /** Download a file. Returns bytes or null. */
    public static byte[] downloadFile(String url) {
        Request req = new Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .get().build();
        try (Response r = HTTP.newCall(req).execute()) {
            if (r.isSuccessful() && r.body() != null) return r.body().bytes();
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Adds auth headers and silently refreshes the token if needed.
     * Throws IllegalStateException if the session has expired (caller handles it).
     */
    private static Request.Builder authRequest(Request.Builder b) {
        if (!ensureFreshToken()) {
            // Session expired — notify the app via a flag; requests will get 401 anyway
            System.err.println("[Auth] Session expired — user needs to re-login.");
        }
        b.addHeader("apikey", SupabaseConfig.ANON_KEY);
        if (accessToken != null) b.addHeader("Authorization", "Bearer " + accessToken);
        return b;
    }

    /**
     * Executes a request and returns the JSON body, or null.
     * Logs the HTTP status + error body if the request was not successful.
     */
    private static JSONObject executeJson(Request req) {
        try (Response r = HTTP.newCall(req).execute()) {
            String txt = r.body() != null ? r.body().string() : "";
            if (r.isSuccessful()) {
                if (!txt.isBlank() && txt.trim().startsWith("{")) return new JSONObject(txt);
                return null;
            }
            System.err.println("[HTTP " + r.code() + "] " + req.url().encodedPath() + " → " + txt);
            if (r.code() == 401) clearSession(); // token definitely dead
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    /**
     * Executes a request and returns the JSON array body.
     * Returns an empty array (never null) on error, and logs non-2xx status.
     */
    private static JSONArray executeJsonArray(Request req) {
        try (Response r = HTTP.newCall(req).execute()) {
            String txt = r.body() != null ? r.body().string() : "";
            if (r.isSuccessful()) {
                if (txt.trim().startsWith("[")) return new JSONArray(txt);
                if (txt.trim().startsWith("{")) {
                    JSONArray a = new JSONArray(); a.put(new JSONObject(txt)); return a;
                }
                return new JSONArray();
            }
            System.err.println("[HTTP " + r.code() + "] " + req.url().encodedPath() + " → " + txt);
            if (r.code() == 401) clearSession();
        } catch (Exception e) { e.printStackTrace(); }
        return new JSONArray();
    }
}
