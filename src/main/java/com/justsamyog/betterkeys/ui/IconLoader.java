package com.justsamyog.betterkeys.ui;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class IconLoader {
    private static final Map<String, Image> cache = new HashMap<>();

    public static Image get(String name, int size) {
        return draw(name, size, new Color(240, 240, 243), new Color(35, 35, 40));
    }

    public static ImageIcon getImageIcon(String name, int size) {
        return new ImageIcon(get(name, size));
    }

    public static Image getDark(String name, int size) {
        return draw(name, size, new Color(17, 17, 19), new Color(240, 240, 243));
    }

    public static Image getVector(String name, int size) {
        String key = "v_" + name.toLowerCase() + "_" + size;
        Image cached = cache.get(key);
        if (cached != null) return cached;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        drawIcon(g, name.toLowerCase(), size, new Color(240, 240, 243), new Color(35, 35, 40));
        g.dispose();
        cache.put(key, img);
        return img;
    }

    public static Image getTrayIcon(String name, int size) {
        String key = "tray_" + name.toLowerCase() + "_" + size;
        Image cached = cache.get(key);
        if (cached != null) return cached;
        Image img = loadTrayIcon(name.toLowerCase(), size);
        cache.put(key, img);
        return img;
    }

    private static Image loadTrayIcon(String name, int size) {
        BufferedImage raw = null;
        try (InputStream is = IconLoader.class.getResourceAsStream("/icons/tray/" + name + ".png")) {
            if (is != null) {
                raw = ImageIO.read(is);
            }
        } catch (Exception ignored) {
        }
        if (raw == null) {
            return getVector(name, size);
        }
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(raw, 0, 0, size, size, null);
        g.dispose();
        int ink = new Color(240, 240, 243).getRGB() & 0xFFFFFF;
        int[] px = img.getRGB(0, 0, size, size, null, 0, size);
        for (int i = 0; i < px.length; i++) {
            px[i] = (px[i] & 0xFF000000) | ink;
        }
        img.setRGB(0, 0, size, size, px, 0, size);
        return img;
    }

    private static Image draw(String name, int size, Color ink, Color detail) {
        String key = name.toLowerCase() + "_" + size + "_" + ink.getRGB();
        Image cached = cache.get(key);
        if (cached != null) return cached;

        BufferedImage img = loadBundled(name.toLowerCase(), size);
        if (img != null) {
            img = recolor(img, ink);
        } else {
            img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            drawIcon(g, name.toLowerCase(), size, ink, detail);
            g.dispose();
        }
        cache.put(key, img);
        return img;
    }

    private static BufferedImage loadBundled(String name, int size) {
        try (InputStream is = IconLoader.class.getResourceAsStream("/icons/" + name + ".png")) {
            if (is == null) return null;
            BufferedImage raw = ImageIO.read(is);
            if (raw == null) return null;
            if (raw.getWidth() == size && raw.getHeight() == size) return raw;
            BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(raw, 0, 0, size, size, null);
            g.dispose();
            return scaled;
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage recolor(BufferedImage src, Color ink) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a > 0) out.setRGB(x, y, (a << 24) | (ink.getRGB() & 0xFFFFFF));
            }
        }
        return out;
    }

    private static void drawIcon(Graphics2D g, String name, int s, Color ink, Color detail) {
        float sw = Math.max(1.6f, s / 9f);
        g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(ink);
        float p = s * 0.10f;
        float w = s - p * 2;

        switch (name) {
            case "keyboard" -> {
                float kx = p, ky = s * 0.24f, kw = w, kh = s * 0.52f;
                g.draw(new RoundRectShape(kx, ky, kw, kh, s * 0.1f, s * 0.1f).shape());
                float dotR = Math.max(1.2f, s * 0.05f);
                for (int r = 0; r < 2; r++) {
                    for (int c = 0; c < 5; c++) {
                        float cx = kx + kw * 0.12f + c * (kw * 0.76f) / 4f;
                        float cy = ky + kh * 0.32f + r * kh * 0.36f;
                        g.fill(new Ellipse2D.Float(cx - dotR, cy - dotR, dotR * 2, dotR * 2));
                    }
                }
            }
            case "plus", "add" -> {
                g.setStroke(new BasicStroke(sw * 1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                float c = s / 2f, len = s * 0.3f;
                g.drawLine(Math.round(c - len), Math.round(c), Math.round(c + len), Math.round(c));
                g.drawLine(Math.round(c), Math.round(c - len), Math.round(c), Math.round(c + len));
            }
            case "volume", "volume-2" -> drawSpeaker(g, s, p, ink, false);
            case "mute", "volume-off" -> drawSpeaker(g, s, p, ink, true);
            case "settings", "gear" -> {
                float cx = s / 2f, cy = s / 2f, rOut = s * 0.38f, rIn = s * 0.26f;
                Path2D gear = new Path2D.Float();
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16 - Math.PI / 2;
                    double rad = (i % 2 == 0) ? rOut : rIn;
                    double x = cx + Math.cos(a) * rad;
                    double y = cy + Math.sin(a) * rad;
                    if (i == 0) gear.moveTo(x, y); else gear.lineTo(x, y);
                }
                gear.closePath();
                g.fill(gear);
                g.setColor(detail);
                g.fill(new Ellipse2D.Float(cx - s * 0.12f, cy - s * 0.12f, s * 0.24f, s * 0.24f));
            }
            case "play" -> {
                Path2D tri = new Path2D.Float();
                tri.moveTo(s * 0.30f, s * 0.22f);
                tri.lineTo(s * 0.78f, s / 2f);
                tri.lineTo(s * 0.30f, s * 0.78f);
                tri.closePath();
                g.fill(tri);
            }
            case "folder" -> {
                Path2D fol = new Path2D.Float();
                float x0 = p, y0 = s * 0.26f, x1 = s - p, y1 = s * 0.80f;
                float tab = s * 0.12f;
                fol.moveTo(x0, y1);
                fol.lineTo(x0, y0 + tab);
                fol.lineTo(x0 + s * 0.24f, y0 + tab);
                fol.lineTo(x0 + s * 0.33f, y0);
                fol.lineTo(x1 - s * 0.05f, y0);
                fol.quadTo(x1, y0, x1, y0 + tab);
                fol.lineTo(x1, y1);
                fol.closePath();
                g.fill(fol);
            }
            case "close", "x" -> {
                g.setStroke(new BasicStroke(sw * 1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                float a = s * 0.22f, b = s - s * 0.22f;
                g.drawLine(Math.round(a), Math.round(a), Math.round(b), Math.round(b));
                g.drawLine(Math.round(b), Math.round(a), Math.round(a), Math.round(b));
            }
            case "check", "ok" -> {
                g.setStroke(new BasicStroke(sw * 1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(Math.round(s * 0.20f), Math.round(s * 0.54f),
                        Math.round(s * 0.43f), Math.round(s * 0.78f));
                g.drawLine(Math.round(s * 0.43f), Math.round(s * 0.78f),
                        Math.round(s * 0.80f), Math.round(s * 0.24f));
            }
            case "save" -> {
                g.fill(new RoundRectShape(p, p, w, w, s * 0.1f, s * 0.1f).shape());
                g.setColor(detail);
                g.fill(new RoundRectShape(p + s * 0.1f, p + s * 0.08f, s * 0.3f, s * 0.24f, 2, 2).shape());
                g.fill(new RoundRectShape(p + s * 0.1f, s * 0.54f, w - s * 0.2f, s * 0.3f, 3, 3).shape());
            }
            default -> {
                g.fill(new RoundRectShape(p, p, w, w, s * 0.14f, s * 0.14f).shape());
                g.setColor(detail);
                g.fill(new Ellipse2D.Float(s * 0.36f, s * 0.36f, s * 0.28f, s * 0.28f));
            }
        }
    }

    private static void drawSpeaker(Graphics2D g, int s, float p, Color ink, boolean crossed) {
        g.setColor(ink);
        Path2D sp = new Path2D.Float();
        float bx = p + s * 0.04f;
        float bw = s * 0.16f;
        float by = s * 0.40f;
        float bh = s * 0.22f;
        sp.moveTo(bx, by + bh * 0.28f);
        sp.lineTo(bx + bw, by + bh * 0.28f);
        sp.lineTo(bx + bw + s * 0.14f, by - s * 0.10f);
        sp.lineTo(bx + bw + s * 0.14f, by + bh + s * 0.10f);
        sp.lineTo(bx + bw, by + bh + s * 0.06f);
        sp.lineTo(bx, by + bh + s * 0.06f);
        sp.closePath();
        g.fill(sp);
        g.setStroke(new BasicStroke(Math.max(1.5f, s / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (crossed) {
            float x0 = s * 0.62f, x1 = s * 0.88f, y0 = s * 0.32f, y1 = s * 0.68f;
            g.drawLine(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1));
            g.drawLine(Math.round(x1), Math.round(y0), Math.round(x0), Math.round(y1));
        } else {
            for (int i = 1; i <= 2; i++) {
                float r = s * (0.18f + i * 0.14f);
                g.draw(new java.awt.geom.Arc2D.Float(
                        s * 0.60f - r / 2, s * 0.5f - r / 2, r, r,
                        -60, 120, java.awt.geom.Arc2D.OPEN));
            }
        }
    }

    private static final class RoundRectShape {
        final java.awt.geom.RoundRectangle2D.Float shape;

        RoundRectShape(float x, float y, float w, float h, float arcW, float arcH) {
            shape = new java.awt.geom.RoundRectangle2D.Float(x, y, w, h,
                    Math.max(arcW, 2), Math.max(arcH, 2));
        }

        java.awt.Shape shape() { return shape; }
    }

    public static Image fromResource(String path, int size) {
        return get(path, size);
    }
}
