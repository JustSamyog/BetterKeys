package com.justsamyog.betterkeys.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;

public class GradientPanel extends JPanel {
    private static final Color TOP_LEFT = new Color(10, 10, 12);
    private static final Color BOTTOM_RIGHT = new Color(220, 220, 225);
    private static final Color VIGNETTE = new Color(0, 0, 0, 30);

    private static BufferedImage background;
    private static boolean backgroundLoaded;

    public GradientPanel() {
        setOpaque(false);
    }

    public GradientPanel(LayoutManager layout) {
        setOpaque(false);
        setLayout(layout);
    }

    private static BufferedImage loadBackground() {
        if (backgroundLoaded) return background;
        backgroundLoaded = true;
        try {
            URL res = GradientPanel.class.getResource("/background.jpg");
            if (res != null) {
                background = ImageIO.read(res);
            }
        } catch (Exception ignored) {
        }
        return background;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        int w = getWidth();
        int h = getHeight();
        BufferedImage bg = loadBackground();

        if (bg != null) {
            double scale = Math.max(w / (double) bg.getWidth(), h / (double) bg.getHeight());
            int dw = (int) Math.ceil(bg.getWidth() * scale);
            int dh = (int) Math.ceil(bg.getHeight() * scale);
            int dx = (w - dw) / 2;
            int dy = (h - dh) / 2;
            g2.drawImage(bg, dx, dy, dw, dh, null);

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.72f));
            g2.setColor(new Color(8, 8, 10));
            g2.fillRect(0, 0, w, h);
            g2.setComposite(AlphaComposite.SrcOver);

            GradientPaint topGlow = new GradientPaint(
                    0, 0, new Color(255, 255, 255, 26),
                    0, h * 0.35f, new Color(255, 255, 255, 0));
            g2.setPaint(topGlow);
            g2.fillRect(0, 0, w, (int) (h * 0.35f));

            GradientPaint bottom = new GradientPaint(
                    0, h * 0.6f, new Color(0, 0, 0, 0),
                    0, h, new Color(0, 0, 0, 90));
            g2.setPaint(bottom);
            g2.fillRect(0, (int) (h * 0.6f), w, h - (int) (h * 0.6f));
        } else {
            GradientPaint gp = new GradientPaint(0, 0, TOP_LEFT, w, h, BOTTOM_RIGHT);
            g2.setPaint(gp);
            g2.fillRect(0, 0, w, h);
            RadialGradientPaint vignette = new RadialGradientPaint(
                    new Point(w / 2, h / 2),
                    Math.max(w, h) * 0.7f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(0, 0, 0, 0), VIGNETTE});
            g2.setPaint(vignette);
            g2.fillRect(0, 0, w, h);
        }

        g2.setColor(new Color(60, 60, 60));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRect(0, 0, w - 1, h - 1);

        g2.dispose();
    }
}
