package com.campusshare.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Objects;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.campusshare.data.DataStore;
import com.campusshare.remote.CredentialStore;

/**
 * Login / Sign-Up window.
 * Full-width building image. Minimal dark overlay only on the right half for card contrast.
 * Card width balanced for content. No department chips in branding.
 * Faculty signup now includes Department selection.
 */
public class LoginWindow extends JFrame {

    private final Runnable onLoginSuccess;

    private JTextField     emailField;
    private JPasswordField passField;
    private JTextField     nameField;
    private JComboBox<String> roleBox;
    private JComboBox<String> deptBox;
    private JComboBox<String> semBox;
    private JLabel         errorLabel;

    private static BufferedImage BG_IMAGE;
    static {
        try (InputStream is = LoginWindow.class.getResourceAsStream("/login_bg.png")) {
            if (is != null) BG_IMAGE = ImageIO.read(is);
        } catch (Exception ignored) {}
    }

    private JTextField studentIdField;

    private static final Color CARD_BG     = new Color(8, 18, 40, 228);
    private static final Color CARD_BORDER = new Color(74, 127, 193, 90);
    private static final Color ACCENT      = new Color(91, 155, 213);
    private static final Color ACCENT_LT   = new Color(143, 196, 240);

    public LoginWindow(Runnable onLoginSuccess) {
        super("CampusShare — Sign In");
        this.onLoginSuccess = onLoginSuccess;
        setSize(1060, 760);
        setMinimumSize(new Dimension(820, 640));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(buildRoot());
    }

    // ── Root: full-window image, branding bottom-left, card right ────────────
    private JPanel buildRoot() {
        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintBg((Graphics2D) g, getWidth(), getHeight());
            }
        };
        root.setOpaque(true);

        // LEFT — branding bottom-left
        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);

        JPanel branding = new JPanel();
        branding.setLayout(new BoxLayout(branding, BoxLayout.Y_AXIS));
        branding.setOpaque(false);
        branding.setBorder(BorderFactory.createEmptyBorder(0, 40, 52, 20));

        JLabel name = new JLabel("CampusShare");
        name.setFont(new Font("SansSerif", Font.BOLD, 44));
        name.setForeground(Color.WHITE);
        name.setAlignmentX(LEFT_ALIGNMENT);

        JLabel tag = new JLabel("Campus. Connected.");
        tag.setFont(new Font("SansSerif", Font.PLAIN, 28));
        tag.setForeground(new Color(255, 255, 255, 190));
        tag.setAlignmentX(LEFT_ALIGNMENT);

        branding.add(name);
        branding.add(Box.createVerticalStrut(6));
        branding.add(tag);
        left.add(branding, BorderLayout.SOUTH);

        // RIGHT — card wrapper (width reduced: 680 → 520)
        JPanel rightWrap = new JPanel(new GridBagLayout()) {
            @Override public Dimension getPreferredSize() {
                return new Dimension(560, super.getPreferredSize().height);
            }
        };
        rightWrap.setOpaque(false);
        rightWrap.setBorder(BorderFactory.createEmptyBorder(24, 12, 24, 40));
        rightWrap.add(buildLoginCard(), new GridBagConstraints());

        root.add(left, BorderLayout.CENTER);
        root.add(rightWrap, BorderLayout.EAST);
        return root;
    }

    private void paintBg(Graphics2D g2, int w, int h) {
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (BG_IMAGE != null) {
            double ir = (double) BG_IMAGE.getWidth() / BG_IMAGE.getHeight();
            double pr = (double) w / h;
            int dw, dh, ox, oy;
            if (ir > pr) { dh = h; dw = (int)(h * ir); ox = (w - dw) / 2; oy = 0; }
            else          { dw = w; dh = (int)(w / ir); ox = 0; oy = (h - dh) / 2; }
            g2.drawImage(BG_IMAGE, ox, oy, dw, dh, null);
        } else {
            g2.setColor(new Color(10, 20, 50)); g2.fillRect(0, 0, w, h);
        }
        // Very light vignette on LEFT — preserve image clarity
        g2.setPaint(new java.awt.GradientPaint(0, h*2/3, new Color(0,0,0,0), 0, h, new Color(0,0,0,120)));
        g2.fillRect(0, 0, w/2, h);
    }

    // ── Cards ────────────────────────────────────────────────────────────────
    private JPanel buildLoginCard() {
        JPanel card = makeCard(400);
        GridBagConstraints gc = gc();

        logoRow(card, gc);
        addTitle(card, gc, "Happy return", "Sign in to your account");

        row(card, gc, "Email Address");
        emailField = field("student@campus.edu"); add(card, gc, emailField, 12);

        row(card, gc, "Password");
        passField = pass("Enter your password"); add(card, gc, passField, 8);

        errorLabel = errLabel(); add(card, gc, errorLabel, 4);

        JButton btn = btn("Sign In"); btn.addActionListener(e -> doLogin());
        passField.addActionListener(e -> doLogin());
        add(card, gc, btn, 16);

        add(card, gc, toggleRow("Don't have an account?", "Sign Up", () -> swap(buildSignUpCard(), "Sign Up")), 24);
        return card;
    }

    private JPanel buildSignUpCard() {
        // Height adjusted: 660 to fit ID field + proper button height
        JPanel card = makeCard(660);
        GridBagConstraints gc = gc();
        logoRow(card, gc);
        addTitle(card, gc, "Create Account", "Join your campus");

        // Role selector: Student or Faculty
        row(card, gc, "I am a");
        roleBox = combo(new String[]{"Student","Faculty"});
        // same height as all inputs
        add(card, gc, roleBox, 12);

        // Name + Dept row
        row(card, gc, "Full Name  /  Department");
        JPanel r1 = new JPanel(new GridLayout(1,2,8,0)); r1.setOpaque(false);
        nameField = field("Full name");
        deptBox   = combo(new String[]{"CSE","EEE","BBA","Law","English","Mathematics"});
        r1.add(nameField); r1.add(deptBox);
        add(card, gc, r1, 10);

        // Email
        row(card, gc, "Email Address");
        emailField = field("you@campus.edu"); add(card, gc, emailField, 10);

        // Student / Employee ID
        row(card, gc, "Student / Employee ID");
        studentIdField = field("e.g. 2021-CSE-001"); add(card, gc, studentIdField, 10);

        // Semester + Password  (semester hidden for Faculty)
        JPanel semRow = new JPanel(new GridLayout(1,2,8,0)); semRow.setOpaque(false);
        semBox    = combo(new String[]{"1","2","3","4","5","6","7","8"});
        passField = pass("Choose a password");
        semRow.add(semBox); semRow.add(passField);

        JLabel semRowLabel = lbl("Semester  /  Password");
        gc.gridy++; gc.insets = new Insets(0,28,4,28);
        card.add(semRowLabel, gc);
        gc.gridy++; gc.insets = new Insets(0,28,10,28);
        card.add(semRow, gc);

        // Hide semester picker for Faculty selection
        roleBox.addActionListener(e -> {
            boolean isFac = "Faculty".equals(roleBox.getSelectedItem());
            semRow.getComponent(0).setVisible(!isFac);   // semBox
            semRowLabel.setText(isFac ? "Password" : "Semester  /  Password");
        });

        errorLabel = errLabel(); add(card, gc, errorLabel, 4);

        JButton btn = btn("Create Account"); btn.addActionListener(e -> doSignUp());
        passField.addActionListener(e -> doSignUp());
        add(card, gc, btn, 12);
        add(card, gc, toggleRow("Already have an account?", "Sign In", () -> swap(buildLoginCard(), "Sign In")), 20);
        return card;
    }

    private void swap(JPanel newCard, String title) {
        JPanel root  = (JPanel) getContentPane();
        JPanel right = (JPanel) ((BorderLayout) root.getLayout()).getLayoutComponent(BorderLayout.EAST);
        right.removeAll();
        GridBagConstraints gc = new GridBagConstraints(); gc.insets = new Insets(0,0,0,0);
        right.add(newCard, gc);
        right.revalidate(); right.repaint();
        setTitle("CampusShare — " + title);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private JPanel makeCard(int h) {
        JPanel c = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG); g2.fillRoundRect(0,0,getWidth(),getHeight(),22,22);
                g2.setColor(CARD_BORDER); g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,22,22);
                g2.setColor(ACCENT); g2.setStroke(new BasicStroke(2.8f));
                g2.drawLine(44,0,getWidth()-44,0);
                g2.dispose(); super.paintComponent(g);
            }
        };
        c.setOpaque(false);
        // Width fixed; height is a minimum — let the card grow to fit all fields naturally
        c.setPreferredSize(new Dimension(500, h));
        c.setMinimumSize(new Dimension(460, h));
        return c;
    }
    private GridBagConstraints gc() {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx=0; g.fill=GridBagConstraints.HORIZONTAL; g.weightx=1; g.gridy=-1; return g;
    }
    private void logoRow(JPanel card, GridBagConstraints gc) {
        // logo removed — minimal top padding only
        gc.gridy++; gc.insets=new Insets(4,28,0,28);
    }
    private void addTitle(JPanel card, GridBagConstraints gc, String t1, String t2) {
        JLabel a = new JLabel(t1); a.setFont(new Font("SansSerif",Font.BOLD,30)); a.setForeground(Color.WHITE); a.setHorizontalAlignment(SwingConstants.CENTER);
        gc.gridy++; gc.insets=new Insets(0,28,4,28); card.add(a,gc);
        JLabel b = new JLabel(t2); b.setFont(new Font("SansSerif",Font.PLAIN,17)); b.setForeground(new Color(255,255,255,120)); b.setHorizontalAlignment(SwingConstants.CENTER);
        gc.gridy++; gc.insets=new Insets(0,28,20,28); card.add(b,gc);
    }
    private JLabel lbl(String t) { JLabel l=new JLabel(t); l.setFont(new Font("SansSerif",Font.BOLD,16)); l.setForeground(new Color(255,255,255,175)); return l; }
    private void row(JPanel card, GridBagConstraints gc, String text) {
        gc.gridy++; gc.insets=new Insets(0,28,5,28); card.add(lbl(text),gc);
    }
    private void add(JPanel card, GridBagConstraints gc, Component c, int bottomGap) {
        gc.gridy++; gc.insets=new Insets(0,28,bottomGap,28); card.add(c,gc);
    }
    private JTextField field(String ph) {
        JTextField f = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create(); g2.setColor(new Color(255,255,255,14)); g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8); g2.dispose();
                super.paintComponent(g);
                if (getText().isEmpty()&&!isFocusOwner()) { Graphics2D g3=(Graphics2D)g.create(); g3.setColor(new Color(255,255,255,50)); g3.setFont(getFont()); Insets ins=getInsets(); FontMetrics fm=g3.getFontMetrics(); g3.drawString(ph,ins.left+2,(getHeight()+fm.getAscent()-fm.getDescent())/2); g3.dispose(); }
            }
        };
        styleInput(f); return f;
    }
    private JPasswordField pass(String ph) {
        JPasswordField f = new JPasswordField() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create(); g2.setColor(new Color(255,255,255,14)); g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8); g2.dispose();
                super.paintComponent(g);
                if (getPassword().length==0&&!isFocusOwner()) { Graphics2D g3=(Graphics2D)g.create(); g3.setColor(new Color(255,255,255,50)); g3.setFont(getFont()); Insets ins=getInsets(); FontMetrics fm=g3.getFontMetrics(); g3.drawString(ph,ins.left+2,(getHeight()+fm.getAscent()-fm.getDescent())/2); g3.dispose(); }
            }
        };
        styleInput(f); f.setEchoChar('•'); return f;
    }
    private JComboBox<String> combo(String[] items) {
        JComboBox<String> c = new JComboBox<>(items);
        c.setBackground(new Color(12,24,52)); c.setForeground(Color.WHITE);
        c.setFont(new Font("SansSerif",Font.PLAIN,17));
        c.setBorder(new RoundBorder(8,new Color(74,127,193,90),1));
        c.setPreferredSize(new Dimension(0,46)); return c;
    }
    private void styleInput(JTextField f) {
        f.setOpaque(false);
        f.setBorder(BorderFactory.createCompoundBorder(new RoundBorder(8,new Color(74,127,193,90),1), BorderFactory.createEmptyBorder(8,12,8,12)));
        f.setForeground(Color.WHITE); f.setCaretColor(ACCENT_LT);
        f.setFont(new Font("SansSerif",Font.PLAIN,17)); f.setPreferredSize(new Dimension(0,46));
    }
    private JButton btn(String text) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg=getModel().isRollover()?new Color(74,138,196):ACCENT;
                g2.setPaint(new java.awt.GradientPaint(0,0,bg.brighter(),0,getHeight(),bg));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
                g2.setColor(Color.WHITE); g2.setFont(new Font("SansSerif",Font.BOLD,20));
                FontMetrics fm=g2.getFontMetrics(); g2.drawString(getText(),(getWidth()-fm.stringWidth(getText()))/2,(getHeight()+fm.getAscent()-fm.getDescent())/2); g2.dispose();
            }
        };
        b.setPreferredSize(new Dimension(0,54)); b.setOpaque(false); b.setContentAreaFilled(false);
        b.setBorderPainted(false); b.setFocusPainted(false); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return b;
    }
    private JLabel errLabel() {
        JLabel l=new JLabel(" "); l.setFont(new Font("SansSerif",Font.PLAIN,15));
        l.setForeground(new Color(252,165,165)); l.setHorizontalAlignment(SwingConstants.CENTER); return l;
    }
    private JPanel toggleRow(String msg, String link, Runnable action) {
        JPanel w=new JPanel(); w.setOpaque(false);
        JPanel inner=new JPanel(new FlowLayout(FlowLayout.CENTER,4,0)); inner.setOpaque(false);
        JLabel m=new JLabel(msg); m.setFont(new Font("SansSerif",Font.PLAIN,16)); m.setForeground(new Color(255,255,255,120));
        JLabel l=new JLabel(link); l.setFont(new Font("SansSerif",Font.BOLD,16)); l.setForeground(ACCENT_LT); l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter(){ public void mousePressed(MouseEvent e){ action.run(); } });
        inner.add(m); inner.add(l); w.add(inner); return w;
    }

    // ── Actions ──────────────────────────────────────────────────────────────
    private void doLogin() {
        if (errorLabel==null) return;
        String email=emailField.getText().trim(), pass=new String(passField.getPassword());
        if (email.isEmpty()||pass.isEmpty()) { errorLabel.setText("Please fill in all fields."); return; }
        DataStore.User u = DataStore.login(email, pass);
        if (u==null) { errorLabel.setText("Invalid email or password."); return; }
        DataStore.setCurrentUser(u); DataStore.loadAll(); CredentialStore.save(email, pass); finish();
    }

    private void doSignUp() {
        if (errorLabel==null) return;
        String name  = nameField  !=null ? nameField.getText().trim()        : "";
        String email = emailField !=null ? emailField.getText().trim()        : "";
        String pass  = passField  !=null ? new String(passField.getPassword()): "";
        String dept  = deptBox    !=null ? (String) deptBox.getSelectedItem() : "CSE";
        String role  = roleBox    !=null ? (String) roleBox.getSelectedItem() : "Student";
        String sid   = studentIdField != null ? studentIdField.getText().trim() : "";
        boolean isFaculty = "Faculty".equals(role);
        int sem = (!isFaculty && semBox!=null)
            ? Integer.parseInt((String) Objects.requireNonNull(semBox.getSelectedItem())) : 0;
        String dbRole = isFaculty ? "FACULTY" : "STUDENT";

        if (name.isEmpty()||email.isEmpty()||pass.isEmpty()) { errorLabel.setText("Please fill in all fields."); return; }
        if (pass.length()<6) { errorLabel.setText("Password must be at least 6 characters."); return; }
        boolean ok = com.campusshare.db.DAO.registerUser(name, email, pass, dbRole, dept, sem);
        if (!ok) { errorLabel.setText("Email already registered."); return; }
        DataStore.User u = DataStore.login(email, pass);
        if (u==null) { errorLabel.setText("Registration error. Try again."); return; }
        // Save student/employee ID if provided
        if (!sid.isEmpty()) { com.campusshare.db.DAO.updateStudentId(u.id, sid); u.studentId = sid; }
        DataStore.setCurrentUser(u); DataStore.loadAll(); finish();
    }

    private void finish() { setVisible(false); dispose(); SwingUtilities.invokeLater(onLoginSuccess); }
    public JPanel getContentPanel() { return (JPanel) getContentPane(); }
}