package com.campusshare.ui.panels;

import com.campusshare.data.DataStore;
import com.campusshare.remote.SupabaseConfig;
import com.campusshare.remote.ConnectionMonitor;
import com.campusshare.db.SyncManager;
import com.campusshare.db.DAO;
import com.campusshare.ui.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class ProfilePanel extends JPanel {
    private static final int AVATAR_SIZE = 80;
    private static final File FILES_DIR = new File(
        System.getProperty("user.home"), "CampusShare" + File.separator + "avatars");

    private final Map<String, JTextField> fields = new HashMap<>();
    private final DataStore.User user;
    private final Runnable avatarRefresh;
    private JPanel avatarPanel;   // repainted when photo changes

    public ProfilePanel(DataStore.User user, Runnable avatarRefresh) {
        this.user = user;
        this.avatarRefresh = avatarRefresh;
        FILES_DIR.mkdirs();
        setBackground(Theme.BG_APP);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));

        JLabel title = new JLabel("My Profile");
        title.setFont(Theme.FONT_TITLE); title.setForeground(Theme.TEXT_DARK);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        add(title, BorderLayout.NORTH);

        JPanel cols = new JPanel(new GridBagLayout());
        cols.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH; g.weighty = 1; g.anchor = GridBagConstraints.NORTHWEST;

        g.gridx = 0; g.gridy = 0; g.weightx = 0.55; g.insets = new Insets(0,0,0,14);
        cols.add(buildProfileCard(), g);

        g.gridx = 1; g.weightx = 0.45; g.insets = new Insets(0,0,0,0);
        cols.add(buildPasswordCard(), g);

        // Cloud connection — admin only
        if (user.isAdmin()) {
            g.gridx = 0; g.gridy = 1; g.gridwidth = 2; g.weightx = 1;
            g.insets = new Insets(14, 0, 0, 0);
            cols.add(buildCloudCard(), g);
        }

        JScrollPane sp = new JScrollPane(cols);
        sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(12);
        add(sp, BorderLayout.CENTER);
    }

    private JPanel buildProfileCard() {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0,0));

        // ── Banner with avatar ────────────────────────────────────────────────
        JPanel banner = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0,0,new Color(0x6366F1),getWidth(),getHeight(),new Color(0xA78BFA)));
                g2.fillRect(0,0,getWidth(),getHeight()); g2.dispose();
            }
        };
        banner.setPreferredSize(new Dimension(0, 110));
        banner.setLayout(null);

        // Avatar — clickable to change photo
        avatarPanel = buildAvatarPanel();
        avatarPanel.setBounds(20, 20, AVATAR_SIZE + 10, AVATAR_SIZE + 10);
        avatarPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        avatarPanel.setToolTipText("Click to change profile photo");
        avatarPanel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { changePhoto(); }
        });
        banner.add(avatarPanel);

        // Name label in banner
        JLabel nameInBanner = new JLabel(user.fullName);
        nameInBanner.setFont(new Font("SansSerif", Font.BOLD, 15));
        nameInBanner.setForeground(Color.WHITE);
        nameInBanner.setBounds(AVATAR_SIZE + 36, 30, 260, 22);
        banner.add(nameInBanner);

        JLabel roleInBanner = new JLabel(user.role + " · " + user.department);
        roleInBanner.setFont(Theme.FONT_SMALL);
        roleInBanner.setForeground(new Color(255,255,255,180));
        roleInBanner.setBounds(AVATAR_SIZE + 36, 56, 260, 16);
        banner.add(roleInBanner);

        // Camera icon hint
        JLabel camHint = new JLabel("📷 change");
        camHint.setFont(Theme.FONT_TINY);
        camHint.setForeground(new Color(255,255,255,160));
        camHint.setBounds(20, AVATAR_SIZE + 12, 80, 14);
        banner.add(camHint);

        card.add(banner, BorderLayout.NORTH);

        // ── Editable fields ───────────────────────────────────────────────────
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(16, 20, 20, 20));

        body.add(Box.createVerticalStrut(4));
        JSeparator sep = Theme.divider();
        sep.setAlignmentX(LEFT_ALIGNMENT);
        body.add(sep);
        body.add(Box.createVerticalStrut(12));

        addEditableField(body, "Full Name",  user.fullName);
        addEditableField(body, "Email",      user.email);
        addEditableField(body, "Department", user.department);
        if (user.semester > 0) addEditableField(body, "Semester", String.valueOf(user.semester));
        addEditableField(body, "Role", user.role);
        body.add(Box.createVerticalStrut(14));

        // Photo action buttons
        JPanel photoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        photoRow.setOpaque(false); photoRow.setAlignmentX(LEFT_ALIGNMENT);
        JButton changePhotoBtn = Theme.ghostButton("📷 Change Photo", Theme.PRIMARY);
        changePhotoBtn.addActionListener(e -> changePhoto());
        JButton removePhotoBtn = Theme.ghostButton("✕ Remove", Theme.DANGER);
        removePhotoBtn.addActionListener(e -> removePhoto());
        photoRow.add(changePhotoBtn);
        if (user.avatarPath != null && !user.avatarPath.isEmpty()) photoRow.add(removePhotoBtn);
        body.add(photoRow);
        body.add(Box.createVerticalStrut(14));

        JButton saveBtn = Theme.primaryButton("Save Changes");
        saveBtn.setAlignmentX(LEFT_ALIGNMENT);
        saveBtn.addActionListener(e -> {
            String newName = fields.getOrDefault("Full Name", new JTextField(user.fullName)).getText().trim();
            String newDept = fields.getOrDefault("Department", new JTextField(user.department)).getText().trim();
            int newSem = user.semester;
            JTextField semF = fields.get("Semester");
            if (semF != null) { String sv = semF.getText().replaceAll("[^0-9]",""); if (!sv.isEmpty()) newSem = Integer.parseInt(sv); }
            boolean ok = DAO.updateProfile(user.id, newName, newDept, newSem);
            if (ok) {
                user.fullName = newName; user.department = newDept; user.semester = newSem;
                JOptionPane.showMessageDialog(this,"Profile updated!","Success",JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,"Update failed.","Error",JOptionPane.ERROR_MESSAGE);
            }
        });
        body.add(saveBtn);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildAvatarPanel() {
        return new JPanel() {
            { setOpaque(false); setPreferredSize(new Dimension(AVATAR_SIZE+10, AVATAR_SIZE+10)); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // White ring
                g2.setColor(Color.WHITE);
                g2.fillOval(0, 0, AVATAR_SIZE+10, AVATAR_SIZE+10);
                // Photo or initials
                if (user.avatarPath != null && !user.avatarPath.isEmpty()) {
                    try {
                        BufferedImage img = ImageIO.read(new File(user.avatarPath));
                        if (img != null) {
                            // Clip to circle
                            BufferedImage circle = new BufferedImage(AVATAR_SIZE+10, AVATAR_SIZE+10, BufferedImage.TYPE_INT_ARGB);
                            Graphics2D cg = circle.createGraphics();
                            cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            cg.fillOval(2, 2, AVATAR_SIZE+6, AVATAR_SIZE+6);
                            cg.setComposite(java.awt.AlphaComposite.SrcIn);
                            cg.drawImage(img, 2, 2, AVATAR_SIZE+6, AVATAR_SIZE+6, null);
                            cg.dispose();
                            g2.drawImage(circle, 0, 0, null);
                            g2.dispose(); return;
                        }
                    } catch (Exception ignored) {}
                }
                // Fallback initials
                g2.setColor(Theme.PRIMARY);
                g2.fillOval(2, 2, AVATAR_SIZE+6, AVATAR_SIZE+6);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, (int)(AVATAR_SIZE*0.36f)));
                FontMetrics fm = g2.getFontMetrics();
                String ini = user.initials();
                g2.drawString(ini, (AVATAR_SIZE+10-fm.stringWidth(ini))/2, (AVATAR_SIZE+10+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
    }

    private void changePhoto() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose Profile Photo");
        fc.setFileFilter(new FileNameExtensionFilter("Images (JPG, PNG, GIF)","jpg","jpeg","png","gif"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File src = fc.getSelectedFile();
        if (!src.exists()) { JOptionPane.showMessageDialog(this,"File not found."); return; }
        try {
            String ext = src.getName().contains(".") ? src.getName().substring(src.getName().lastIndexOf('.')+1) : "jpg";
            File dest = new File(FILES_DIR, "avatar_" + user.id + "." + ext);
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            user.avatarPath = dest.getAbsolutePath();
            DAO.updateAvatar(user.id, user.avatarPath);
            avatarPanel.repaint();
            JOptionPane.showMessageDialog(this,"Profile photo updated!","Success",JOptionPane.INFORMATION_MESSAGE);
            if (avatarRefresh != null) javax.swing.SwingUtilities.invokeLater(avatarRefresh);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,"Could not save photo: " + ex.getMessage());
        }
    }

    private void removePhoto() {
        user.avatarPath = "";
        DAO.updateAvatar(user.id, "");
        avatarPanel.repaint();
        if (avatarRefresh != null) javax.swing.SwingUtilities.invokeLater(avatarRefresh);
    }

    private void addEditableField(JPanel parent, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(label);
        lbl.setFont(Theme.FONT_SMALL); lbl.setForeground(Theme.TEXT_MUTE);
        JTextField field = Theme.styledField(value);
        boolean editable = !label.equals("Email") && !label.equals("Role");
        field.setEditable(editable); field.setFocusable(editable);
        if (!editable) field.setForeground(Theme.TEXT_MUTE);
        fields.put(label, field);
        row.add(lbl, BorderLayout.NORTH); row.add(field, BorderLayout.CENTER);
        parent.add(row);
    }

    private JPanel buildPasswordCard() {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0,0));

        JPanel header = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0,0,new Color(0x1E1B4B),getWidth(),0,new Color(0x221B40)));
                g2.fillRect(0,0,getWidth(),getHeight()); g2.dispose(); super.paintComponent(g);
            }
        };
        header.setOpaque(false); header.setPreferredSize(new Dimension(0, 60));
        header.setLayout(new FlowLayout(FlowLayout.LEFT, 16, 14));
        JLabel lock = new JLabel("🔒"); lock.setFont(Theme.font(Font.PLAIN, 20));
        JPanel headerText = new JPanel(new GridLayout(2,1,0,2)); headerText.setOpaque(false);
        JLabel ht1 = new JLabel("Change Password"); ht1.setFont(Theme.FONT_H2); ht1.setForeground(Theme.TEXT_DARK);
        JLabel ht2 = new JLabel("Update your account password"); ht2.setFont(Theme.FONT_SMALL); ht2.setForeground(Theme.TEXT_MUTE);
        headerText.add(ht1); headerText.add(ht2);
        header.add(lock); header.add(headerText);
        card.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(16, 20, 20, 20));

        JPasswordField currentPF = Theme.styledPassword("Current password");
        JPasswordField newPF     = Theme.styledPassword("New password (8+ chars)");
        JPasswordField confirmPF = Theme.styledPassword("Confirm new password");
        String[][] pwLabels = {{"Current Password"},{"New Password"},{"Confirm Password"}};
        JPasswordField[] pfs = {currentPF, newPF, confirmPF};
        for (int i = 0; i < pwLabels.length; i++) {
            JPanel row = new JPanel(new BorderLayout(0,4));
            row.setOpaque(false); row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
            row.setBorder(BorderFactory.createEmptyBorder(0,0,10,0)); row.setAlignmentX(LEFT_ALIGNMENT);
            JLabel lbl = new JLabel(pwLabels[i][0]); lbl.setFont(Theme.FONT_SMALL); lbl.setForeground(Theme.TEXT_MUTE);
            row.add(lbl, BorderLayout.NORTH); row.add(pfs[i], BorderLayout.CENTER);
            body.add(row);
        }

        JLabel hint = new JLabel("Use 8+ characters with letters, numbers & symbols");
        hint.setFont(Theme.FONT_SMALL); hint.setForeground(Theme.TEXT_FAINT);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        body.add(hint); body.add(Box.createVerticalStrut(16));

        JButton changeBtn = Theme.primaryButton("Update Password");
        changeBtn.setAlignmentX(LEFT_ALIGNMENT);
        changeBtn.addActionListener(e -> {
            String cur = new String(currentPF.getPassword());
            String nw  = new String(newPF.getPassword());
            String cnf = new String(confirmPF.getPassword());
            if (cur.isEmpty() || nw.isEmpty()) { JOptionPane.showMessageDialog(this,"Please fill all fields.","Error",JOptionPane.ERROR_MESSAGE); return; }
            if (!nw.equals(cnf)) { JOptionPane.showMessageDialog(this,"Passwords do not match.","Error",JOptionPane.ERROR_MESSAGE); return; }
            if (nw.length() < 8) { JOptionPane.showMessageDialog(this,"Password must be 8+ characters.","Error",JOptionPane.ERROR_MESSAGE); return; }
            boolean ok = DAO.updatePassword(user.id, cur, nw);
            if (ok) { currentPF.setText(""); newPF.setText(""); confirmPF.setText(""); JOptionPane.showMessageDialog(this,"Password updated!","Success",JOptionPane.INFORMATION_MESSAGE); }
            else    { JOptionPane.showMessageDialog(this,"Current password is incorrect.","Error",JOptionPane.ERROR_MESSAGE); }
        });
        body.add(changeBtn);
        card.add(body, BorderLayout.CENTER);
        return card;
    }
    private JPanel buildCloudCard() {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 0));

        // Header
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));
        header.setOpaque(false);
        JLabel icon = new JLabel("☁");
        icon.setFont(Theme.font(Font.PLAIN, 22));
        JPanel ht = new JPanel(new GridLayout(2, 1, 0, 2)); ht.setOpaque(false);
        JLabel h1 = new JLabel("Cloud Connection — Admin Only");
        h1.setFont(Theme.FONT_H2); h1.setForeground(Theme.TEXT_DARK);
        boolean on = com.campusshare.remote.ConnectionMonitor.isOnline();
        String statusText = SupabaseConfig.isConfigured()
            ? (on ? "● Connected  (" + SupabaseConfig.configSource + ")" : "● Offline  (credentials saved)")
            : "Not configured — running in offline mode";
        JLabel h2 = new JLabel(statusText);
        h2.setFont(Theme.FONT_SMALL);
        h2.setForeground(on ? Theme.SUCCESS : Theme.TEXT_MUTE);
        ht.add(h1); ht.add(h2);
        header.add(icon); header.add(ht);
        card.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(0, 20, 16, 20));

        // URL field — URL is not a secret, show it
        JTextField urlF = Theme.styledField(
            SupabaseConfig.isConfigured() ? SupabaseConfig.SUPABASE_URL : "https://your-project.supabase.co");
        addCloudFieldRow(body, "Project URL (from Supabase → Settings → API)", urlF);

        // Show masked key — never display the real key value
        if (SupabaseConfig.isConfigured()) {
            JLabel maskedLbl = new JLabel("Saved key: " + SupabaseConfig.maskedKey());
            maskedLbl.setFont(Theme.FONT_SMALL); maskedLbl.setForeground(Theme.SUCCESS);
            maskedLbl.setAlignmentX(LEFT_ALIGNMENT);
            body.add(maskedLbl);
            body.add(Box.createVerticalStrut(4));
        }

        // Password field for new key entry
        JPasswordField keyF = Theme.styledPassword("Paste new anon key to update (leave blank to keep current)");
        addCloudFieldRow(body, "New Anon Key (paste from Supabase → Settings → API → anon public)", keyF);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow.setOpaque(false); btnRow.setAlignmentX(LEFT_ALIGNMENT);

        JButton saveBtn = Theme.primaryButton("Save & Connect");
        saveBtn.addActionListener(e -> {
            String url = urlF.getText().trim();
            String newKey = new String(keyF.getPassword()).trim();
            if (url.isEmpty() || !url.startsWith("https://")) {
                JOptionPane.showMessageDialog(this, "Enter a valid URL starting with https://");
                return;
            }
            String keyToSave = newKey.isEmpty() ? SupabaseConfig.ANON_KEY : newKey;
            if (keyToSave.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please paste the Anon key from Supabase.");
                return;
            }
            try {
                SupabaseConfig.saveUserConfig(url, keyToSave);
                SupabaseConfig.FORCE_OFFLINE = false;
                keyF.setText("");
                JOptionPane.showMessageDialog(this,
                    "Saved! The app will connect automatically when internet is available.",
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
            } catch (java.io.IOException ex) {
                JOptionPane.showMessageDialog(this, "Could not save: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton syncBtn = Theme.ghostButton("↑ Force Sync", Theme.PRIMARY);
        syncBtn.addActionListener(e -> {
            if (!SupabaseConfig.isConfigured()) {
                JOptionPane.showMessageDialog(this, "No credentials configured yet."); return;
            }
            new Thread(() -> {
                SyncManager.SyncResult r = SyncManager.syncLocalToCloud();
                javax.swing.SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this, r.summary(), "Sync Result",
                        JOptionPane.INFORMATION_MESSAGE));
            }, "ManualSync").start();
        });

        JButton clearBtn = Theme.dangerButton("Clear");
        clearBtn.setPreferredSize(new Dimension(70, 34));
        clearBtn.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(this,
                "Remove saved Supabase credentials?\nApp will run offline until re-configured.",
                "Clear credentials", JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) {
                try {
                    SupabaseConfig.saveUserConfig("", "");
                    SupabaseConfig.FORCE_OFFLINE = true;
                    JOptionPane.showMessageDialog(this, "Credentials cleared. Running offline.");
                } catch (java.io.IOException ex) {
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
                }
            }
        });

        btnRow.add(saveBtn); btnRow.add(syncBtn); btnRow.add(clearBtn);
        body.add(btnRow);
        body.add(Box.createVerticalStrut(12));

        JLabel distNote = new JLabel("<html><i>💡 To distribute to teammates without showing keys:<br>"
            + "Put campusshare.properties next to CampusShare.jar — they just run the JAR.</i></html>");
        distNote.setFont(Theme.FONT_SMALL);
        distNote.setForeground(Theme.TEXT_FAINT);
        distNote.setAlignmentX(LEFT_ALIGNMENT);
        body.add(distNote);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private void addCloudFieldRow(JPanel parent, String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(label);
        lbl.setFont(Theme.FONT_SMALL); lbl.setForeground(Theme.TEXT_MUTE);
        row.add(lbl, BorderLayout.NORTH); row.add(field, BorderLayout.CENTER);
        parent.add(row);
    }


}
