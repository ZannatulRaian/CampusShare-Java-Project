package com.campusshare.ui;

import com.campusshare.data.DataStore;
import com.campusshare.ui.panels.*;
import javax.swing.*;
import com.campusshare.remote.ConnectionMonitor;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/**
 * Main application window — dark sidebar + light content area.
 * Sidebar: logo, user pill, icon-based nav, logout.
 * Top bar: search + user pill.
 */
public class MainWindow extends JFrame {

    private String activeNav = "dashboard";
    private final JPanel contentArea;
    private final CardLayout cardLayout;
    private final Map<String, JPanel>   navPanels = new HashMap<>();
    private final Map<String, JLabel[]> navLabels = new HashMap<>();
    private JLabel connectionBadge;

    private static final String[] NAV_KEYS   = {"dashboard","notes","events","announcements","chat","profile"};
    private static final String[] NAV_LABELS = {"Dashboard","Notes","Events","Announcements","Chat","Profile"};
    private static final String[] NAV_ICONS  = {"⬛","📄","📅","📢","💬","👤"};

    // Accent colors per nav item
    private static final Color[] NAV_ACCENTS = {
        new Color(0x6366F1), new Color(0xF43F5E), new Color(0x0D9488),
        new Color(0xF59E0B), new Color(0x7C3AED), new Color(0x0EA5E9)
    };

    public MainWindow() {
        super("CampusShare");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(960, 620));
        setSize(1320, 840);
        setLocationRelativeTo(null);

        DataStore.User user = DataStore.getCurrentUser();
        cardLayout  = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setBackground(Theme.BG_APP);

        setContentPane(buildRootPanel(user));
        setVisible(true);
    }

    private JPanel buildRootPanel(DataStore.User user) {
        activeNav = "dashboard";
        navPanels.clear();
        navLabels.clear();

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG_APP);
        root.add(buildSidebar(user), BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(Theme.BG_APP);
        right.add(buildTopBar(user), BorderLayout.NORTH);

        contentArea.removeAll();
        // Navigator callback — DashboardPanel uses this to switch tabs
        java.util.function.Consumer<String> navigator = this::switchNav;
        // Avatar refresh callback — ProfilePanel calls this after photo change
        Runnable avatarRefresh = () -> {
            setContentPane(buildRootPanel(DataStore.getCurrentUser()));
            revalidate(); repaint();
        };
        contentArea.add(new DashboardPanel(user, navigator),    "dashboard");
        contentArea.add(new NotesPanel(user),                    "notes");
        contentArea.add(new EventsPanel(user),                   "events");
        contentArea.add(new AnnouncementsPanel(user),            "announcements");
        contentArea.add(new ForumPanel(user),                    "chat");
        contentArea.add(new ProfilePanel(user, avatarRefresh),   "profile");
        cardLayout.show(contentArea, "dashboard");

        right.add(contentArea, BorderLayout.CENTER);
        root.add(right, BorderLayout.CENTER);
        return root;
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────
    private JPanel buildSidebar(DataStore.User user) {
        JPanel sb = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(Theme.BG_SIDEBAR);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Subtle left glow
                g2.setPaint(new java.awt.GradientPaint(
                    getWidth(), 0, new Color(0x4F46E5, false),
                    0, 0, new Color(0x4F46E5, true)));
                g2.setColor(new Color(0x4F46E5, false));
                // border right divider
                g2.setColor(new Color(255, 255, 255, 10));
                g2.fillRect(getWidth() - 1, 0, 1, getHeight());
                g2.dispose();
            }
        };
        sb.setOpaque(false);
        sb.setPreferredSize(new Dimension(220, 0));

        // ── TOP: logo + user pill ─────────────────────────────────────────
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(22, 0, 0, 0));

        // Logo
        JPanel logoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        logoRow.setOpaque(false); logoRow.setAlignmentX(LEFT_ALIGNMENT);
        JPanel logoIcon = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new java.awt.GradientPaint(0,0,new Color(0x6366F1),28,28,new Color(0x7C3AED)));
                g2.fillRoundRect(0,0,28,28,8,8);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif",Font.BOLD,14));
                g2.drawString("CS", 5, 20);
                g2.dispose();
            }
        };
        logoIcon.setOpaque(false); logoIcon.setPreferredSize(new Dimension(28,28));
        JLabel logoText = new JLabel("CampusShare");
        logoText.setFont(new Font("SansSerif", Font.BOLD, 14));
        logoText.setForeground(Color.WHITE);
        logoRow.add(logoIcon); logoRow.add(logoText);
        top.add(logoRow);
        top.add(Box.createVerticalStrut(14));
        top.add(makeSidebarDivider());
        top.add(Box.createVerticalStrut(10));
        top.add(buildUserPill(user));
        top.add(Box.createVerticalStrut(10));
        top.add(makeSidebarDivider());
        sb.add(top, BorderLayout.NORTH);

        // ── CENTER: nav ───────────────────────────────────────────────────
        JPanel nav = new JPanel();
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setOpaque(false);
        nav.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        for (int i = 0; i < NAV_KEYS.length; i++) {
            nav.add(buildNavItem(NAV_KEYS[i], NAV_LABELS[i], NAV_ICONS[i], NAV_ACCENTS[i]));
            nav.add(Box.createVerticalStrut(3));
        }
        sb.add(nav, BorderLayout.CENTER);

        // ── BOTTOM: logout ────────────────────────────────────────────────
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 14));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(255, 255, 255, 12)));
        JLabel logoutLbl = new JLabel("⇤  Log Out");
        logoutLbl.setFont(Theme.font(Font.PLAIN, 12));
        logoutLbl.setForeground(new Color(252, 165, 165));
        logoutLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutLbl.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { doLogout(); }
        });
        bottom.add(logoutLbl);
        sb.add(bottom, BorderLayout.SOUTH);
        return sb;
    }

    private JPanel buildUserPill(DataStore.User user) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        row.setOpaque(false); row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(220, 48));
        row.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        // Avatar — shows profile photo if set, else initials
        Color avColor = user.isFaculty() ? Theme.BLOCK_TEAL : Theme.BLOCK_INDIGO;
        JPanel av = buildAvatarPanel(user, 30);
        av.setPreferredSize(new Dimension(30, 30));

        JPanel nameCol = new JPanel(new GridLayout(2, 1, 0, 1)); nameCol.setOpaque(false);
        String dn = user.fullName.length() > 14 ? user.fullName.substring(0, 14) + "…" : user.fullName;
        JLabel nameL = new JLabel(dn); nameL.setFont(Theme.font(Font.BOLD, 12)); nameL.setForeground(Color.WHITE);
        JLabel roleL = new JLabel(user.role.toLowerCase()); roleL.setFont(Theme.FONT_TINY);
        roleL.setForeground(user.isFaculty() ? new Color(0x6EE7B7) : new Color(0xA5B4FC));
        nameCol.add(nameL); nameCol.add(roleL);

        row.add(av); row.add(nameCol);
        return row;
    }

    private JPanel makeSidebarDivider() {
        JPanel d = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(new Color(255, 255, 255, 14)); g.fillRect(0, 0, getWidth(), 1);
            }
        };
        d.setOpaque(false);
        d.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        d.setPreferredSize(new Dimension(220, 1));
        return d;
    }

    private JPanel buildNavItem(String key, String label, String icon, Color accent) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 7)) {
            @Override protected void paintComponent(Graphics g) {
                boolean active = key.equals(activeNav);
                boolean hover  = getMousePosition() != null;
                Graphics2D g2  = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (active) {
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 22));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    // left accent strip
                    g2.setColor(accent);
                    g2.fillRoundRect(0, 6, 3, getHeight() - 12, 3, 3);
                } else if (hover) {
                    g2.setColor(new Color(255, 255, 255, 8));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        p.setOpaque(false);
        p.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        p.setMaximumSize(new Dimension(200, 38));
        p.setAlignmentX(LEFT_ALIGNMENT);

        // Small colored icon box
        JPanel iconBox = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                boolean active = key.equals(activeNav);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color boxBg = active
                    ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40)
                    : new Color(255, 255, 255, 8);
                g2.setColor(boxBg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.setFont(Theme.font(Font.PLAIN, 12));
                g2.setColor(active ? accent : new Color(255, 255, 255, 120));
                g2.drawString(icon, 4, 16);
                g2.dispose();
            }
        };
        iconBox.setOpaque(false); iconBox.setPreferredSize(new Dimension(24, 22));

        JLabel text = new JLabel(label);
        text.setFont(Theme.font(Font.PLAIN, 13));
        text.setForeground(key.equals(activeNav) ? Color.WHITE : new Color(255, 255, 255, 140));

        p.add(iconBox); p.add(text);
        navPanels.put(key, p);
        navLabels.put(key, new JLabel[]{new JLabel(), text});

        p.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { p.repaint(); }
            public void mouseExited(MouseEvent e)  { p.repaint(); }
            public void mouseClicked(MouseEvent e) { switchNav(key); }
        });
        return p;
    }

    private void switchNav(String key) {
        activeNav = key;
        navPanels.forEach((k, p) -> {
            // Update text label color
            Component[] comps = p.getComponents();
            for (Component c : comps) {
                if (c instanceof JLabel) {
                    ((JLabel) c).setFont(Theme.font(k.equals(key) ? Font.BOLD : Font.PLAIN, 13));
                    ((JLabel) c).setForeground(k.equals(key) ? Color.WHITE : new Color(255, 255, 255, 140));
                }
            }
            p.repaint();
        });
        cardLayout.show(contentArea, key);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────
    private JPanel buildTopBar(DataStore.User user) {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.BG_CARD);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
            BorderFactory.createEmptyBorder(8, 20, 8, 20)));

        // Search
        JPanel searchWrap = new JPanel(new BorderLayout());
        searchWrap.setBackground(Theme.BG_INPUT);
        searchWrap.setBorder(new RoundBorder(8, Theme.BORDER, 1));
        searchWrap.setPreferredSize(new Dimension(260, 34));
        JLabel sIcon = new JLabel("  🔍 ");
        sIcon.setFont(Theme.font(Font.PLAIN, 11)); sIcon.setForeground(Theme.TEXT_MUTE);
        JTextField search = new JTextField();
        search.setOpaque(false);
        search.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        search.setFont(Theme.font(Font.PLAIN, 13)); search.setForeground(Theme.TEXT_DARK);
        search.setCaretColor(Theme.PRIMARY);
        searchWrap.add(sIcon, BorderLayout.WEST); searchWrap.add(search, BorderLayout.CENTER);
        bar.add(searchWrap, BorderLayout.WEST);

        // Right: badge chip + user pill — use BoxLayout so nothing clips
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        right.setBackground(Theme.BG_CARD);
        right.setOpaque(true);

        // Faculty/Admin badge chip
        if (user.isFaculty()) {
            Color chipFg = user.role.equals("FACULTY") ? Theme.ACCENT_TEAL : Theme.ACCENT_VIOLET;
            Color chipBg = new Color(chipFg.getRed(), chipFg.getGreen(), chipFg.getBlue(), 30);
            JLabel badge = Theme.chip(
                user.role.equals("FACULTY") ? " 👩‍🏫 Faculty " : " 🛡 Admin ",
                chipFg, chipBg);
            right.add(badge);
            right.add(Box.createHorizontalStrut(8));
        }

        // User pill — fixed minimum width avoids clipping
        String dn = user.fullName.length() > 22 ? user.fullName.substring(0, 22) + "…" : user.fullName;
        JPanel pill = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_INPUT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 20, 20);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        pill.setLayout(new BoxLayout(pill, BoxLayout.X_AXIS));
        pill.setOpaque(false);
        pill.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 14));

        Color avColor = user.isFaculty() ? Theme.BLOCK_TEAL : Theme.BLOCK_INDIGO;
        JPanel avT = buildAvatarPanel(user, 26);
        avT.setPreferredSize(new Dimension(26, 26));
        avT.setMinimumSize(new Dimension(26, 26));
        avT.setMaximumSize(new Dimension(26, 26));

        JLabel nL = new JLabel(dn);
        nL.setFont(Theme.font(Font.BOLD, 12));
        nL.setForeground(Theme.TEXT_DARK);
        JLabel rL = new JLabel(user.role.toLowerCase());
        rL.setFont(Theme.FONT_TINY);
        rL.setForeground(Theme.TEXT_MUTE);
        JPanel nc = new JPanel();
        nc.setLayout(new BoxLayout(nc, BoxLayout.Y_AXIS));
        nc.setOpaque(false);
        nc.add(nL); nc.add(rL);

        pill.add(avT);
        pill.add(Box.createHorizontalStrut(8));
        pill.add(nc);

        // Ensure pill never shrinks below its natural size
        Dimension pillPref = new Dimension(
            26 + 8 + Math.max(120, dn.length() * 7 + 20) + 24, 36);
        pill.setPreferredSize(pillPref);
        pill.setMinimumSize(pillPref);
        pill.setMaximumSize(pillPref);

        right.add(pill);
        // Connection status badge
        connectionBadge = buildConnectionBadge();
        right.add(Box.createHorizontalStrut(4));
        right.add(connectionBadge);

        bar.add(right, BorderLayout.EAST);

        // Register for real-time connectivity updates
        ConnectionMonitor.addListener(online -> SwingUtilities.invokeLater(() -> {
            updateConnectionBadge(online);
            if (online) { DataStore.loadAll(); revalidate(); repaint(); }
        }));
        return bar;
    }

    private JLabel buildConnectionBadge() {
        boolean online = ConnectionMonitor.isOnline();
        JLabel badge = new JLabel(online ? "● Online" : "● Offline");
        badge.setFont(Theme.font(Font.BOLD, 10));
        badge.setForeground(online ? Theme.SUCCESS : Theme.TEXT_FAINT);
        badge.setBorder(BorderFactory.createCompoundBorder(
            new RoundBorder(10, online ? new Color(Theme.SUCCESS.getRed(),Theme.SUCCESS.getGreen(),Theme.SUCCESS.getBlue(),60)
                                       : Theme.BORDER, 1),
            BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        badge.setOpaque(false);
        return badge;
    }

    private void updateConnectionBadge(boolean online) {
        if (connectionBadge == null) return;
        connectionBadge.setText(online ? "● Online" : "● Offline");
        connectionBadge.setForeground(online ? Theme.SUCCESS : Theme.TEXT_FAINT);
        connectionBadge.revalidate(); connectionBadge.repaint();
    }

    /** Builds an avatar panel that shows profile photo if available, else initials. */
    private JPanel buildAvatarPanel(DataStore.User user, int size) {
        Color avColor = user.isFaculty() ? Theme.BLOCK_TEAL : Theme.BLOCK_INDIGO;
        if (user.avatarPath != null && !user.avatarPath.isEmpty()) {
            java.io.File imgFile = new java.io.File(user.avatarPath);
            if (imgFile.exists()) {
                return new JPanel() {
                    { setOpaque(false); setPreferredSize(new Dimension(size,size)); }
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        try {
                            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(imgFile);
                            if (img != null) {
                                java.awt.Shape clip = new java.awt.geom.Ellipse2D.Float(0,0,size,size);
                                g2.setClip(clip);
                                g2.drawImage(img, 0, 0, size, size, null);
                                g2.dispose(); return;
                            }
                        } catch (Exception ignored) {}
                        g2.setColor(avColor); g2.fillOval(0,0,size,size);
                        g2.setColor(Color.WHITE);
                        g2.setFont(Theme.font(Font.BOLD, (int)(size*0.36f)));
                        FontMetrics fm = g2.getFontMetrics(); String ini = user.initials();
                        g2.drawString(ini,(size-fm.stringWidth(ini))/2,(size+fm.getAscent()-fm.getDescent())/2);
                        g2.dispose();
                    }
                };
            }
        }
        return Theme.avatar(user.initials(), avColor, size);
    }

    private void doLogout() {
        // Clear cloud session + identifiers so switching accounts doesn't leak state.
        try { com.campusshare.remote.SupabaseClient.signOut(); } catch (Exception ignored) {}
        DataStore.cloudUserId = -1;
        DataStore.setCurrentUser(null);
        LoginWindow lw = new LoginWindow(() -> {
            DataStore.User u2 = DataStore.getCurrentUser();
            setContentPane(buildRootPanel(u2));
            revalidate(); repaint();
        });
        setContentPane(lw.getContentPanel());
        revalidate(); repaint();
    }
}
