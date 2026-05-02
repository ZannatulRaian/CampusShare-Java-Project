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
 * Main application window — Claude-style slim sidebar + content area.
 * Sidebar: collapsed (icon-only, 58px) with hover-expand to 220px.
 */
public class MainWindow extends JFrame {

    private String activeNav = "dashboard";
    private final JPanel contentArea;
    private final CardLayout cardLayout;
    private final Map<String, JPanel> navPanels  = new HashMap<>();
    private JLabel connectionBadge;

    // Sidebar sizing
    private static final int SIDEBAR_COLLAPSED = 58;
    private static final int SIDEBAR_EXPANDED  = 220;
    private static final Color SB_BG           = new Color(0x0A0918); // same as Theme.BG_SIDEBAR

    private static final String[] NAV_KEYS   = {"dashboard","notes","events","announcements","chat","profile"};
    private static final String[] NAV_LABELS = {"Dashboard","Notes","Events","Announcements","Chat","Profile"};
    private static final String[] NAV_ICONS  = {"\u2302","\uD83D\uDCC4","\uD83D\uDCC5","\uD83D\uDCE2","\uD83D\uDCAC","\uD83D\uDC64"};

    private static final Color[] NAV_ACCENTS = {
        new Color(0x6366F1), new Color(0xF43F5E), new Color(0x0D9488),
        new Color(0xF59E0B), new Color(0x7C3AED), new Color(0x0EA5E9)
    };

    // Sidebar panel — we keep a reference to animate width
    private JPanel sidebar;
    private boolean sidebarExpanded = false;
    private javax.swing.Timer expandTimer;
    private int currentSidebarWidth = SIDEBAR_COLLAPSED;

    // ── Logo image (loaded once, shared across sidebar + window icon) ──────────
    private static java.awt.image.BufferedImage LOGO_IMAGE = null;
    static {
        try {
            java.io.InputStream is = MainWindow.class.getResourceAsStream("/icons/logo.png");
            if (is != null) LOGO_IMAGE = javax.imageio.ImageIO.read(is);
        } catch (Exception ignored) {}
    }

    public MainWindow() {
        super("CampusShare");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(860, 620));
        setSize(1280, 820);
        setLocationRelativeTo(null);

        // Set window/taskbar icon
        if (LOGO_IMAGE != null) {
            setIconImage(LOGO_IMAGE);
        }

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

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG_APP);

        sidebar = buildSidebar(user);
        root.add(sidebar, BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(Theme.BG_APP);
        right.add(buildTopBar(user), BorderLayout.NORTH);

        contentArea.removeAll();
        java.util.function.Consumer<String> navigator = this::switchNav;
        Runnable avatarRefresh = () -> {
            setContentPane(buildRootPanel(DataStore.getCurrentUser()));
            revalidate(); repaint();
        };
        contentArea.add(new DashboardPanel(user, navigator),  "dashboard");
        contentArea.add(new NotesPanel(user),                  "notes");
        contentArea.add(new EventsPanel(user),                 "events");
        contentArea.add(new AnnouncementsPanel(user),          "announcements");
        contentArea.add(new ForumPanel(user),                  "chat");
        contentArea.add(new ProfilePanel(user, avatarRefresh), "profile");
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
                g2.setColor(SB_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // right edge divider
                g2.setColor(new Color(255, 255, 255, 12));
                g2.fillRect(getWidth() - 1, 0, 1, getHeight());
                g2.dispose();
            }
        };
        sb.setOpaque(false);
        sb.setPreferredSize(new Dimension(SIDEBAR_COLLAPSED, 0));

        // ── TOP: toggle + logo row ────────────────────────────────────────
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(10, 0, 6, 0));

        // Toggle button row (mimics Claude's sidebar toggle)
        JPanel toggleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        toggleRow.setOpaque(false);
        JPanel toggleBtn = makeIconButton("\u2630", new Color(255,255,255,100), () -> toggleSidebar(sb));
        toggleRow.add(toggleBtn);
        top.add(toggleRow);

        // Logo row (only label visible when expanded)
        JPanel logoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        logoRow.setOpaque(false);
        logoRow.setName("logoRow");

        JPanel logoIcon = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (LOGO_IMAGE != null) {
                    // Draw logo image clipped to rounded rect
                    g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, 32, 32, 8, 8));
                    g2.drawImage(LOGO_IMAGE, 0, 0, 32, 32, null);
                } else {
                    g2.setPaint(new GradientPaint(0,0,new Color(0x6366F1),32,32,new Color(0x7C3AED)));
                    g2.fillRoundRect(0,0,32,32,8,8);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("SansSerif",Font.BOLD,16));
                    g2.drawString("CS", 5, 22);
                }
                g2.dispose();
            }
        };
        logoIcon.setOpaque(false); logoIcon.setPreferredSize(new Dimension(32,32));
        logoIcon.setMinimumSize(new Dimension(32,32)); logoIcon.setMaximumSize(new Dimension(32,32));

        JLabel logoText = new JLabel("CampusShare");
        logoText.setFont(new Font("SansSerif", Font.BOLD, 17));
        logoText.setForeground(Color.WHITE);
        logoText.setName("logoText");
        logoText.setVisible(false); // hidden when collapsed

        logoRow.add(logoIcon);
        logoRow.add(logoText);
        top.add(logoRow);

        top.add(Box.createVerticalStrut(8));
        top.add(makeSidebarDivider());

        sb.add(top, BorderLayout.NORTH);

        // ── CENTER: nav ───────────────────────────────────────────────────
        JPanel nav = new JPanel();
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setOpaque(false);
        nav.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));
        for (int i = 0; i < NAV_KEYS.length; i++) {
            nav.add(buildNavItem(NAV_KEYS[i], NAV_LABELS[i], NAV_ICONS[i], NAV_ACCENTS[i]));
            nav.add(Box.createVerticalStrut(2));
        }
        sb.add(nav, BorderLayout.CENTER);

        // ── BOTTOM: user pill + logout ────────────────────────────────────
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(255, 255, 255, 12)),
            BorderFactory.createEmptyBorder(6, 0, 8, 0)));

        // User pill row (avatar always visible, name shown when expanded)
        JPanel userRow = buildUserRow(user);
        bottom.add(userRow);
        bottom.add(Box.createVerticalStrut(2));

        // Divider between user pill and logout
        bottom.add(makeSidebarDivider());
        bottom.add(Box.createVerticalStrut(2));

        // Logout row
        JPanel logoutRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 11, 4));
        logoutRow.setOpaque(false);
        logoutRow.setAlignmentX(LEFT_ALIGNMENT);
        JPanel logoutIcon = makeIconButton("\u21A4", new Color(252,165,165), this::doLogout);
        JLabel logoutText = new JLabel("Log Out");
        logoutText.setFont(Theme.font(Font.PLAIN, 15));
        logoutText.setForeground(new Color(252,165,165));
        logoutText.setName("logoutText");
        logoutText.setVisible(false);
        logoutRow.add(logoutIcon);
        logoutRow.add(logoutText);
        logoutRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutRow.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { doLogout(); }
        });
        bottom.add(logoutRow);
        sb.add(bottom, BorderLayout.SOUTH);

        // Hover expand/collapse
        MouseAdapter hoverAdapter = new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { if (!sidebarExpanded) animateSidebar(sb, true); }
            public void mouseExited(MouseEvent e) {
                // Only collapse if mouse left the entire sidebar
                Point p = e.getLocationOnScreen();
                SwingUtilities.convertPointFromScreen(p, sb);
                if (!sb.contains(p)) animateSidebar(sb, false);
            }
        };
        addHoverListeners(sb, hoverAdapter);

        return sb;
    }

    private JPanel buildUserRow(DataStore.User user) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 11, 5));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        JPanel av = buildAvatarPanel(user, 28);
        av.setPreferredSize(new Dimension(28,28)); av.setMinimumSize(new Dimension(28,28)); av.setMaximumSize(new Dimension(28,28));

        JPanel nameCol = new JPanel(); nameCol.setLayout(new BoxLayout(nameCol, BoxLayout.Y_AXIS)); nameCol.setOpaque(false);
        nameCol.setName("userNameCol");
        String dn = user.fullName.length() > 14 ? user.fullName.substring(0,14)+"…" : user.fullName;
        JLabel nameL = new JLabel(dn); nameL.setFont(Theme.font(Font.BOLD,15)); nameL.setForeground(Color.WHITE);
        JLabel roleL = new JLabel(user.role.toLowerCase()); roleL.setFont(Theme.FONT_TINY);
        roleL.setForeground(user.isFaculty() ? new Color(0x6EE7B7) : new Color(0xA5B4FC));
        nameCol.add(nameL); nameCol.add(roleL);
        nameCol.setVisible(false);

        row.add(av);
        row.add(nameCol);
        return row;
    }

    private JPanel makeIconButton(String symbol, Color fg, Runnable action) {
        JPanel btn = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getMousePosition() != null) {
                    g2.setColor(new Color(255,255,255,14));
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                }
                g2.setColor(fg);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 17));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(symbol, (getWidth()-fm.stringWidth(symbol))/2, (getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        btn.setOpaque(false);
        btn.setPreferredSize(new Dimension(36,32));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { action.run(); }
            public void mouseEntered(MouseEvent e) { btn.repaint(); }
            public void mouseExited(MouseEvent e)  { btn.repaint(); }
        });
        return btn;
    }

    private void toggleSidebar(JPanel sb) {
        animateSidebar(sb, !sidebarExpanded);
    }

    private void animateSidebar(JPanel sb, boolean expand) {
        if (expandTimer != null && expandTimer.isRunning()) expandTimer.stop();
        int target = expand ? SIDEBAR_EXPANDED : SIDEBAR_COLLAPSED;
        int step   = expand ? 8 : -8;
        expandTimer = new javax.swing.Timer(8, null);
        expandTimer.addActionListener(e -> {
            currentSidebarWidth += step;
            boolean done = expand ? currentSidebarWidth >= target : currentSidebarWidth <= target;
            if (done) { currentSidebarWidth = target; expandTimer.stop(); sidebarExpanded = expand; }
            sb.setPreferredSize(new Dimension(currentSidebarWidth, 0));
            setNavLabelsVisible(sb, expand && currentSidebarWidth > SIDEBAR_COLLAPSED + 60);
            sb.revalidate(); repaint();
        });
        expandTimer.start();
    }

    /** Show/hide text labels inside the sidebar based on expanded state */
    private void setNavLabelsVisible(JPanel sb, boolean visible) {
        // nav items
        for (JPanel p : navPanels.values()) {
            for (Component c : p.getComponents()) {
                if (c instanceof JLabel) c.setVisible(visible);
            }
        }
        // logo text, logout text, user name col
        setNamedVisible(sb, "logoText", visible);
        setNamedVisible(sb, "logoutText", visible);
        setNamedVisible(sb, "userNameCol", visible);
    }

    private void setNamedVisible(Container root, String name, boolean visible) {
        for (Component c : root.getComponents()) {
            if (name.equals(c.getName())) { c.setVisible(visible); return; }
            if (c instanceof Container) setNamedVisible((Container)c, name, visible);
        }
    }

    /** Recursively add mouse listeners for hover detection */
    private void addHoverListeners(Component comp, MouseAdapter adapter) {
        comp.addMouseListener(adapter);
        if (comp instanceof Container) {
            for (Component child : ((Container)comp).getComponents()) {
                addHoverListeners(child, adapter);
            }
        }
    }

    private JPanel makeSidebarDivider() {
        JPanel d = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(new Color(255,255,255,14)); g.fillRect(0,0,getWidth(),1);
            }
        };
        d.setOpaque(false);
        d.setMaximumSize(new Dimension(Integer.MAX_VALUE,1));
        d.setPreferredSize(new Dimension(SIDEBAR_EXPANDED,1));
        return d;
    }

    private JPanel buildNavItem(String key, String label, String icon, Color accent) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6)) {
            @Override protected void paintComponent(Graphics g) {
                boolean active = key.equals(activeNav);
                boolean hover  = getMousePosition() != null;
                Graphics2D g2  = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (active) {
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 22));
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
                    // left accent strip
                    g2.setColor(accent);
                    g2.fillRoundRect(0,5,3,getHeight()-10,3,3);
                } else if (hover) {
                    g2.setColor(new Color(255,255,255,9));
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        p.setOpaque(false);
        p.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        p.setAlignmentX(LEFT_ALIGNMENT);

        // Icon box
        JPanel iconBox = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                boolean active = key.equals(activeNav);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(active ? new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),38) : new Color(255,255,255,8));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),7,7);
                g2.setFont(Theme.font(Font.PLAIN, 16));
                g2.setColor(active ? accent : new Color(255,255,255,130));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(icon,(getWidth()-fm.stringWidth(icon))/2,(getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        iconBox.setOpaque(false); iconBox.setPreferredSize(new Dimension(28,26)); iconBox.setMinimumSize(new Dimension(28,26)); iconBox.setMaximumSize(new Dimension(28,26));

        JLabel text = new JLabel(label);
        text.setFont(Theme.font(Font.PLAIN, 16));
        text.setForeground(key.equals(activeNav) ? Color.WHITE : new Color(255,255,255,145));
        text.setVisible(false); // hidden until expanded

        p.add(iconBox); p.add(text);
        navPanels.put(key, p);

        MouseAdapter navClick = new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { p.repaint(); }
            public void mouseExited(MouseEvent e)  { p.repaint(); }
            public void mousePressed(MouseEvent e) { switchNav(key); }
        };
        p.addMouseListener(navClick);
        // Forward clicks from child components (icon box, label) to the parent nav item
        for (Component child : p.getComponents()) {
            child.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) { switchNav(key); }
            });
        }
        return p;
    }

    private void switchNav(String key) {
        activeNav = key;
        navPanels.forEach((k, p) -> {
            for (Component c : p.getComponents()) {
                if (c instanceof JLabel) {
                    boolean active = k.equals(key);
                    ((JLabel)c).setFont(Theme.font(active ? Font.BOLD : Font.PLAIN, 13));
                    ((JLabel)c).setForeground(active ? Color.WHITE : new Color(255,255,255,145));
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
            BorderFactory.createMatteBorder(0,0,1,0,Theme.BORDER),
            BorderFactory.createEmptyBorder(8,20,8,20)));

        JPanel searchWrap = new JPanel(new BorderLayout());
        searchWrap.setBackground(Theme.BG_INPUT);
        searchWrap.setBorder(new RoundBorder(8,Theme.BORDER,1));
        searchWrap.setPreferredSize(new Dimension(260,34));
        JLabel sIcon = new JLabel("  \uD83D\uDD0D ");
        sIcon.setFont(Theme.font(Font.PLAIN,14)); sIcon.setForeground(Theme.TEXT_MUTE);
        JTextField search = new JTextField();
        search.setOpaque(false);
        search.setBorder(BorderFactory.createEmptyBorder(0,0,0,8));
        search.setFont(Theme.font(Font.PLAIN,16)); search.setForeground(Theme.TEXT_DARK);
        search.setCaretColor(Theme.PRIMARY);
        searchWrap.add(sIcon, BorderLayout.WEST); searchWrap.add(search, BorderLayout.CENTER);
        bar.add(searchWrap, BorderLayout.WEST);

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        right.setBackground(Theme.BG_CARD);

        if (user.isFaculty()) {
            Color chipFg = user.role.equals("FACULTY") ? Theme.ACCENT_TEAL : Theme.ACCENT_VIOLET;
            Color chipBg = new Color(chipFg.getRed(),chipFg.getGreen(),chipFg.getBlue(),30);
            JLabel badge = Theme.chip(user.role.equals("FACULTY") ? " \uD83D\uDC69\u200D\uD83C\uDFEB Faculty " : " \uD83D\uDEE1 Admin ", chipFg, chipBg);
            right.add(badge);
            right.add(Box.createHorizontalStrut(8));
        }

        String dn = user.fullName.length()>22 ? user.fullName.substring(0,22)+"…" : user.fullName;
        JPanel pill = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_INPUT); g2.fillRoundRect(0,0,getWidth(),getHeight(),20,20);
                g2.setColor(Theme.BORDER);   g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,20,20);
                g2.dispose(); super.paintComponent(g);
            }
        };
        pill.setLayout(new BoxLayout(pill,BoxLayout.X_AXIS));
        pill.setOpaque(false);
        pill.setBorder(BorderFactory.createEmptyBorder(4,10,4,14));

        JPanel avT=buildAvatarPanel(user,26);
        avT.setPreferredSize(new Dimension(26,26)); avT.setMinimumSize(new Dimension(26,26)); avT.setMaximumSize(new Dimension(26,26));
        JLabel nL=new JLabel(dn); nL.setFont(Theme.font(Font.BOLD,15)); nL.setForeground(Theme.TEXT_DARK);
        JLabel rL=new JLabel(user.role.toLowerCase()); rL.setFont(Theme.FONT_TINY); rL.setForeground(Theme.TEXT_MUTE);
        JPanel nc=new JPanel(); nc.setLayout(new BoxLayout(nc,BoxLayout.Y_AXIS)); nc.setOpaque(false);
        nc.add(nL); nc.add(rL);

        pill.add(avT); pill.add(Box.createHorizontalStrut(8)); pill.add(nc);
        Dimension pillPref=new Dimension(26+8+Math.max(120,dn.length()*7+20)+24,36);
        pill.setPreferredSize(pillPref); pill.setMinimumSize(pillPref); pill.setMaximumSize(pillPref);

        right.add(pill);
        connectionBadge=buildConnectionBadge();
        right.add(Box.createHorizontalStrut(4));
        right.add(connectionBadge);
        bar.add(right, BorderLayout.EAST);

        ConnectionMonitor.addListener(online -> SwingUtilities.invokeLater(() -> {
            updateConnectionBadge(online);
            if (online) { DataStore.loadAll(); revalidate(); repaint(); }
        }));
        return bar;
    }

    private JLabel buildConnectionBadge() {
        boolean online=ConnectionMonitor.isOnline();
        JLabel badge=new JLabel(online?"● Online":"● Offline");
        badge.setFont(Theme.font(Font.BOLD,13));
        badge.setForeground(online?Theme.SUCCESS:Theme.TEXT_FAINT);
        badge.setBorder(BorderFactory.createCompoundBorder(
            new RoundBorder(10,online?new Color(Theme.SUCCESS.getRed(),Theme.SUCCESS.getGreen(),Theme.SUCCESS.getBlue(),60):Theme.BORDER,1),
            BorderFactory.createEmptyBorder(3,8,3,8)));
        badge.setOpaque(false);
        return badge;
    }

    private void updateConnectionBadge(boolean online) {
        if (connectionBadge==null) return;
        connectionBadge.setText(online?"● Online":"● Offline");
        connectionBadge.setForeground(online?Theme.SUCCESS:Theme.TEXT_FAINT);
        connectionBadge.revalidate(); connectionBadge.repaint();
    }

    private JPanel buildAvatarPanel(DataStore.User user,int size) {
        Color avColor=user.isFaculty()?Theme.BLOCK_TEAL:Theme.BLOCK_INDIGO;
        if (user.avatarPath!=null&&!user.avatarPath.isEmpty()) {
            java.io.File imgFile=new java.io.File(user.avatarPath);
            if (imgFile.exists()) {
                return new JPanel() {
                    { setOpaque(false); setPreferredSize(new Dimension(size,size)); }
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2=(Graphics2D)g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                        try {
                            java.awt.image.BufferedImage img=javax.imageio.ImageIO.read(imgFile);
                            if (img!=null) { g2.setClip(new java.awt.geom.Ellipse2D.Float(0,0,size,size)); g2.drawImage(img,0,0,size,size,null); g2.dispose(); return; }
                        } catch(Exception ignored){}
                        g2.setColor(avColor); g2.fillOval(0,0,size,size);
                        g2.setColor(Color.WHITE); g2.setFont(Theme.font(Font.BOLD,(int)(size*0.36f)));
                        FontMetrics fm=g2.getFontMetrics(); String ini=user.initials();
                        g2.drawString(ini,(size-fm.stringWidth(ini))/2,(size+fm.getAscent()-fm.getDescent())/2); g2.dispose();
                    }
                };
            }
        }
        return Theme.avatar(user.initials(),avColor,size);
    }

    private void doLogout() {
        try { com.campusshare.remote.SupabaseClient.signOut(); } catch(Exception ignored){}
        DataStore.cloudUserId=-1;
        DataStore.setCurrentUser(null);
        LoginWindow lw=new LoginWindow(()->{
            DataStore.User u2=DataStore.getCurrentUser();
            setContentPane(buildRootPanel(u2));
            revalidate(); repaint();
        });
        setContentPane(lw.getContentPanel());
        revalidate(); repaint();
    }
}
