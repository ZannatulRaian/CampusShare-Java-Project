package com.campusshare.ui;

import com.campusshare.data.DataStore;
import com.campusshare.remote.CredentialStore;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.Random;

public class LoginWindow extends JFrame {

    private final float[] starX, starY, starR, starAlpha;
    private float phase1 = 0f, phase2 = 0f;
    private final Runnable onLoginSuccess;

    private boolean isSignUp = false;
    private JPanel cardHolder;   // swapped on toggle

    private JTextField    emailField;
    private JPasswordField passField;
    private JTextField    nameField;
    private JComboBox<String> deptBox;
    private JLabel        errorLabel;

    public LoginWindow(Runnable onLoginSuccess) {
        super("CampusShare \u2014 Sign In");
        this.onLoginSuccess = onLoginSuccess;

        // Resizable, decorated window
        setSize(900, 660);
        setMinimumSize(new Dimension(720, 520));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Stars
        Random rng = new Random(42);
        int N = 120;
        starX = new float[N]; starY = new float[N];
        starR = new float[N]; starAlpha = new float[N];
        for (int i = 0; i < N; i++) {
            starX[i] = rng.nextFloat(); starY[i] = rng.nextFloat();
            starR[i] = 0.5f + rng.nextFloat() * 1.5f;
            starAlpha[i] = 0.3f + rng.nextFloat() * 0.7f;
        }

        // Root: left space panel + right card panel, split 55/45
        JPanel root = new JPanel(new GridLayout(1, 2, 0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                paintSpace((Graphics2D) g, getWidth(), getHeight());
            }
        };
        root.setOpaque(true);

        // Left: space branding panel (transparent, drawn by root)
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setOpaque(false);

        // Logo top-left
        JPanel logoArea = new JPanel(new FlowLayout(FlowLayout.LEFT, 24, 20));
        logoArea.setOpaque(false);
        logoArea.add(buildLogoWidget());
        leftPanel.add(logoArea, BorderLayout.NORTH);

        // Hero text bottom-left
        JPanel heroArea = new JPanel();
        heroArea.setLayout(new BoxLayout(heroArea, BoxLayout.Y_AXIS));
        heroArea.setOpaque(false);
        heroArea.setBorder(BorderFactory.createEmptyBorder(0, 28, 48, 20));

        JLabel tagline = new JLabel("SIGN IN TO YOUR");
        tagline.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tagline.setForeground(new Color(255, 255, 255, 160));
        tagline.setAlignmentX(LEFT_ALIGNMENT);

        JLabel headline = new JLabel("<html>CAMPUS<br>ADVENTURE!</html>");
        headline.setFont(new Font("SansSerif", Font.BOLD, 34));
        headline.setForeground(Color.WHITE);
        headline.setAlignmentX(LEFT_ALIGNMENT);

        JLabel caption = new JLabel("Your campus, connected.");
        caption.setFont(new Font("SansSerif", Font.PLAIN, 12));
        caption.setForeground(new Color(255, 255, 255, 100));
        caption.setAlignmentX(LEFT_ALIGNMENT);

        heroArea.add(tagline);
        heroArea.add(Box.createVerticalStrut(6));
        heroArea.add(headline);
        heroArea.add(Box.createVerticalStrut(10));
        heroArea.add(caption);
        leftPanel.add(heroArea, BorderLayout.SOUTH);

        root.add(leftPanel);

        // Right: glass card, centred
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);
        rightPanel.setBorder(BorderFactory.createEmptyBorder(20, 12, 20, 24));
        cardHolder = buildLoginCard();
        rightPanel.add(cardHolder, new GridBagConstraints());
        root.add(rightPanel);

        setContentPane(root);

        // Animation
        animTimer = new Timer(40, e -> {
            phase1 += 0.018f; phase2 += 0.022f;
            for (int i = 0; i < starAlpha.length; i++) {
                starAlpha[i] += (float)(Math.random() * 0.05 - 0.025);
                starAlpha[i] = Math.max(0.05f, Math.min(1f, starAlpha[i]));
            }
            root.repaint();
        });
        animTimer.start();
    }

    // \u2500\u2500 Logo widget \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    private JPanel buildLogoWidget() {
        JPanel p = new JPanel(null) {
            @Override public Dimension getPreferredSize() { return new Dimension(220, 36); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cx=16, cy=16, r=13;
                int[] xs=new int[6], ys=new int[6];
                for(int i=0;i<6;i++){
                    double a=Math.toRadians(60*i-30);
                    xs[i]=cx+(int)(r*Math.cos(a)); ys[i]=cy+(int)(r*Math.sin(a));
                }
                g2.setPaint(new GradientPaint(cx-r,cy-r,new Color(0x6366F1),cx+r,cy+r,new Color(0xA78BFA)));
                g2.fillPolygon(xs,ys,6);
                g2.setColor(new Color(196,181,253,120));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawPolygon(xs,ys,6);
                g2.setColor(Color.WHITE);
                Font bf = new Font("SansSerif",Font.BOLD,13);
                g2.setFont(bf);
                FontMetrics fm=g2.getFontMetrics();
                g2.drawString("C",cx-fm.stringWidth("C")/2,cy+fm.getAscent()/2-1);
                g2.setFont(new Font("SansSerif",Font.BOLD,15));
                fm = g2.getFontMetrics();
                g2.setColor(Color.WHITE); g2.drawString("Campus",38,22);
                int cw = fm.stringWidth("Campus");
                g2.setColor(new Color(0xC4B5FD)); g2.drawString("Share",38+cw+2,22);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        return p;
    }

    // \u2500\u2500 Space background \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    private void paintSpace(Graphics2D g2, int w, int h) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(0,0,Theme.SPACE_1,w,h,Theme.SPACE_4));
        g2.fillRect(0,0,w,h);
        for (int i=0;i<starX.length;i++){
            g2.setColor(new Color(1f,1f,1f,starAlpha[i]));
            float x=starX[i]*w, sy=starY[i]*h, r=starR[i];
            g2.fill(new Ellipse2D.Float(x-r,sy-r,r*2,r*2));
        }
        float p1y=(float)(Math.sin(phase1)*12);
        float p2y=(float)(Math.sin(phase2)*8);
        float p3y=(float)(Math.sin(phase1+1f)*10);
        int lw = w/2; // planets stay on left half
        paintPlanet(g2,(int)(lw*0.38),(int)(h*0.2+p1y),   (int)(lw*0.35), new Color(0x6B21C8),new Color(0x2E0B5E),new Color(0x110435));
        paintPlanet(g2,(int)(lw*0.6), (int)(h*0.55+p2y),  (int)(lw*0.17), new Color(0x9B59B6),new Color(0x4A235A),new Color(0x1E0B2E));
        paintPlanet(g2,(int)(lw*0.12),(int)(h*0.75+p3y),  (int)(lw*0.18), new Color(0x3B82F6),new Color(0x1D4ED8),new Color(0x0D2563));
    }

    private void paintPlanet(Graphics2D g2, int cx, int cy, int r, Color c1, Color c2, Color c3) {
        g2.setPaint(new RadialGradientPaint(
            new Point2D.Float(cx-r*0.3f,cy-r*0.3f),r,
            new float[]{0f,0.6f,1f}, new Color[]{c1,c2,c3}));
        g2.fillOval(cx-r,cy-r,r*2,r*2);
        g2.setColor(new Color(0,0,0,50));
        g2.drawOval(cx-r,cy-r,r*2,r*2);
        // Shimmer
        g2.setPaint(new RadialGradientPaint(
            new Point2D.Float(cx-r*0.4f,cy-r*0.4f),r*0.6f,
            new float[]{0f,1f}, new Color[]{new Color(255,255,255,60),new Color(255,255,255,0)}));
        g2.fillOval((int)(cx-r*0.75),(int)(cy-r*0.75),(int)(r*1.1),(int)(r*1.1));
    }

    // \u2500\u2500 Glass card builder \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    private JPanel buildLoginCard() {
        JPanel card = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(15,8,50,210));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),20,20);
                g2.setColor(new Color(140,100,255,60));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,20,20);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(340, 440));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx=0; gc.fill=GridBagConstraints.HORIZONTAL; gc.weightx=1;
        gc.insets=new Insets(0,28,0,28);

        // Title
        JLabel title = new JLabel("Welcome Back");
        title.setFont(new Font("SansSerif",Font.BOLD,22)); title.setForeground(Color.WHITE);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        gc.gridy=0; gc.insets=new Insets(28,28,4,28);
        card.add(title, gc);

        JLabel sub = new JLabel("Sign in to CampusShare");
        sub.setFont(Theme.font(Font.PLAIN,12)); sub.setForeground(new Color(255,255,255,140));
        sub.setHorizontalAlignment(SwingConstants.CENTER);
        gc.gridy=1; gc.insets=new Insets(0,28,20,28);
        card.add(sub, gc);

        // Email
        gc.insets=new Insets(0,28,6,28);
        JLabel emailLbl = makeFieldLabel("Email Address");
        gc.gridy=2; card.add(emailLbl, gc);
        emailField = makeTextField("student@campus.edu");
        gc.gridy=3; gc.insets=new Insets(0,28,14,28);
        card.add(emailField, gc);

        // Password
        gc.insets=new Insets(0,28,6,28);
        JLabel passLbl = makeFieldLabel("Password");
        gc.gridy=4; card.add(passLbl, gc);
        passField = makePasswordField("Enter your password");
        gc.gridy=5; gc.insets=new Insets(0,28,8,28);
        card.add(passField, gc);

        // Error label
        errorLabel = new JLabel(" ");
        errorLabel.setFont(Theme.font(Font.PLAIN,11));
        errorLabel.setForeground(new Color(252,165,165));
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gc.gridy=6; gc.insets=new Insets(0,28,6,28);
        card.add(errorLabel, gc);

        // Login button
        JButton loginBtn = buildPrimaryButton("Sign In");
        loginBtn.addActionListener(e -> doLogin());
        passField.addActionListener(e -> doLogin());
        gc.gridy=7; gc.insets=new Insets(0,28,14,28);
        card.add(loginBtn, gc);

        // Toggle to sign up
        JPanel toggle = new JPanel(new FlowLayout(FlowLayout.CENTER,4,0));
        toggle.setOpaque(false);
        JLabel noAcct = new JLabel("Don't have an account?");
        noAcct.setFont(Theme.font(Font.PLAIN,11)); noAcct.setForeground(new Color(255,255,255,140));
        JLabel signUpLink = new JLabel("Sign Up");
        signUpLink.setFont(Theme.font(Font.BOLD,11)); signUpLink.setForeground(new Color(0xA78BFA));
        signUpLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        signUpLink.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e){ switchToSignUp(); }
        });
        toggle.add(noAcct); toggle.add(signUpLink);
        gc.gridy=8; gc.insets=new Insets(0,28,24,28);
        card.add(toggle, gc);

        return card;
    }

    private JPanel buildSignUpCard() {
        JPanel card = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(15,8,50,210));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),20,20);
                g2.setColor(new Color(140,100,255,60));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,20,20);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(340, 500));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx=0; gc.fill=GridBagConstraints.HORIZONTAL; gc.weightx=1;

        JLabel title = new JLabel("Create Account");
        title.setFont(new Font("SansSerif",Font.BOLD,22)); title.setForeground(Color.WHITE);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        gc.gridy=0; gc.insets=new Insets(28,28,4,28); card.add(title,gc);

        JLabel sub = new JLabel("Join CampusShare today");
        sub.setFont(Theme.font(Font.PLAIN,12)); sub.setForeground(new Color(255,255,255,140));
        sub.setHorizontalAlignment(SwingConstants.CENTER);
        gc.gridy=1; gc.insets=new Insets(0,28,18,28); card.add(sub,gc);

        gc.insets=new Insets(0,28,6,28);
        gc.gridy=2; card.add(makeFieldLabel("Full Name"),gc);
        nameField = makeTextField("Your full name");
        gc.gridy=3; gc.insets=new Insets(0,28,12,28); card.add(nameField,gc);

        gc.insets=new Insets(0,28,6,28);
        gc.gridy=4; card.add(makeFieldLabel("Email Address"),gc);
        emailField = makeTextField("student@campus.edu");
        gc.gridy=5; gc.insets=new Insets(0,28,12,28); card.add(emailField,gc);

        gc.insets=new Insets(0,28,6,28);
        gc.gridy=6; card.add(makeFieldLabel("Department"),gc);
        deptBox = new JComboBox<>(new String[]{"CSE","EEE","BBA","Law","English","Mathematics"});
        styleCombo(deptBox);
        gc.gridy=7; gc.insets=new Insets(0,28,12,28); card.add(deptBox,gc);

        gc.insets=new Insets(0,28,6,28);
        gc.gridy=8; card.add(makeFieldLabel("Password"),gc);
        passField = makePasswordField("Choose a password");
        gc.gridy=9; gc.insets=new Insets(0,28,8,28); card.add(passField,gc);

        errorLabel = new JLabel(" ");
        errorLabel.setFont(Theme.font(Font.PLAIN,11));
        errorLabel.setForeground(new Color(252,165,165));
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gc.gridy=10; gc.insets=new Insets(0,28,6,28); card.add(errorLabel,gc);

        JButton btn = buildPrimaryButton("Create Account");
        btn.addActionListener(e -> doSignUp());
        gc.gridy=11; gc.insets=new Insets(0,28,12,28); card.add(btn,gc);

        JPanel toggle = new JPanel(new FlowLayout(FlowLayout.CENTER,4,0));
        toggle.setOpaque(false);
        JLabel has = new JLabel("Already have an account?");
        has.setFont(Theme.font(Font.PLAIN,11)); has.setForeground(new Color(255,255,255,140));
        JLabel signInLink = new JLabel("Sign In");
        signInLink.setFont(Theme.font(Font.BOLD,11)); signInLink.setForeground(new Color(0xA78BFA));
        signInLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        signInLink.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e){ switchToLogin(); }
        });
        toggle.add(has); toggle.add(signInLink);
        gc.gridy=12; gc.insets=new Insets(0,28,22,28); card.add(toggle,gc);

        return card;
    }

    // \u2500\u2500 Field helpers \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    private JLabel makeFieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.BOLD,11));
        l.setForeground(new Color(255,255,255,180));
        return l;
    }

    private JTextField makeTextField(String placeholder) {
        JTextField f = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setColor(new Color(255,255,255,18));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                g2.dispose(); super.paintComponent(g);
                if(getText().isEmpty()&&!isFocusOwner()){
                    g2=( Graphics2D)g.create();
                    g2.setColor(new Color(255,255,255,60));
                    g2.setFont(getFont());
                    Insets ins=getInsets();
                    FontMetrics fm=g2.getFontMetrics();
                    g2.drawString(placeholder,ins.left+2,(getHeight()+fm.getAscent()-fm.getDescent())/2);
                    g2.dispose();
                }
            }
        };
        styleInput(f);
        return f;
    }

    private JPasswordField makePasswordField(String placeholder) {
        JPasswordField f = new JPasswordField() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setColor(new Color(255,255,255,18));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                g2.dispose(); super.paintComponent(g);
                if(getPassword().length==0&&!isFocusOwner()){
                    g2=(Graphics2D)g.create();
                    g2.setColor(new Color(255,255,255,60));
                    g2.setFont(getFont());
                    Insets ins=getInsets();
                    FontMetrics fm=g2.getFontMetrics();
                    g2.drawString(placeholder,ins.left+2,(getHeight()+fm.getAscent()-fm.getDescent())/2);
                    g2.dispose();
                }
            }
        };
        styleInput(f);
        f.setEchoChar('\u2022');
        return f;
    }

    private void styleInput(JTextField f) {
        f.setOpaque(false);
        f.setBorder(BorderFactory.createCompoundBorder(
            new RoundBorder(8,new Color(140,100,255,80),1),
            BorderFactory.createEmptyBorder(8,12,8,12)));
        f.setForeground(Color.WHITE); f.setCaretColor(Color.WHITE);
        f.setFont(Theme.font(Font.PLAIN,13));
        f.setPreferredSize(new Dimension(0,38));
    }

    private void styleCombo(JComboBox<String> c) {
        c.setBackground(new Color(30,20,80));
        c.setForeground(Color.WHITE);
        c.setFont(Theme.font(Font.PLAIN,13));
        c.setBorder(new RoundBorder(8,new Color(140,100,255,80),1));
        c.setPreferredSize(new Dimension(0,38));
    }

    private JButton buildPrimaryButton(String text) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isRollover() ? new Color(0x4F46E5) : new Color(0x6366F1);
                g2.setColor(bg); g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
                g2.setColor(Color.WHITE); g2.setFont(Theme.font(Font.BOLD,13));
                FontMetrics fm=g2.getFontMetrics();
                g2.drawString(getText(),(getWidth()-fm.stringWidth(getText()))/2,(getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        b.setPreferredSize(new Dimension(0,42));
        b.setOpaque(false); b.setContentAreaFilled(false);
        b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // \u2500\u2500 Actions \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    private void doLogin() {
        if (errorLabel == null) return;
        String email = emailField.getText().trim();
        String pass  = new String(passField.getPassword());
        if (email.isEmpty() || pass.isEmpty()) { errorLabel.setText("Please fill in all fields."); return; }
        if (isSignUp) {
            String name = nameField != null ? nameField.getText().trim() : "";
            if (name.isEmpty()) { errorLabel.setText("Please enter your name."); return; }
            boolean ok = com.campusshare.db.DAO.registerUser(name, email, pass, "STUDENT", "CSE", 1);
            if (!ok) { errorLabel.setText("Email already registered."); return; }
            DataStore.User u = DataStore.login(email, pass);
            if (u == null) { errorLabel.setText("Registration error."); return; }
            DataStore.setCurrentUser(u);
            DataStore.loadAll();
            if (animTimer != null) animTimer.stop();
            setVisible(false);
            dispose();
            SwingUtilities.invokeLater(onLoginSuccess);
            return;
        }
        DataStore.User u = DataStore.login(email, pass);
        if (u == null) { errorLabel.setText("Invalid email or password."); return; }
        DataStore.setCurrentUser(u);
        DataStore.loadAll();
        // Save for auto re-auth on reconnect
        CredentialStore.save(email, pass);
        if (animTimer != null) animTimer.stop();
        setVisible(false);
        dispose();
        SwingUtilities.invokeLater(onLoginSuccess);
    }

    private Timer animTimer;

    private void doSignUp() {
        String name  = nameField.getText().trim();
        String email = emailField.getText().trim();
        String pass  = new String(passField.getPassword());
        if (name.isEmpty()||email.isEmpty()||pass.isEmpty()) {
            errorLabel.setText("Please fill in all fields."); return;
        }
        if (pass.length() < 6) {
            errorLabel.setText("Password must be at least 6 characters."); return;
        }
        String dept = deptBox != null ? (String)deptBox.getSelectedItem() : "CSE";
        boolean ok = com.campusshare.db.DAO.registerUser(name, email, pass, "STUDENT", dept, 1);
        if (!ok) { errorLabel.setText("Email already registered."); return; }
        DataStore.User u = DataStore.login(email, pass);
        if (u == null) { errorLabel.setText("Registration error. Try again."); return; }
        DataStore.setCurrentUser(u);
        DataStore.loadAll();
        if (animTimer != null) animTimer.stop();
        onLoginSuccess.run();
    }

    private void switchToSignUp() {
        Container rp = ((JPanel)getContentPane()).getComponent(1).getParent();
        // Find right panel and swap card
        Component rightComp = ((JPanel)getContentPane()).getComponent(1);
        if (rightComp instanceof JPanel rPanel) {
            rPanel.removeAll();
            JPanel newCard = buildSignUpCard();
            rPanel.add(newCard, new GridBagConstraints());
            rPanel.revalidate(); rPanel.repaint();
        }
        setTitle("CampusShare \u2014 Sign Up");
    }

    private void switchToLogin() {
        Component rightComp = ((JPanel)getContentPane()).getComponent(1);
        if (rightComp instanceof JPanel rPanel) {
            rPanel.removeAll();
            JPanel newCard = buildLoginCard();
            rPanel.add(newCard, new GridBagConstraints());
            rPanel.revalidate(); rPanel.repaint();
        }
        setTitle("CampusShare \u2014 Sign In");
    }

    /** Returns the root panel so it can be embedded in another JFrame without creating a new window. */
    public JPanel getContentPanel() {
        return (JPanel) getContentPane();
    }
}