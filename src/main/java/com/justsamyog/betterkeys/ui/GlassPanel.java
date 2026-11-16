package com.justsamyog.betterkeys.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class GlassPanel extends JPanel {
    private static final Color PANEL_BG_TOP = new Color(24, 24, 28, 215);
    private static final Color PANEL_BG_BOTTOM = new Color(14, 14, 17, 225);
    private static final Color BORDER_COLOR = new Color(255, 255, 255, 28);
    private static final Color TOP_HIGHLIGHT = new Color(255, 255, 255, 46);

    public GlassPanel() {
        setOpaque(false);
        setBorder(new EmptyBorder(8, 8, 8, 8));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int w = getWidth();
        int h = getHeight();
        int arc = 14;

        g2.setColor(new Color(0, 0, 0, 70));
        g2.fill(new RoundRectangle2D.Float(3, 5, w - 1, h - 1, arc, arc));

        GradientPaint body = new GradientPaint(
                0, 0, PANEL_BG_TOP,
                0, h, PANEL_BG_BOTTOM);
        g2.setPaint(body);
        g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, arc, arc));

        g2.setColor(new Color(255, 255, 255, 10));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Float(1, 1, w - 3, h - 3, arc - 2, arc - 2));

        g2.setColor(BORDER_COLOR);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, arc, arc));

        g2.setColor(TOP_HIGHLIGHT);
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(16, 1, w - 16, 1);

        g2.dispose();
    }
}
