package com.campusshare.ui;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;
import javax.swing.border.*;

public class Theme {

    // Login bg (kept for LoginWindow)
    public static final Color SPACE_1 = new Color(0x050510);
    public static final Color SPACE_2 = new Color(0x0A0824);
    public static final Color SPACE_3 = new Color(0x0F0F3A);
    public static final Color SPACE_4 = new Color(0x0D1B4A);

    // Brand
    public static final Color PRIMARY       = new Color(0x6366F1);
    public static final Color PRIMARY_DARK  = new Color(0x4F46E5);
    public static final Color PRIMARY_LIGHT = new Color(0x1E1B4B);
    public static final Color ACCENT_VIOLET = new Color(0x8B5CF6);
    public static final Color ACCENT_ROSE   = new Color(0xF43F5E);
    public static final Color ACCENT_AMBER  = new Color(0xF59E0B);
    public static final Color ACCENT_TEAL   = new Color(0x2DD4BF);
    public static final Color SUCCESS       = new Color(0x34D399);
    public static final Color WARNING       = new Color(0xFBBF24);
    public static final Color DANGER        = new Color(0xF87171);
    public static final Color PINK          = new Color(0xF472B6);

    // Bento block fills
    public static final Color BLOCK_INDIGO = new Color(0x6366F1);
    public static final Color BLOCK_VIOLET = new Color(0x8B5CF6);
    public static final Color BLOCK_ROSE   = new Color(0xF43F5E);
    public static final Color BLOCK_AMBER  = new Color(0xF59E0B);
    public static final Color BLOCK_TEAL   = new Color(0x0D9488);
    public static final Color BLOCK_LIME   = new Color(0x84CC16);
    public static final Color BLOCK_SKY    = new Color(0x38BDF8);
    public static final Color BLOCK_ORANGE = new Color(0xFB923C);

    // ── Dark theme backgrounds ────────────────────────────────────────────────
    public static final Color BG_APP     = new Color(0x0D0C1A);   // deepest bg
    public static final Color BG_CARD    = new Color(0x17152A);   // card surface
    public static final Color BG_SIDEBAR = new Color(0x0A0918);   // sidebar (slightly darker)
    public static final Color BG_INPUT   = new Color(0x1E1C30);   // input / pill bg
    public static final Color BG_CHIP    = new Color(0x252244);   // chip bg
    public static final Color BG_SUBTLE  = new Color(0x1A1829);   // subtle divider rows

    // ── Text (light on dark) ──────────────────────────────────────────────────
    public static final Color TEXT_DARK  = new Color(0xEEECFF);   // primary text
    public static final Color TEXT_MID   = new Color(0xC4C2E0);   // secondary text
    public static final Color TEXT_MUTE  = new Color(0x8884A8);   // muted text
    public static final Color TEXT_FAINT = new Color(0x504D6E);   // very faint

    // ── Borders ───────────────────────────────────────────────────────────────
    public static final Color BORDER      = new Color(0x2A2645);
    public static final Color BORDER_DARK = new Color(0x1A1735);

    // Subject colors (same vivid colors work on dark bg)
    public static final Color[] SUBJECT_COLORS = {
        BLOCK_INDIGO, BLOCK_VIOLET, BLOCK_TEAL, BLOCK_AMBER, BLOCK_ROSE, BLOCK_SKY
    };

    // Fonts
    public static Font font(int style, float size) {
        return new Font("SansSerif", style, (int) size);
    }
    public static final Font FONT_DISPLAY = font(Font.BOLD, 28);
    public static final Font FONT_TITLE   = font(Font.BOLD, 20);
    public static final Font FONT_H2      = font(Font.BOLD, 14);
    public static final Font FONT_BODY    = font(Font.PLAIN, 13);
    public static final Font FONT_SMALL   = font(Font.PLAIN, 11);
    public static final Font FONT_TINY    = font(Font.PLAIN, 10);
    public static final Font FONT_LABEL   = font(Font.BOLD, 10);

    // Cards
    public static JPanel card() {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 16, 16);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        return p;
    }

    public static JPanel bentoBlock(Color fill) {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        return p;
    }

    public static JPanel gradientBlock(Color c1, Color c2) {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, c1, getWidth(), getHeight(), c2));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(new Color(255, 255, 255, 22));
                g2.fillOval(-20, -20, 120, 120);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        return p;
    }

    // Buttons
    public static JButton primaryButton(String text) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isPressed() ? PRIMARY_DARK
                         : getModel().isRollover() ? new Color(0x818CF8) : PRIMARY;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(Color.WHITE);
                g2.setFont(font(Font.BOLD, 12));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        b.setPreferredSize(new Dimension(140, 34));
        b.setOpaque(false); b.setContentAreaFilled(false);
        b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static JButton dangerButton(String text) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isRollover() ? new Color(0xEF4444) : DANGER;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(Color.WHITE);
                g2.setFont(font(Font.BOLD, 11));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        b.setPreferredSize(new Dimension(72, 28));
        b.setOpaque(false); b.setContentAreaFilled(false);
        b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static JButton ghostButton(String text, Color fg) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isRollover()
                    ? new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 35)
                    : new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 18);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 80));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 8, 8);
                g2.setColor(fg);
                g2.setFont(font(Font.BOLD, 11));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        b.setOpaque(false); b.setContentAreaFilled(false);
        b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // Input fields (dark style)
    public static JTextField styledField(String placeholder) {
        JTextField f = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(TEXT_FAINT);
                    g2.setFont(getFont());
                    Insets ins = getInsets();
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(placeholder, ins.left + 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                    g2.dispose();
                }
            }
        };
        f.setBackground(BG_INPUT);
        f.setBorder(BorderFactory.createCompoundBorder(
            new RoundBorder(8, BORDER, 1),
            BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        f.setFont(font(Font.PLAIN, 13));
        f.setForeground(TEXT_DARK);
        f.setCaretColor(PRIMARY);
        return f;
    }

    public static JPasswordField styledPassword(String placeholder) {
        JPasswordField f = new JPasswordField() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getPassword().length == 0 && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(TEXT_FAINT);
                    g2.setFont(getFont());
                    Insets ins = getInsets();
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(placeholder, ins.left + 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                    g2.dispose();
                }
            }
        };
        f.setBackground(BG_INPUT);
        f.setBorder(BorderFactory.createCompoundBorder(
            new RoundBorder(8, BORDER, 1),
            BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        f.setFont(font(Font.PLAIN, 13));
        f.setForeground(TEXT_DARK);
        f.setEchoChar('\u2022');
        return f;
    }

    // Avatar
    public static JPanel avatar(String initials, Color bg, int size) {
        return new JPanel() {
            { setOpaque(false); setPreferredSize(new Dimension(size, size)); setMinimumSize(new Dimension(size, size)); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillOval(0, 0, size, size);
                g2.setColor(Color.WHITE);
                g2.setFont(font(Font.BOLD, size * 0.36f));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(initials,
                    (size - fm.stringWidth(initials)) / 2,
                    (size + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
    }

    // Chip
    public static JLabel chip(String text, Color fg, Color bg) {
        JLabel l = new JLabel(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setFont(font(Font.BOLD, 10));
        l.setForeground(fg);
        l.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        l.setOpaque(false);
        return l;
    }

    public static JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(font(Font.BOLD, 14));
        l.setForeground(TEXT_DARK);
        return l;
    }

    public static JSeparator divider() {
        JSeparator s = new JSeparator();
        s.setForeground(BORDER);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return s;
    }
}
