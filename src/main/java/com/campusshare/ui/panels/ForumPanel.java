package com.campusshare.ui.panels;

import com.campusshare.data.DataStore;
import com.campusshare.db.DAO;
import com.campusshare.ui.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.*;

/**
 * Chat panel — Facebook Messenger-style layout.
 *
 * LEFT SIDEBAR
 * ┌──────────────────┐
 * │ 🔍 Search users  │  ← search at very top
 * ├──────────────────┤
 * │ ● Active users   │  ← horizontal avatar strip
 * │ AU  AJ  BW  DS   │
 * ├──────────────────┤
 * │ CHANNELS         │
 * │ # general        │
 * │ # cse ...        │
 * ├──────────────────┤
 * │ DIRECT MESSAGES  │
 * │ Alice  ●         │
 * │ Bob    ●         │
 * └──────────────────┘
 *
 * RIGHT: chat messages + input bar
 */
public class ForumPanel extends JPanel {
    private final DataStore.User user;
    private String currentChannel = "general";
    private boolean isDM = false;
    private DataStore.User dmTarget = null;

    private JPanel messagesPanel;
    private JScrollPane messagesScroll;
    private JTextField inputField;
    private JPanel sidePanel;
    private JPanel mainArea;
    private JTextField userSearchField;
    private JPanel userListPanel;
    private Timer refreshTimer;
    /** ID of the last message rendered — used to detect new messages without wiping the panel. */
    private int lastLoadedMsgId = -1;

    public ForumPanel(DataStore.User user) {
        this.user = user;
        setBackground(Theme.BG_APP);
        setLayout(new BorderLayout());
        sidePanel = buildSidePanel();
        add(sidePanel, BorderLayout.WEST);
        mainArea = buildChatArea();
        add(mainArea, BorderLayout.CENTER);
        startAutoRefresh();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SIDEBAR
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildSidePanel() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(Theme.BG_SUBTLE);
        side.setPreferredSize(new Dimension(210, 0));
        side.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));

        // ── 1. Search bar at the very top ─────────────────────────────────────
        JPanel searchWrap = new JPanel(new BorderLayout(6, 0));
        searchWrap.setOpaque(false);
        searchWrap.setBorder(BorderFactory.createEmptyBorder(10, 10, 8, 10));
        searchWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        searchWrap.setAlignmentX(LEFT_ALIGNMENT);

        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setFont(Theme.font(Font.PLAIN, 11));
        searchIcon.setForeground(Theme.TEXT_MUTE);

        userSearchField = new JTextField();
        userSearchField.setBackground(Theme.BG_INPUT);
        userSearchField.setForeground(Theme.TEXT_DARK);
        userSearchField.setCaretColor(Theme.PRIMARY);
        userSearchField.setFont(Theme.font(Font.PLAIN, 12));
        userSearchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        userSearchField.putClientProperty("JTextField.placeholderText", "Search users…");
        userSearchField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                refreshUserList(userSearchField.getText().trim().toLowerCase());
            }
        });

        searchWrap.add(searchIcon, BorderLayout.WEST);
        searchWrap.add(userSearchField, BorderLayout.CENTER);
        side.add(searchWrap);

        // ── 2. Active users strip (horizontal, like Messenger) ────────────────
        side.add(buildActiveUsersStrip());

        // thin divider
        side.add(makeDivider());

        // ── 3. Group channels ─────────────────────────────────────────────────
        side.add(sectionLabel("CHANNELS"));
        for (String ch : new String[]{"general", "cse", "eee", "announcements"}) {
            side.add(channelBtn(ch));
        }
        side.add(makeDivider());

        // ── 4. Direct messages list ───────────────────────────────────────────
        side.add(sectionLabel("DIRECT MESSAGES"));
        userListPanel = new JPanel();
        userListPanel.setLayout(new BoxLayout(userListPanel, BoxLayout.Y_AXIS));
        userListPanel.setOpaque(false);
        userListPanel.setAlignmentX(LEFT_ALIGNMENT);
        refreshUserList("");

        JScrollPane userScroll = new JScrollPane(userListPanel);
        userScroll.setBorder(null);
        userScroll.setOpaque(false);
        userScroll.getViewport().setOpaque(false);
        userScroll.setAlignmentX(LEFT_ALIGNMENT);
        side.add(userScroll);

        return side;
    }

    /** Facebook Messenger-style horizontal active users strip */
    private JPanel buildActiveUsersStrip() {
        List<DataStore.User> all = DAO.getAllUsers();

        JPanel strip = new JPanel();
        strip.setLayout(new BoxLayout(strip, BoxLayout.Y_AXIS));
        strip.setOpaque(false);
        strip.setAlignmentX(LEFT_ALIGNMENT);
        strip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        strip.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        // Label row
        JPanel labelRow = new JPanel(new BorderLayout());
        labelRow.setOpaque(false);
        labelRow.setAlignmentX(LEFT_ALIGNMENT);
        labelRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        JLabel lbl = new JLabel("● ACTIVE NOW");
        lbl.setFont(Theme.FONT_TINY);
        lbl.setForeground(Theme.SUCCESS);
        long onlineCount = all.stream().filter(DataStore.User::isOnline).count();
        JLabel count = new JLabel(onlineCount + " online");
        count.setFont(Theme.FONT_TINY);
        count.setForeground(Theme.TEXT_FAINT);
        labelRow.add(lbl, BorderLayout.WEST);
        labelRow.add(count, BorderLayout.EAST);
        strip.add(labelRow);
        strip.add(Box.createVerticalStrut(6));

        // Horizontal avatar strip
        JPanel avatarRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        avatarRow.setOpaque(false);
        avatarRow.setAlignmentX(LEFT_ALIGNMENT);

        for (DataStore.User u : all) {
            JPanel cell = new JPanel();
            cell.setOpaque(false);
            cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
            cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cell.setToolTipText(u.fullName);

            // Avatar with green online dot
            JPanel avWrap = new JPanel(null);
            avWrap.setOpaque(false);
            avWrap.setPreferredSize(new Dimension(38, 38));

            Color avColor = u.isFaculty() ? Theme.BLOCK_TEAL : Theme.BLOCK_INDIGO;
            JPanel av = Theme.avatar(u.initials(), avColor, 32);
            av.setBounds(0, 0, 32, 32);

            // Status dot — green if online (last_seen < 3 min), grey otherwise
            Color statusColor = u.isOnline() ? Theme.SUCCESS : new Color(0x4B5563);
            JPanel dot = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Theme.BG_SUBTLE); g2.fillOval(0, 0, 10, 10);
                    g2.setColor(statusColor);     g2.fillOval(2, 2, 7, 7);
                    g2.dispose();
                }
            };
            dot.setOpaque(false);
            dot.setBounds(22, 22, 10, 10);

            avWrap.add(av); avWrap.add(dot);

            // Short name below
            String firstName = u.firstName().length() > 6 ? u.firstName().substring(0, 5) + "." : u.firstName();
            JLabel nameL = new JLabel(firstName);
            nameL.setFont(Theme.FONT_TINY);
            nameL.setForeground(Theme.TEXT_MUTE);
            nameL.setAlignmentX(CENTER_ALIGNMENT);

            cell.add(avWrap);
            cell.add(Box.createVerticalStrut(2));
            cell.add(nameL);

            // Click to open DM
            cell.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (u.id == user.id) return; // can't DM yourself
                    isDM = true; dmTarget = u; currentChannel = "dm_" + u.id;
                    rebuildAll();
                }
            });
            avatarRow.add(cell);
        }
        strip.add(avatarRow);
        return strip;
    }

    private void refreshUserList(String query) {
        userListPanel.removeAll();
        List<DataStore.User> all = DAO.getAllUsers();
        for (DataStore.User u : all) {
            if (u.id == user.id) continue;
            if (!query.isEmpty() && !u.fullName.toLowerCase().contains(query)) continue;
            userListPanel.add(dmUserBtn(u));
        }
        userListPanel.revalidate(); userListPanel.repaint();
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_TINY); l.setForeground(Theme.TEXT_FAINT);
        l.setBorder(BorderFactory.createEmptyBorder(10, 12, 5, 0));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JPanel makeDivider() {
        JPanel d = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.BORDER.brighter()); g.fillRect(0, 0, getWidth(), 1);
            }
        };
        d.setOpaque(false);
        d.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        d.setPreferredSize(new Dimension(210, 1));
        d.setAlignmentX(LEFT_ALIGNMENT);
        return d;
    }

    private JPanel channelBtn(String ch) {
        boolean active = !isDM && ch.equals(currentChannel);
        JPanel btn = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5)) {
            @Override protected void paintComponent(Graphics g) {
                if (!isDM && ch.equals(currentChannel)) {
                    g.setColor(Theme.PRIMARY_LIGHT); g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(Theme.PRIMARY);       g.fillRect(0, 0, 3, getHeight());
                }
                super.paintComponent(g);
            }
        };
        btn.setOpaque(false);
        btn.setMaximumSize(new Dimension(210, 32));
        btn.setAlignmentX(LEFT_ALIGNMENT);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel lbl = new JLabel("# " + ch);
        lbl.setFont(Theme.font(active ? Font.BOLD : Font.PLAIN, 12));
        lbl.setForeground(active ? Theme.PRIMARY : Theme.TEXT_MUTE);
        btn.add(lbl);
        btn.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                currentChannel = ch; isDM = false; dmTarget = null; rebuildAll();
            }
        });
        return btn;
    }

    private JPanel dmUserBtn(DataStore.User u) {
        boolean active = isDM && dmTarget != null && dmTarget.id == u.id;
        JPanel btn = new JPanel(new BorderLayout(8, 0)) {
            @Override protected void paintComponent(Graphics g) {
                if (active) { g.setColor(Theme.PRIMARY_LIGHT); g.fillRect(0, 0, getWidth(), getHeight()); }
                super.paintComponent(g);
            }
        };
        btn.setOpaque(false);
        btn.setMaximumSize(new Dimension(210, 40));
        btn.setAlignmentX(LEFT_ALIGNMENT);
        btn.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Color avColor = u.isFaculty() ? Theme.BLOCK_TEAL : Theme.BLOCK_INDIGO;
        JPanel av = Theme.avatar(u.initials(), avColor, 28);
        av.setPreferredSize(new Dimension(28, 28));

        JPanel namePanel = new JPanel(); namePanel.setOpaque(false);
        namePanel.setLayout(new BoxLayout(namePanel, BoxLayout.Y_AXIS));
        JLabel nameL = new JLabel(u.firstName());
        nameL.setFont(Theme.font(Font.BOLD, 11));
        nameL.setForeground(active ? Theme.PRIMARY : Theme.TEXT_DARK);
        JLabel roleL = new JLabel(u.role.toLowerCase());
        roleL.setFont(Theme.FONT_TINY); roleL.setForeground(Theme.TEXT_FAINT);
        namePanel.add(nameL); namePanel.add(roleL);

        // Online indicator
        JPanel dotP = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.SUCCESS); g2.fillOval(3, 7, 8, 8);
                g2.dispose();
            }
        };
        dotP.setOpaque(false); dotP.setPreferredSize(new Dimension(16, 28));

        btn.add(av, BorderLayout.WEST);
        btn.add(namePanel, BorderLayout.CENTER);
        btn.add(dotP, BorderLayout.EAST);

        btn.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                isDM = true; dmTarget = u; currentChannel = "dm_" + u.id; rebuildAll();
            }
        });
        return btn;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CHAT AREA
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildChatArea() {
        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(Theme.BG_CARD);

        // Top bar — channel name only (no active strip here anymore — it's in sidebar)
        JPanel topBar = new JPanel(new BorderLayout(10, 0));
        topBar.setBackground(Theme.BG_CARD);
        topBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
            BorderFactory.createEmptyBorder(10, 16, 10, 16)));

        String chanTitle = isDM && dmTarget != null
            ? "💬  " + dmTarget.fullName + "  (" + dmTarget.role.toLowerCase() + ")"
            : "#  " + currentChannel;
        JLabel chLabel = new JLabel(chanTitle);
        chLabel.setFont(Theme.FONT_H2); chLabel.setForeground(Theme.TEXT_DARK);
        topBar.add(chLabel, BorderLayout.WEST);

        // Small online count badge on right of chat header
        List<DataStore.User> allUsers = DAO.getAllUsers();
        JPanel badge = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        badge.setOpaque(false);
        JLabel dotLbl = new JLabel("●");
        dotLbl.setFont(Theme.font(Font.PLAIN, 9)); dotLbl.setForeground(Theme.SUCCESS);
        long onlineCnt = allUsers.stream().filter(DataStore.User::isOnline).count();
        JLabel cntLbl = new JLabel(onlineCnt + " online");
        cntLbl.setFont(Theme.FONT_TINY); cntLbl.setForeground(Theme.TEXT_MUTE);
        badge.add(dotLbl); badge.add(cntLbl);
        // Right side: badge + ⋮ menu
        JPanel rightBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightBar.setOpaque(false);
        rightBar.add(badge);
        JButton menuBtn = new JButton("⋮");
        menuBtn.setFont(Theme.font(Font.BOLD, 16));
        menuBtn.setForeground(Theme.TEXT_MUTE);
        menuBtn.setBackground(Theme.BG_CARD);
        menuBtn.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 6));
        menuBtn.setFocusPainted(false);
        menuBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        menuBtn.setContentAreaFilled(false);
        menuBtn.addActionListener(e -> showChatMenu(menuBtn));
        rightBar.add(menuBtn);
        topBar.add(rightBar, BorderLayout.EAST);
        area.add(topBar, BorderLayout.NORTH);

        // Messages
        messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBackground(Theme.BG_APP);
        messagesPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        loadMessages(true);  // always force on initial build

        messagesScroll = new JScrollPane(messagesPanel);
        messagesScroll.setBorder(null);
        messagesScroll.setOpaque(false);
        messagesScroll.getViewport().setBackground(Theme.BG_APP);
        area.add(messagesScroll, BorderLayout.CENTER);

        // Input bar
        JPanel inputBar = new JPanel(new BorderLayout(8, 0));
        inputBar.setBackground(Theme.BG_CARD);
        inputBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
            BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        inputField = Theme.styledField("Type a message…");
        JButton send = Theme.primaryButton("Send");
        send.setPreferredSize(new Dimension(80, 34));
        send.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        inputBar.add(inputField, BorderLayout.CENTER);
        inputBar.add(send, BorderLayout.EAST);
        area.add(inputBar, BorderLayout.SOUTH);
        // Scroll after full layout is complete
        SwingUtilities.invokeLater(() -> scrollToBottom());
        return area;
    }

    private void loadMessages() {
        loadMessages(false);
    }

    /**
     * Load (or reload) messages.
     * @param force  true = always rebuild (e.g. after channel switch).
     *               false = skip rebuild if the newest message ID has not changed.
     */
    private void loadMessages(boolean force) {
        if (messagesPanel == null) return;

        List<DataStore.Message> msgs = new java.util.ArrayList<>(
            isDM && dmTarget != null
            ? DAO.getDirectMessages(user.id, dmTarget.id)
            : DAO.getMessages(currentChannel));

        // Determine the newest message id in the fetched list
        int newestId = msgs.isEmpty() ? -1 : msgs.get(msgs.size() - 1).id;

        // If Supabase returned empty but we previously had messages rendered,
        // fall back to SQLite so we don't blank the screen on a sync gap.
        if (msgs.isEmpty() && lastLoadedMsgId > 0 && !force) return;

        // Skip full rebuild if nothing has changed (avoids wiping optimistic bubbles)
        if (!force && newestId == lastLoadedMsgId) return;
        lastLoadedMsgId = newestId;

        messagesPanel.removeAll();
        if (msgs.isEmpty()) {
            // Last resort: try SQLite directly if Supabase returned nothing
            List<DataStore.Message> fallback = DAO.getMessagesLocal(
                isDM && dmTarget != null ? null : currentChannel,
                isDM && dmTarget != null ? user.id : -1,
                isDM && dmTarget != null ? dmTarget.id : -1);
            if (!fallback.isEmpty()) {
                msgs = fallback;
                lastLoadedMsgId = msgs.get(msgs.size() - 1).id;
            }
        }
        if (msgs.isEmpty()) {
            JLabel empty = new JLabel("No messages yet. Say hello! 👋");
            empty.setFont(Theme.font(Font.PLAIN, 13));
            empty.setForeground(Theme.TEXT_FAINT);
            empty.setAlignmentX(CENTER_ALIGNMENT);
            messagesPanel.add(Box.createVerticalGlue());
            messagesPanel.add(empty);
        } else {
            for (DataStore.Message m : msgs) addBubble(m);
        }
        messagesPanel.revalidate(); messagesPanel.repaint();
    }

    private void addBubble(DataStore.Message m) {
        // mine = true  →  bubble+avatar flush to the RIGHT
        // mine = false →  avatar+bubble flush to the LEFT
        // Primary: compare senderName (most reliable across local/cloud)
        // Fallback: compare IDs (for cloud sync)
        boolean mine = (m.senderName != null && m.senderName.equals(user.fullName))
            || (m.senderId > 0 && m.senderId == user.id)
            || (DataStore.cloudUserId > 0 && m.senderId == DataStore.cloudUserId);
        
        // DEBUG: Print info to console
        if (m.senderName != null && m.senderName.contains("Admin")) {
            System.out.println("[DEBUG] Message from: '" + m.senderName + "' | User: '" + user.fullName + "' | Match: " + m.senderName.equals(user.fullName) + " | senderId: " + m.senderId + " | user.id: " + user.id + " | mine: " + mine);
        }

        // ── Avatar ────────────────────────────────────────────────────────────
        Color avColor = m.isFaculty ? Theme.BLOCK_TEAL : (mine ? Theme.PRIMARY : new Color(0x8B5CF6));
        String initial = (m.senderName != null && !m.senderName.isEmpty())
            ? m.senderName.substring(0, 1).toUpperCase() : "?";
        JPanel av = Theme.avatar(initial, avColor, 32);
        av.setMinimumSize(new Dimension(32, 32));
        av.setPreferredSize(new Dimension(32, 32));
        av.setMaximumSize(new Dimension(32, 32));

        // ── Sender label ──────────────────────────────────────────────────────
        String senderDisplay = (m.senderName != null ? m.senderName : "?")
            + (m.isFaculty ? " \uD83D\uDC69\u200D\uD83C\uDFEB" : "")
            + "   " + (m.sentAt != null ? m.sentAt : "");
        JLabel sender = new JLabel(senderDisplay);
        sender.setFont(Theme.FONT_SMALL);
        sender.setForeground(Theme.TEXT_MUTE);
        sender.setHorizontalAlignment(mine ? SwingConstants.RIGHT : SwingConstants.LEFT);
        sender.setAlignmentX(mine ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);

        // ── Content bubble ────────────────────────────────────────────────────
        Color bubbleBg = mine ? new Color(0x2D2B6B) : new Color(0x1A1836);
        String safeContent = escapeHtml(m.content != null ? m.content : "");
        JLabel content = new JLabel("<html><div style='padding:6px 10px;background:#"
            + Integer.toHexString(bubbleBg.getRGB()).substring(2)
            + ";border-radius:8px;color:#EEE;font-size:12pt'>"
            + safeContent + "</div></html>");
        content.setFont(Theme.FONT_BODY);
        content.setAlignmentX(mine ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);

        // ── Bubble column (sender + content stacked vertically) ───────────────
        JPanel bubble = new JPanel();
        bubble.setOpaque(false);
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.add(sender);
        bubble.add(Box.createVerticalStrut(2));
        bubble.add(content);
        // Bubble must NOT stretch horizontally — let glue do the pushing
        // Set a reasonable max width (500 pixels) so horizontal glue can position it
        bubble.setMaximumSize(new Dimension(500, bubble.getMaximumSize().height));

        // ── Row: horizontal BoxLayout + glue pushes to correct side ──────────
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));  // Reduced from 4 to 2 for compact spacing
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (mine) {
            row.add(Box.createHorizontalGlue());   // ← pushes content right
            row.add(bubble);
            row.add(Box.createHorizontalStrut(8));
            row.add(av);
        } else {
            row.add(av);
            row.add(Box.createHorizontalStrut(8));
            row.add(bubble);
            row.add(Box.createHorizontalGlue());   // ← pushes content left
        }
        messagesPanel.add(row);
    }

    private String escapeHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    private void sendMessage() {
        if (inputField == null) return;
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        int id = isDM && dmTarget != null
            ? DAO.saveDirectMessage(user.id, dmTarget.id, text)
            : DAO.saveMessage(user.id, currentChannel, text);

        if (id > 0) {
            // Update lastLoadedMsgId so the auto-refresh does NOT wipe this message
            // before the DB round-trip confirms it.
            lastLoadedMsgId = id;
            DataStore.Message m = new DataStore.Message(id, user.id, user.fullName, text,
                java.time.LocalTime.now().toString().substring(0, 5), user.isFaculty());
            addBubble(m);
            inputField.setText("");
            messagesPanel.revalidate(); messagesPanel.repaint();
            scrollToBottom();
        }
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            if (messagesScroll != null) {
                JScrollBar vsb = messagesScroll.getVerticalScrollBar();
                vsb.setValue(vsb.getMaximum());
            }
        });
    }

    private void startAutoRefresh() {
        refreshTimer = new Timer(true);
        refreshTimer.schedule(new TimerTask() {
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    if (messagesPanel != null) { loadMessages(); scrollToBottom(); }
                    // Refresh user list so online dots stay current
                    if (userListPanel != null) {
                        String q = userSearchField != null ? userSearchField.getText().trim().toLowerCase() : "";
                        refreshUserList(q);
                    }
                });
            }
        }, 5000, 5000);
    }

    @Override public void removeNotify() {
        super.removeNotify();
        if (refreshTimer != null) refreshTimer.cancel();
    }

    private void rebuildAll() {
        lastLoadedMsgId = -1;  // force reload for new channel/DM
        remove(sidePanel);  sidePanel = buildSidePanel();  add(sidePanel, BorderLayout.WEST);
        remove(mainArea);   mainArea  = buildChatArea();   add(mainArea,  BorderLayout.CENTER);
        revalidate(); repaint();
    }

    /**
     * Context menu for the ⋮ button in the chat header.
     * Options vary by role and chat type (channel vs DM).
     */
    private void showChatMenu(JButton anchor) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(Theme.BG_CARD);
        menu.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        // ── Delete my messages ────────────────────────────────────────────────
        JMenuItem delMine = new JMenuItem(isDM ? "🗑  Clear this conversation" : "🗑  Delete my messages");
        delMine.setFont(Theme.FONT_BODY);
        delMine.setForeground(Theme.TEXT_DARK);
        delMine.setBackground(Theme.BG_CARD);
        delMine.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                isDM ? "Delete all your messages in this conversation?"
                     : "Delete all your messages in #" + currentChannel + "?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;

            int localId = user.id;
            int cloudId = DataStore.cloudUserId > 0 ? DataStore.cloudUserId : user.id;

            if (isDM && dmTarget != null) {
                int cloudTarget = DataStore.cloudUserId > 0 ? dmTarget.id : dmTarget.id;
                DAO.clearDMHistory(localId, dmTarget.id, cloudId, cloudTarget);
            } else {
                DAO.deleteMyMessagesInChannel(currentChannel, localId, cloudId);
            }
            lastLoadedMsgId = -1; loadMessages(true); scrollToBottom();
        });
        menu.add(delMine);

        // ── Clear entire channel (admin only) ─────────────────────────────────
        if (!isDM && user.isAdmin()) {
            menu.addSeparator();
            JMenuItem clearAll = new JMenuItem("⚠  Clear entire #" + currentChannel);
            clearAll.setFont(Theme.FONT_BODY);
            clearAll.setForeground(new Color(0xEF4444));
            clearAll.setBackground(Theme.BG_CARD);
            clearAll.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(this,
                    "Delete ALL messages in #" + currentChannel + " for everyone?\n"
                    + "This cannot be undone.",
                    "Clear Channel", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) return;
                DAO.clearChannel(currentChannel);
                lastLoadedMsgId = -1; loadMessages(true); scrollToBottom();
            });
            menu.add(clearAll);
        }

        // ── Switch channel (leave current) ───────────────────────────────────
        if (!isDM) {
            menu.addSeparator();
            JMenuItem switchCh = new JMenuItem("↩  Go to #general");
            switchCh.setFont(Theme.FONT_BODY);
            switchCh.setForeground(Theme.TEXT_MUTE);
            switchCh.setBackground(Theme.BG_CARD);
            switchCh.addActionListener(e -> {
                currentChannel = "general"; isDM = false; dmTarget = null; rebuildAll();
            });
            menu.add(switchCh);
        }

        // ── Close DM ──────────────────────────────────────────────────────────
        if (isDM) {
            menu.addSeparator();
            JMenuItem closeDM = new JMenuItem("✖  Close this conversation");
            closeDM.setFont(Theme.FONT_BODY);
            closeDM.setForeground(Theme.TEXT_MUTE);
            closeDM.setBackground(Theme.BG_CARD);
            closeDM.addActionListener(e -> {
                isDM = false; dmTarget = null; currentChannel = "general"; rebuildAll();
            });
            menu.add(closeDM);
        }

        menu.show(anchor, 0, anchor.getHeight());
    }

}