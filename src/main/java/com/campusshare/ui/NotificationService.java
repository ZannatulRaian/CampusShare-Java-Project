package com.campusshare.ui;

import com.campusshare.data.DataStore;
import com.campusshare.db.DAO;
import com.campusshare.remote.RealtimeClient;
import com.campusshare.remote.SupabaseConfig;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Live notification service.
 *
 * PRIMARY:  Supabase Realtime WebSocket — instant push when cloud is available.
 * FALLBACK: SQLite polling every 5 s — works fully offline / local-only mode.
 *
 * Toast popups appear bottom-right. Bell badge tracks unread count.
 */
public class NotificationService {

    private static volatile NotificationService instance;

    private final java.util.List<Consumer<Integer>> badgeListeners = new CopyOnWriteArrayList<>();
    private volatile ScheduledExecutorService poller = newPoller();

    private RealtimeClient realtimeClient;
    private int            lastSeenId  = 0;   // highest notif id we've shown a toast for
    private int            userId;
    private JFrame         ownerFrame;
    private boolean        realtimeConnected = false;

    private NotificationService() {}

    private static ScheduledExecutorService newPoller() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CS-NotifPoll"); t.setDaemon(true); return t;
        });
    }

    public static NotificationService get() {
        if (instance == null) {
            synchronized (NotificationService.class) {
                if (instance == null) instance = new NotificationService();
            }
        }
        return instance;
    }

    public void start(int userId, JFrame owner) {
        this.userId     = userId;
        this.ownerFrame = owner;
        // Recreate the executor if it was shut down by a previous stop()
        if (poller.isShutdown()) {
            poller = newPoller();
        }

        // Seed lastSeenId to the highest existing notification ID (read or unread).
        // This prevents toasting old notifications on every login.
        // The badge still correctly shows the unread COUNT separately.
        // Only notifications that arrive AFTER login will show toasts.
        try {
            java.sql.PreparedStatement ps = com.campusshare.db.Database.get()
                .prepareStatement("SELECT MAX(id) FROM notifications WHERE user_id=?");
            ps.setInt(1, userId);
            java.sql.ResultSet rs = ps.executeQuery();
            lastSeenId = rs.next() ? rs.getInt(1) : 0;
        } catch (Exception ignored) {}

        // ── Primary: Supabase Realtime ────────────────────────────────────
        boolean hasCloud = SupabaseConfig.SUPABASE_URL != null && !SupabaseConfig.SUPABASE_URL.isEmpty()
            && SupabaseConfig.ANON_KEY != null && !SupabaseConfig.ANON_KEY.isEmpty();

        if (hasCloud) {
            try {
                realtimeClient = new RealtimeClient();
                // Subscribe to notifications table — fires on INSERT
                realtimeClient.onTableChange("notifications", row -> {
                    // Only handle rows addressed to this user
                    int targetUser = row.optInt("user_id", -1);
                    if (targetUser != userId) return;
                    // Re-read from DB to get full row (Supabase sends minimal payload)
                    SwingUtilities.invokeLater(this::pollOnce);
                });
                // Also subscribe to messages for live chat updates
                realtimeClient.onTableChange("messages", row -> {
                    SwingUtilities.invokeLater(() -> {
                        DataStore.reloadAnnouncements(); // triggers panel refresh if open
                    });
                });
                realtimeClient.onTableChange("announcements", row ->
                    SwingUtilities.invokeLater(DataStore::reloadAnnouncements));
                realtimeClient.onTableChange("events", row ->
                    SwingUtilities.invokeLater(DataStore::reloadEvents));
                realtimeClient.connect();
                realtimeConnected = true;
                System.out.println("[NotifService] Supabase Realtime connected — live push active");
            } catch (Exception e) {
                System.err.println("[NotifService] Realtime failed, using polling: " + e.getMessage());
                realtimeConnected = false;
            }
        }

        // ── Fallback / supplement: SQLite polling every 5 s ──────────────
        // Always run polling regardless (handles local-only mode & missed WS frames)
        poller.scheduleAtFixedRate(this::pollOnce, 3, 5, TimeUnit.SECONDS);
    }

    public void stop() {
        poller.shutdownNow();
        if (realtimeClient != null) { realtimeClient.disconnect(); realtimeClient = null; }
        // Reset state so next login gets a clean slate
        userId = 0;
        lastSeenId = 0;
        realtimeConnected = false;
        ownerFrame = null;
        badgeListeners.clear();
        // Reset singleton so next get() creates a fresh instance with a live executor
        synchronized (NotificationService.class) { instance = null; }
    }

    private void pollOnce() {
        if (userId <= 0) return;
        try {
            List<DataStore.Notification> notifs = DAO.getNotifications(userId, 30);
            int maxId = lastSeenId;
            java.util.List<DataStore.Notification> newOnes = new java.util.ArrayList<>();
            for (DataStore.Notification n : notifs) {
                if (n.id > lastSeenId) { newOnes.add(n); maxId = Math.max(maxId, n.id); }
            }
            if (!newOnes.isEmpty()) {
                lastSeenId = maxId;
                int count = Math.min(newOnes.size(), 3);
                for (int i = 0; i < count; i++) {
                    final DataStore.Notification n = newOnes.get(i);
                    final int delay = i * 420;
                    SwingUtilities.invokeLater(() -> {
                        javax.swing.Timer t = new javax.swing.Timer(delay, e -> showToast(n));
                        t.setRepeats(false); t.start();
                    });
                }
            }
            int unread = DAO.getUnreadNotificationCount(userId);
            SwingUtilities.invokeLater(() -> badgeListeners.forEach(l -> l.accept(unread)));
        } catch (Exception ignored) {}
    }

    private void showToast(DataStore.Notification n) {
        if (ownerFrame == null || !ownerFrame.isVisible()) return;

        Color accent = switch (n.type) {
            case "chat"  -> new Color(0x6366F1);
            case "note"  -> new Color(0x0D9488);
            case "event" -> new Color(0xF59E0B);
            case "ann"   -> new Color(0xF43F5E);
            default      -> new Color(0x8B5CF6);
        };
        String icon = switch (n.type) {
            case "chat"  -> "💬";
            case "note"  -> "📄";
            case "event" -> "📅";
            case "ann"   -> "📢";
            default      -> "🔔";
        };

        JWindow toast = new JWindow(ownerFrame);
        toast.setAlwaysOnTop(true);

        JPanel panel = new JPanel(new BorderLayout(10, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(18, 22, 42, 245));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(accent); g2.setStroke(new java.awt.BasicStroke(1.8f));
                g2.drawRoundRect(1,1,getWidth()-2,getHeight()-2,14,14);
                g2.fillRoundRect(0, 10, 4, getHeight()-20, 3, 3);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12,14,12,14));

        JLabel iconLbl = new JLabel(icon);
        iconLbl.setFont(new Font("SansSerif",Font.PLAIN,22));

        JPanel textP = new JPanel();
        textP.setOpaque(false);
        textP.setLayout(new BoxLayout(textP, BoxLayout.Y_AXIS));
        JLabel titleLbl = new JLabel(n.title);
        titleLbl.setFont(new Font("SansSerif",Font.BOLD,13)); titleLbl.setForeground(Color.WHITE);
        textP.add(titleLbl);
        if (!n.body.isEmpty()) {
            JLabel bodyLbl = new JLabel("<html><body style='width:190px'>" + n.body + "</body></html>");
            bodyLbl.setFont(new Font("SansSerif",Font.PLAIN,12)); bodyLbl.setForeground(new Color(200,200,225));
            textP.add(Box.createVerticalStrut(2)); textP.add(bodyLbl);
        }

        panel.add(iconLbl, BorderLayout.WEST);
        panel.add(textP, BorderLayout.CENTER);
        toast.add(panel);
        toast.pack();
        toast.setSize(Math.max(toast.getWidth(), 290), toast.getHeight());

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Insets si = Toolkit.getDefaultToolkit().getScreenInsets(ownerFrame.getGraphicsConfiguration());
        toast.setLocation(screen.width - toast.getWidth() - 16 - si.right,
                          screen.height - toast.getHeight() - 52 - si.bottom);

        // Count existing toasts, stack upward
        toast.setVisible(true);

        // Fade out after 4s
        javax.swing.Timer dismiss = new javax.swing.Timer(4000, ev -> {
            javax.swing.Timer fade = new javax.swing.Timer(30, null);
            float[] alpha = {1f};
            fade.addActionListener(fe -> {
                alpha[0] -= 0.07f;
                if (alpha[0] <= 0) { fade.stop(); toast.dispose(); return; }
                toast.setOpacity(Math.max(0f, alpha[0]));
            });
            fade.start();
        });
        dismiss.setRepeats(false); dismiss.start();

        panel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { toast.dispose(); }
        });
    }

    public void addBadgeListener(Consumer<Integer> listener) { badgeListeners.add(listener); }
    public void refreshBadge() { if (!poller.isShutdown()) poller.execute(this::pollOnce); }
    public int getUserId() { return userId; }
    public boolean isRealtimeConnected() { return realtimeConnected; }
}