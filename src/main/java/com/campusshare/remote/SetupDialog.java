package com.campusshare.remote;

import com.campusshare.ui.Theme;
import com.campusshare.db.SyncManager;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;

/**
 * Mode-selection dialog shown on EVERY launch.
 *
 * Regardless of whether Supabase credentials are saved, the user always
 * picks their mode for this session:
 *
 *   ┌─────────────────┐   ┌─────────────────┐
 *   │  ☁ Use Online   │   │  🖥 Run Offline  │
 *   │   (Supabase)    │   │    (SQLite)      │
 *   └─────────────────┘   └─────────────────┘
 *
 * If "Online" is picked but no credentials are saved yet, an inline
 * credential form slides into view so the user can enter them.
 *
 * SupabaseConfig.FORCE_OFFLINE is set by this dialog; DAO.useSupabase()
 * checks that flag on every query.
 */
public class SetupDialog extends JDialog {

    private boolean saved = false;

    // Credential form fields (lazily built)
    private JTextField urlField;
    private JTextField keyField;
    private JPanel credPanel;
    private JPanel mainPanel;
    private CardLayout cards;

    public SetupDialog(Frame parent) {
        super(parent, "CampusShare — Choose Mode", true);
        setSize(520, 340);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        buildUI();
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void buildUI() {
        cards  = new CardLayout();
        mainPanel = new JPanel(cards);
        mainPanel.setBackground(Theme.BG_APP);

        mainPanel.add(buildChoicePanel(), "choice");
        mainPanel.add(buildCredPanel(),   "creds");

        setContentPane(mainPanel);
    }

    /** Page 1 — two big mode buttons. */
    private JPanel buildChoicePanel() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Theme.BG_APP);

        // ── Header ────────────────────────────────────────────────────────────
        JPanel header = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0,0,new Color(0x3730A3),getWidth(),0,new Color(0x5B21B6)));
                g2.fillRect(0,0,getWidth(),getHeight());
                g2.dispose();
            }
        };
        header.setPreferredSize(new Dimension(0, 68));
        header.setLayout(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(0, 26, 0, 26));

        JLabel title = new JLabel("How do you want to run CampusShare?");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setForeground(Color.WHITE);
        JLabel sub = new JLabel("You can change this every time you launch.");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sub.setForeground(new Color(255,255,255,160));

        JPanel hText = new JPanel();
        hText.setOpaque(false);
        hText.setLayout(new BoxLayout(hText, BoxLayout.Y_AXIS));
        hText.add(Box.createVerticalGlue());
        hText.add(title);
        hText.add(Box.createVerticalStrut(3));
        hText.add(sub);
        hText.add(Box.createVerticalGlue());
        header.add(hText, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH);

        // ── Two mode cards ────────────────────────────────────────────────────
        JPanel body = new JPanel(new GridLayout(1, 2, 16, 0));
        body.setBackground(Theme.BG_APP);
        body.setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));

        body.add(buildModeCard(
            "☁",
            "Online Mode",
            "Cloud database via Supabase.\nMultiple users, real-time sync.",
            new Color(0x6366F1), new Color(0x1E1B4B),
            () -> onOnlineClicked()
        ));

        body.add(buildModeCard(
            "🖥",
            "Offline Mode",
            "Local SQLite on this computer.\nNo internet needed.",
            new Color(0x059669), new Color(0x0A2A1F),
            () -> onOfflineClicked()
        ));

        root.add(body, BorderLayout.CENTER);

        // ── Footer note ───────────────────────────────────────────────────────
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        footer.setBackground(Theme.BG_APP);
        JLabel note = new JLabel("Demo accounts always work in either mode.");
        note.setFont(new Font("SansSerif", Font.PLAIN, 11));
        note.setForeground(Theme.TEXT_FAINT);
        footer.add(note);
        root.add(footer, BorderLayout.SOUTH);

        return root;
    }

    private JPanel buildModeCard(String icon, String title, String desc,
                                  Color accent, Color bgColor, Runnable action) {
        JPanel card = new JPanel() {
            boolean hover = false;
            {
                addMouseListener(new java.awt.event.MouseAdapter() {
                    public void mouseEntered(java.awt.event.MouseEvent e) { hover=true; repaint(); }
                    public void mouseExited (java.awt.event.MouseEvent e) { hover=false; repaint(); }
                    public void mouseClicked(java.awt.event.MouseEvent e) { action.run(); }
                });
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hover
                    ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60)
                    : bgColor);
                g2.fillRoundRect(0,0,getWidth(),getHeight(),18,18);
                g2.setColor(hover ? accent : new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),80));
                g2.setStroke(new BasicStroke(hover ? 2f : 1.5f));
                g2.drawRoundRect(1,1,getWidth()-2,getHeight()-2,18,18);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel ico = new JLabel(icon);
        ico.setFont(new Font("SansSerif", Font.PLAIN, 30));
        ico.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel ttl = new JLabel(title);
        ttl.setFont(new Font("SansSerif", Font.BOLD, 15));
        ttl.setForeground(accent);
        ttl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel dsc = new JLabel("<html><center>" + desc.replace("\n","<br>") + "</center></html>");
        dsc.setFont(new Font("SansSerif", Font.PLAIN, 12));
        dsc.setForeground(Theme.TEXT_MUTE);
        dsc.setAlignmentX(Component.CENTER_ALIGNMENT);
        dsc.setHorizontalAlignment(SwingConstants.CENTER);

        // Show "credentials saved" badge if configured
        if (title.contains("Online") && SupabaseConfig.isConfigured()) {
            JLabel badge = new JLabel(" ✓ Credentials saved ");
            badge.setFont(new Font("SansSerif", Font.BOLD, 10));
            badge.setForeground(accent);
            badge.setOpaque(false);
            badge.setAlignmentX(Component.CENTER_ALIGNMENT);
            card.add(ico); card.add(Box.createVerticalStrut(8));
            card.add(ttl); card.add(Box.createVerticalStrut(6));
            card.add(dsc); card.add(Box.createVerticalStrut(8));
            card.add(badge);
        } else {
            card.add(ico); card.add(Box.createVerticalStrut(8));
            card.add(ttl); card.add(Box.createVerticalStrut(6));
            card.add(dsc);
        }

        return card;
    }

    /** Page 2 — credential entry (shown when Online clicked but no creds yet). */
    private JPanel buildCredPanel() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Theme.BG_APP);

        // Header
        JPanel header = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setPaint(new GradientPaint(0,0,new Color(0x3730A3),getWidth(),0,new Color(0x5B21B6)));
                g2.fillRect(0,0,getWidth(),getHeight()); g2.dispose();
            }
        };
        header.setPreferredSize(new Dimension(0, 56));
        header.setLayout(new FlowLayout(FlowLayout.LEFT, 20, 14));
        JLabel back = new JLabel("← Back");
        back.setFont(new Font("SansSerif", Font.PLAIN, 12));
        back.setForeground(new Color(255,255,255,180));
        back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        back.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                cards.show(mainPanel, "choice");
            }
        });
        JLabel ttl = new JLabel("Enter Supabase Credentials");
        ttl.setFont(new Font("SansSerif", Font.BOLD, 15));
        ttl.setForeground(Color.WHITE);
        header.add(back); header.add(ttl);
        root.add(header, BorderLayout.NORTH);

        // Form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.BG_APP);
        form.setBorder(BorderFactory.createEmptyBorder(20, 26, 16, 26));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(6,0,6,0);

        JLabel hint = new JLabel("Supabase Dashboard → Settings → API → copy both values:");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 12));
        hint.setForeground(Theme.TEXT_MUTE);
        gc.gridx=0; gc.gridy=0; gc.gridwidth=2; form.add(hint, gc);

        urlField = new JTextField();
        urlField.setBackground(Theme.BG_INPUT); urlField.setForeground(Theme.TEXT_DARK);
        urlField.setCaretColor(Theme.PRIMARY);
        urlField.setBorder(BorderFactory.createCompoundBorder(
            new RoundBorder(6, Theme.BORDER, 1), BorderFactory.createEmptyBorder(7,10,7,10)));
        urlField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        urlField.setToolTipText("https://xxxx.supabase.co");

        keyField = new JTextField();
        keyField.setBackground(Theme.BG_INPUT); keyField.setForeground(Theme.TEXT_DARK);
        keyField.setCaretColor(Theme.PRIMARY);
        keyField.setBorder(urlField.getBorder());
        keyField.setFont(new Font("SansSerif", Font.PLAIN, 11));
        keyField.setToolTipText("eyJhbGci...");

        String[][] rows = {{"Project URL:", null}, {"Anon / Public Key:", null}};
        JTextField[] fields = {urlField, keyField};
        for (int i = 0; i < 2; i++) {
            gc.gridwidth=1; gc.gridy=i+1; gc.gridx=0; gc.weightx=0.3;
            JLabel lbl = new JLabel(rows[i][0]);
            lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
            lbl.setForeground(Theme.TEXT_DARK);
            form.add(lbl, gc);
            gc.gridx=1; gc.weightx=0.7;
            form.add(fields[i], gc);
        }

        // Buttons
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setBackground(Theme.BG_APP);

        JButton saveBtn = Theme.primaryButton("Save & Go Online");
        saveBtn.setPreferredSize(new Dimension(160, 36));
        saveBtn.addActionListener(e -> {
            String url = urlField.getText().trim();
            String key = keyField.getText().trim();
            if (url.isEmpty() || !url.startsWith("https://")) {
                JOptionPane.showMessageDialog(this, "Enter a valid Supabase project URL (https://...).",
                    "Invalid URL", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (key.isEmpty() || !key.startsWith("eyJ")) {
                JOptionPane.showMessageDialog(this, "Enter a valid anon key (starts with eyJ...).",
                    "Invalid Key", JOptionPane.WARNING_MESSAGE);
                return;
            }
            saveCredentials(url, key);
            SupabaseConfig.FORCE_OFFLINE = false;
            saved = true;
            dispose();
            scheduleSync();
        });
        btns.add(saveBtn);

        gc.gridx=0; gc.gridy=3; gc.gridwidth=2; gc.fill=GridBagConstraints.NONE;
        gc.anchor=GridBagConstraints.EAST; gc.insets=new Insets(16,0,0,0);
        form.add(btns, gc);

        root.add(form, BorderLayout.CENTER);
        return root;
    }

    // ── Button actions ────────────────────────────────────────────────────────

    private void onOnlineClicked() {
        if (SupabaseConfig.isConfigured()) {
            SupabaseConfig.FORCE_OFFLINE = false;
            saved = true;
            dispose();
            // Sync local data to cloud in background after login
            scheduleSync();
        } else {
            cards.show(mainPanel, "creds");
            setSize(520, 360);
        }
    }

    /**
     * Runs sync in a background thread after the dialog closes.
     * Shows a brief notification if any data was pushed to Supabase.
     */
    static void scheduleSync() {
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            SyncManager.SyncResult result = SyncManager.syncLocalToCloud();
            if (result.anyChanged()) {
                javax.swing.SwingUtilities.invokeLater(() ->
                    javax.swing.JOptionPane.showMessageDialog(null,
                        result.summary(),
                        "Offline → Online Sync Complete",
                        javax.swing.JOptionPane.INFORMATION_MESSAGE));
            }
        }, "CampusShare-Sync").start();
    }

    private void onOfflineClicked() {
        SupabaseConfig.FORCE_OFFLINE = true;
        saved = false; // offline — no Supabase session
        dispose();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void saveCredentials(String url, String key) {
        try {
            new File(SupabaseConfig.CONFIG_DIR).mkdirs();
            String content =
                "# CampusShare — Supabase Configuration\n" +
                "# Saved by setup dialog.\n" +
                "supabase.url=" + url + "\n" +
                "supabase.anon_key=" + key + "\n";
            Files.writeString(Path.of(SupabaseConfig.CONFIG_FILE), content);
            SupabaseConfig.reload();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                "Could not save credentials: " + ex.getMessage(), "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean wasSaved() { return saved; }

    // ── Inner border class ────────────────────────────────────────────────────
    private static class RoundBorder extends AbstractBorder {
        private final int radius; private final Color color; private final int thickness;
        RoundBorder(int r, Color c, int t) { radius=r; color=c; thickness=t; }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color); g2.setStroke(new BasicStroke(thickness));
            g2.drawRoundRect(x,y,w-1,h-1,radius,radius); g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) {
            return new Insets(radius/2, radius/2, radius/2, radius/2);
        }
    }
}
