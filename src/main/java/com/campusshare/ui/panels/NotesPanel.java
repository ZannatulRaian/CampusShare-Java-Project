package com.campusshare.ui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.campusshare.data.DataStore;
import com.campusshare.db.DAO;
import com.campusshare.ui.Theme;

public class NotesPanel extends JPanel {

    private final DataStore.User user;
    private JTextField searchField;
    private JComboBox<String> subjectFilter;
    private JComboBox<String> semesterFilter;
    private JComboBox<String> deptFilter;
    private static final String[] DEPTS = {"ALL","CSE","EEE","BBA","LAW","ENG","MATH"};
    private JPanel cardGrid;
    private JComboBox<DataStore.Subject> adminDelBox; // field so it can be refreshed after add/delete

    private static final File FILES_DIR = new File(
        System.getProperty("user.home"), "CampusShare" + File.separator + "files");

    // Type → Color mapping (deterministic by file type)
    private static Color colorForType(String type) {
        if (type == null) return new Color(0x6366F1);
        return switch (type.toUpperCase().trim()) {
            case "PDF"            -> new Color(0xF43F5E);
            case "PPTX", "PPT"   -> new Color(0xF59E0B);
            case "DOCX", "DOC"   -> new Color(0x3B82F6);
            case "XLSX", "XLS"   -> new Color(0x0D9488);
            case "ZIP", "RAR"    -> new Color(0x8B5CF6);
            case "TXT"           -> new Color(0x64748B);
            case "PNG", "JPG", "JPEG" -> new Color(0xEC4899);
            default              -> new Color(0x6366F1);
        };
    }

    private static String iconForType(String type) {
        if (type == null) return "\uD83D\uDCC4";
        return switch (type.toUpperCase().trim()) {
            case "PDF"            -> "\uD83D\uDCD5";
            case "PPTX", "PPT"   -> "\uD83D\uDCCA";
            case "DOCX", "DOC"   -> "\uD83D\uDCDD";
            case "XLSX", "XLS"   -> "\uD83D\uDCD7";
            case "ZIP", "RAR"    -> "\uD83D\uDDDC";
            case "TXT"           -> "\uD83D\uDCC3";
            case "PNG", "JPG", "JPEG" -> "\uD83D\uDDBC";
            default              -> "\uD83D\uDCC4";
        };
    }

    // Derive file type from fileName if fileType is blank/unknown
    private static String resolveType(DataStore.Note n) {
        String t = n.fileType == null ? "" : n.fileType.trim().toUpperCase();
        if (!t.isEmpty() && !t.equals("FILE") && !t.equals("UNKNOWN")) return t;
        String name = n.fileName;
        if (name != null && name.contains("."))
            return name.substring(name.lastIndexOf('.') + 1).toUpperCase();
        return "FILE";
    }

    public NotesPanel(DataStore.User user) {
        this.user = user;
        FILES_DIR.mkdirs();
        setBackground(Theme.BG_APP);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        // cardGrid MUST be initialised before buildHeader() — the header's
        // combo boxes fire ActionEvents during construction which call populateGrid()
        cardGrid = new JPanel(new GridLayout(0, 3, 14, 14));
        cardGrid.setOpaque(false);
        add(buildHeader(), BorderLayout.NORTH);
        populateGrid();
        JPanel wrap = new JPanel(new BorderLayout()); wrap.setOpaque(false);
        wrap.add(cardGrid, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrap);
        sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        add(sp, BorderLayout.CENTER);

        // Admin: subject management panel at bottom
        if (user.isAdmin()) add(buildAdminSubjectPanel(), BorderLayout.SOUTH);
    }

    private JPanel buildHeader() {
        JPanel h = new JPanel(new BorderLayout(14, 0));
        h.setOpaque(false); h.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));

        JPanel left = new JPanel(); left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS)); left.setOpaque(false);
        JLabel title = new JLabel("Notes & Resources");
        title.setFont(new Font("SansSerif", Font.BOLD, 22)); title.setForeground(Theme.TEXT_DARK);
        JLabel sub = new JLabel("Filter by semester and subject");
        sub.setFont(Theme.font(Font.PLAIN, 15)); sub.setForeground(Theme.TEXT_MUTE);
        left.add(title); left.add(Box.createVerticalStrut(2)); left.add(sub);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);

        searchField = Theme.styledField("Search notes…");
        searchField.setPreferredSize(new Dimension(180, 34));
        searchField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) { populateGrid(); }
        });

        // Semester filter — faculty/admin see all semesters; students only their own
        List<Integer> semesters = getAvailableSemesters();
        String[] semOpts;
        if (user.isFaculty()) {
            semOpts = new String[semesters.size() + 1];
            semOpts[0] = "All Semesters";
            for (int i = 0; i < semesters.size(); i++) semOpts[i+1] = "Semester " + semesters.get(i);
        } else {
            // Students: only their own semester (guard against semester=0)
            int sem = user.semester > 0 ? user.semester : 1;
            semOpts = new String[]{"Semester " + sem};
        }
        semesterFilter = new JComboBox<>(semOpts);
        semesterFilter.setBackground(Theme.BG_INPUT); semesterFilter.setForeground(Theme.TEXT_DARK);
        semesterFilter.setFont(Theme.font(Font.PLAIN, 15));
        semesterFilter.setPreferredSize(new Dimension(130, 34));
        semesterFilter.addActionListener(e -> { rebuildSubjectFilter(); populateGrid(); });

        controls.add(searchField); controls.add(semesterFilter);

        // Department filter — only for faculty/admin
        if (user.isFaculty()) {
            deptFilter = new JComboBox<>(DEPTS);
            deptFilter.setBackground(Theme.BG_INPUT); deptFilter.setForeground(Theme.TEXT_DARK);
            deptFilter.setFont(Theme.font(Font.PLAIN, 15));
            deptFilter.setPreferredSize(new Dimension(100, 34));
            deptFilter.addActionListener(e -> { rebuildSubjectFilter(); populateGrid(); });
            controls.add(deptFilter);
        }

        // Subject filter
        subjectFilter = new JComboBox<>(new String[]{"All Subjects"});
        subjectFilter.setBackground(Theme.BG_INPUT); subjectFilter.setForeground(Theme.TEXT_DARK);
        subjectFilter.setFont(Theme.font(Font.PLAIN, 15));
        subjectFilter.setPreferredSize(new Dimension(150, 34));
        subjectFilter.addActionListener(e -> populateGrid());
        rebuildSubjectFilter();
        controls.add(subjectFilter);

        JButton uploadBtn = Theme.primaryButton("+ Upload Note");
        uploadBtn.setPreferredSize(new Dimension(130, 36));
        uploadBtn.addActionListener(e -> showUploadDialog());
        controls.add(uploadBtn);

        h.add(left, BorderLayout.WEST); h.add(controls, BorderLayout.EAST);
        return h;
    }

    private String getSelectedDept() {
        if (!user.isFaculty()) return user.department;
        if (deptFilter == null) return "ALL";
        String sel = (String) deptFilter.getSelectedItem();
        return sel == null ? "ALL" : sel;
    }

    private List<Integer> getAvailableSemesters() {
        // Always reload from DB so newly added subjects appear immediately
        DataStore.SUBJECTS = DAO.getAllSubjectsFull();
        String selDept = getSelectedDept();
        return DataStore.SUBJECTS.stream()
            .filter(s -> "ALL".equalsIgnoreCase(selDept) || s.department.equalsIgnoreCase(selDept))
            .map(s -> s.semester)
            .filter(sem -> sem > 0)
            .distinct().sorted().collect(Collectors.toList());
    }

    private void rebuildSubjectFilter() {
        if (subjectFilter == null) return;
        subjectFilter.removeAllItems();
        subjectFilter.addItem("All Subjects");
        int selSem = getSelectedSemester();
        String selDept = getSelectedDept();
        DataStore.SUBJECTS.stream()
            .filter(s -> "ALL".equalsIgnoreCase(selDept) || s.department.equalsIgnoreCase(selDept))
            .filter(s -> selSem <= 0 || s.semester == selSem)
            .forEach(s -> subjectFilter.addItem(s.name));
    }

    private int getSelectedSemester() {
        if (semesterFilter == null) return 0;
        String sel = (String) semesterFilter.getSelectedItem();
        if (sel == null || sel.startsWith("All")) return 0;
        try { return Integer.parseInt(sel.replace("Semester ", "").trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private void populateGrid() {
        if (cardGrid == null) return;
        cardGrid.removeAll();
        String query  = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        String selSub = subjectFilter == null ? "All Subjects" : (String) subjectFilter.getSelectedItem();
        int selSem    = getSelectedSemester();

        String selDept = getSelectedDept();
        List<DataStore.Note> filtered = DataStore.NOTES.stream()
            .filter(n -> user.isFaculty() || n.approved)
            // Dept isolation
            .filter(n -> user.isFaculty()
                ? ("ALL".equalsIgnoreCase(selDept) || n.subjectDepartment().equalsIgnoreCase(selDept))
                : n.subjectDepartment().equalsIgnoreCase(user.department))
            // Semester isolation: students only see their semester (guard against semester=0)
            .filter(n -> user.isFaculty() || user.semester <= 0 || n.subjectSemester() == user.semester)
            .filter(n -> selSem <= 0 || n.subjectSemester() == selSem)
            .filter(n -> query.isEmpty()
                || n.fileName.toLowerCase().contains(query)
                || n.subjectName().toLowerCase().contains(query)
                || n.uploadedBy.toLowerCase().contains(query))
            .filter(n -> "All Subjects".equals(selSub) || n.subjectName().equals(selSub))
            .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            cardGrid.setLayout(new BorderLayout());
            JLabel empty = new JLabel("No notes found");
            empty.setFont(Theme.font(Font.PLAIN, 24)); empty.setForeground(Theme.TEXT_FAINT);
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            cardGrid.add(empty, BorderLayout.CENTER);
        } else {
            if (!(cardGrid.getLayout() instanceof GridLayout))
                cardGrid.setLayout(new GridLayout(0, 3, 14, 14));
            filtered.forEach(n -> cardGrid.add(buildNoteCard(n)));
        }
        cardGrid.revalidate(); cardGrid.repaint();
    }

    private JPanel buildNoteCard(DataStore.Note n) {
        String fType = resolveType(n);
        Color tc = colorForType(fType);
        String fIcon = iconForType(fType);
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

        JPanel band = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Gradient background using type color
                g2.setPaint(new java.awt.GradientPaint(0, 0, tc, getWidth(), getHeight() + 16, tc.darker()));
                g2.fillRoundRect(0, 0, getWidth(), getHeight() + 16, 16, 16);
                // Large faded type label in background
                g2.setColor(new Color(0, 0, 0, 25));
                g2.setFont(new Font("SansSerif", Font.BOLD, 36));
                g2.drawString(fType, 8, 64);
                // Emoji icon — large and centred
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 26));
                FontMetrics fm = g2.getFontMetrics();
                int iy = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(fIcon, 14, iy);
                // Type badge pill — larger, on the right, clearly readable
                String badge = fType;
                g2.setFont(new Font("SansSerif", Font.BOLD, 13));
                fm = g2.getFontMetrics();
                int bw = fm.stringWidth(badge) + 20;
                int bh = 26;
                int bx = getWidth() - bw - 10;
                int by = (getHeight() - bh) / 2;
                g2.setColor(new Color(0, 0, 0, 80));
                g2.fillRoundRect(bx, by, bw, bh, 12, 12);
                g2.setColor(Color.WHITE);
                g2.drawString(badge, bx + 10, by + fm.getAscent() + (bh - fm.getHeight()) / 2);
                g2.dispose();
            }
        };
        band.setPreferredSize(new Dimension(0, 72)); band.setOpaque(false);

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false); info.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JLabel nameLbl = new JLabel("<html><b>" + n.fileName + "</b></html>");
        nameLbl.setFont(Theme.font(Font.BOLD, 16)); nameLbl.setForeground(Theme.TEXT_DARK);

        JLabel subjLbl = new JLabel(n.subjectName());
        subjLbl.setFont(Theme.font(Font.PLAIN, 14)); subjLbl.setForeground(Theme.TEXT_MUTE);

        JLabel semLbl = new JLabel("Semester " + n.subjectSemester() + "  •  " + n.subjectDepartment());
        semLbl.setFont(Theme.FONT_TINY); semLbl.setForeground(Theme.TEXT_FAINT);

        JLabel sizeLbl = new JLabel(n.fileSize + "  •  by " + n.uploadedBy);
        sizeLbl.setFont(Theme.FONT_TINY); sizeLbl.setForeground(Theme.TEXT_FAINT);

        info.add(nameLbl); info.add(Box.createVerticalStrut(4));
        info.add(subjLbl); info.add(Box.createVerticalStrut(2));
        info.add(semLbl);  info.add(Box.createVerticalStrut(2));
        info.add(sizeLbl);

        if (!n.approved) {
            JLabel pendingLbl = new JLabel("⏳ Pending approval");
            pendingLbl.setFont(Theme.FONT_TINY); pendingLbl.setForeground(new Color(0xF59E0B));
            info.add(Box.createVerticalStrut(2)); info.add(pendingLbl);
        }

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        actions.setOpaque(false);
        JButton dlBtn = Theme.primaryButton("⬇ Open");
        dlBtn.setFont(Theme.font(Font.BOLD, 14)); dlBtn.setPreferredSize(new Dimension(100, 34));
        dlBtn.addActionListener(e -> openNote(n));
        actions.add(dlBtn);

        if (user.isFaculty() && !n.approved) {
            JButton approveBtn = new JButton("✓ Approve");
            approveBtn.setFont(Theme.font(Font.BOLD, 14)); approveBtn.setPreferredSize(new Dimension(110, 34));
            approveBtn.setBackground(new Color(0x0D9488)); approveBtn.setForeground(Color.WHITE);
            approveBtn.addActionListener(e -> {
                DAO.approveNote(n.id); DataStore.reloadNotes(); populateGrid();
                // Notify uploader
                DAO.pushNoteNotificationToAll(n.subjectDepartment(), n.subjectSemester(),
                    "Note Approved!", n.fileName + " is now available.", n.id, user.id);
            });
            actions.add(approveBtn);
        }
        if (user.isFaculty() || n.uploadedBy.equals(user.fullName)) {
            JButton delBtn = new JButton("🗑  Delete");
            delBtn.setFont(Theme.font(Font.BOLD, 14)); delBtn.setPreferredSize(new Dimension(105, 34));
            delBtn.setBackground(new Color(0xF43F5E)); delBtn.setForeground(Color.WHITE);
            delBtn.setFocusPainted(false);
            delBtn.addActionListener(e -> {
                if (JOptionPane.showConfirmDialog(this, "Delete this note?",
                    "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    DAO.deleteNote(n.id); DataStore.reloadNotes(); populateGrid();
                }
            });
            actions.add(delBtn);
        }

        card.add(band, BorderLayout.NORTH);
        card.add(info, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    // ── Admin subject management panel ─────────────────────────────────────
    private JPanel buildAdminSubjectPanel() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(Theme.BG_SUBTLE);
        outer.setMinimumSize(new Dimension(0, 120));
        outer.setPreferredSize(new Dimension(0, 120));
        outer.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));

        JLabel hdr = new JLabel("Admin: Manage Subjects");
        hdr.setFont(Theme.font(Font.BOLD, 15)); hdr.setForeground(Theme.TEXT_DARK);
        outer.add(hdr, BorderLayout.NORTH);

        // ── Add subject row ──────────────────────────────────────────────────
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        form.setOpaque(false);

        JLabel nameL = new JLabel("Name:"); nameL.setForeground(Theme.TEXT_MUTE); nameL.setFont(Theme.font(Font.PLAIN,13));
        JTextField nameF = Theme.styledField("Subject name"); nameF.setPreferredSize(new Dimension(140, 30));

        JLabel codeL = new JLabel("Code:"); codeL.setForeground(Theme.TEXT_MUTE); codeL.setFont(Theme.font(Font.PLAIN,13));
        JTextField codeF = Theme.styledField("e.g. CSE301"); codeF.setPreferredSize(new Dimension(100, 30));

        String[] depts = {"CSE","EEE","BBA","LAW","ENG","MATH"};
        JLabel deptL = new JLabel("Dept:"); deptL.setForeground(Theme.TEXT_MUTE); deptL.setFont(Theme.font(Font.PLAIN,13));
        JComboBox<String> deptBox = new JComboBox<>(depts);
        deptBox.setBackground(Theme.BG_INPUT); deptBox.setForeground(Theme.TEXT_DARK);
        deptBox.setFont(Theme.font(Font.PLAIN, 13)); deptBox.setPreferredSize(new Dimension(75, 30));

        String[] sems = {"1","2","3","4","5","6","7","8"};
        JLabel semL = new JLabel("Sem:"); semL.setForeground(Theme.TEXT_MUTE); semL.setFont(Theme.font(Font.PLAIN,13));
        JComboBox<String> semBox = new JComboBox<>(sems);
        semBox.setBackground(Theme.BG_INPUT); semBox.setForeground(Theme.TEXT_DARK);
        semBox.setFont(Theme.font(Font.PLAIN, 13)); semBox.setPreferredSize(new Dimension(55, 30));

        JButton addBtn = Theme.primaryButton("+ Add Subject"); addBtn.setPreferredSize(new Dimension(130, 30));

        form.add(nameL); form.add(nameF);
        form.add(codeL); form.add(codeF);
        form.add(deptL); form.add(deptBox);
        form.add(semL);  form.add(semBox);
        form.add(addBtn);

        // ── Delete subject row ──────────────────────────────────────────────
        JPanel delPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        delPanel.setOpaque(false);

        JLabel delLbl = new JLabel("Delete subject:");
        delLbl.setFont(Theme.font(Font.PLAIN, 13)); delLbl.setForeground(Theme.TEXT_MUTE);

        adminDelBox = new JComboBox<>();
        adminDelBox.setBackground(Theme.BG_INPUT); adminDelBox.setForeground(Theme.TEXT_DARK);
        adminDelBox.setFont(Theme.font(Font.PLAIN, 13)); adminDelBox.setPreferredSize(new Dimension(220, 30));
        refreshAdminDelBox(); // populate from current DataStore.SUBJECTS

        JButton delBtn = Theme.dangerButton("🗑 Delete");
        delBtn.setPreferredSize(new Dimension(100, 30));

        delPanel.add(delLbl); delPanel.add(adminDelBox); delPanel.add(delBtn);

        // ── Wire up actions ─────────────────────────────────────────────────
        addBtn.addActionListener(e -> {
            String nm = nameF.getText().trim(); String cd = codeF.getText().trim();
            if (nm.isEmpty() || cd.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please fill in Name and Code.");
                return;
            }
            int sem = Integer.parseInt((String) semBox.getSelectedItem());
            String dept = (String) deptBox.getSelectedItem();
            int id = DAO.createSubjectWithSemester(nm, cd, dept, sem, 3);
            if (id > 0) {
                DataStore.SUBJECTS = DAO.getAllSubjectsFull();
                nameF.setText(""); codeF.setText("");
                refreshAdminDelBox();
                rebuildSemesterFilter();
                rebuildSubjectFilter();
                populateGrid();
                JOptionPane.showMessageDialog(this, "Subject '" + nm + "' added!");
            } else {
                JOptionPane.showMessageDialog(this, "Failed to add subject. Check for duplicate code.");
            }
        });

        delBtn.addActionListener(e -> {
            DataStore.Subject sel = (DataStore.Subject) adminDelBox.getSelectedItem();
            if (sel == null) { JOptionPane.showMessageDialog(this, "No subject selected."); return; }
            if (JOptionPane.showConfirmDialog(this,
                "Delete '" + sel.name + "'? This also removes all linked notes.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                DAO.deleteSubject(sel.id);
                DataStore.SUBJECTS = DAO.getAllSubjectsFull();
                DataStore.reloadNotes();
                refreshAdminDelBox();
                rebuildSemesterFilter();
                rebuildSubjectFilter();
                populateGrid();
            }
        });

        JPanel rows = new JPanel(); rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS)); rows.setOpaque(false);
        rows.add(form);
        rows.add(delPanel);
        outer.add(rows, BorderLayout.CENTER);
        return outer;
    }

    /** Refresh the admin delete combo box from current DataStore.SUBJECTS */
    private void refreshAdminDelBox() {
        if (adminDelBox == null) return;
        DataStore.Subject prev = (DataStore.Subject) adminDelBox.getSelectedItem();
        adminDelBox.removeAllItems();
        for (DataStore.Subject s : DataStore.SUBJECTS) adminDelBox.addItem(s);
        // Try restore previous selection
        if (prev != null) {
            for (int i = 0; i < adminDelBox.getItemCount(); i++) {
                if (adminDelBox.getItemAt(i).id == prev.id) { adminDelBox.setSelectedIndex(i); break; }
            }
        }
    }

    /** Rebuild the semester filter dropdown from current subjects */
    private void rebuildSemesterFilter() {
        if (semesterFilter == null) return;
        String prevSel = (String) semesterFilter.getSelectedItem();
        semesterFilter.removeAllItems();
        if (user.isFaculty()) {
            semesterFilter.addItem("All Semesters");
            getAvailableSemesters().forEach(s -> semesterFilter.addItem("Semester " + s));
            // Restore selection
            if (prevSel != null) {
                for (int i = 0; i < semesterFilter.getItemCount(); i++) {
                    if (semesterFilter.getItemAt(i).equals(prevSel)) { semesterFilter.setSelectedIndex(i); break; }
                }
            }
        }
        // Students: fixed to their own semester — no change needed
    }

    private void showUploadDialog() {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Upload Note", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(520, 420); dlg.setLocationRelativeTo(this);
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Theme.BG_CARD);
        p.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1; gc.gridx = 0;

        JLabel title = new JLabel("Upload Note"); title.setFont(Theme.font(Font.BOLD, 18));
        title.setForeground(Theme.TEXT_DARK); gc.gridy = 0; gc.insets = new Insets(0, 0, 16, 0);
        p.add(title, gc);

        // Reload subjects fresh
        DataStore.SUBJECTS = DAO.getAllSubjectsFull();

        // ── Department picker (faculty/admin only) ──────────────────────────
        JComboBox<String> deptPicker = new JComboBox<>();
        if (user.isFaculty()) {
            gc.gridy = 1; gc.insets = new Insets(0, 0, 4, 0);
            JLabel deptLbl = new JLabel("Department"); deptLbl.setFont(Theme.font(Font.PLAIN, 13));
            deptLbl.setForeground(Theme.TEXT_MUTE); p.add(deptLbl, gc);

            gc.gridy = 2; gc.insets = new Insets(0, 0, 10, 0);
            List<String> depts = DataStore.SUBJECTS.stream()
                .map(s -> s.department).filter(d -> d != null && !d.isEmpty())
                .distinct().sorted().collect(Collectors.toList());
            depts.forEach(deptPicker::addItem);
            deptPicker.setBackground(Theme.BG_INPUT); deptPicker.setForeground(Theme.TEXT_DARK);
            deptPicker.setFont(Theme.font(Font.PLAIN, 14));
            p.add(deptPicker, gc);
        }

        // Semester picker
        int nextRow = user.isFaculty() ? 3 : 1;
        gc.gridy = nextRow; gc.insets = new Insets(0, 0, 4, 0);
        JLabel semLbl = new JLabel("Semester"); semLbl.setFont(Theme.font(Font.PLAIN, 13));
        semLbl.setForeground(Theme.TEXT_MUTE); p.add(semLbl, gc);

        gc.gridy = nextRow + 1; gc.insets = new Insets(0, 0, 10, 0);
        String[] semOpts;
        if (user.isFaculty()) {
            List<Integer> sems = DataStore.SUBJECTS.stream()
                .map(s -> s.semester).filter(s -> s > 0).distinct().sorted()
                .collect(Collectors.toList());
            semOpts = sems.stream().map(s -> "Semester " + s).toArray(String[]::new);
            if (semOpts.length == 0) semOpts = new String[]{"Semester 1","Semester 2","Semester 3","Semester 4","Semester 5","Semester 6","Semester 7","Semester 8"};
        } else {
            int sem = user.semester > 0 ? user.semester : 1;
            semOpts = new String[]{"Semester " + sem};
        }
        JComboBox<String> semPicker = new JComboBox<>(semOpts);
        semPicker.setBackground(Theme.BG_INPUT); semPicker.setForeground(Theme.TEXT_DARK);
        semPicker.setFont(Theme.font(Font.PLAIN, 14));
        p.add(semPicker, gc);

        // Subject picker label
        gc.gridy = nextRow + 2; gc.insets = new Insets(0, 0, 4, 0);
        JLabel subjLblTxt = new JLabel("Subject"); subjLblTxt.setFont(Theme.font(Font.PLAIN, 13));
        subjLblTxt.setForeground(Theme.TEXT_MUTE); p.add(subjLblTxt, gc);

        // Subject picker
        gc.gridy = nextRow + 3; gc.insets = new Insets(0, 0, 10, 0);
        JComboBox<DataStore.Subject> subjPicker = new JComboBox<>();
        subjPicker.setBackground(Theme.BG_INPUT); subjPicker.setForeground(Theme.TEXT_DARK);
        subjPicker.setFont(Theme.font(Font.PLAIN, 14));

        // Populate subjects based on selected department + semester
        Runnable updateSubjs = () -> {
            subjPicker.removeAllItems();
            String selSemStr = (String) semPicker.getSelectedItem();
            int selSem = 0;
            if (selSemStr != null) {
                try { selSem = Integer.parseInt(selSemStr.replace("Semester ", "").trim()); }
                catch (Exception ignored) {}
            }
            final int fs = selSem;
            String selDept = user.isFaculty() ? (String) deptPicker.getSelectedItem() : user.department;
            DataStore.SUBJECTS.stream()
                .filter(s -> selDept == null || selDept.isEmpty() || s.department.equalsIgnoreCase(selDept))
                .filter(s -> fs <= 0 || s.semester == fs)
                .forEach(subjPicker::addItem);
        };

        // When dept changes, also refresh the semester list then subjects
        if (user.isFaculty()) {
            deptPicker.addActionListener(e -> {
                String selDept = (String) deptPicker.getSelectedItem();
                semPicker.removeAllItems();
                DataStore.SUBJECTS.stream()
                    .filter(s -> selDept == null || selDept.isEmpty() || s.department.equalsIgnoreCase(selDept))
                    .map(s -> s.semester).filter(s -> s > 0).distinct().sorted()
                    .forEach(s -> semPicker.addItem("Semester " + s));
                updateSubjs.run();
            });
        }
        updateSubjs.run();
        semPicker.addActionListener(e -> updateSubjs.run());
        p.add(subjPicker, gc);

        // File picker label
        gc.gridy = nextRow + 4; gc.insets = new Insets(0, 0, 4, 0);
        JLabel fileLbl = new JLabel("File"); fileLbl.setFont(Theme.font(Font.PLAIN, 13));
        fileLbl.setForeground(Theme.TEXT_MUTE); p.add(fileLbl, gc);

        // File button
        gc.gridy = nextRow + 5; gc.insets = new Insets(0, 0, 16, 0);
        JButton fileBtn = Theme.primaryButton("Choose File…");
        final File[] chosen = {null};
        fileBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("Documents (PDF, PPTX, DOCX)", "pdf", "pptx", "docx"));
            if (fc.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION) {
                chosen[0] = fc.getSelectedFile();
                fileBtn.setText(chosen[0].getName());
            }
        });
        p.add(fileBtn, gc);

        // Upload button
        gc.gridy = nextRow + 6; gc.insets = new Insets(0, 0, 0, 0);
        JButton uploadBtn = Theme.primaryButton("Upload");
        uploadBtn.addActionListener(e -> {
            if (chosen[0] == null) { JOptionPane.showMessageDialog(dlg, "Please choose a file first."); return; }
            DataStore.Subject subj = (DataStore.Subject) subjPicker.getSelectedItem();
            if (subj == null) { JOptionPane.showMessageDialog(dlg, "Please select a subject."); return; }
            try {
                FILES_DIR.mkdirs();
                File dest = new File(FILES_DIR, System.currentTimeMillis() + "_" + chosen[0].getName());
                Files.copy(chosen[0].toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                String ext = chosen[0].getName().contains(".")
                    ? chosen[0].getName().substring(chosen[0].getName().lastIndexOf('.') + 1).toUpperCase() : "FILE";
                long bytes = chosen[0].length();
                String size = bytes > 1_048_576 ? String.format("%.1f MB", bytes / 1_048_576.0) : (bytes / 1024) + " KB";
                boolean autoApprove = user.isFaculty();
                DAO.addNote(subj.id, user.id, chosen[0].getName(), ext, size, dest.getAbsolutePath(), autoApprove);
                DataStore.reloadNotes();
                DAO.pushNoteNotificationToAll(subj.department, subj.semester,
                    "New Note: " + chosen[0].getName(),
                    "Uploaded in " + subj.name + " (Semester " + subj.semester + ")", 0, user.id);
                populateGrid(); dlg.dispose();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dlg, "Upload failed: " + ex.getMessage());
            }
        });
        p.add(uploadBtn, gc);

        dlg.setContentPane(p); dlg.setVisible(true);
    }

    private void openNote(DataStore.Note n) {
        try {
            File f = new File(n.filePath);
            if (f.exists()) {
                Desktop.getDesktop().open(f);
            } else {
                JOptionPane.showMessageDialog(this, "File not found locally.\nPath: " + n.filePath);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Cannot open file: " + ex.getMessage());
        }
    }
}