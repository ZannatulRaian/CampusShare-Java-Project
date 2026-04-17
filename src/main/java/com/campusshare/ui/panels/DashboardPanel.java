package com.campusshare.ui.panels;

import com.campusshare.data.DataStore;
import java.util.function.Consumer;
import com.campusshare.ui.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * Dashboard — Bento-grid layout inspired by bold campus app design.
 * Big stat numbers, vivid color blocks, editorial card hierarchy.
 *
 * Layout (left heavier, right sidebar):
 *  ┌──────────────────────────┬──────────────┐
 *  │  HERO (gradient, tall)   │ QUICK STATS  │
 *  ├──────┬──────┬──────┬─────┤ (2 blocks)   │
 *  │NOTES │PEND. │EVNTS │SUBJ │              │
 *  ├──────────────────────────┼──────────────┤
 *  │  RECENT NOTES (table)    │ ANNOUNCEMENTS│
 *  └──────────────────────────┴──────────────┘
 */
public class DashboardPanel extends JPanel {

    private final DataStore.User user;
    private final Consumer<String> navigator;
    private static final Color[] BLOCK_FILLS = {
        new Color(0x4F46E5), new Color(0x7C3AED),
        new Color(0x0D9488), new Color(0xF59E0B)
    };

    public DashboardPanel(DataStore.User user, Consumer<String> navigator) {
        this.user = user;
        this.navigator = navigator;
        setBackground(Theme.BG_APP);
        setLayout(new BorderLayout());

        JPanel scroll = new JPanel(new BorderLayout());
        scroll.setBackground(Theme.BG_APP);
        scroll.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        scroll.add(buildBento(), BorderLayout.CENTER);

        JScrollPane sp = new JScrollPane(scroll);
        sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        add(sp, BorderLayout.CENTER);
    }

    private JPanel buildBento() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.insets = new Insets(0, 0, 12, 0);

        // ── ROW 1: hero (left 3/4) + right column top (right 1/4) ───────
        JPanel rightCol = new JPanel();
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
        rightCol.setOpaque(false);
        rightCol.add(buildQuickStats());
        rightCol.add(Box.createVerticalStrut(12));
        rightCol.add(buildUpcomingEvents());
        rightCol.add(Box.createVerticalStrut(12));
        rightCol.add(buildAnnouncements());

        g.gridx = 0; g.gridy = 0; g.gridheight = 3; g.weightx = 1.55; g.weighty = 1.0;
        g.insets = new Insets(0, 0, 0, 12);
        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setOpaque(false);
        leftCol.add(buildHero());
        leftCol.add(Box.createVerticalStrut(12));
        leftCol.add(buildStatRow());
        leftCol.add(Box.createVerticalStrut(12));
        leftCol.add(buildRecentNotes());
        root.add(leftCol, g);

        g.gridx = 1; g.gridy = 0; g.gridheight = 3; g.weightx = 0.7; g.weighty = 1.0;
        g.insets = new Insets(0, 0, 0, 0);
        root.add(rightCol, g);

        return root;
    }

    // ── Hero ─────────────────────────────────────────────────────────────────
    private JPanel buildHero() {
        JPanel card = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Dark indigo background
                g2.setColor(new Color(0x14121E));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                // Vivid indigo blob
                g2.setPaint(new RadialGradientPaint(
                    new Point2D.Float(getWidth() * 0.7f, getHeight() * 0.3f), getWidth() * 0.5f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(0x6366F1, false), new Color(0x4F46E5, true)}
                ));
                g2.fillOval((int)(getWidth() * 0.35f), -40, (int)(getWidth() * 0.75f), (int)(getHeight() * 1.6f));
                // Violet accent blob
                g2.setPaint(new RadialGradientPaint(
                    new Point2D.Float(getWidth() * 0.88f, getHeight() * 0.75f), 100,
                    new float[]{0f, 1f},
                    new Color[]{new Color(0x7C3AED, false), new Color(0x7C3AED, true)}
                ));
                g2.fillOval((int)(getWidth() * 0.7f), (int)(getHeight() * 0.4f), 200, 200);
                // Subtle grid pattern
                g2.setColor(new Color(255, 255, 255, 6));
                for (int x = 0; x < getWidth(); x += 32)
                    g2.drawLine(x, 0, x, getHeight());
                for (int y = 0; y < getHeight(); y += 32)
                    g2.drawLine(0, y, getWidth(), y);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(0, 130));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

        // Big greeting
        JLabel greeting = new JLabel("Hey, " + user.firstName() + " \uD83D\uDC4B");
        greeting.setFont(new Font("SansSerif", Font.BOLD, 26));
        greeting.setForeground(Color.WHITE);
        greeting.setBounds(24, 18, 500, 36);
        card.add(greeting);

        // Role · dept · semester sub-line
        String sub = user.role + "  ·  " + user.department
            + (user.semester > 0 ? "  ·  Semester " + user.semester : "");
        JLabel subLbl = new JLabel(sub);
        subLbl.setFont(Theme.font(Font.PLAIN, 13));
        subLbl.setForeground(new Color(255, 255, 255, 160));
        subLbl.setBounds(24, 58, 560, 18);
        card.add(subLbl);

        // Tiny summary line
        long approved = DataStore.NOTES.stream().filter(n -> n.approved).count();
        JLabel info = new JLabel(
            approved + " notes  ·  " + DataStore.EVENTS.size() + " events  ·  "
            + DataStore.ANNOUNCEMENTS.size() + " announcements");
        info.setFont(Theme.font(Font.PLAIN, 11));
        info.setForeground(new Color(255, 255, 255, 100));
        info.setBounds(24, 84, 560, 16);
        card.add(info);

        // Faculty badge if applicable
        if (user.isFaculty()) {
            JLabel badge = new JLabel(user.role.equals("FACULTY") ? "👩‍🏫 Faculty" : "🛡 Admin");
            badge.setFont(Theme.font(Font.BOLD, 11));
            badge.setForeground(new Color(0xA5F3FC));
            badge.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(6, new Color(0xA5F3FC, false).brighter(), 1),
                BorderFactory.createEmptyBorder(3, 10, 3, 10)));
            badge.setBounds(24, 104, 120, 22);
            card.add(badge);
        }

        return card;
    }

    // ── Stat row (4 bento mini-blocks) ───────────────────────────────────────
    private JPanel buildStatRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 10, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));

        long approved = DataStore.NOTES.stream().filter(n -> n.approved).count();
        long pending  = DataStore.NOTES.stream().filter(n -> !n.approved).count();

        addStatBlock(row, String.valueOf(approved),                "Notes Available",  new Color(0x4F46E5), new Color(0x1E1B4B), "notes");
        addStatBlock(row, String.valueOf(pending),                 "Pending Review",   new Color(0xD97706), new Color(0x2A1F0A), "notes");
        addStatBlock(row, String.valueOf(DataStore.EVENTS.size()), "Events",           new Color(0x059669), new Color(0x0A2A1F), "events");
        addStatBlock(row, String.valueOf(DataStore.SUBJECTS.size()),"Subjects",        new Color(0x7C3AED), new Color(0x221B40), null);
        return row;
    }

    private void addStatBlock(JPanel parent, String value, String label, Color accent, Color bg, String navTarget) {
        JPanel block = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(accent);
                g2.fillRoundRect(0, 12, 4, getHeight() - 24, 3, 3);
                g2.dispose();
            }
        };
        block.setOpaque(false);
        if (navTarget != null && navigator != null) {
            block.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            block.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent e) { navigator.accept(navTarget); }
            });
        }

        JLabel numLbl = new JLabel(value);
        numLbl.setFont(new Font("SansSerif", Font.BOLD, 28));
        numLbl.setForeground(accent);
        numLbl.setBounds(16, 10, 100, 38);
        block.add(numLbl);

        JLabel lbl = new JLabel(label);
        lbl.setFont(Theme.font(Font.PLAIN, 11));
        lbl.setForeground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 180));
        lbl.setBounds(16, 50, 160, 16);
        block.add(lbl);

        parent.add(block);
    }

    // ── Recent Notes ──────────────────────────────────────────────────────────
    private JPanel buildRecentNotes() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setOpaque(false);
        card.setBackground(Theme.BG_CARD);
        card.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // Card container
        JPanel inner = new JPanel(new BorderLayout(0, 12)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 16, 16);
                g2.dispose();
            }
        };
        inner.setOpaque(false);
        inner.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        // Header row
        JPanel hdr = new JPanel(new BorderLayout()); hdr.setOpaque(false);
        JLabel title = new JLabel("Recent Notes");
        title.setFont(Theme.font(Font.BOLD, 15)); title.setForeground(Theme.TEXT_DARK);
        JLabel viewAll = new JLabel("View all →");
        viewAll.setFont(Theme.font(Font.BOLD, 11)); viewAll.setForeground(Theme.PRIMARY);
        viewAll.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        viewAll.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (navigator != null) navigator.accept("notes");
            }
        });
        hdr.add(title, BorderLayout.WEST); hdr.add(viewAll, BorderLayout.EAST);
        inner.add(hdr, BorderLayout.NORTH);

        // Table-style note rows
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);

        String[] typeIcons = {"📄", "📊", "📝", "📋"};
        Color[] typeColors = {Theme.BLOCK_ROSE, Theme.BLOCK_TEAL, Theme.BLOCK_INDIGO, Theme.BLOCK_AMBER};
        int[] idx = {0};

        DataStore.NOTES.stream().filter(n -> n.approved).limit(5).forEach(n -> {
            Color tc = typeColors[idx[0] % typeColors.length];
            String ti = typeIcons[idx[0] % typeIcons.length];

            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

            // Type dot
            JPanel dot = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), 20));
                    g2.fillRoundRect(0, 0, 34, 34, 8, 8);
                    g2.setColor(tc);
                    g2.setFont(Theme.font(Font.PLAIN, 14));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(ti, (34 - fm.stringWidth(ti)) / 2, 22);
                    g2.dispose();
                }
            };
            dot.setOpaque(false);
            dot.setPreferredSize(new Dimension(34, 34));

            // Name + subject
            JPanel info = new JPanel(new GridLayout(2, 1, 0, 1)); info.setOpaque(false);
            String fname = n.fileName.length() > 32 ? n.fileName.substring(0, 32) + "…" : n.fileName;
            JLabel fn = new JLabel(fname);
            fn.setFont(Theme.font(Font.BOLD, 12)); fn.setForeground(Theme.TEXT_DARK);
            JLabel subj = new JLabel(n.subjectName() + "  ·  " + n.uploadedBy);
            subj.setFont(Theme.FONT_SMALL); subj.setForeground(Theme.TEXT_MUTE);
            info.add(fn); info.add(subj);

            // Stars + file type chip
            JPanel right = new JPanel(new GridLayout(2, 1, 0, 1)); right.setOpaque(false);
            JLabel star = new JLabel(n.fileType);
            star.setFont(Theme.font(Font.PLAIN, 11));
            star.setForeground(Theme.TEXT_MUTE);
            star.setHorizontalAlignment(SwingConstants.RIGHT);
            JLabel type = Theme.chip(n.fileType, tc, new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), 18));
            type.setHorizontalAlignment(SwingConstants.RIGHT);
            right.add(star); right.add(type);

            row.add(dot, BorderLayout.WEST);
            row.add(info, BorderLayout.CENTER);
            row.add(right, BorderLayout.EAST);
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            row.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    showNoteDetail(n);
                }
            });
            list.add(row);

            if (idx[0] < 4) {
                JSeparator sep = new JSeparator();
                sep.setForeground(Theme.BORDER);
                sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                list.add(sep);
            }
            idx[0]++;
        });

        if (DataStore.NOTES.stream().noneMatch(n -> n.approved)) {
            JLabel empty = new JLabel("No approved notes yet");
            empty.setFont(Theme.FONT_BODY); empty.setForeground(Theme.TEXT_FAINT);
            list.add(empty);
        }

        inner.add(list, BorderLayout.CENTER);
        card.add(inner);
        return card;
    }

    // ── Quick Stats (right column top) ────────────────────────────────────────
    private JPanel buildQuickStats() {
        JPanel col = new JPanel(new GridLayout(2, 1, 0, 10));
        col.setOpaque(false);

        // Block 1: big indigo — total notes
        JPanel b1 = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, new Color(0x4F46E5), getWidth(), getHeight(), new Color(0x6D28D9)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(new Color(255, 255, 255, 15));
                g2.fillOval(getWidth() - 60, -20, 120, 120);
                g2.dispose();
            }
        };
        b1.setOpaque(false);
        b1.setPreferredSize(new Dimension(0, 90));

        long total = DataStore.NOTES.stream().filter(n -> n.approved).count();
        JLabel n1 = new JLabel(String.valueOf(total));
        n1.setFont(new Font("SansSerif", Font.BOLD, 36));
        n1.setForeground(Color.WHITE);
        n1.setBounds(16, 12, 120, 44);
        JLabel l1 = new JLabel("Notes Available");
        l1.setFont(Theme.font(Font.PLAIN, 11));
        l1.setForeground(new Color(255, 255, 255, 160));
        l1.setBounds(16, 56, 160, 16);
        b1.add(n1); b1.add(l1);
        b1.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) { if (navigator!=null) navigator.accept("notes"); }
        });
        col.add(b1);

        // Block 2: teal — events
        JPanel b2 = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, new Color(0x0D9488), getWidth(), getHeight(), new Color(0x0F766E)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(new Color(255, 255, 255, 12));
                g2.fillOval(-30, getHeight() - 40, 100, 100);
                g2.dispose();
            }
        };
        b2.setOpaque(false);
        b2.setPreferredSize(new Dimension(0, 90));

        JLabel n2 = new JLabel(String.valueOf(DataStore.EVENTS.size()));
        n2.setFont(new Font("SansSerif", Font.BOLD, 36));
        n2.setForeground(Color.WHITE);
        n2.setBounds(16, 12, 120, 44);
        JLabel l2 = new JLabel("Upcoming Events");
        l2.setFont(Theme.font(Font.PLAIN, 11));
        l2.setForeground(new Color(255, 255, 255, 160));
        l2.setBounds(16, 56, 160, 16);
        b2.add(n2); b2.add(l2);
        b2.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b2.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) { if (navigator!=null) navigator.accept("events"); }
        });
        col.add(b2);

        return col;
    }

    // ── Upcoming Events (right column) ────────────────────────────────────────
    private JPanel buildUpcomingEvents() {
        JPanel outer = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 16, 16);
                g2.dispose();
            }
        };
        outer.setOpaque(false);
        outer.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JLabel title = new JLabel("Upcoming Events  →");
        title.setFont(Theme.font(Font.BOLD, 13)); title.setForeground(Theme.PRIMARY);
        title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        title.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (navigator != null) navigator.accept("events");
            }
        });
        outer.add(title, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        list.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        Color[] dots = {Theme.BLOCK_INDIGO, Theme.BLOCK_TEAL, Theme.BLOCK_ROSE, Theme.BLOCK_AMBER, Theme.BLOCK_VIOLET};
        int[] ci = {0};
        DataStore.EVENTS.stream().limit(4).forEach(ev -> {
            Color c = dots[ci[0] % dots.length];
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
            row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));

            // Colored left dot
            JPanel dotP = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 30));
                    g2.fillOval(0, 6, 14, 14);
                    g2.setColor(c); g2.fillOval(3, 9, 8, 8);
                    g2.dispose();
                }
            };
            dotP.setOpaque(false); dotP.setPreferredSize(new Dimension(18, 28));

            JPanel info = new JPanel(new GridLayout(2, 1, 0, 1)); info.setOpaque(false);
            String t = ev.title.length() > 22 ? ev.title.substring(0, 22) + "…" : ev.title;
            JLabel tl = new JLabel(t); tl.setFont(Theme.font(Font.BOLD, 11)); tl.setForeground(Theme.TEXT_DARK);
            JLabel dl = new JLabel(ev.date + " · " + ev.time);
            dl.setFont(Theme.FONT_TINY); dl.setForeground(Theme.TEXT_MUTE);
            info.add(tl); info.add(dl);

            row.add(dotP, BorderLayout.WEST);
            row.add(info, BorderLayout.CENTER);
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            row.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    showEventDetail(ev, c);
                }
            });
            list.add(row);
            ci[0]++;
        });

        if (DataStore.EVENTS.isEmpty()) {
            JLabel empty = new JLabel("No events scheduled");
            empty.setFont(Theme.FONT_SMALL); empty.setForeground(Theme.TEXT_FAINT);
            list.add(empty);
        }

        outer.add(list, BorderLayout.CENTER);
        return outer;
    }

    // ── Announcements (right column bottom) ───────────────────────────────────
    private JPanel buildAnnouncements() {
        JPanel outer = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x12101E));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
            }
        };
        outer.setOpaque(false);
        outer.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("Announcements  →");
        title.setFont(Theme.font(Font.BOLD, 13)); title.setForeground(Theme.PRIMARY);
        title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        title.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (navigator != null) navigator.accept("announcements");
            }
        });
        outer.add(title, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        list.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        Color[] tagCols = {Theme.BLOCK_ROSE, Theme.BLOCK_AMBER, Theme.BLOCK_TEAL, Theme.BLOCK_VIOLET};
        int[] ci = {0};
        DataStore.ANNOUNCEMENTS.stream().limit(3).forEach(a -> {
            Color c = tagCols[ci[0] % tagCols.length];
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
            row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

            // Left bar
            JPanel bar = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    g.setColor(c); g.fillRoundRect(0, 2, 3, getHeight() - 4, 2, 2);
                }
            };
            bar.setOpaque(false); bar.setPreferredSize(new Dimension(6, 40));

            JPanel content = new JPanel(new GridLayout(2, 1, 0, 2)); content.setOpaque(false);
            String t = a.title.length() > 24 ? a.title.substring(0, 24) + "…" : a.title;
            JLabel tl = new JLabel(t); tl.setFont(Theme.font(Font.BOLD, 11)); tl.setForeground(Color.WHITE);
            String b = a.body.length() > 38 ? a.body.substring(0, 38) + "…" : a.body;
            JLabel bl = new JLabel(b); bl.setFont(Theme.FONT_TINY); bl.setForeground(new Color(255, 255, 255, 120));
            content.add(tl); content.add(bl);

            row.add(bar, BorderLayout.WEST); row.add(content, BorderLayout.CENTER);
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            row.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    showAnnouncementDetail(a, c);
                }
            });
            list.add(row);
            ci[0]++;
        });

        if (DataStore.ANNOUNCEMENTS.isEmpty()) {
            JLabel empty = new JLabel("No announcements");
            empty.setFont(Theme.FONT_SMALL); empty.setForeground(new Color(255, 255, 255, 80));
            list.add(empty);
        }

        outer.add(list, BorderLayout.CENTER);
        return outer;
    }
// ── Dashboard quick-view popups ───────────────────────────────────────────

    private void showAnnouncementDetail(DataStore.Announcement a, Color tagColor) {
        javax.swing.JDialog dlg = new javax.swing.JDialog(
            (java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(this), a.title, true);
        dlg.setSize(560, 420); dlg.setLocationRelativeTo(this);

        javax.swing.JPanel root = new javax.swing.JPanel(new java.awt.BorderLayout());
        root.setBackground(Theme.BG_CARD);

        // Header band
        javax.swing.JPanel band = new javax.swing.JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new java.awt.GradientPaint(0,0,tagColor.darker(),getWidth(),0,tagColor));
                g2.fillRect(0,0,getWidth(),getHeight()); g2.dispose();
            }
        };
        band.setPreferredSize(new java.awt.Dimension(0, 80));
        band.setLayout(new java.awt.BorderLayout(0,0));
        band.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 22, 12, 22));
        javax.swing.JLabel tagLbl = Theme.chip("  " + (a.tag!=null?a.tag.toUpperCase():"") + "  ",
            java.awt.Color.WHITE, new java.awt.Color(255,255,255,50));
        javax.swing.JLabel titleLbl = new javax.swing.JLabel(a.title);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 17)); titleLbl.setForeground(java.awt.Color.WHITE);
        javax.swing.JPanel bandTop = new javax.swing.JPanel(new java.awt.BorderLayout()); bandTop.setOpaque(false);
        bandTop.add(tagLbl, java.awt.BorderLayout.WEST);
        javax.swing.JLabel meta = new javax.swing.JLabel((a.date!=null?a.date:"") + "  by " + a.postedBy);
        meta.setFont(Theme.FONT_SMALL); meta.setForeground(new java.awt.Color(255,255,255,160));
        bandTop.add(meta, java.awt.BorderLayout.EAST);
        band.add(bandTop, java.awt.BorderLayout.NORTH); band.add(titleLbl, java.awt.BorderLayout.SOUTH);
        root.add(band, java.awt.BorderLayout.NORTH);

        javax.swing.JTextArea body = new javax.swing.JTextArea(a.body);
        body.setEditable(false); body.setLineWrap(true); body.setWrapStyleWord(true);
        body.setBackground(Theme.BG_CARD); body.setForeground(Theme.TEXT_DARK);
        body.setFont(new Font("SansSerif", Font.PLAIN, 14));
        body.setBorder(javax.swing.BorderFactory.createEmptyBorder(20, 24, 20, 24));
        body.setCaretColor(Theme.BG_CARD);
        javax.swing.JScrollPane sp = new javax.swing.JScrollPane(body);
        sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
        root.add(sp, java.awt.BorderLayout.CENTER);

        javax.swing.JPanel footer = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT,12,10));
        footer.setBackground(Theme.BG_CARD);
        footer.setBorder(javax.swing.BorderFactory.createMatteBorder(1,0,0,0,Theme.BORDER));
        javax.swing.JButton close = Theme.primaryButton("Close"); close.addActionListener(e2->dlg.dispose());
        footer.add(close);
        root.add(footer, java.awt.BorderLayout.SOUTH);
        dlg.setContentPane(root); dlg.setVisible(true);
    }

    private void showEventDetail(DataStore.Event ev, Color accent) {
        javax.swing.JDialog dlg = new javax.swing.JDialog(
            (java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(this), ev.title, true);
        dlg.setSize(440, 320); dlg.setLocationRelativeTo(this);

        javax.swing.JPanel root = new javax.swing.JPanel(new java.awt.BorderLayout());
        root.setBackground(Theme.BG_CARD);

        javax.swing.JPanel band = new javax.swing.JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new java.awt.GradientPaint(0,0,accent.darker(),getWidth(),0,accent));
                g2.fillRect(0,0,getWidth(),getHeight()); g2.dispose();
            }
        };
        band.setPreferredSize(new java.awt.Dimension(0, 80));
        band.setLayout(new java.awt.BorderLayout(0,0));
        band.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 22, 14, 22));
        javax.swing.JLabel catChip = Theme.chip(" " + ev.category.toUpperCase() + " ",
            java.awt.Color.WHITE, new java.awt.Color(255,255,255,50));
        javax.swing.JLabel titleLbl = new javax.swing.JLabel(ev.title);
        titleLbl.setFont(new Font("SansSerif",Font.BOLD,18)); titleLbl.setForeground(java.awt.Color.WHITE);
        band.add(catChip, java.awt.BorderLayout.NORTH); band.add(titleLbl, java.awt.BorderLayout.SOUTH);
        root.add(band, java.awt.BorderLayout.NORTH);

        javax.swing.JPanel body = new javax.swing.JPanel();
        body.setBackground(Theme.BG_CARD);
        body.setLayout(new javax.swing.BoxLayout(body, javax.swing.BoxLayout.Y_AXIS));
        body.setBorder(javax.swing.BorderFactory.createEmptyBorder(20,24,20,24));

        addDetailRow(body, "📅 Date",     ev.date,     accent);
        addDetailRow(body, "🕐 Time",     ev.time,     accent);
        addDetailRow(body, "📍 Location", ev.location, accent);
        addDetailRow(body, "🏷 Category", ev.category, accent);
        root.add(body, java.awt.BorderLayout.CENTER);

        javax.swing.JPanel footer = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT,12,10));
        footer.setBackground(Theme.BG_CARD);
        footer.setBorder(javax.swing.BorderFactory.createMatteBorder(1,0,0,0,Theme.BORDER));
        javax.swing.JButton close = Theme.primaryButton("Close"); close.addActionListener(e2->dlg.dispose());
        footer.add(close);
        root.add(footer, java.awt.BorderLayout.SOUTH);
        dlg.setContentPane(root); dlg.setVisible(true);
    }

    private void addDetailRow(javax.swing.JPanel p, String label, String value, Color accent) {
        javax.swing.JPanel row = new javax.swing.JPanel(new java.awt.BorderLayout(10,0));
        row.setOpaque(false);
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 36));
        row.setBorder(javax.swing.BorderFactory.createEmptyBorder(0,0,8,0));
        javax.swing.JLabel lbl = new javax.swing.JLabel(label);
        lbl.setFont(Theme.font(Font.BOLD,12)); lbl.setForeground(Theme.TEXT_MUTE);
        lbl.setPreferredSize(new java.awt.Dimension(110,24));
        javax.swing.JLabel val = new javax.swing.JLabel(value);
        val.setFont(Theme.font(Font.PLAIN,13)); val.setForeground(Theme.TEXT_DARK);
        row.add(lbl, java.awt.BorderLayout.WEST); row.add(val, java.awt.BorderLayout.CENTER);
        row.setAlignmentX(LEFT_ALIGNMENT);
        p.add(row);
    }

    private void showNoteDetail(DataStore.Note n) {
        javax.swing.JDialog dlg = new javax.swing.JDialog(
            (java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(this), n.fileName, true);
        dlg.setSize(460, 320); dlg.setLocationRelativeTo(this);

        javax.swing.JPanel root = new javax.swing.JPanel(new java.awt.BorderLayout());
        root.setBackground(Theme.BG_CARD);

        // Header
        javax.swing.JPanel band = new javax.swing.JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new java.awt.GradientPaint(0,0,new java.awt.Color(0x6366F1),getWidth(),0,new java.awt.Color(0x8B5CF6)));
                g2.fillRect(0,0,getWidth(),getHeight()); g2.dispose();
            }
        };
        band.setPreferredSize(new java.awt.Dimension(0, 70));
        band.setLayout(new java.awt.BorderLayout(0,0));
        band.setBorder(javax.swing.BorderFactory.createEmptyBorder(12,22,12,22));
        javax.swing.JLabel typeLbl = Theme.chip(" " + n.fileType + " ", java.awt.Color.WHITE, new java.awt.Color(255,255,255,50));
        javax.swing.JLabel nameLbl = new javax.swing.JLabel(n.fileName);
        nameLbl.setFont(new Font("SansSerif",Font.BOLD,14)); nameLbl.setForeground(java.awt.Color.WHITE);
        band.add(typeLbl, java.awt.BorderLayout.NORTH); band.add(nameLbl, java.awt.BorderLayout.SOUTH);
        root.add(band, java.awt.BorderLayout.NORTH);

        javax.swing.JPanel body = new javax.swing.JPanel();
        body.setBackground(Theme.BG_CARD);
        body.setLayout(new javax.swing.BoxLayout(body, javax.swing.BoxLayout.Y_AXIS));
        body.setBorder(javax.swing.BorderFactory.createEmptyBorder(20,24,20,24));
        addDetailRow(body, "📚 Subject",  n.subjectName(),       Theme.PRIMARY);
        addDetailRow(body, "👤 Uploaded", n.uploadedBy,         Theme.PRIMARY);
        addDetailRow(body, "📦 Size",     n.fileSize,           Theme.PRIMARY);
        // Rating removed
        root.add(body, java.awt.BorderLayout.CENTER);

        javax.swing.JPanel footer = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT,12,10));
        footer.setBackground(Theme.BG_CARD);
        footer.setBorder(javax.swing.BorderFactory.createMatteBorder(1,0,0,0,Theme.BORDER));
        javax.swing.JButton view = Theme.primaryButton("View in Notes");
        view.addActionListener(e2->{ dlg.dispose(); if(navigator!=null) navigator.accept("notes"); });
        javax.swing.JButton close = Theme.ghostButton("Close", Theme.TEXT_MUTE);
        close.addActionListener(e2->dlg.dispose());
        footer.add(close); footer.add(view);
        root.add(footer, java.awt.BorderLayout.SOUTH);
        dlg.setContentPane(root); dlg.setVisible(true);
    }
}
