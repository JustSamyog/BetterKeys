package com.justsamyog.betterkeys.ui;
import com.justsamyog.betterkeys.config.AppConfig;
import com.justsamyog.betterkeys.audio.AudioEngine;
import com.justsamyog.betterkeys.input.GlobalInputListener;
import com.justsamyog.betterkeys.model.Soundpack;
import com.justsamyog.betterkeys.model.SoundpackManager;
import com.justsamyog.betterkeys.audio.WavGenerator;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatClientProperties;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.sound.sampled.Mixer;

public class TrayApp {
    private final AppConfig config = AppConfig.load();
    private final SoundpackManager soundpackManager = new SoundpackManager();
    private final AudioEngine audioEngine = new AudioEngine();
    private final GlobalInputListener inputListener = new GlobalInputListener(audioEngine, null);

    private JFrame mainFrame;
    private TrayIcon trayIcon;
    private JPopupMenu trayMenu;
    private SystemTray systemTray;
    private JComboBox<Soundpack> soundpackCombo;
    private JComboBox<String> speakerCombo;
    private JSlider volumeSlider;
    private JButton muteButton;
    private JMenuItem muteMenuItem;
    private JMenuItem packMenuItem;
    private boolean updatingCombo;
    private java.util.List<String> lastLoadFailures = new java.util.ArrayList<>();
    private String packNotice = "";

    public void start() {
        List<Soundpack> packs = soundpackManager.loadAll();
        Soundpack saved = packs.stream()
                .filter(p -> p.getName().equalsIgnoreCase(config.getSelectedSoundpack()))
                .findFirst()
                .orElse(packs.get(0));
        soundpackManager.setActive(saved);
        config.setSelectedSoundpack(saved.getName());

        loadSoundsForPack(saved);

        inputListener.setSoundpack(soundpackManager.getActive());
        inputListener.setEnabled(!config.isMute());
        audioEngine.setMasterVolume(config.getVolume());
        audioEngine.setMuted(config.isMute());
        audioEngine.setSelectedLine(config.getSelectedAudioLine());

        boolean hookOk = GlobalInputListener.registerHook();
        if (hookOk) {
            GlobalInputListener.addListeners(inputListener, inputListener);
        }

        buildMainFrame();
        buildSystemTray();

        showTray(hookOk);
        showMainFrame();
        if (config.isStartOnBoot()) {
            ensureStartOnBoot(true);
        }
        config.save();
    }

    private void loadSoundsForPack(Soundpack pack) {
        audioEngine.stopAll();
        java.util.Map<String, byte[]> synthetic = WavGenerator.generateDefaultPack();
        java.util.List<String> failures = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, String> e : pack.getSounds().entrySet()) {
            String id = e.getKey();
            String path = e.getValue();
            if (path == null || pack.getSounds().containsKey(path)) {
                continue;
            }
            if (new File(path).exists()) {
                if (!audioEngine.load(id, path)) {
                    failures.add(id);
                }
            } else if (synthetic.containsKey(id)) {
                audioEngine.load(id, synthetic.get(id));
            } else {
                failures.add(id);
            }
        }
        lastLoadFailures = failures;
        packNotice = pack.getSoundPath("mouse_click") == null
                ? "Mouse clicks stay silent, this pack has no mouse click sound."
                : "";
    }

    private String problemSummary() {
        StringBuilder sb = new StringBuilder();
        int failed = lastLoadFailures.size();
        int issues = soundpackManager.getIssues().size();
        if (failed > 0) {
            sb.append(" ").append(failed).append(" sound(s) could not be played.");
        }
        if (issues > 0) {
            sb.append(" ").append(issues).append(" pack folder(s) need attention.");
        }
        if (!packNotice.isEmpty()) {
            sb.append(" ").append(packNotice);
        }
        return sb.toString();
    }

    private void buildSystemTray() {
        if (!SystemTray.isSupported()) {
            System.err.println("System tray not supported on this platform.");
            return;
        }
        systemTray = SystemTray.getSystemTray();

        Image image = createTrayIconImage();

        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(24, 24, 27));
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 66), 1),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        menu.setFocusable(true);

        JMenuItem showItem = styledMenuItem("Show Better Keys", "keyboard");
        showItem.addActionListener(e -> runMenuAction(this::showMainFrame));
        menu.add(showItem);

        muteMenuItem = styledMenuItem(config.isMute() ? "Unmute" : "Mute",
                config.isMute() ? "mute" : "volume");
        muteMenuItem.addActionListener(e -> runMenuAction(this::toggleMute));
        menu.add(muteMenuItem);

        menu.addSeparator();

        Soundpack active = soundpackManager.getActive();
        packMenuItem = styledMenuItem(active == null ? "No soundpack" : active.getName(), "check");
        packMenuItem.addActionListener(e -> runMenuAction(this::showMainFrame));
        menu.add(packMenuItem);

        menu.addSeparator();

        JMenuItem exitItem = styledMenuItem("Exit", "close");
        exitItem.addActionListener(e -> runMenuAction(this::shutdown));
        menu.add(exitItem);

        trayIcon = new TrayIcon(image, "Better Keys Desktop");
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> SwingUtilities.invokeLater(this::toggleMainFrame));
        trayIcon.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                showTrayMenu(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                showTrayMenu(e);
            }
        });

        trayMenu = menu;
        installTrayMenuDismiss();

        try {
            systemTray.add(trayIcon);
        } catch (AWTException ex) {
            System.err.println("Failed to add tray icon: " + ex.getMessage());
        }
    }

    private void runMenuAction(Runnable action) {
        if (trayMenu != null) {
            trayMenu.setVisible(false);
        }
        SwingUtilities.invokeLater(action);
    }

    private void refreshTrayMenu() {
        if (muteMenuItem != null) {
            boolean muted = config.isMute();
            muteMenuItem.setText(muted ? "Unmute" : "Mute");
            muteMenuItem.setIcon(new ImageIcon(IconLoader.getTrayIcon(muted ? "mute" : "volume", trayIconPx(14))));
        }
        if (packMenuItem != null) {
            Soundpack active = soundpackManager.getActive();
            packMenuItem.setText(active == null ? "No soundpack" : active.getName());
        }
    }

    private void installTrayMenuDismiss() {
        inputListener.setGlobalMousePress(() -> SwingUtilities.invokeLater(this::dismissTrayMenuIfOutside));
        Toolkit.getDefaultToolkit().addAWTEventListener(
                ev -> {
                    if (ev.getID() == MouseEvent.MOUSE_PRESSED) {
                        dismissTrayMenuIfOutside();
                    }
                },
                AWTEvent.MOUSE_EVENT_MASK);
    }

    private void dismissTrayMenuIfOutside() {
        if (trayMenu == null || !trayMenu.isVisible()) return;
        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi == null) return;
        Point p = pi.getLocation();
        Rectangle menuBounds;
        try {
            menuBounds = new Rectangle(trayMenu.getLocationOnScreen(), trayMenu.getSize());
        } catch (IllegalComponentStateException ignored) {
            return;
        }
        Rectangle work = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Rectangle screen = screenContaining(new Rectangle(
                menuBounds.x + menuBounds.width / 2, menuBounds.y + menuBounds.height / 2, 1, 1));
        int taskbarTop = Math.min(work.y + work.height, screen.y + screen.height - 48);
        if (p.y >= taskbarTop
                && p.x >= menuBounds.x - 8 && p.x <= menuBounds.x + menuBounds.width + 8) {
            return;
        }
        if (!menuBounds.contains(p)) {
            trayMenu.setVisible(false);
        }
    }

    private void showTrayMenu(MouseEvent e) {
        if (!e.isPopupTrigger() || trayMenu == null) return;
        if (trayMenu.isVisible()) {
            trayMenu.setVisible(false);
            return;
        }
        Point click = null;
        try {
            Point c = e.getLocationOnScreen();
            if (c.x > 0 || c.y > 0) click = c;
        } catch (Throwable ignored) {
        }
        showTrayMenuAt(click);
    }

    public void showTrayMenuAt(Point click) {
        if (trayMenu == null) return;
        refreshTrayMenu();
        trayMenu.pack();
        Dimension sz = trayMenu.getPreferredSize();

        Rectangle work = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();

        boolean nearTaskbar = click != null
                && click.y > work.y + work.height - 64
                && click.x > work.x + work.width - 320;

        Rectangle screen;
        if (nearTaskbar) {
            screen = screenContaining(new Rectangle(click.x, click.y, 1, 1));
        } else {
            click = new Point(work.x + work.width - 12, work.y + work.height + 12);
            screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        }

        int right = click.x + 12;
        if (right > screen.x + screen.width - 6) right = screen.x + screen.width - 6;
        int x = right - sz.width;
        if (x < screen.x + 6) x = screen.x + 6;

        int y;
        if (click.y >= work.y + work.height - 2) {
            int taskbarTop = Math.min(work.y + work.height, screen.y + screen.height - 48);
            y = taskbarTop - sz.height;
            if (y < screen.y + 6) y = screen.y + 6;
        } else {
            y = click.y - sz.height - 6;
            if (y < screen.y + 6) y = click.y + 24;
        }
        if (y + sz.height > screen.y + screen.height - 6) y = screen.y + screen.height - sz.height - 6;

        trayMenu.setLocation(x, y);
        trayMenu.setVisible(true);
        trayMenu.requestFocusInWindow();
    }

    private Rectangle screenContaining(Rectangle r) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Point center = new Point(r.x + r.width / 2, r.y + r.height / 2);
        for (GraphicsDevice gd : ge.getScreenDevices()) {
            Rectangle b = gd.getDefaultConfiguration().getBounds();
            if (b.contains(center)) return b;
        }
        return ge.getDefaultScreenDevice().getDefaultConfiguration().getBounds();
    }

    private static int trayIconPx(int logical) {
        try {
            double s = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration()
                    .getDefaultTransform().getScaleX();
            return (int) Math.round(logical * Math.max(1.0, s));
        } catch (Throwable t) {
            return logical;
        }
    }

    private JMenuItem styledMenuItem(String text, String iconName) {
        JMenuItem item = new JMenuItem(text);
        if (iconName != null) {
            item.setIcon(new ImageIcon(IconLoader.getTrayIcon(iconName, trayIconPx(14))));
        }
        item.setFont(new Font("Comic Sans MS", Font.PLAIN, 12));
        item.setForeground(new Color(230, 230, 234));
        item.setBackground(new Color(24, 24, 27));
        item.setOpaque(true);
        item.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        item.setIconTextGap(9);
        item.setCursor(new Cursor(Cursor.HAND_CURSOR));
        item.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent ev) {
                item.setBackground(new Color(50, 50, 56));
                item.setForeground(new Color(240, 240, 243));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent ev) {
                item.setBackground(new Color(24, 24, 27));
                item.setForeground(new Color(230, 230, 234));
            }
        });
        return item;
    }

    private void showTray(boolean hookOk) {
        if (trayIcon != null) {
            String msg = hookOk
                    ? "Running in the background. Press keys to hear keyboard sounds."
                    : "Running, but global hook failed. Run as Administrator for global key capture.";
            String problems = problemSummary();
            if (!problems.isEmpty()) {
                msg = msg + problems;
            }
            trayIcon.displayMessage("Better Keys Desktop", msg,
                    hookOk && problems.isEmpty()
                            ? TrayIcon.MessageType.INFO : TrayIcon.MessageType.WARNING);
        }
    }

    private Image createTrayImage() {
        try {
            URL res = TrayApp.class.getResource("/icon.png");
            if (res != null) {
                BufferedImage raw = ImageIO.read(res);
                if (raw != null) {
                    BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = img.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(raw, 0, 0, 64, 64, null);
                    g.dispose();
                    return img;
                }
            }
        } catch (IOException ignored) {}
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g.setColor(new Color(0, 0, 0, 60));
        g.fillRoundRect(12, 14, 40, 40, 14, 14);

        GradientPaint gp = new GradientPaint(0, 0, new Color(240, 240, 243), 0, 64, new Color(180, 180, 185));
        g.setPaint(gp);
        g.fillRoundRect(10, 10, 44, 44, 12, 12);
        g.setColor(new Color(16, 16, 20));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(10, 10, 44, 44, 12, 12);

        g.setColor(new Color(248, 248, 250));
        g.fillRoundRect(14, 14, 36, 36, 8, 8);
        g.setColor(new Color(16, 16, 20));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(14, 14, 36, 36, 8, 8);

        g.setColor(new Color(16, 16, 20));
        g.fillRoundRect(22, 4, 20, 10, 4, 4);

        g.dispose();
        return img;
    }

    private Image createTrayIconImage() {
        try {
            URL res = TrayApp.class.getResource("/icon.png");
            if (res != null) {
                BufferedImage raw = ImageIO.read(res);
                if (raw != null) {
                    BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = img.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(raw, 0, 0, 64, 64, null);
                    g.dispose();
                    return img;
                }
            }
        } catch (IOException ignored) {}
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g.setColor(new Color(24, 24, 27, 235));
        g.fillRoundRect(12, 12, 40, 40, 10, 10);

        g.setColor(new Color(240, 240, 243));
        g.fillRoundRect(14, 14, 36, 36, 8, 8);

        g.setColor(new Color(16, 16, 20));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(14, 14, 36, 36, 8, 8);

        g.setFont(new Font("Comic Sans MS", Font.BOLD, 34));
        FontMetrics fm = g.getFontMetrics();
        String letter = "B";
        int tx = (64 - fm.stringWidth(letter)) / 2;
        int ty = (64 - fm.getHeight()) / 2 + fm.getAscent();
        g.setColor(new Color(16, 16, 20));
        g.drawString(letter, tx, ty);

        g.dispose();
        return img;
    }

    private void buildMainFrame() {
        mainFrame = new JFrame("Better Keys Desktop");
        mainFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        mainFrame.setResizable(false);
        mainFrame.setIconImage(createTrayImage());

        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                config.save();
                hideMainFrame();
            }
        });

        GradientPanel content = new GradientPanel();
        content.setLayout(new BorderLayout(0, 0));
        content.setBorder(new EmptyBorder(14, 18, 16, 18));

        GlassPanel header = new GlassPanel();
        header.setLayout(new BorderLayout(0, 8));
        header.setBorder(new EmptyBorder(22, 22, 16, 22));

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        titlePanel.setOpaque(false);

        Image keyIcon = IconLoader.get("keyboard", 34);
        JLabel iconLabel = new JLabel(new ImageIcon(keyIcon));
        iconLabel.setOpaque(false);
        titlePanel.add(iconLabel);

        JLabel title = new JLabel("Better Keys");
        title.setFont(new Font("Comic Sans MS", Font.BOLD, 30));
        title.putClientProperty(FlatClientProperties.STYLE, "foreground: #f0f0f2");
        titlePanel.add(title);

        header.add(titlePanel, BorderLayout.WEST);

        content.add(header, BorderLayout.NORTH);

        JPanel mainPanel = new JPanel();
        mainPanel.setOpaque(false);
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.add(Box.createVerticalStrut(14));

        GlassPanel packSection = createSectionPanel("Sound Pack", "keyboard");
        JPanel packContent = new JPanel(new BorderLayout(10, 0));
        packContent.setOpaque(false);
        packContent.setBorder(new EmptyBorder(10, 0, 0, 0));

        soundpackCombo = new JComboBox<>();
        soundpackCombo.addActionListener(e -> {
            if (updatingCombo) {
                return;
            }
            Soundpack selected = (Soundpack) soundpackCombo.getSelectedItem();
            if (selected != null) {
                switchSoundpack(selected);
            }
        });
        rebuildSoundpackCombo(soundpackManager.getActive());
        styleCombo(soundpackCombo);
        soundpackCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        packContent.add(soundpackCombo, BorderLayout.CENTER);

        JButton importPackBtn = createIconButton("Import", "plus", false);
        importPackBtn.addActionListener(e -> new SoundpackEditorDialog(mainFrame, audioEngine, soundpackManager, this).setVisible(true));

        JButton reloadPackBtn = createIconButton("Reload", "reload", false);
        reloadPackBtn.addActionListener(e -> refreshSoundpacks());

        JPanel packBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        packBtns.setOpaque(false);
        packBtns.add(importPackBtn);
        packBtns.add(reloadPackBtn);
        packContent.add(packBtns, BorderLayout.EAST);

        packSection.add(packContent);
        mainPanel.add(packSection);
        mainPanel.add(Box.createVerticalStrut(14));

        GlassPanel audioSection = createSectionPanel("Audio Output", "volume");
        JPanel audioContent = new JPanel(new BorderLayout(10, 0));
        audioContent.setOpaque(false);
        audioContent.setBorder(new EmptyBorder(10, 0, 0, 0));

        speakerCombo = new JComboBox<>();
        speakerCombo.addItem("System Default");
        for (AudioEngine.MixerLine line : AudioEngine.getOutputLines()) {
            speakerCombo.addItem(line.name);
        }
        styleCombo(speakerCombo);
        speakerCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        if (config.getSelectedAudioLine() != null) {
            for (int i = 0; i < speakerCombo.getItemCount(); i++) {
                if (speakerCombo.getItemAt(i).equals(config.getSelectedAudioLine())) {
                    speakerCombo.setSelectedIndex(i);
                    break;
                }
            }
        }
        speakerCombo.addActionListener(e -> {
            String sel = (String) speakerCombo.getSelectedItem();
            if ("System Default".equals(sel)) {
                audioEngine.setSelectedLine(null);
                config.setSelectedAudioLine(null);
            } else {
                audioEngine.setSelectedLine(sel);
                config.setSelectedAudioLine(sel);
            }
            config.save();
        });
        audioContent.add(speakerCombo, BorderLayout.CENTER);

        audioSection.add(audioContent);
        mainPanel.add(audioSection);
        mainPanel.add(Box.createVerticalStrut(14));

        GlassPanel volumeSection = createSectionPanel("Volume & Mute", "volume");
        JPanel volumeContent = new JPanel(new BorderLayout(10, 0));
        volumeContent.setOpaque(false);
        volumeContent.setBorder(new EmptyBorder(10, 0, 0, 0));

        muteButton = createIconButton(config.isMute() ? "Unmute" : "Mute",
                config.isMute() ? "mute" : "volume", true);
        muteButton.addActionListener(e -> toggleMute());
        muteButton.setPreferredSize(new Dimension(110, 38));
        volumeContent.add(muteButton, BorderLayout.WEST);

        JPanel sliderPanel = new JPanel(new BorderLayout(8, 0));
        sliderPanel.setOpaque(false);
        JLabel volLabel = new JLabel("Volume");
        volLabel.setFont(new Font("Comic Sans MS", Font.PLAIN, 12));
        volLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: #b8b9c0");
        sliderPanel.add(volLabel, BorderLayout.NORTH);

        volumeSlider = new JSlider(0, 100, (int) (config.getVolume() * 100));
        styleSlider(volumeSlider);
        volumeSlider.addChangeListener(ev -> {
            double vol = volumeSlider.getValue() / 100.0;
            audioEngine.setMasterVolume(vol);
            config.setVolume(vol);
            config.save();
        });
        sliderPanel.add(volumeSlider, BorderLayout.CENTER);

        volumeContent.add(sliderPanel, BorderLayout.CENTER);
        volumeSection.add(volumeContent);
        mainPanel.add(volumeSection);
        mainPanel.add(Box.createVerticalStrut(14));

        GlassPanel actionsSection = createSectionPanel("Actions", "settings");
        JPanel actionsContent = new JPanel(new GridLayout(1, 3, 10, 0));
        actionsContent.setOpaque(false);
        actionsContent.setBorder(new EmptyBorder(10, 0, 0, 0));

        JButton mapKeysBtn = createIconButton("Map Keys", "keymap", true);
        mapKeysBtn.addActionListener(e -> new KeyMapperDialog(mainFrame, audioEngine, soundpackManager, this).setVisible(true));
        actionsContent.add(mapKeysBtn);

        JButton folderBtn = createIconButton("Open Folder", "folder", false);
        folderBtn.addActionListener(e -> openSoundpacksFolder());
        actionsContent.add(folderBtn);

        JPanel bootPanel = new JPanel(new GridBagLayout());
        bootPanel.setOpaque(false);
        JCheckBox bootCheck = new JCheckBox("Start on boot");
        bootCheck.setFont(new Font("Comic Sans MS", Font.PLAIN, 12));
        bootCheck.putClientProperty(FlatClientProperties.STYLE, "foreground: #f0f0f2;");
        bootCheck.setOpaque(false);
        bootCheck.setSelected(config.isStartOnBoot());
        bootCheck.addActionListener(e -> {
            boolean enable = bootCheck.isSelected();
            config.setStartOnBoot(enable);
            ensureStartOnBoot(enable);
            if (enable) {
                launchExe();
            }
            config.save();
        });
        bootPanel.add(bootCheck);
        actionsContent.add(bootPanel);

        actionsSection.add(actionsContent);
        mainPanel.add(actionsSection);

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setOpaque(false);
        scrollPane.putClientProperty(FlatClientProperties.STYLE, "background: #00000000");
        content.add(scrollPane, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(14, 0, 0, 0));
        JButton exitBtn = createIconButton("Exit Better Keys", "close", false);
        exitBtn.addActionListener(e -> shutdown());
        footer.add(exitBtn, BorderLayout.CENTER);
        content.add(footer, BorderLayout.SOUTH);

        mainFrame.setContentPane(content);

        Insets bi = content.getBorder().getBorderInsets(content);
        int viewW = 704 - bi.left - bi.right;
        int viewH = mainPanel.getPreferredSize().height + 4;
        scrollPane.setPreferredSize(new Dimension(viewW, viewH));
        mainFrame.pack();
        Rectangle work = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if (mainFrame.getHeight() > work.height - 20) {
            mainFrame.setSize(mainFrame.getWidth(), work.height - 20);
        }
        mainFrame.setLocationRelativeTo(null);
    }

    private GlassPanel createSectionPanel(String title, String iconName) {
        GlassPanel panel = new GlassPanel();
        panel.setLayout(new BorderLayout(0, 10));
        panel.setBorder(new EmptyBorder(18, 18, 18, 18));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

        JPanel headerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        headerRow.setOpaque(false);

        Image icon = IconLoader.get(iconName, 20);
        JLabel iconLabel = new JLabel(new ImageIcon(icon));
        iconLabel.setOpaque(false);
        headerRow.add(iconLabel);

        JLabel sectionTitle = new JLabel(title);
        sectionTitle.setFont(new Font("Comic Sans MS", Font.BOLD, 14));
        sectionTitle.putClientProperty(FlatClientProperties.STYLE, "foreground: #f0f0f2");
        headerRow.add(sectionTitle);

        panel.add(headerRow, BorderLayout.NORTH);
        return panel;
    }

    private JButton createIconButton(String text, String iconName, boolean accent) {
        JButton btn = new JButton(text);
        btn.setIcon(new ImageIcon(accent
                ? IconLoader.getDark(iconName, 16)
                : IconLoader.get(iconName, 16)));
        btn.setHorizontalTextPosition(SwingConstants.RIGHT);
        btn.setIconTextGap(8);
        btn.setFont(new Font("Comic Sans MS", Font.BOLD, 12));
        btn.putClientProperty(FlatClientProperties.STYLE, accent
                ? "background: #f0f0f2;"
                + "foreground: #111113;"
                + "hoverBackground: #f6f6f8;"
                + "pressedBackground: #d8d8dd;"
                + "focusColor: #f0f0f2;"
                + "arc: 10;"
                + "borderWidth: 1;"
                + "borderColor: #c8c8cd;"
                : "background: #232327;"
                + "foreground: #f0f0f2;"
                + "hoverBackground: #2b2b30;"
                + "pressedBackground: #1c1c20;"
                + "focusColor: #f0f0f2;"
                + "arc: 10;"
                + "borderWidth: 1;"
                + "borderColor: #45454b;");
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(150, 38));
        return btn;
    }

    private void styleCombo(JComboBox<?> cb) {
        cb.putClientProperty(FlatClientProperties.STYLE, ""
                + "background: #1a1a1c;"
                + "foreground: #f0f0f2;"
                + "focusColor: #f0f0f2;"
                + "borderColor: #333333;"
                + "borderWidth: 1;");
        cb.setFont(new Font("Comic Sans MS", Font.PLAIN, 12));
        cb.setPreferredSize(new Dimension(120, 34));
    }

    private void styleSlider(JSlider s) {
        s.putClientProperty(FlatClientProperties.STYLE, ""
                + "trackColor: #1a1a1c;"
                + "trackValueColor: #f0f0f2;"
                + "thumbColor: #f0f0f2;"
                + "focusedColor: #f0f0f2;"
                + "focusedThumbBorderColor: #f0f0f2;"
                + "trackWidth: 6;"
                + "thumbSize: 16,16");
        s.setOpaque(false);
    }

    public void showMainFrame() {
        if (mainFrame == null) {
            buildMainFrame();
        }
        mainFrame.setVisible(true);
        mainFrame.toFront();
        mainFrame.requestFocus();
    }

    public void openKeyMapper() {
        if (mainFrame == null) {
            buildMainFrame();
        }
        new KeyMapperDialog(mainFrame, audioEngine, soundpackManager, this).setVisible(true);
    }

    private void hideMainFrame() {
        if (mainFrame != null) {
            mainFrame.setVisible(false);
        }
    }

    private void toggleMainFrame() {
        if (mainFrame != null && mainFrame.isVisible()) {
            hideMainFrame();
        } else {
            showMainFrame();
        }
    }

    private void toggleMute() {
        boolean next = !config.isMute();
        config.setMute(next);
        audioEngine.setMuted(next);
        inputListener.setEnabled(!next);
        updateMuteButton();
        config.save();
        if (trayIcon != null) {
            trayIcon.displayMessage("Better Keys", next ? "Sounds muted" : "Sounds unmuted",
                    TrayIcon.MessageType.INFO);
        }
    }

    private void updateMuteButton() {
        boolean muted = config.isMute();
        if (muteButton != null) {
            muteButton.setText(muted ? "Unmute" : "Mute");
            muteButton.setIcon(new ImageIcon(IconLoader.getDark(muted ? "mute" : "volume", 16)));
        }
        if (muteMenuItem != null) {
            muteMenuItem.setText(muted ? "Unmute" : "Mute");
            muteMenuItem.setIcon(new ImageIcon(IconLoader.getTrayIcon(muted ? "mute" : "volume", trayIconPx(14))));
        }
    }

    public void switchSoundpack(Soundpack pack) {
        soundpackManager.setActive(pack);
        config.setSelectedSoundpack(pack.getName());
        inputListener.setSoundpack(pack);
        loadSoundsForPack(pack);
        audioEngine.setMuted(config.isMute());
        if (soundpackCombo != null) {
            soundpackCombo.setSelectedItem(pack);
        }
        config.save();
        if (trayIcon != null) {
            String problems = problemSummary();
            trayIcon.displayMessage("Better Keys", "Sound pack: " + pack.getName() + "." + problems,
                    problems.isEmpty() ? TrayIcon.MessageType.INFO : TrayIcon.MessageType.WARNING);
        }
    }

    private void refreshSoundpacks() {
        Soundpack previous = soundpackManager.getActive();
        String keepName = previous != null ? previous.getName() : config.getSelectedSoundpack();
        soundpackManager.loadAll();
        Soundpack resolved = soundpackManager.findByName(keepName);
        if (resolved == null) {
            List<Soundpack> packs = soundpackManager.getSoundpacks();
            resolved = packs.isEmpty() ? null : packs.get(0);
        }
        if (resolved != null) {
            soundpackManager.setActive(resolved);
            config.setSelectedSoundpack(resolved.getName());
            inputListener.setSoundpack(resolved);
            loadSoundsForPack(resolved);
        }
        rebuildSoundpackCombo(resolved);
        config.save();
        if (trayIcon != null) {
            trayIcon.displayMessage("Better Keys", "Sound packs reloaded." + problemSummary(),
                    problemSummary().isEmpty() ? TrayIcon.MessageType.INFO : TrayIcon.MessageType.WARNING);
        }
    }

    public void rebuildSoundpackCombo(Soundpack select) {
        if (soundpackCombo == null) {
            return;
        }
        updatingCombo = true;
        try {
            soundpackCombo.removeAllItems();
            for (Soundpack p : soundpackManager.getSoundpacks()) {
                soundpackCombo.addItem(p);
            }
            if (select != null) {
                soundpackCombo.setSelectedItem(select);
            }
        } finally {
            updatingCombo = false;
        }
    }

    private void openSoundpacksFolder() {
        Path dir = Paths.get(System.getProperty("user.home"), ".betterkeys", "soundpacks");
        try {
            Files.createDirectories(dir);
            Desktop.getDesktop().open(dir.toFile());
        } catch (IOException ex) {
            System.err.println("Cannot open soundpacks folder: " + ex.getMessage());
        }
    }

    private static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String RUN_VALUE = "BetterKeysDesktop";

    private void ensureStartOnBoot(boolean enable) {
        try {
            String current = queryRunValue();
            if (enable) {
                String target = bootTargetPath();
                if (!target.toLowerCase(java.util.Locale.ROOT).endsWith(".exe")) return;
                if (current != null && current.equalsIgnoreCase(target)) return;
                runReg("add", RUN_KEY, "/v", RUN_VALUE, "/t", "REG_SZ", "/d", target, "/f");
            } else {
                if (current == null) return;
                runReg("delete", RUN_KEY, "/v", RUN_VALUE, "/f");
            }
        } catch (Exception ex) {
            System.err.println("Failed to set start-on-boot: " + ex.getMessage());
        }
    }

    private String queryRunValue() {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query", RUN_KEY, "/v", RUN_VALUE);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            String out;
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
                out = sb.toString();
            }
            p.waitFor();
            for (String line : out.split("\n")) {
                int i = line.indexOf("REG_SZ");
                if (i >= 0) {
                    String v = line.substring(i + "REG_SZ".length()).trim();
                    if (!v.isEmpty()) return v;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void runReg(String... args) throws IOException, InterruptedException {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "reg";
        System.arraycopy(args, 0, cmd, 1, args.length);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        pb.start().waitFor();
    }

    private String bootTargetPath() {
        try {
            File runtimeDir = new File(System.getProperty("java.home"));
            File appDir = runtimeDir.getParentFile();
            if (appDir != null) {
                File exe = new File(appDir, "BetterKeys.exe");
                if (exe.isFile()) return exe.getAbsolutePath();
            }
        } catch (Exception ignored) {
        }
        try {
            return new File(TrayApp.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getAbsolutePath();
        } catch (URISyntaxException | NullPointerException ex) {
            return "BetterKeys.exe";
        }
    }

    private void launchExe() {
        try {
            String exe = bootTargetPath();
            if (!exe.toLowerCase(java.util.Locale.ROOT).endsWith(".exe")) return;
            ProcessBuilder pb = new ProcessBuilder(
                    "cmd", "/c", "start", "\"BetterKeys\"", "\"" + exe + "\"");
            pb.start();
        } catch (Exception ex) {
            System.err.println("Failed to launch BetterKeys.exe: " + ex.getMessage());
        }
    }

    public void shutdown() {
        inputListener.setEnabled(false);
        GlobalInputListener.removeListeners(inputListener, inputListener);
        GlobalInputListener.unregisterHook();
        if (systemTray != null && trayIcon != null) {
            systemTray.remove(trayIcon);
        }
        audioEngine.shutdown();
        config.save();
        if (mainFrame != null) {
            mainFrame.dispose();
        }
        System.exit(0);
    }

    SoundpackManager getSoundpackManager() {
        return soundpackManager;
    }
}
