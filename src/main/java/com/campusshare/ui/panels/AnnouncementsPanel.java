package com.campusshare.ui.panels;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.campusshare.data.DataStore;
import com.campusshare.db.DAO;
import com.campusshare.ui.Theme;

public class AnnouncementsPanel extends JPanel {

    private final DataStore.User user;
    private JComboBox<String> deptFilter;
    private JPanel contentPanel;

    private static final Color[] TAG_COLORS = {
        new Color(0xDC2626), new Color(0x6366F1), new Color(0xD97706),
        new Color(0x059669), new Color(0x7C3AED), new Color(0x0EA5E9),
    };
    private static final String[] DEPTS = {"ALL","CSE","EEE","BBA","Law","English","Mathematics"};

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

        // ── Header ──────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));

        JPanel left = new JPanel(); left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS)); left.setOpaque(false);
        JLabel title = new JLabel("Announcements");
        title.setFont(new Font("SansSerif", Font.BOLD, 20)); title.setForeground(Theme.TEXT_DARK);
        JLabel sub = new JLabel(DataStore.ANNOUNCEMENTS.size() + " announcements posted");
        sub.setFont(Theme.font(Font.PLAIN, 14)); sub.setForeground(Theme.TEXT_MUTE);
        left.add(title); left.add(Box.createVerticalStrut(2)); left.add(sub);
        header.add(left, BorderLayout.WEST);

        JPanel rightH = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightH.setOpaque(false);

        // Department filter — faculty/admin sees all depts; students see ALL + their dept only
        if (user.isFaculty()) {
            deptFilter = new JComboBox<>(DEPTS);
        } else {
            deptFilter = new JComboBox<>(new String[]{"ALL", user.department});
        }
        deptFilter.setBackground(Theme.BG_INPUT); deptFilter.setForeground(Theme.TEXT_DARK);
        deptFilter.setFont(Theme.font(Font.PLAIN, 15));
        deptFilter.setPreferredSize(new Dimension(130, 32));
        deptFilter.addActionListener(e -> repopulate());
        rightH.add(deptFilter);

        if (user.isFaculty()) {
            JButton post = Theme.primaryButton("+ Post");
            post.setPreferredSize(new Dimension(90, 32));
            post.addActionListener(e -> showAddDialog());
            rightH.add(post);
        }
        header.add(rightH, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ── Content area ────────────────────────────────────────────────────
        contentPanel = new JPanel();
        contentPanel.setOpaque(false);
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        JScrollPane sp = new JScrollPane(contentPanel);
        sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(14);
        add(sp, BorderLayout.CENTER);

        repopulate();
    }

    private void repopulate() {
        if (contentPanel == null) return;
        contentPanel.removeAll();

        String selDept = deptFilter != null ? (String) deptFilter.getSelectedItem() : "ALL";

        List<DataStore.Announcement> list = DataStore.ANNOUNCEMENTS.stream()
            .filter(a -> a.visibleTo(user))
            .filter(a -> "ALL".equalsIgnoreCase(selDept) || "ALL".equalsIgnoreCase(a.department) || a.department.equalsIgnoreCase(selDept))
            .collect(Collectors.toList());

        if (list.isEmpty()) {
            JLabel lbl = new JLabel("No announcements found");
            lbl.setFont(Theme.font(Font.PLAIN, 18)); lbl.setForeground(Theme.TEXT_FAINT);
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
            lbl.setAlignmentX(CENTER_ALIGNMENT);
            contentPanel.add(Box.createVerticalStrut(40));
            contentPanel.add(lbl);
        } else {
            boolean first = true;
            for (int i = 0; i < list.size(); i++) {
                DataStore.Announcement a = list.get(i);
                Color tc = TAG_COLORS[i % TAG_COLORS.length];
                JPanel card = first ? buildFeaturedCard(a, tc) : buildListCard(a, tc);
                card.setAlignmentX(LEFT_ALIGNMENT);
                contentPanel.add(card);
                contentPanel.add(Box.createVerticalStrut(12));
                first = false;
            }
        }
        contentPanel.revalidate(); contentPanel.repaint();
    }

    private JPanel buildFeaturedCard(DataStore.Announcement a, Color tagColor) {
        JPanel card = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0,
                    new Color(tagColor.getRed(), tagColor.getGreen(), tagColor.getBlue(), 130),
                    getWidth(), 0, new Color(tagColor.getRed(), tagColor.getGreen(), tagColor.getBlue(), 60)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(Theme.BORDER); g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 14, 14);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 175));
        card.setPreferredSize(new Dimension(0, 175));
        card.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        card.addMouseListener(new MouseAdapter() { public void mousePressed(MouseEvent e) { showDetail(a, tagColor); } });

        // Header row: tag chip left, meta right
        JPanel topRow = new JPanel(new BorderLayout()); topRow.setOpaque(false);
        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); chips.setOpaque(false);
        chips.add(Theme.chip("  " + a.tag.toUpperCase() + "  ", Color.WHITE, new Color(tagColor.getRed(), tagColor.getGreen(), tagColor.getBlue(), 160)));
        if (!"ALL".equalsIgnoreCase(a.department))
            chips.add(Theme.chip("  " + a.department.toUpperCase() + "  ", Theme.TEXT_DARK, Theme.BG_SUBTLE));
        JLabel meta = new JLabel(a.date);
        meta.setFont(Theme.FONT_TINY); meta.setForeground(new Color(255,255,255,160));
        topRow.add(chips, BorderLayout.WEST); topRow.add(meta, BorderLayout.EAST);

        JLabel titleLbl = new JLabel(a.title);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 18)); titleLbl.setForeground(Color.WHITE);
        // Body truncated to one line to keep card compact — click to see full
        String bodyText = a.body.length() > 120 ? a.body.substring(0, 120) + "…" : a.body;
        JLabel bodyLbl = new JLabel("<html><body style='width:100%'><p style='color:rgba(255,255,255,0.85)'>" + bodyText + "</p></body></html>");
        bodyLbl.setFont(Theme.font(Font.PLAIN, 13));

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);
        topRow.setAlignmentX(LEFT_ALIGNMENT);
        titleLbl.setAlignmentX(LEFT_ALIGNMENT);
        bodyLbl.setAlignmentX(LEFT_ALIGNMENT);
        inner.add(topRow);
        inner.add(Box.createVerticalStrut(8));
        inner.add(titleLbl);
        inner.add(Box.createVerticalStrut(4));
        inner.add(bodyLbl);
        inner.add(Box.createVerticalGlue()); // push content up
        card.add(inner, BorderLayout.CENTER);

        if (user.isFaculty()) {
            JPanel delWrapper = new JPanel(new GridBagLayout());
            delWrapper.setOpaque(false);
            delWrapper.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
            delWrapper.add(buildDelBtn(a));
            card.add(delWrapper, BorderLayout.EAST);
        }
        return card;
    }

    private JPanel buildListCard(DataStore.Announcement a, Color tagColor) {
        JPanel card = new JPanel(new BorderLayout(14, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_CARD); g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                g2.setColor(tagColor); g2.fillRoundRect(0,0,5,getHeight(),3,3);
                g2.setColor(Theme.BORDER); g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        card.setPreferredSize(new Dimension(0, 100));
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        card.addMouseListener(new MouseAdapter() { public void mousePressed(MouseEvent e) { showDetail(a, tagColor); } });

        JPanel left2 = new JPanel(); left2.setLayout(new BoxLayout(left2, BoxLayout.Y_AXIS)); left2.setOpaque(false);
        // Single row: chip + title inline to save vertical space
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        topRow.setOpaque(false);
        topRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        topRow.add(Theme.chip(a.tag.toUpperCase(), tagColor, new Color(tagColor.getRed(), tagColor.getGreen(), tagColor.getBlue(), 35)));
        if (!"ALL".equalsIgnoreCase(a.department))
            topRow.add(Theme.chip(a.department.toUpperCase(), Theme.TEXT_MUTE, Theme.BG_SUBTLE));
        JLabel titleLbl = new JLabel("<html><b>" + a.title + "</b></html>");
        titleLbl.setFont(Theme.font(Font.BOLD, 15)); titleLbl.setForeground(Theme.TEXT_DARK);
        JLabel metaLbl = new JLabel(a.date + "  •  " + a.postedBy);
        metaLbl.setFont(Theme.FONT_TINY); metaLbl.setForeground(Theme.TEXT_FAINT);
        topRow.setAlignmentX(LEFT_ALIGNMENT);
        titleLbl.setAlignmentX(LEFT_ALIGNMENT);
        metaLbl.setAlignmentX(LEFT_ALIGNMENT);
        left2.add(topRow); left2.add(Box.createVerticalStrut(3)); left2.add(titleLbl); left2.add(metaLbl);
        card.add(left2, BorderLayout.CENTER);
        if (user.isFaculty()) {
            JPanel delWrapper = new JPanel(new GridBagLayout());
            delWrapper.setOpaque(false);
            delWrapper.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
            delWrapper.add(buildDelBtn(a));
            card.add(delWrapper, BorderLayout.EAST);
        }
        return card;
    }

    private JButton buildDelBtn(DataStore.Announcement a) {
        JButton del = new JButton("🗑  Delete");
        del.setFont(Theme.font(Font.BOLD, 14));
        del.setPreferredSize(new Dimension(100, 32));
        del.setBackground(new Color(0xF43F5E));
        del.setForeground(Color.WHITE);
        del.setFocusPainted(false);
        del.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        del.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        del.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Delete this announcement?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                DAO.deleteAnnouncement(a.id); rebuild();
            }
        });
        return del;
    }

    private void showDetail(DataStore.Announcement a, Color tagColor) {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), a.title, Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(640, 500); dlg.setLocationRelativeTo(this);
        JPanel root = new JPanel(new BorderLayout()); root.setBackground(Theme.BG_CARD);

        JPanel band = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0,0,tagColor.darker(),getWidth(),0,tagColor)); g2.fillRect(0,0,getWidth(),getHeight()); g2.dispose();
            }
        };
        // FIX: Increased height from 70 to 90 to prevent title clipping
        band.setPreferredSize(new Dimension(0, 90));
        band.setBorder(BorderFactory.createEmptyBorder(14,20,14,20));
        JPanel bandContent = new JPanel(); bandContent.setLayout(new BoxLayout(bandContent,BoxLayout.Y_AXIS)); bandContent.setOpaque(false);
        JPanel chipRow = new JPanel(new FlowLayout(FlowLayout.LEFT,6,0)); chipRow.setOpaque(false);
        chipRow.add(Theme.chip("  " + a.tag.toUpperCase() + "  ", Color.WHITE, new Color(255,255,255,60)));
        if (!"ALL".equalsIgnoreCase(a.department)) chipRow.add(Theme.chip("  " + a.department + "  ", Color.WHITE, new Color(255,255,255,40)));
        JLabel tl = new JLabel(a.title); tl.setFont(new Font("SansSerif",Font.BOLD,22)); tl.setForeground(Color.WHITE);
        bandContent.add(chipRow); bandContent.add(Box.createVerticalStrut(6)); bandContent.add(tl);
        band.add(bandContent,BorderLayout.CENTER);
        root.add(band,BorderLayout.NORTH);

        JTextArea body = new JTextArea(a.body); body.setEditable(false); body.setLineWrap(true); body.setWrapStyleWord(true);
        body.setBackground(Theme.BG_CARD); body.setForeground(Theme.TEXT_DARK); body.setFont(Theme.font(Font.PLAIN,18));
        body.setBorder(BorderFactory.createEmptyBorder(18,20,18,20));
        root.add(new JScrollPane(body),BorderLayout.CENTER);

        JLabel meta = new JLabel("  Posted by " + a.postedBy + "  •  " + a.date);
        meta.setFont(Theme.FONT_TINY); meta.setForeground(Theme.TEXT_FAINT);
        meta.setBorder(BorderFactory.createEmptyBorder(8,20,12,20));
        root.add(meta,BorderLayout.SOUTH);

        dlg.setContentPane(root); dlg.setVisible(true);
    }

    private void showAddDialog() {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Post Announcement", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(580, 500); dlg.setLocationRelativeTo(this);
        JPanel root = new JPanel(new BorderLayout(0,12)); root.setBackground(Theme.BG_CARD);
        root.setBorder(BorderFactory.createEmptyBorder(20,24,20,24));

        JLabel hdr = new JLabel("New Announcement"); hdr.setFont(Theme.font(Font.BOLD,22)); hdr.setForeground(Theme.TEXT_DARK);
        root.add(hdr, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout()); form.setBackground(Theme.BG_CARD);
        GridBagConstraints gc = new GridBagConstraints(); gc.fill=GridBagConstraints.HORIZONTAL; gc.weightx=1; gc.gridx=0;

        JTextField titleF = Theme.styledField("Announcement title…"); titleF.setFont(Theme.font(Font.PLAIN,18));
        gc.gridy=0; gc.insets=new Insets(0,0,10,0); form.add(titleF,gc);

        JTextArea bodyA = new JTextArea(5, 20); bodyA.setLineWrap(true); bodyA.setWrapStyleWord(true);
        bodyA.setBackground(Theme.BG_INPUT); bodyA.setForeground(Theme.TEXT_DARK); bodyA.setFont(Theme.font(Font.PLAIN,14));
        bodyA.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));
        gc.gridy=1; form.add(new JScrollPane(bodyA),gc);

        // Tag + Department row
        JPanel selRow = new JPanel(new GridLayout(1,2,10,0)); selRow.setOpaque(false);
        String[] tags = {"GENERAL","URGENT","ACADEMIC","FACILITY","EXAM","EVENT"};
        JComboBox<String> tagBox = new JComboBox<>(tags);
        tagBox.setBackground(Theme.BG_INPUT); tagBox.setForeground(Theme.TEXT_DARK); tagBox.setFont(Theme.font(Font.PLAIN,14));
        JComboBox<String> deptBox = new JComboBox<>(DEPTS);
        deptBox.setBackground(Theme.BG_INPUT); deptBox.setForeground(Theme.TEXT_DARK); deptBox.setFont(Theme.font(Font.PLAIN,14));
        selRow.add(tagBox); selRow.add(deptBox);
        gc.gridy=2; gc.insets=new Insets(10,0,0,0); form.add(selRow,gc);

        root.add(form, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0)); btnRow.setOpaque(false);
        JButton cancel = new JButton("Cancel"); cancel.addActionListener(e -> dlg.dispose());
        JButton post = Theme.primaryButton("Post");
        post.addActionListener(e -> {
            if (titleF.getText().trim().isEmpty()) { JOptionPane.showMessageDialog(dlg,"Title required"); return; }
            if (bodyA.getText().trim().isEmpty())  { JOptionPane.showMessageDialog(dlg,"Body required"); return; }
            String dept = (String) deptBox.getSelectedItem();
            int annId = DAO.addAnnouncement(titleF.getText().trim(), bodyA.getText().trim(), user.id, (String)tagBox.getSelectedItem(), dept);
            DAO.pushNotificationToAll(dept, "ann", "📢 " + titleF.getText().trim(),
                bodyA.getText().trim().length()>80 ? bodyA.getText().trim().substring(0,80)+"…" : bodyA.getText().trim(), annId, user.id);
            DataStore.reloadAnnouncements(); rebuild(); dlg.dispose();
        });
        btnRow.add(cancel); btnRow.add(post);
        root.add(btnRow, BorderLayout.SOUTH);

        dlg.setContentPane(root); dlg.setVisible(true);
    }
}