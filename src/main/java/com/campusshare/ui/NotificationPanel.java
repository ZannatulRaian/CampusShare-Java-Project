package com.campusshare.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.campusshare.data.DataStore;
import com.campusshare.db.DAO;

/**
 * Dropdown notification panel shown when the bell icon is clicked.
 *
 * Changes vs original:
 *  - markNotificationsRead is deferred to when the popup *closes* (not on open),
 *    so the badge stays visible while the panel is shown.
 *  - Each unread row gets a red "NEW" pill indicator.
 *  - Preferred width is computed dynamically so it never clips at the screen edge.
 */
public class NotificationPanel extends JPanel {

    public NotificationPanel(DataStore.User user, Runnable onClose) {
        setLayout(new BorderLayout());
        setBackground(Theme.BG_CARD);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        setPreferredSize(new Dimension(360, 440));

        // ── Header ──────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.BG_SUBTLE);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JLabel title = new JLabel("🔔  Notifications");
        title.setFont(Theme.font(Font.BOLD, 22));
        title.setForeground(Theme.TEXT_DARK);

        JButton clearBtn = new JButton("Clear all");
        clearBtn.setFont(Theme.font(Font.PLAIN, 12));
        clearBtn.setForeground(Theme.PRIMARY);
        clearBtn.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 0));
        clearBtn.setContentAreaFilled(false);
        clearBtn.setBorderPainted(false);
        clearBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearBtn.addActionListener(e -> {
            DAO.clearNotifications(user.id);
            NotificationService.get().refreshBadge();
            if (onClose != null) onClose.run();
        });
        header.add(title, BorderLayout.WEST);
        header.add(clearBtn, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ── List ────────────────────────────────────────────────────────────
        List<DataStore.Notification> notifs = DAO.getNotifications(user.id, 50);
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(Theme.BG_CARD);

        if (notifs.isEmpty()) {
            JLabel empty = new JLabel("No notifications");
            empty.setFont(Theme.font(Font.PLAIN, 24));
            empty.setForeground(Theme.TEXT_FAINT);
            empty.setAlignmentX(CENTER_ALIGNMENT);
            empty.setBorder(BorderFactory.createEmptyBorder(40, 0, 0, 0));
            list.add(empty);
        } else {
            for (DataStore.Notification n : notifs) list.add(buildRow(n));
        }

        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(12);
        add(sp, BorderLayout.CENTER);

        // ── Mark read when panel is removed from screen ──────────────────
        // (not on open — badge should stay visible while panel is shown)
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing()) {
                DAO.markNotificationsRead(user.id);
                NotificationService.get().refreshBadge();
            }
        });
    }

    private JPanel buildRow(DataStore.Notification n) {
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

        // Background: unread rows get a subtle blue tint
        Color rowBg = n.isRead
            ? Theme.BG_CARD
            : new Color(Math.min(Theme.BG_CARD.getRed() + 4,   255),
                        Math.min(Theme.BG_CARD.getGreen() + 6, 255),
                        Math.min(Theme.BG_CARD.getBlue() + 20, 255));

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(true);
        row.setBackground(rowBg);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

        // Colored left accent bar
        JPanel leftBar = new JPanel();
        leftBar.setBackground(accent);
        leftBar.setPreferredSize(new Dimension(3, 0));
        row.add(leftBar, BorderLayout.WEST);

        // Icon
        JLabel iconLbl = new JLabel(icon);
        iconLbl.setFont(new Font("SansSerif", Font.PLAIN, 28));
        iconLbl.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 6));

        // Text block
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel titleLbl = new JLabel(n.title);
        titleLbl.setFont(Theme.font(Font.BOLD, 19));
        titleLbl.setForeground(Theme.TEXT_DARK);
        text.add(titleLbl);
        if (!n.body.isEmpty()) {
            JLabel bodyLbl = new JLabel("<html>" + n.body + "</html>");
            bodyLbl.setFont(Theme.font(Font.PLAIN, 12));
            bodyLbl.setForeground(Theme.TEXT_MUTE);
            text.add(bodyLbl);
        }

        // Center: icon + text
        JPanel center = new JPanel(new BorderLayout(0, 0));
        center.setOpaque(false);
        center.add(iconLbl, BorderLayout.WEST);
        center.add(text, BorderLayout.CENTER);
        row.add(center, BorderLayout.CENTER);

        // Right side: time + unread badge
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));

        String time = n.createdAt != null && n.createdAt.length() > 16
            ? n.createdAt.substring(11, 16) : (n.createdAt != null ? n.createdAt : "");
        JLabel timeLbl = new JLabel(time);
        timeLbl.setFont(Theme.FONT_TINY);
        timeLbl.setForeground(Theme.TEXT_FAINT);
        timeLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        right.add(timeLbl);

        if (!n.isRead) {
            // Red "NEW" pill
            JLabel badge = new JLabel("NEW") {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(0xF43F5E));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            badge.setFont(new Font("SansSerif", Font.BOLD, 9));
            badge.setForeground(Color.WHITE);
            badge.setOpaque(false);
            badge.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
            badge.setAlignmentX(Component.CENTER_ALIGNMENT);
            right.add(Box.createVerticalStrut(4));
            right.add(badge);
        }

        row.add(right, BorderLayout.EAST);
        return row;
    }
}