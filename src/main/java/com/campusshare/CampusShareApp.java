package com.campusshare;

import com.campusshare.chat.ChatServer;
import com.campusshare.db.DAO;
import com.campusshare.data.DataStore;
import com.campusshare.db.Database;
import com.campusshare.remote.ConnectionMonitor;
import com.campusshare.remote.CredentialStore;
import com.campusshare.remote.SupabaseConfig;
import com.campusshare.ui.LoginWindow;
import com.campusshare.ui.MainWindow;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;

/**
 * Boot sequence — works like a real application:
 *
 * 1. Always init SQLite (local storage, works with no internet)
 * 2. Test Supabase connectivity
 *    - Reachable  → FORCE_OFFLINE = false  → try cloud first for all operations
 *    - Unreachable→ FORCE_OFFLINE = true   → use SQLite only
 * 3. Start ConnectionMonitor — watches for connectivity changes in the background
 *    - Lost:     switches to offline automatically
 *    - Restored: re-authenticates + syncs offline data + switches back to online
 * 4. Show LoginWindow (no mode-choice dialog)
 * 5. After login, start heartbeat so online status is real
 */
public class CampusShareApp {

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");

        // ── 1. SQLite — always ready ──────────────────────────────────────────
        try {
            Database.get();
            System.out.println("[CampusShare] Local database ready.");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                "Failed to initialize local database:\n" + e.getMessage(),
                "Startup Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        // ── 2. Auto-detect connectivity ───────────────────────────────────────
        boolean online = SupabaseConfig.isConfigured() && canReach();
        SupabaseConfig.FORCE_OFFLINE = !online;
        System.out.println("[CampusShare] Mode: " + (online ? "ONLINE (Supabase)" : "OFFLINE (SQLite)"));

        // ── 3. Load local data ────────────────────────────────────────────────
        DataStore.loadAll();

        // ── 4. Chat server ────────────────────────────────────────────────────
        ChatServer.start();

        // ── 5. UI ─────────────────────────────────────────────────────────────
        final boolean startOnline = online;
        SwingUtilities.invokeLater(() -> {
            try {
                FlatLightLaf.setup();
                UIManager.put("defaultFont",      new Font("SansSerif", Font.PLAIN, 13));
                UIManager.put("Button.arc",        10);
                UIManager.put("Component.arc",     10);
                UIManager.put("TextComponent.arc", 10);
                UIManager.put("ScrollBar.thumbArc",    999);
                UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
                UIManager.put("ScrollBar.width",       8);
            } catch (Exception e) {
                System.err.println("FlatLaf not available");
            }

            // No mode-choice dialog — straight to login
            new LoginWindow(() -> SwingUtilities.invokeLater(() -> {

                DataStore.User me = DataStore.getCurrentUser();
                if (me == null) return;

                // Save credentials for auto re-auth on reconnect
                // LoginWindow stores them in CredentialStore after successful login
                ConnectionMonitor.saveCredentials(
                    CredentialStore.email, CredentialStore.password);

                // Update last_seen immediately
                DAO.updateLastSeen(me.id);

                // Start background connection monitor
                ConnectionMonitor.start(startOnline);

                // Heartbeat every 60s
                java.util.Timer hb = new java.util.Timer("CampusShare-Heartbeat", true);
                hb.scheduleAtFixedRate(new java.util.TimerTask() {
                    public void run() {
                        DataStore.User u = DataStore.getCurrentUser();
                        if (u != null) DAO.updateLastSeen(u.id);
                    }
                }, 60_000, 60_000);

                new MainWindow();

            })).setVisible(true);
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ConnectionMonitor.stop();
            ChatServer.stop();
            Database.close();
        }));
    }

    /** Quick reachability check — try opening a TCP socket to Supabase on port 443. */
    private static boolean canReach() {
        try {
            String host = SupabaseConfig.SUPABASE_URL
                .replace("https://", "").replace("http://", "")
                .split("/")[0];
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(host, 443), 3000);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
