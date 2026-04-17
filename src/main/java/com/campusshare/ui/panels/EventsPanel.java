package com.campusshare.ui.panels;

import com.campusshare.data.DataStore;
import com.campusshare.db.DAO;
import com.campusshare.ui.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

public class EventsPanel extends JPanel {

    private final DataStore.User user;

    private static final Color[] ACCENTS = {
        new Color(0x6366F1), new Color(0x0D9488), new Color(0xF43F5E),
        new Color(0xF59E0B), new Color(0x8B5CF6), new Color(0x38BDF8),
    };

    public EventsPanel(DataStore.User user) {
        this.user = user;
        setBackground(Theme.BG_APP);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        rebuild();
    }

    private void rebuild() {
        removeAll();
        DataStore.reloadEvents();

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));

        JPanel left = new JPanel(); left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS)); left.setOpaque(false);
        JLabel title = new JLabel("Campus Events");
        title.setFont(new Font("SansSerif", Font.BOLD, 22)); title.setForeground(Theme.TEXT_DARK);
        JLabel sub = new JLabel(DataStore.EVENTS.size() + " events scheduled");
        sub.setFont(Theme.font(Font.PLAIN, 12)); sub.setForeground(Theme.TEXT_MUTE);
        left.add(title); left.add(Box.createVerticalStrut(2)); left.add(sub);
        header.add(left, BorderLayout.WEST);

        if (user.isFaculty()) {
            JButton add = Theme.primaryButton("+ Add Event");
            add.addActionListener(e -> showAddDialog());
            header.add(add, BorderLayout.EAST);
        }
        add(header, BorderLayout.NORTH);

        if (DataStore.EVENTS.isEmpty()) {
            JPanel empty = new JPanel(new BorderLayout()); empty.setOpaque(false);
            JLabel lbl = new JLabel("No events yet");
            lbl.setFont(Theme.font(Font.PLAIN, 14)); lbl.setForeground(Theme.TEXT_FAINT);
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
            empty.add(lbl, BorderLayout.CENTER);
            add(empty, BorderLayout.CENTER);
        } else {
            JPanel grid = new JPanel(new GridLayout(0, 2, 14, 14));
            grid.setOpaque(false);
            int ci = 0;
            for (DataStore.Event ev : DataStore.EVENTS) {
                grid.add(buildCard(ev, ACCENTS[ci % ACCENTS.length]));
                ci++;
            }
            JPanel wrap = new JPanel(new BorderLayout()); wrap.setOpaque(false);
            wrap.add(grid, BorderLayout.NORTH);
            JScrollPane sp = new JScrollPane(wrap);
            sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
            sp.getVerticalScrollBar().setUnitIncrement(16);
            add(sp, BorderLayout.CENTER);
        }
        revalidate(); repaint();
    }

    private JPanel buildCard(DataStore.Event ev, Color accent) {
        // ── Outer card — DARK ─────────────────────────────────────────────────
        JPanel card = new JPanel(new BorderLayout(0, 0)) {
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
        card.setOpaque(false);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { showDetail(ev, accent); }
        });

        // ── Top color band ────────────────────────────────────────────────────
        JPanel band = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(accent);
                g2.fillRoundRect(0, 0, getWidth(), getHeight() + 16, 16, 16);
                g2.setColor(new Color(255,255,255,15));
                g2.fillOval(getWidth() - 60, -20, 110, 110);
                g2.dispose();
            }
        };
        band.setPreferredSize(new Dimension(0, 70));
        band.setOpaque(false);

        // Date badge
        String[] parts = ev.date.split("-");
        String day = parts.length >= 3 ? parts[2] : ev.date;
        String mon = parts.length >= 2 ? monthShort(parts[1]) : "";
        JLabel dateBadge = new JLabel("<html><center><b style='font-size:15pt;color:white'>" + day +
            "</b><br><span style='font-size:8pt;color:white'>" + mon + "</span></center></html>");
        dateBadge.setOpaque(true);
        dateBadge.setBackground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120));
        dateBadge.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        dateBadge.setBounds(12, 10, 50, 48);
        band.add(dateBadge);

        // Category chip
        JLabel cat = new JLabel(ev.category.toUpperCase());
        cat.setFont(Theme.font(Font.BOLD, 9));
        cat.setForeground(Color.WHITE);
        cat.setOpaque(false);
        cat.setBounds(72, 12, 120, 16);
        band.add(cat);

        JLabel timeLbl = new JLabel("🕐 " + ev.time);
        timeLbl.setFont(Theme.FONT_SMALL);
        timeLbl.setForeground(new Color(255,255,255,160));
        timeLbl.setBounds(72, 34, 150, 16);
        band.add(timeLbl);

        card.add(band, BorderLayout.NORTH);

        // ── Body — dark ────────────────────────────────────────────────────────
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JLabel titleLbl = new JLabel(ev.title);
        titleLbl.setFont(Theme.font(Font.BOLD, 14));
        titleLbl.setForeground(Theme.TEXT_DARK);   // light on dark
        body.add(titleLbl, BorderLayout.NORTH);

        JLabel locLbl = new JLabel("📍 " + ev.location);
        locLbl.setFont(Theme.FONT_SMALL);
        locLbl.setForeground(Theme.TEXT_MUTE);
        body.add(locLbl, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout()); footer.setOpaque(false);
        JLabel hint = new JLabel("Click for details");
        hint.setFont(Theme.FONT_TINY);
        hint.setForeground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 160));
        footer.add(hint, BorderLayout.WEST);

        if (user.isFaculty()) {
            JButton del = Theme.dangerButton("Delete");
            del.setPreferredSize(new Dimension(62, 26));
            del.addActionListener(e -> {
                int r = JOptionPane.showConfirmDialog(this, "Delete \"" + ev.title + "\"?",
                    "Confirm", JOptionPane.YES_NO_OPTION);
                if (r == JOptionPane.YES_OPTION) { DAO.deleteEvent(ev.id); DataStore.reloadEvents(); rebuild(); }
            });
            footer.add(del, BorderLayout.EAST);
        }
        body.add(footer, BorderLayout.SOUTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private void showDetail(DataStore.Event ev, Color accent) {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), ev.title, true);
        dlg.setSize(440, 320);
        dlg.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG_CARD);

        JPanel band = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0,0,accent.darker(),getWidth(),0,accent));
                g2.fillRect(0,0,getWidth(),getHeight()); g2.dispose();
            }
        };
        band.setPreferredSize(new Dimension(0, 80));
        band.setLayout(new BorderLayout(0,0));
        band.setBorder(BorderFactory.createEmptyBorder(14, 22, 14, 22));
        JLabel catChip = Theme.chip(" " + ev.category.toUpperCase() + " ", Color.WHITE,
            new Color(255,255,255,50));
        JLabel titleLbl = new JLabel(ev.title);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 18)); titleLbl.setForeground(Color.WHITE);
        band.add(catChip, BorderLayout.NORTH); band.add(titleLbl, BorderLayout.SOUTH);
        root.add(band, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setBackground(Theme.BG_CARD);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        addDetail(body, "📅 Date", ev.date);
        addDetail(body, "🕐 Time", ev.time);
        addDetail(body, "📍 Location", ev.location);
        addDetail(body, "🏷 Category", ev.category);
        root.add(body, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        footer.setBackground(Theme.BG_CARD);
        footer.setBorder(BorderFactory.createMatteBorder(1,0,0,0,Theme.BORDER));
        JButton close = Theme.primaryButton("Close");
        close.addActionListener(e -> dlg.dispose());
        footer.add(close);
        root.add(footer, BorderLayout.SOUTH);

        dlg.setContentPane(root); dlg.setVisible(true);
    }

    private void addDetail(JPanel p, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(10, 0)); row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        row.setBorder(BorderFactory.createEmptyBorder(0,0,8,0));
        JLabel lbl = new JLabel(label);
        lbl.setFont(Theme.font(Font.BOLD, 12)); lbl.setForeground(Theme.TEXT_MUTE);
        lbl.setPreferredSize(new Dimension(100, 24));
        JLabel val = new JLabel(value);
        val.setFont(Theme.font(Font.PLAIN, 13)); val.setForeground(Theme.TEXT_DARK);
        row.add(lbl, BorderLayout.WEST); row.add(val, BorderLayout.CENTER);
        row.setAlignmentX(LEFT_ALIGNMENT);
        p.add(row);
    }

    private String monthShort(String mon) {
        try {
            int m = Integer.parseInt(mon.trim());
            String[] months={"JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"};
            if (m>=1&&m<=12) return months[m-1];
        } catch (NumberFormatException ignored) {}
        return mon.toUpperCase();
    }

    private void showAddDialog() {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Add Event", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.setSize(460, 500);
        dlg.setMinimumSize(new Dimension(400, 460));
        dlg.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Theme.BG_CARD);

        // Header
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 14));
        header.setBackground(new Color(0x1A1836));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        JLabel head = new JLabel("New Campus Event");
        head.setFont(new Font("SansSerif", Font.BOLD, 16)); head.setForeground(Theme.TEXT_DARK);
        header.add(head);
        root.add(header, BorderLayout.NORTH);

        // Scrollable form in center
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.BG_CARD);
        form.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0; gc.weightx = 1.0;

        JTextField titleF = Theme.styledField("Event title");
        JTextField dateF  = Theme.styledField("Date  (YYYY-MM-DD)  e.g. 2026-05-15");
        JTextField timeF  = Theme.styledField("Time  e.g. 10:00");
        JTextField locF   = Theme.styledField("Location / venue");
        String[] cats = {"Seminar","Workshop","Cultural","Sports","Academic","Health","Other"};
        JComboBox<String> catBox = new JComboBox<>(cats);
        catBox.setBackground(Theme.BG_INPUT); catBox.setForeground(Theme.TEXT_DARK);
        catBox.setFont(Theme.font(Font.PLAIN, 13));

        String[] labels = {"Title","Date","Time","Category","Location"};
        JComponent[] comps = {titleF, dateF, timeF, catBox, locF};
        for (int i = 0; i < labels.length; i++) {
            JLabel lbl = new JLabel(labels[i]);
            lbl.setForeground(Theme.TEXT_MUTE); lbl.setFont(Theme.FONT_SMALL);
            gc.gridy = i*2; gc.insets = new Insets(i==0?0:8, 0, 4, 0);
            form.add(lbl, gc);
            gc.gridy = i*2+1; gc.insets = new Insets(0, 0, 0, 0);
            form.add(comps[i], gc);
        }

        JScrollPane sp = new JScrollPane(form);
        sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
        root.add(sp, BorderLayout.CENTER);

        // Button row — always visible at bottom
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        btnRow.setBackground(Theme.BG_CARD);
        btnRow.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        JButton cancel = Theme.ghostButton("Cancel", Theme.TEXT_MUTE);
        cancel.setPreferredSize(new Dimension(90, 34));
        cancel.addActionListener(e -> dlg.dispose());
        JButton save = Theme.primaryButton("Add Event");
        save.setPreferredSize(new Dimension(120, 34));
        save.addActionListener(e -> {
            if (titleF.getText().trim().isEmpty()) { JOptionPane.showMessageDialog(dlg,"Title required"); return; }
            if (dateF.getText().trim().isEmpty()) { JOptionPane.showMessageDialog(dlg,"Date required (YYYY-MM-DD)"); return; }
            DAO.addEvent(titleF.getText().trim(), dateF.getText().trim(),
                timeF.getText().trim(), (String) catBox.getSelectedItem(),
                locF.getText().trim(), user.id);
            DataStore.reloadEvents(); rebuild(); dlg.dispose();
        });
        btnRow.add(cancel); btnRow.add(save);
        root.add(btnRow, BorderLayout.SOUTH);

        dlg.setContentPane(root); dlg.setVisible(true);
    }
}
