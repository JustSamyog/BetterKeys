package com.justsamyog.betterkeys.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class KeyboardPanel extends JPanel {
    private static final float UNIT = 54f;
    private static final int PAD = 22;
    private static final String FONT_NAME = "Comic Sans MS";
    private static final long TICK_MS = 20;

    public static final class KeyDef {
        public final String label;
        public final String vc;
        public final float x, y, w, h;
        double appear, press, hover;
        boolean hoverTarget;

        KeyDef(String label, String vc, float x, float y, float w, float h) {
            this.label = label;
            this.vc = vc;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        Rectangle2D.Double rect() {
            return new Rectangle2D.Double(PAD + x * UNIT, PAD + y * UNIT, w * UNIT, h * UNIT);
        }

        boolean isArrow() {
            return "VC_UP".equals(vc) || "VC_DOWN".equals(vc)
                    || "VC_LEFT".equals(vc) || "VC_RIGHT".equals(vc);
        }
    }

    private static final KeyDef[] KEY_LAYOUT = {
            new KeyDef("Esc", "VC_ESCAPE", 0, 0, 1, 1),
            new KeyDef("F1", "VC_F1", 2, 0, 1, 1), new KeyDef("F2", "VC_F2", 3, 0, 1, 1),
            new KeyDef("F3", "VC_F3", 4, 0, 1, 1), new KeyDef("F4", "VC_F4", 5, 0, 1, 1),
            new KeyDef("F5", "VC_F5", 6.5f, 0, 1, 1), new KeyDef("F6", "VC_F6", 7.5f, 0, 1, 1),
            new KeyDef("F7", "VC_F7", 8.5f, 0, 1, 1), new KeyDef("F8", "VC_F8", 9.5f, 0, 1, 1),
            new KeyDef("F9", "VC_F9", 11, 0, 1, 1), new KeyDef("F10", "VC_F10", 12, 0, 1, 1),
            new KeyDef("F11", "VC_F11", 13, 0, 1, 1), new KeyDef("F12", "VC_F12", 14, 0, 1, 1),
            new KeyDef("`", "VC_BACKQUOTE", 0, 1, 1, 1), new KeyDef("1", "VC_1", 1, 1, 1, 1),
            new KeyDef("2", "VC_2", 2, 1, 1, 1), new KeyDef("3", "VC_3", 3, 1, 1, 1),
            new KeyDef("4", "VC_4", 4, 1, 1, 1), new KeyDef("5", "VC_5", 5, 1, 1, 1),
            new KeyDef("6", "VC_6", 6, 1, 1, 1), new KeyDef("7", "VC_7", 7, 1, 1, 1),
            new KeyDef("8", "VC_8", 8, 1, 1, 1), new KeyDef("9", "VC_9", 9, 1, 1, 1),
            new KeyDef("0", "VC_0", 10, 1, 1, 1), new KeyDef("-", "VC_MINUS", 11, 1, 1, 1),
            new KeyDef("=", "VC_EQUALS", 12, 1, 1, 1), new KeyDef("Backspace", "VC_BACKSPACE", 13, 1, 2, 1),
            new KeyDef("Tab", "VC_TAB", 0, 2, 1.5f, 1),
            new KeyDef("Q", "VC_Q", 1.5f, 2, 1, 1), new KeyDef("W", "VC_W", 2.5f, 2, 1, 1),
            new KeyDef("E", "VC_E", 3.5f, 2, 1, 1), new KeyDef("R", "VC_R", 4.5f, 2, 1, 1),
            new KeyDef("T", "VC_T", 5.5f, 2, 1, 1), new KeyDef("Y", "VC_Y", 6.5f, 2, 1, 1),
            new KeyDef("U", "VC_U", 7.5f, 2, 1, 1), new KeyDef("I", "VC_I", 8.5f, 2, 1, 1),
            new KeyDef("O", "VC_O", 9.5f, 2, 1, 1), new KeyDef("P", "VC_P", 10.5f, 2, 1, 1),
            new KeyDef("[", "VC_OPEN_BRACKET", 11.5f, 2, 1, 1),
            new KeyDef("]", "VC_CLOSE_BRACKET", 12.5f, 2, 1, 1),
            new KeyDef("\\", "VC_BACK_SLASH", 13.5f, 2, 1.5f, 1),
            new KeyDef("Del", "VC_DELETE", 15.5f, 2, 1, 1), new KeyDef("End", "VC_END", 16.5f, 2, 1, 1),
            new KeyDef("PgUp", "VC_PAGE_UP", 17.5f, 2, 1, 1),
            new KeyDef("Caps", "VC_CAPS_LOCK", 0, 3, 1.75f, 1),
            new KeyDef("A", "VC_A", 1.75f, 3, 1, 1), new KeyDef("S", "VC_S", 2.75f, 3, 1, 1),
            new KeyDef("D", "VC_D", 3.75f, 3, 1, 1), new KeyDef("F", "VC_F", 4.75f, 3, 1, 1),
            new KeyDef("G", "VC_G", 5.75f, 3, 1, 1), new KeyDef("H", "VC_H", 6.75f, 3, 1, 1),
            new KeyDef("J", "VC_J", 7.75f, 3, 1, 1), new KeyDef("K", "VC_K", 8.75f, 3, 1, 1),
            new KeyDef("L", "VC_L", 9.75f, 3, 1, 1),
            new KeyDef(";", "VC_SEMICOLON", 10.75f, 3, 1, 1),
            new KeyDef("'", "VC_QUOTE", 11.75f, 3, 1, 1),
            new KeyDef("Enter", "VC_ENTER", 12.75f, 3, 2.25f, 1),
            new KeyDef("Shift", "VC_SHIFT", 0, 4, 2.25f, 1),
            new KeyDef("Z", "VC_Z", 2.25f, 4, 1, 1), new KeyDef("X", "VC_X", 3.25f, 4, 1, 1),
            new KeyDef("C", "VC_C", 4.25f, 4, 1, 1), new KeyDef("V", "VC_V", 5.25f, 4, 1, 1),
            new KeyDef("B", "VC_B", 6.25f, 4, 1, 1), new KeyDef("N", "VC_N", 7.25f, 4, 1, 1),
            new KeyDef("M", "VC_M", 8.25f, 4, 1, 1),
            new KeyDef(",", "VC_COMMA", 9.25f, 4, 1, 1),
            new KeyDef(".", "VC_PERIOD", 10.25f, 4, 1, 1),
            new KeyDef("/", "VC_SLASH", 11.25f, 4, 1, 1),
            new KeyDef("Shift", "VC_SHIFT", 12.25f, 4, 2.75f, 1),
            new KeyDef("Ctrl", "VC_CONTROL", 0, 5, 1.25f, 1),
            new KeyDef("Win", "VC_META", 1.25f, 5, 1.25f, 1),
            new KeyDef("Alt", "VC_ALT", 2.5f, 5, 1.25f, 1),
            new KeyDef("Space", "VC_SPACE", 3.75f, 5, 6.25f, 1),
            new KeyDef("Alt", "VC_ALT", 10, 5, 1.25f, 1),
            new KeyDef("Win", "VC_META", 11.25f, 5, 1.25f, 1),
            new KeyDef("Menu", "VC_CONTEXT_MENU", 12.5f, 5, 1.25f, 1),
            new KeyDef("Ctrl", "VC_CONTROL", 13.75f, 5, 1.25f, 1),
            new KeyDef("Left", "VC_LEFT", 15.5f, 5, 1, 1),
            new KeyDef("Down", "VC_DOWN", 16.5f, 5, 1, 1),
            new KeyDef("Right", "VC_RIGHT", 17.5f, 5, 1, 1),
    };

    private final List<KeyDef> keys = new ArrayList<>();
    private Map<String, String> keyMap;
    private KeyDef selected;
    private Consumer<KeyDef> onSelect;
    private long start;
    private final Timer timer;

    public KeyboardPanel() {
        setOpaque(true);
        setBackground(new Color(14, 14, 16));
        setPreferredSize(new Dimension(
                (int) (PAD * 2 + 18.5f * UNIT),
                (int) (PAD * 2 + 6 * UNIT)));
        setFont(new Font(FONT_NAME, Font.BOLD, 14));
        buildLayout();

        timer = new Timer((int) TICK_MS, e -> tick());
        timer.setCoalesce(true);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                KeyDef k = hit(e.getPoint());
                if (k == null) return;
                k.press = 1;
                selected = k;
                if (onSelect != null) onSelect.accept(k);
                wake();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                clearHover();
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                KeyDef k = hit(e.getPoint());
                boolean changed = false;
                for (KeyDef def : keys) {
                    boolean target = def == k;
                    if (def.hoverTarget != target) {
                        def.hoverTarget = target;
                        changed = true;
                    }
                }
                setCursor(Cursor.getPredefinedCursor(
                        k != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                if (changed) wake();
            }
        });
        setToolTipText("");
    }

    private void buildLayout() {
        for (KeyDef k : KEY_LAYOUT) {
            keys.add(new KeyDef(k.label, k.vc, k.x, k.y, k.w, k.h));
        }
    }

    public void setKeyMap(Map<String, String> keyMap) {
        this.keyMap = keyMap;
        repaint();
    }

    public void setOnSelect(Consumer<KeyDef> onSelect) {
        this.onSelect = onSelect;
    }

    public KeyDef getSelected() {
        return selected;
    }

    public List<KeyDef> getKeys() {
        return keys;
    }

    public void flash(String vc) {
        if (vc == null) return;
        boolean any = false;
        for (KeyDef k : keys) {
            if (k.vc.equals(vc)) {
                k.press = 1;
                any = true;
            }
        }
        if (any) wake();
    }

    private boolean isMapped(String vc) {
        if (keyMap == null) return false;
        String s = keyMap.get(vc);
        return s != null && !"RANDOM".equals(s);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        start = System.currentTimeMillis();
        for (KeyDef k : keys) {
            k.appear = 0;
        }
        wake();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    private void wake() {
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    private void clearHover() {
        boolean changed = false;
        for (KeyDef k : keys) {
            if (k.hoverTarget) {
                k.hoverTarget = false;
                changed = true;
            }
        }
        if (changed) wake();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long t = now - start;
        boolean busy = false;

        for (KeyDef k : keys) {
            double delay = (k.x + k.y * 2) * 26;
            double p = Math.max(0, Math.min(1, (t - delay) / 300.0));
            k.appear = 1 - Math.pow(1 - p, 3);
            if (p < 1) busy = true;

            double target = k.hoverTarget ? 1 : 0;
            k.hover += (target - k.hover) * 0.3;
            if (Math.abs(target - k.hover) > 0.01) {
                busy = true;
            } else {
                k.hover = target;
            }

            if (k.press > 0.004) {
                k.press *= 0.86;
                busy = true;
            } else {
                k.press = 0;
            }
        }
        if (selected != null) busy = true;

        repaint();
        if (!busy) {
            timer.stop();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        long now = System.currentTimeMillis();
        for (KeyDef k : keys) {
            drawKey(g2, k, now);
        }
        g2.dispose();
    }

    private void drawKey(Graphics2D g2, KeyDef k, long now) {
        if (k.appear <= 0.01) return;

        Rectangle2D.Double r = k.rect();
        double lift = k.hover * 3;
        double inset = k.press * 5;
        float kx = (float) (r.x + inset);
        float ky = (float) (r.y - lift + inset + (1 - k.appear) * 16);
        float kw = (float) (r.width - inset * 2);
        float kh = (float) (r.height - inset * 2);
        float arc = 10;
        boolean isSel = k == selected;
        float alpha = (float) k.appear;
        RoundRectangle2D.Float rr = new RoundRectangle2D.Float(kx, ky, kw, kh, arc, arc);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                alpha * (0.35f + 0.25f * (float) k.hover)));
        g2.setColor(new Color(6, 6, 8));
        g2.fill(new RoundRectangle2D.Float(kx + 1, ky + 3 + (float) (k.hover * 2),
                kw, kh, arc, arc));

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        GradientPaint body = isSel
                ? new GradientPaint(kx, ky, new Color(44, 44, 50),
                kx, ky + kh, new Color(14, 14, 16))
                : new GradientPaint(kx, ky, new Color(250, 250, 252),
                kx, ky + kh, new Color(220, 220, 226));
        g2.setPaint(body);
        g2.fill(rr);

        if (k.press > 0.01) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    alpha * (float) k.press * 0.55f));
            g2.setColor(new Color(120, 120, 128));
            g2.fill(rr);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    alpha * (float) k.press * 0.8f));
            g2.setColor(new Color(245, 245, 247));
            g2.setStroke(new BasicStroke(2f + 2f * (float) k.press));
            g2.draw(new RoundRectangle2D.Float(kx - 2, ky - 2, kw + 4, kh + 4,
                    arc + 2, arc + 2));
        }

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        if (isSel) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 300.0);
            g2.setColor(new Color(245, 245, 247, (int) (90 + 150 * pulse)));
            g2.setStroke(new BasicStroke(2f + 1.5f * pulse));
            g2.draw(new RoundRectangle2D.Float(kx - 1, ky - 1, kw + 2, kh + 2,
                    arc + 1, arc + 1));
        } else {
            if (k.hover > 0.05) {
                g2.setColor(new Color(245, 245, 247, (int) (90 * k.hover)));
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new RoundRectangle2D.Float(kx - 1, ky - 1, kw + 2, kh + 2,
                        arc + 1, arc + 1));
            }
            g2.setColor(new Color(16, 16, 20));
            g2.setStroke(new BasicStroke(1.4f));
            g2.draw(rr);
        }

        if (isMapped(k.vc)) {
            g2.setColor(isSel ? new Color(245, 245, 247) : new Color(34, 34, 38));
            g2.fill(new Ellipse2D.Float(kx + kw - 11, ky + 7, 5, 5));
        }

        Color text = isSel ? new Color(245, 245, 247) : new Color(22, 22, 26);
        g2.setColor(text);
        if (k.isArrow()) {
            drawArrow(g2, k.vc, kx, ky, kw, kh);
        } else {
            Font f = fitFont(g2, k, kw);
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (int) (kx + (kw - fm.stringWidth(k.label)) / 2);
            int ty = (int) (ky + (kh - fm.getAscent() - fm.getDescent()) / 2) + fm.getAscent();
            g2.drawString(k.label, tx, ty);
        }
    }

    private Font fitFont(Graphics2D g2, KeyDef k, float kw) {
        int size = k.h < 0.9f ? 11 : 14;
        Font f = new Font(FONT_NAME, Font.BOLD, size);
        while (size > 8) {
            FontMetrics fm = g2.getFontMetrics(f);
            if (fm.stringWidth(k.label) <= kw - 12) break;
            size--;
            f = new Font(FONT_NAME, Font.BOLD, size);
        }
        return f;
    }

    private void drawArrow(Graphics2D g2, String vc, float kx, float ky, float kw, float kh) {
        double cx = kx + kw / 2;
        double cy = ky + kh / 2;
        double s = Math.min(kw, kh) * 0.26;
        Polygon p = new Polygon();
        switch (vc) {
            case "VC_UP" -> {
                p.addPoint((int) cx, (int) (cy - s));
                p.addPoint((int) (cx - s), (int) (cy + s));
                p.addPoint((int) (cx + s), (int) (cy + s));
            }
            case "VC_DOWN" -> {
                p.addPoint((int) cx, (int) (cy + s));
                p.addPoint((int) (cx - s), (int) (cy - s));
                p.addPoint((int) (cx + s), (int) (cy - s));
            }
            case "VC_LEFT" -> {
                p.addPoint((int) (cx - s), (int) cy);
                p.addPoint((int) (cx + s), (int) (cy - s));
                p.addPoint((int) (cx + s), (int) (cy + s));
            }
            default -> {
                p.addPoint((int) (cx + s), (int) cy);
                p.addPoint((int) (cx - s), (int) (cy - s));
                p.addPoint((int) (cx - s), (int) (cy + s));
            }
        }
        g2.fillPolygon(p);
    }

    private KeyDef hit(Point p) {
        for (KeyDef k : keys) {
            if (k.rect().contains(p)) return k;
        }
        return null;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        KeyDef k = hit(e.getPoint());
        if (k == null) return null;
        String s = keyMap == null ? "RANDOM" : keyMap.getOrDefault(k.vc, "RANDOM");
        String pretty = "RANDOM".equals(s) ? "random variation" : s;
        return "<html><b>" + k.label + "</b> &nbsp; " + k.vc + "<br>" + pretty + "</html>";
    }
}
