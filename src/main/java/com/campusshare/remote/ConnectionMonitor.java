package com.campusshare.remote;

import com.campusshare.db.DAO;
import com.campusshare.db.SyncManager;
import com.campusshare.data.DataStore;

import org.json.JSONObject;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background connectivity monitor.
 *
 * Pings the Supabase host every 15 seconds.
 *
 * When connection is RESTORED:
 *   1. Sets FORCE_OFFLINE = false
 *   2. Re-authenticates the current user
 *   3. Runs SyncManager to push any data created while offline
 *   4. Notifies all registered listeners so the UI can update its badge
 *
 * When connection is LOST:
 *   1. Sets FORCE_OFFLINE = true so all DAO calls use SQLite
 *   2. Notifies listeners so the UI shows the offline badge
 *
 * This is how real applications handle connectivity.
 */
public class ConnectionMonitor {

    public interface Listener {
        void onConnectionChanged(boolean nowOnline);
    }

    private static final int CHECK_INTERVAL_MS = 15_000;  // check every 15s
    private static final int TIMEOUT_MS         = 3_000;   // 3s timeout per check

    private static Timer timer;
    private static final AtomicBoolean currentlyOnline = new AtomicBoolean(false);
    private static final List<Listener> listeners = new ArrayList<>();

    // Credentials for re-auth after reconnect
    private static String savedEmail;
    private static String savedPassword;

    public static void saveCredentials(String email, String password) {
        savedEmail = email;
        savedPassword = password;
    }

    public static void addListener(Listener l) {
        synchronized (listeners) { listeners.add(l); }
    }

    public static boolean isOnline() { return currentlyOnline.get(); }

    /**
     * Start monitoring. Call once after login.
     * initialOnline should match the connection state at login time.
     */
    public static synchronized void start(boolean initialOnline) {
        if (timer != null) timer.cancel();
        currentlyOnline.set(initialOnline);
        SupabaseConfig.FORCE_OFFLINE = !initialOnline;

        timer = new Timer("CampusShare-ConnMonitor", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() { check(); }
        }, CHECK_INTERVAL_MS, CHECK_INTERVAL_MS);
    }

    public static synchronized void stop() {
        if (timer != null) { timer.cancel(); timer = null; }
    }

    private static void check() {
        boolean reachable = canReachSupabase();
        boolean wasOnline = currentlyOnline.get();

        if (reachable && !wasOnline) {
            // Connection RESTORED
            currentlyOnline.set(true);
            SupabaseConfig.FORCE_OFFLINE = false;
            System.out.println("[ConnMonitor] Connection restored — going online.");
            handleReconnect();
            notifyListeners(true);

        } else if (!reachable && wasOnline) {
            // Connection LOST
            currentlyOnline.set(false);
            SupabaseConfig.FORCE_OFFLINE = true;
            System.out.println("[ConnMonitor] Connection lost — switching to offline mode.");
            notifyListeners(false);
        }
    }

    private static void handleReconnect() {
        System.out.println("[ConnMonitor] Internet restored — attempting cloud reconnect...");

        // Step 1: Try to get a Supabase session.
        // Only sign in via Supabase if credentials exist.
        // Demo accounts (admin@campus.edu etc.) only exist in SQLite,
        // so we ONLY try Supabase login here — do NOT fall back to local.
        boolean cloudSession = false;
        if (savedEmail != null && savedPassword != null && SupabaseConfig.isConfigured()) {
            try {
                JSONObject session = SupabaseClient.signIn(savedEmail, savedPassword);
                if (session != null && session.has("access_token")) {
                    String token   = session.getString("access_token");
                    String refresh = session.optString("refresh_token", null);
                    int    exp     = session.optInt("expires_in", 3600);
                    String uid     = session.getJSONObject("user").getString("id");
                    SupabaseClient.setSession(token, refresh, uid, exp);
                    cloudSession = true;
                    System.out.println("[ConnMonitor] Cloud session established.");
                } else {
                    // Supabase account does not exist for this user yet.
                    // This is normal if they only have a local demo account.
                    System.out.println("[ConnMonitor] No Supabase account for " + savedEmail
                        + " — sync skipped. Sign Up in the app to create a cloud account.");
                }
            } catch (Exception e) {
                System.err.println("[ConnMonitor] Re-auth error: " + e.getMessage());
            }
        }

        // Step 2: Only sync if we have a real cloud session.
        // Without a session, all inserts hit the RLS wall (HTTP 401).
        if (cloudSession) {
            SyncManager.SyncResult result = SyncManager.syncLocalToCloud();
            if (result.anyChanged()) {
                System.out.println("[ConnMonitor] Auto-sync complete: " + result.summary());
            } else {
                System.out.println("[ConnMonitor] Already in sync — nothing to push.");
            }
            // Step 3: Reload fresh cloud data
            DataStore.loadAll();
        } else {
            System.out.println("[ConnMonitor] Skipping sync — no cloud session.");
        }
    }

    private static boolean canReachSupabase() {
        if (!SupabaseConfig.isConfigured()) return false;
        try {
            String host = SupabaseConfig.SUPABASE_URL
                .replace("https://", "").replace("http://", "")
                .split("/")[0];
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, 443), TIMEOUT_MS);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void notifyListeners(boolean online) {
        synchronized (listeners) {
            for (Listener l : listeners) {
                try { l.onConnectionChanged(online); } catch (Exception ignored) {}
            }
        }
    }
}
