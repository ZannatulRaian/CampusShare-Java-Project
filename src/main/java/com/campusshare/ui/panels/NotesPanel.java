package com.campusshare.ui.panels;

import com.campusshare.data.DataStore;
import com.campusshare.db.DAO;
import com.campusshare.ui.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

public class NotesPanel extends JPanel {

    private final DataStore.User user;
    private JTextField searchField;
    private JComboBox<String> subjectFilter;
    private JPanel cardGrid;

    private static final File FILES_DIR = new File(
        System.getProperty("user.home"), "CampusShare" + File.separator + "files");

    // Dark-friendly vivid type colors
    private static final Color[] TYPE_COLORS = {
        new Color(0xF43F5E), new Color(0x0D9488), new Color(0x6366F1),
        new Color(0xF59E0B), new Color(0x8B5CF6), new Color(0x38BDF8)
    };

    public NotesPanel(DataStore.User user) {
        this.user = user;
        FILES_DIR.mkdirs();
        setBackground(Theme.BG_APP);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        add(buildHeader(), BorderLayout.NORTH);
        cardGrid = new JPanel(new GridLayout(0, 3, 14, 14));
        cardGrid.setOpaque(false);
        populateGrid();
        JPanel wrap = new JPanel(new BorderLayout()); wrap.setOpaque(false);
        wrap.add(cardGrid, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrap);
        sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        add(sp, BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel h = new JPanel(new BorderLayout(14, 0));
        h.setOpaque(false); h.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));

        JPanel left = new JPanel(); left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS)); left.setOpaque(false);
        JLabel title = new JLabel("Notes & Resources");
        title.setFont(new Font("SansSerif", Font.BOLD, 22)); title.setForeground(Theme.TEXT_DARK);
        long total = DataStore.NOTES.stream().filter(n -> n.approved || user.isFaculty()).count();
        JLabel sub = new JLabel(total + " documents available");
        sub.setFont(Theme.font(Font.PLAIN, 12)); sub.setForeground(Theme.TEXT_MUTE);
        left.add(title); left.add(Box.createVerticalStrut(2)); left.add(sub);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);

        searchField = Theme.styledField("Search notes…");
        searchField.setPreferredSize(new Dimension(200, 34));
        searchField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) { populateGrid(); }
        });

        DataStore.SUBJECTS = DAO.getAllSubjects();
        String[] subs = new String[DataStore.SUBJECTS.size() + 1];
        subs[0] = "All Subjects";
        for (int i = 0; i < DataStore.SUBJECTS.size(); i++) subs[i+1] = DataStore.SUBJECTS.get(i).name;
        subjectFilter = new JComboBox<>(subs);
        subjectFilter.setBackground(Theme.BG_INPUT); subjectFilter.setForeground(Theme.TEXT_DARK);
        subjectFilter.setFont(Theme.font(Font.PLAIN, 12));
        subjectFilter.setPreferredSize(new Dimension(160, 34));
        subjectFilter.addActionListener(e -> populateGrid());

        JButton uploadBtn = Theme.primaryButton("+ Upload Note");
        uploadBtn.setPreferredSize(new Dimension(130, 34));
        uploadBtn.addActionListener(e -> showUploadDialog());

        controls.add(searchField); controls.add(subjectFilter); controls.add(uploadBtn);
        h.add(left, BorderLayout.WEST); h.add(controls, BorderLayout.EAST);
        return h;
    }

    private void populateGrid() {
        cardGrid.removeAll();
        String query  = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        String selSub = subjectFilter == null ? "All Subjects" : (String) subjectFilter.getSelectedItem();

        List<DataStore.Note> filtered = DataStore.NOTES.stream()
            .filter(n -> user.isFaculty() || n.approved)
            .filter(n -> query.isEmpty()
                || n.fileName.toLowerCase().contains(query)
                || n.subjectName().toLowerCase().contains(query))
            .filter(n -> "All Subjects".equals(selSub) || n.subjectName().equals(selSub))
            .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            cardGrid.setLayout(new BorderLayout());
            JLabel empty = new JLabel("No notes found");
            empty.setFont(Theme.font(Font.PLAIN, 14)); empty.setForeground(Theme.TEXT_FAINT);
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            cardGrid.add(empty, BorderLayout.CENTER);
        } else {
            if (!(cardGrid.getLayout() instanceof GridLayout))
                cardGrid.setLayout(new GridLayout(0, 3, 14, 14));
            int[] idx = {0};
            filtered.forEach(n -> { cardGrid.add(buildNoteCard(n, idx[0])); idx[0]++; });
        }
        cardGrid.revalidate(); cardGrid.repaint();
    }

    private JPanel buildNoteCard(DataStore.Note n, int idx) {
        Color tc = TYPE_COLORS[idx % TYPE_COLORS.length];

        // ── Outer card — DARK background ──────────────────────────────────────
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

        // ── Top color band ────────────────────────────────────────────────────
        JPanel band = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(tc);
                g2.fillRoundRect(0, 0, getWidth(), getHeight() + 16, 16, 16);
                g2.setColor(new Color(255,255,255,25));
                g2.setFont(new Font("SansSerif", Font.BOLD, 48));
                g2.drawString(n.fileType.substring(0, Math.min(3, n.fileType.length())), 12, 60);
                g2.dispose();
            }
        };
        band.setPreferredSize(new Dimension(0, 80));
        band.setOpaque(false);

        if (!n.approved) {
            JLabel pending = new JLabel("PENDING");
            pending.setFont(Theme.font(Font.BOLD, 9));
            pending.setForeground(Color.WHITE);
            pending.setBackground(new Color(0xD97706));
            pending.setOpaque(true);
            pending.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            pending.setBounds(8, 8, 58, 16);
            band.add(pending);
        }
        card.add(band, BorderLayout.NORTH);

        // ── Body — dark bg, light text ────────────────────────────────────────
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(10, 14, 12, 14));

        String fname = n.fileName.length() > 28 ? n.fileName.substring(0, 28) + "…" : n.fileName;
        JLabel nameLbl = new JLabel(fname);
        nameLbl.setFont(Theme.font(Font.BOLD, 12));
        nameLbl.setForeground(Theme.TEXT_DARK);   // light text on dark card

        JLabel subjLbl = new JLabel(n.subjectName());
        subjLbl.setFont(Theme.FONT_SMALL);
        subjLbl.setForeground(Theme.TEXT_MUTE);

        JPanel meta = new JPanel(new BorderLayout()); meta.setOpaque(false);
        meta.add(nameLbl, BorderLayout.NORTH);
        meta.add(subjLbl, BorderLayout.SOUTH);
        body.add(meta, BorderLayout.NORTH);

        JLabel star = new JLabel(n.uploadedBy);
        star.setFont(Theme.FONT_TINY);
        star.setForeground(Theme.TEXT_MUTE);
        body.add(star, BorderLayout.CENTER);

        // ── Action buttons ────────────────────────────────────────────────────
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        actions.setOpaque(false);

        JButton openBtn = Theme.ghostButton("Open", tc);
        openBtn.setPreferredSize(new Dimension(60, 26));
        openBtn.addActionListener(e -> openNote(n));
        actions.add(openBtn);

        if (user.isFaculty() && !n.approved) {
            JButton approveBtn = new JButton("✓ Approve") {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D)g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getModel().isRollover() ? new Color(0x047857) : Theme.SUCCESS);
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),7,7);
                    g2.setColor(Color.WHITE); g2.setFont(Theme.font(Font.BOLD, 10));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(getText(),(getWidth()-fm.stringWidth(getText()))/2,(getHeight()+fm.getAscent()-fm.getDescent())/2);
                    g2.dispose();
                }
            };
            approveBtn.setPreferredSize(new Dimension(80,26));
            approveBtn.setOpaque(false); approveBtn.setContentAreaFilled(false);
            approveBtn.setBorderPainted(false); approveBtn.setFocusPainted(false);
            approveBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            approveBtn.addActionListener(e -> { DAO.approveNote(n.id); DataStore.reloadNotes(); populateGrid(); });
            actions.add(approveBtn);
        }

        if (user.isFaculty() || n.uploadedBy.equals(user.fullName)) {
            JButton del = Theme.dangerButton("Delete");
            del.setPreferredSize(new Dimension(62,26));
            del.addActionListener(e -> {
                int r = JOptionPane.showConfirmDialog(this, "Delete \"" + n.fileName + "\"?",
                    "Confirm Delete", JOptionPane.YES_NO_OPTION);
                if (r == JOptionPane.YES_OPTION) { DAO.deleteNote(n.id); DataStore.reloadNotes(); populateGrid(); }
            });
            actions.add(del);
        }

        body.add(actions, BorderLayout.SOUTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    // ── Upload dialog ─────────────────────────────────────────────────────────
    private void showUploadDialog() {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Upload Note", true);
        dlg.setSize(500, 340);
        dlg.setLocationRelativeTo(this);

        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Theme.BG_CARD);
        p.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(6,0,6,0); gc.weightx = 1.0;

        JLabel titleLbl = new JLabel("Upload a Note");
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLbl.setForeground(Theme.TEXT_DARK);
        gc.gridx = 0; gc.gridy = 0; p.add(titleLbl, gc);

        // Subject
        JLabel subLbl = new JLabel("Subject");
        subLbl.setForeground(Theme.TEXT_MUTE); subLbl.setFont(Theme.FONT_SMALL);
        DataStore.SUBJECTS = DAO.getAllSubjects();
        String[] subs = DataStore.SUBJECTS.stream().map(s -> s.name).toArray(String[]::new);
        JComboBox<String> subjectBox = new JComboBox<>(subs);
        subjectBox.setEditable(true);  // ← Allow typing new subjects
        subjectBox.setBackground(Theme.BG_INPUT); subjectBox.setForeground(Theme.TEXT_DARK);
        subjectBox.setFont(Theme.font(Font.PLAIN, 13));
        gc.gridy = 1; p.add(subLbl, gc);
        gc.gridy = 2; p.add(subjectBox, gc);

        // File type filter — supports PDF, DOCX, PPTX, images, text
        JLabel fileLbl = new JLabel("File");
        fileLbl.setForeground(Theme.TEXT_MUTE); fileLbl.setFont(Theme.FONT_SMALL);

        File[] chosen = {null};
        JLabel fileLabel = new JLabel("No file chosen");
        fileLabel.setFont(Theme.font(Font.PLAIN, 12)); fileLabel.setForeground(Theme.TEXT_MUTE);

        JButton chooseBtn = Theme.ghostButton("Choose File", Theme.PRIMARY);
        chooseBtn.setPreferredSize(new Dimension(120, 34));
        chooseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Select a file to upload");
            fc.addChoosableFileFilter(new FileNameExtensionFilter(
                "All supported (PDF, Word, PPT, Images, Text)",
                "pdf","docx","doc","pptx","ppt","xlsx","xls","txt","md","png","jpg","jpeg","gif","zip"));
            fc.addChoosableFileFilter(new FileNameExtensionFilter("PDF Documents","pdf"));
            fc.addChoosableFileFilter(new FileNameExtensionFilter("Word Documents","docx","doc"));
            fc.addChoosableFileFilter(new FileNameExtensionFilter("PowerPoint","pptx","ppt"));
            fc.addChoosableFileFilter(new FileNameExtensionFilter("Images","png","jpg","jpeg","gif"));
            fc.addChoosableFileFilter(new FileNameExtensionFilter("Text / Markdown","txt","md"));
            fc.setAcceptAllFileFilterUsed(true);
            if (fc.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION) {
                chosen[0] = fc.getSelectedFile();
                fileLabel.setText(chosen[0].getName());
                fileLabel.setForeground(Theme.TEXT_DARK);
            }
        });

        JPanel fileRow = new JPanel(new BorderLayout(8,0)); fileRow.setOpaque(false);
        fileRow.add(chooseBtn, BorderLayout.WEST); fileRow.add(fileLabel, BorderLayout.CENTER);
        gc.gridy = 3; p.add(fileLbl, gc);
        gc.gridy = 4; p.add(fileRow, gc);

        JButton cancel = Theme.ghostButton("Cancel", Theme.TEXT_MUTE);
        cancel.setPreferredSize(new Dimension(90, 36));
        cancel.addActionListener(e -> dlg.dispose());

        JButton upload = Theme.primaryButton("Upload");
        upload.setPreferredSize(new Dimension(120, 36));
        upload.addActionListener(e -> {
            if (chosen[0] == null) { JOptionPane.showMessageDialog(dlg,"Please choose a file first."); return; }
            if (!chosen[0].exists()) { JOptionPane.showMessageDialog(dlg,"File not found: " + chosen[0].getPath()); return; }
            try {
                FILES_DIR.mkdirs();
                File dest = new File(FILES_DIR, chosen[0].getName());
                Files.copy(chosen[0].toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                
                // ── Get or create subject ──────────────────────────────────
                String subjectName = (String) subjectBox.getSelectedItem();
                if (subjectName == null || subjectName.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(dlg, "Please enter a subject.");
                    return;
                }
                subjectName = subjectName.trim();
                
                // Find subject by name
                int subId = -1;
                for (DataStore.Subject s : DataStore.SUBJECTS) {
                    if (s.name.equalsIgnoreCase(subjectName)) {
                        subId = s.id;
                        break;
                    }
                }
                
                // If not found, create it automatically
                if (subId == -1) {
                    // Auto-generate subject code: SUBJ001, SUBJ002, etc
                    String subCode = "CUSTOM" + (DataStore.SUBJECTS.size() + 1);
                    int createdId = DAO.createSubject(subjectName, subCode, "General", 3);
                    if (createdId > 0) {
                        subId = createdId;
                        // Reload subjects so dropdown updates
                        DataStore.SUBJECTS = DAO.getAllSubjects();
                        System.out.println("[Notes] Auto-created subject: " + subjectName + " (ID: " + subId + ")");
                    } else {
                        JOptionPane.showMessageDialog(dlg, "Failed to create subject: " + subjectName);
                        return;
                    }
                }
                
                String name = chosen[0].getName();
                String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')+1).toUpperCase() : "FILE";
                long bytes = chosen[0].length();
                String size = bytes > 1_048_576 ? String.format("%.1f MB", bytes/1_048_576.0)
                    : String.format("%.0f KB", bytes/1024.0);
                upload.setText("Uploading…"); upload.setEnabled(false);
                DAO.addNote(subId, user.id, name, ext, size, dest.getAbsolutePath(), user.isFaculty());
                DataStore.reloadNotes(); populateGrid(); dlg.dispose();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dlg,"Upload failed: " + ex.getMessage());
                upload.setText("Upload"); upload.setEnabled(true);
            }
        });

        // Button row at bottom — always visible
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);
        btnRow.add(cancel); btnRow.add(upload);
        gc.gridy = 5; gc.anchor = GridBagConstraints.EAST; gc.fill = GridBagConstraints.NONE;
        p.add(btnRow, gc);
        dlg.setContentPane(p); dlg.setVisible(true);
    }

    private void openNote(DataStore.Note n) {
        // For seeded notes with no real file path, show info instead
        if (n.filePath == null || n.filePath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "This is a demo note — no actual file is stored locally.\nUpload a real file using '+ Upload Note' to open files.",
                "Demo Note", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        File f = new File(n.filePath);
        if (!f.exists()) {
            File alt = new File(FILES_DIR, n.fileName);
            if (alt.exists()) f = alt;
            else {
                JOptionPane.showMessageDialog(this,
                    "File not found on disk.\nExpected: " + n.filePath +
                    "\n\nThis may be a demo note or the file was moved.",
                    "File Not Found", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        openFile(f);
    }

    /** Open a file using the OS default application.
     *  Uses ProcessBuilder on Windows to properly handle Unicode/non-ASCII paths.
     */
    private void openFile(File f) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                // "start" is a shell command — use cmd /c start "" "<path>"
                // This correctly handles Unicode filenames and spaces
                new ProcessBuilder("cmd", "/c", "start", "", f.getAbsolutePath())
                    .redirectErrorStream(true).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", f.getAbsolutePath()).start();
            } else {
                new ProcessBuilder("xdg-open", f.getAbsolutePath()).start();
            }
        } catch (IOException ex) {
            // Fallback: try java.awt.Desktop
            try { Desktop.getDesktop().open(f); }
            catch (Exception e2) {
                JOptionPane.showMessageDialog(this,
                    "Cannot open the file. Try opening it manually from:\n" + f.getAbsolutePath(),
                    "Cannot Open", JOptionPane.WARNING_MESSAGE);
            }
        }
    }
}
