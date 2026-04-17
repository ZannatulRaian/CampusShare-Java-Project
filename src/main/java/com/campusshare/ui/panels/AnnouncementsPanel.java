package com.campusshare.ui.panels;

import com.campusshare.data.DataStore;
import com.campusshare.db.DAO;
import com.campusshare.ui.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

public class AnnouncementsPanel extends JPanel {

    private final DataStore.User user;

    private static final Color[] TAG_COLORS = {
        new Color(0xDC2626), new Color(0x6366F1), new Color(0xD97706),
        new Color(0x059669), new Color(0x7C3AED), new Color(0x0EA5E9),
    };

    public AnnouncementsPanel(DataStore.User user) {
        this.user = user;
        setBackground(Theme.BG_APP);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        rebuild();
    }

    private void rebuild() {
        removeAll();
        DataStore.reloadAnnouncements();

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));

        JPanel left = new JPanel(); left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS)); left.setOpaque(false);
        JLabel title = new JLabel("Announcements");
        title.setFont(new Font("SansSerif", Font.BOLD, 22)); title.setForeground(Theme.TEXT_DARK);
        JLabel sub = new JLabel(DataStore.ANNOUNCEMENTS.size() + " announcements posted");
        sub.setFont(Theme.font(Font.PLAIN, 12)); sub.setForeground(Theme.TEXT_MUTE);
        left.add(title); left.add(Box.createVerticalStrut(2)); left.add(sub);
        header.add(left, BorderLayout.WEST);

        if (user.isFaculty()) {
            JButton post = Theme.primaryButton("+ Post");
            post.setPreferredSize(new Dimension(100, 34));
            post.addActionListener(e -> showAddDialog());
            header.add(post, BorderLayout.EAST);
        }
        add(header, BorderLayout.NORTH);

        if (DataStore.ANNOUNCEMENTS.isEmpty()) {
            JPanel empty = new JPanel(new BorderLayout()); empty.setOpaque(false);
            JLabel lbl = new JLabel("No announcements yet");
            lbl.setFont(Theme.font(Font.PLAIN, 14)); lbl.setForeground(Theme.TEXT_FAINT);
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
            empty.add(lbl, BorderLayout.CENTER);
            add(empty, BorderLayout.CENTER);
            revalidate(); repaint(); return;
        }

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        int ci = 0;
        for (DataStore.Announcement a : DataStore.ANNOUNCEMENTS) {
            Color tagColor = TAG_COLORS[ci % TAG_COLORS.length];
            if (ci == 0) {
                content.add(buildFeaturedCard(a, tagColor));
                content.add(Box.createVerticalStrut(14));
            } else {
                content.add(buildListCard(a, tagColor));
                content.add(Box.createVerticalStrut(10));
            }
            ci++;
        }

        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        add(sp, BorderLayout.CENTER);
        revalidate(); repaint();
    }

    private JPanel buildFeaturedCard(DataStore.Announcement a, Color tagColor) {
        JPanel card = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x12101E));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setPaint(new GradientPaint(0, 0,
                    new Color(tagColor.getRed(), tagColor.getGreen(), tagColor.getBlue(), 120),
                    getWidth(), 0, new Color(0,0,0,0)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setColor(new Color(255,255,255,5));
                for (int x = 0; x < getWidth(); x += 28) g2.drawLine(x, 0, x, getHeight());
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        card.setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { showDetail(a, tagColor); }
        });

        JPanel topRow = new JPanel(new BorderLayout()); topRow.setOpaque(false);
        JLabel tagChip = Theme.chip("  " + a.tag.toUpperCase() + "  ", Color.WHITE,
            new Color(tagColor.getRed(), tagColor.getGreen(), tagColor.getBlue(), 160));
        JLabel dateLbl = new JLabel(a.date != null ? a.date : "");
        dateLbl.setFont(Theme.FONT_SMALL); dateLbl.setForeground(new Color(255,255,255,120));
        JLabel authorLbl = new JLabel("  — " + a.postedBy);
        authorLbl.setFont(Theme.FONT_SMALL); authorLbl.setForeground(new Color(255,255,255,100));
        JPanel dateAuthor = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)); dateAuthor.setOpaque(false);
        dateAuthor.add(dateLbl); dateAuthor.add(authorLbl);
        topRow.add(tagChip, BorderLayout.WEST); topRow.add(dateAuthor, BorderLayout.EAST);
        card.add(topRow, BorderLayout.NORTH);

        JLabel titleLbl = new JLabel("<html><span style='font-size:15pt;font-weight:bold;color:white'>"
            + a.title + "</span></html>");
        titleLbl.setBorder(BorderFactory.createEmptyBorder(10, 0, 8, 0));
        card.add(titleLbl, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(12, 0)); bottom.setOpaque(false);
        String preview = a.body.length() > 140 ? a.body.substring(0, 140) + "…" : a.body;
        JLabel bodyLbl = new JLabel("<html><div style='color:rgba(255,255,255,0.65);font-size:11pt'>"
            + preview + "</div></html>");
        JLabel clickHint = new JLabel("Click to read more →");
        clickHint.setFont(Theme.FONT_SMALL);
        clickHint.setForeground(new Color(tagColor.getRed(), tagColor.getGreen(), tagColor.getBlue(), 200));
        bottom.add(bodyLbl, BorderLayout.CENTER);

        JPanel rightCol = new JPanel(); rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS)); rightCol.setOpaque(false);
        rightCol.add(clickHint);
        if (user.isFaculty()) {
            JButton del = Theme.dangerButton("Delete");
            del.setPreferredSize(new Dimension(64, 26));
            del.addActionListener(e -> confirmDelete(a));
            rightCol.add(Box.createVerticalStrut(6));
            rightCol.add(del);
        }
        bottom.add(rightCol, BorderLayout.EAST);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildListCard(DataStore.Announcement a, Color tagColor) {
        JPanel card = new JPanel(new BorderLayout(0, 6)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_CARD);  // DARK card
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(tagColor);
                g2.fillRoundRect(0, 0, 5, getHeight(), 4, 4);
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 14, 14);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        card.setBorder(BorderFactory.createEmptyBorder(14, 22, 14, 18));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { showDetail(a, tagColor); }
            public void mouseEntered(MouseEvent e) { card.repaint(); }
            public void mouseExited(MouseEvent e)  { card.repaint(); }
        });

        JPanel topRow = new JPanel(new BorderLayout(8, 0)); topRow.setOpaque(false);
        JLabel tagChip = Theme.chip(a.tag.toUpperCase(), tagColor,
            new Color(tagColor.getRed(), tagColor.getGreen(), tagColor.getBlue(), 35));
        JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0)); rightTop.setOpaque(false);
        JLabel dateLbl = new JLabel(a.date != null ? a.date : "");
        dateLbl.setFont(Theme.FONT_SMALL); dateLbl.setForeground(Theme.TEXT_FAINT);
        if (user.isFaculty()) {
            JButton del = Theme.dangerButton("Delete");
            del.setPreferredSize(new Dimension(60, 22));
            del.addActionListener(e -> confirmDelete(a));
            rightTop.add(del);
        }
        rightTop.add(dateLbl);
        topRow.add(tagChip, BorderLayout.WEST); topRow.add(rightTop, BorderLayout.EAST);
        card.add(topRow, BorderLayout.NORTH);

        JLabel titleLbl = new JLabel(a.title);
        titleLbl.setFont(Theme.font(Font.BOLD, 13)); titleLbl.setForeground(Theme.TEXT_DARK);
        card.add(titleLbl, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(10, 0)); bottom.setOpaque(false);
        String preview = a.body.length() > 100 ? a.body.substring(0, 100) + "…" : a.body;
        JLabel bodyLbl = new JLabel(preview);
        bodyLbl.setFont(Theme.FONT_SMALL); bodyLbl.setForeground(Theme.TEXT_MUTE);
        bottom.add(bodyLbl, BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    /** Full-detail popup when clicking an announcement */
    private void showDetail(DataStore.Announcement a, Color tagColor) {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), a.title, true);
        dlg.setSize(560, 420);
        dlg.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG_CARD);

        // Header band
        JPanel band = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0,0,tagColor.darker(),getWidth(),0,tagColor));
                g2.fillRect(0,0,getWidth(),getHeight()); g2.dispose();
            }
        };
        band.setPreferredSize(new Dimension(0, 80));
        band.setLayout(new BorderLayout(0,0));
        band.setBorder(BorderFactory.createEmptyBorder(16, 24, 12, 24));
        JLabel tagLbl = Theme.chip("  " + a.tag.toUpperCase() + "  ", Color.WHITE,
            new Color(255,255,255,50));
        JLabel titleLbl = new JLabel(a.title);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 17));
        titleLbl.setForeground(Color.WHITE);
        JPanel bandTop = new JPanel(new BorderLayout()); bandTop.setOpaque(false);
        bandTop.add(tagLbl, BorderLayout.WEST);
        JLabel meta = new JLabel((a.date != null ? a.date : "") + "  by " + a.postedBy);
        meta.setFont(Theme.FONT_SMALL); meta.setForeground(new Color(255,255,255,160));
        bandTop.add(meta, BorderLayout.EAST);
        band.add(bandTop, BorderLayout.NORTH);
        band.add(titleLbl, BorderLayout.SOUTH);
        root.add(band, BorderLayout.NORTH);

        // Body text
        JTextArea body = new JTextArea(a.body);
        body.setEditable(false); body.setLineWrap(true); body.setWrapStyleWord(true);
        body.setBackground(Theme.BG_CARD); body.setForeground(Theme.TEXT_DARK);
        body.setFont(new Font("SansSerif", Font.PLAIN, 14));
        body.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        body.setCaretColor(Theme.BG_CARD);
        JScrollPane sp = new JScrollPane(body);
        sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
        root.add(sp, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        footer.setBackground(Theme.BG_CARD);
        footer.setBorder(BorderFactory.createMatteBorder(1,0,0,0,Theme.BORDER));
        JButton close = Theme.primaryButton("Close");
        close.addActionListener(e -> dlg.dispose());
        footer.add(close);
        root.add(footer, BorderLayout.SOUTH);

        dlg.setContentPane(root); dlg.setVisible(true);
    }

    private void confirmDelete(DataStore.Announcement a) {
        int r = JOptionPane.showConfirmDialog(this, "Delete \"" + a.title + "\"?",
            "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION) {
            DAO.deleteAnnouncement(a.id);
            DataStore.reloadAnnouncements();
            rebuild();
        }
    }

    private void showAddDialog() {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Post Announcement", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.setSize(520, 460);
        dlg.setMinimumSize(new Dimension(420, 400));
        dlg.setLocationRelativeTo(this);

        // Use BorderLayout so button row is always visible at bottom
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Theme.BG_CARD);

        // Header
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 16));
        header.setBackground(new Color(0x1A1836));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        JLabel head = new JLabel("Post an Announcement");
        head.setFont(new Font("SansSerif", Font.BOLD, 16)); head.setForeground(Theme.TEXT_DARK);
        header.add(head);
        root.add(header, BorderLayout.NORTH);

        // Form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.BG_CARD);
        form.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0; gc.weightx = 1.0;

        // Title
        gc.gridy = 0; gc.insets = new Insets(0, 0, 4, 0);
        form.add(makeLabel("Title"), gc);
        JTextField titleF = Theme.styledField("Announcement title…");
        gc.gridy = 1; gc.insets = new Insets(0, 0, 12, 0);
        form.add(titleF, gc);

        // Body — fills available space
        gc.gridy = 2; gc.insets = new Insets(0, 0, 4, 0); gc.weighty = 0;
        form.add(makeLabel("Body"), gc);
        JTextArea bodyA = new JTextArea();
        bodyA.setBackground(Theme.BG_INPUT); bodyA.setForeground(Theme.TEXT_DARK);
        bodyA.setCaretColor(Theme.PRIMARY); bodyA.setFont(Theme.FONT_BODY);
        bodyA.setLineWrap(true); bodyA.setWrapStyleWord(true);
        bodyA.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane bsp = new JScrollPane(bodyA);
        bsp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        gc.gridy = 3; gc.insets = new Insets(0, 0, 12, 0); gc.weighty = 1.0;
        gc.fill = GridBagConstraints.BOTH;
        form.add(bsp, gc);

        // Tag
        gc.gridy = 4; gc.insets = new Insets(0, 0, 4, 0); gc.weighty = 0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        form.add(makeLabel("Tag"), gc);
        String[] tags = {"GENERAL","URGENT","ACADEMIC","FACILITY","EXAM","EVENT"};
        JComboBox<String> tagBox = new JComboBox<>(tags);
        tagBox.setBackground(Theme.BG_INPUT); tagBox.setForeground(Theme.TEXT_DARK);
        tagBox.setFont(Theme.font(Font.PLAIN, 13));
        gc.gridy = 5; gc.insets = new Insets(0, 0, 0, 0);
        form.add(tagBox, gc);

        root.add(form, BorderLayout.CENTER);

        // Button row — always visible at the bottom
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        btnRow.setBackground(Theme.BG_CARD);
        btnRow.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        JButton cancel = Theme.ghostButton("Cancel", Theme.TEXT_MUTE);
        cancel.setPreferredSize(new Dimension(90, 34));
        cancel.addActionListener(e -> dlg.dispose());
        JButton post = Theme.primaryButton("Post");
        post.setPreferredSize(new Dimension(100, 34));
        post.addActionListener(e -> {
            if (titleF.getText().trim().isEmpty()) { JOptionPane.showMessageDialog(dlg,"Title required"); return; }
            if (bodyA.getText().trim().isEmpty())  { JOptionPane.showMessageDialog(dlg,"Body required"); return; }
            DAO.addAnnouncement(titleF.getText().trim(), bodyA.getText().trim(),
                user.id, (String) tagBox.getSelectedItem());
            DataStore.reloadAnnouncements(); rebuild(); dlg.dispose();
        });
        btnRow.add(cancel); btnRow.add(post);
        root.add(btnRow, BorderLayout.SOUTH);

        dlg.setContentPane(root); dlg.setVisible(true);
    }

    private JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_SMALL); l.setForeground(Theme.TEXT_MUTE);
        return l;
    }
    // Inner border class for dialog text areas
    private static class RoundBorder extends javax.swing.border.AbstractBorder {
        private final int radius; private final Color color; private final int thickness;
        RoundBorder(int r, Color c, int t) { radius=r; color=c; thickness=t; }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color); g2.setStroke(new java.awt.BasicStroke(thickness));
            g2.drawRoundRect(x,y,w-1,h-1,radius,radius); g2.dispose();
        }
        @Override public java.awt.Insets getBorderInsets(Component c) {
            return new java.awt.Insets(radius/2, radius/2, radius/2, radius/2);
        }
    }
}