package com.justsamyog.betterkeys.ui;
import com.justsamyog.betterkeys.audio.AudioEngine;
import com.justsamyog.betterkeys.model.Soundpack;
import com.justsamyog.betterkeys.model.SoundpackManager;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public class SoundpackEditorDialog extends JDialog {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final TrayApp app;
    private final SoundpackManager manager;
    private final AudioEngine audio;

    private JTextField nameField;
    private JTextField authorField;
    private JSlider volumeSlider;
    private JLabel volumeValueLabel;
    private JLabel statusLabel;
    private JButton importButton;
    private JButton browseButton;

    private File selectedFolder;

    public SoundpackEditorDialog(JFrame owner, AudioEngine audio,
                                 SoundpackManager manager, TrayApp app) {
        super(owner, "Import Soundpack", true);
        this.audio = audio;
        this.manager = manager;
        this.app = app;
        setSize(560, 480);
        setMinimumSize(new Dimension(520, 440));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUI();
        setupDragAndDrop();
    }

    private void buildUI() {
        GradientPanel root = new GradientPanel(new BorderLayout(0, 16));
        root.setBorder(BorderFactory.createEmptyBorder(24, 24, 20, 24));

        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleRow.setOpaque(false);

        JLabel iconLabel = new JLabel(new ImageIcon(
                IconLoader.get("folder", 26)));
        titleRow.add(iconLabel);

        JLabel title = new JLabel("Import / Customize Sound Pack");
        title.setFont(new Font("Comic Sans MS", Font.BOLD, 20));
        title.putClientProperty(FlatClientProperties.STYLE, "foreground: #f0f0f2");
        titleRow.add(title);
        header.add(titleRow, BorderLayout.NORTH);

        JLabel hint = new JLabel("Pick a folder of sound files (.wav, .ogg, .aiff, .flac), with or without a pack.json.");
        hint.setFont(new Font("Comic Sans MS", Font.PLAIN, 12));
        hint.putClientProperty(FlatClientProperties.STYLE, "foreground: #b8b9c0");
        hint.setBorder(BorderFactory.createEmptyBorder(0, 36, 0, 0));
        header.add(hint, BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        GlassPanel card = new GlassPanel();
        card.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 14, 10, 14);
        gbc.anchor = GridBagConstraints.WEST;

        Font labelFont = new Font("Comic Sans MS", Font.BOLD, 13);

        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel l1 = styledLabel("Pack Name:", labelFont);
        card.add(l1, gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        nameField = styledField("Custom Pack");
        card.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel l2 = styledLabel("Author:", labelFont);
        card.add(l2, gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        authorField = styledField("User");
        card.add(authorField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel l3 = styledLabel("Volume:", labelFont);
        card.add(l3, gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JPanel volRow = new JPanel(new BorderLayout(12, 0));
        volRow.setOpaque(false);
        volumeSlider = new JSlider(0, 100, 85);
        styleSlider(volumeSlider);
        volRow.add(volumeSlider, BorderLayout.CENTER);
        volumeValueLabel = new JLabel("85%");
        volumeValueLabel.setFont(new Font("Comic Sans MS", Font.BOLD, 12));
        volumeValueLabel.putClientProperty(FlatClientProperties.STYLE,
                "foreground: #f0f0f2");
        volumeValueLabel.setPreferredSize(new Dimension(44, 20));
        volumeValueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        volRow.add(volumeValueLabel, BorderLayout.EAST);
        volumeSlider.addChangeListener(e ->
                volumeValueLabel.setText(volumeSlider.getValue() + "%"));
        card.add(volRow, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        gbc.insets = new Insets(16, 14, 6, 14);
        browseButton = styledButton("Choose Soundpack Folder...", "folder");
        browseButton.addActionListener(e -> chooseFolder());
        card.add(browseButton, gbc);

        gbc.gridy = 4; gbc.insets = new Insets(4, 14, 10, 14);
        JLabel dropHint = new JLabel("...or drag & drop a soundpack folder anywhere in this window");
        dropHint.setFont(new Font("Comic Sans MS", Font.ITALIC, 11));
        dropHint.putClientProperty(FlatClientProperties.STYLE, "foreground: #888888");
        card.add(dropHint, gbc);

        root.add(card, BorderLayout.CENTER);

        statusLabel = new JLabel("  No folder selected.");
        statusLabel.setFont(new Font("Comic Sans MS", Font.PLAIN, 11));
        statusLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: #999999");
        statusLabel.setIcon(new ImageIcon(IconLoader.get("close", 14)));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        root.add(statusLabel, BorderLayout.SOUTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        JButton cancel = styledButton("Cancel", "close");
        cancel.addActionListener(e -> dispose());
        importButton = styledButton("Import Pack", "save");
        importButton.setEnabled(false);
        importButton.addActionListener(e -> importPack());
        buttons.add(cancel);
        buttons.add(importButton);
        root.add(buttons, BorderLayout.PAGE_END);

        setContentPane(root);
        getRootPane().setDefaultButton(importButton);
    }

    private JLabel styledLabel(String text, Font font) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.putClientProperty(FlatClientProperties.STYLE, "foreground: #dddddd");
        return l;
    }

    private JTextField styledField(String value) {
        JTextField f = new JTextField(value);
        f.setFont(new Font("Comic Sans MS", Font.PLAIN, 13));
        f.putClientProperty(FlatClientProperties.STYLE, ""
                + "background: #1a1a1c;"
                + "foreground: #f0f0f2;"
                + "caretColor: #f0f0f2;"
                + "borderColor: #3a3a3e;"
                + "focusedBorderColor: #f0f0f2;"
                + "borderWidth: 1;"
                + "arc: 8;");
        f.setPreferredSize(new Dimension(100, 34));
        return f;
    }

    private JButton styledButton(String text, String iconName) {
        JButton b = new JButton(text);
        b.setIcon(new ImageIcon(IconLoader.getDark(iconName, 16)));
        b.setHorizontalTextPosition(SwingConstants.RIGHT);
        b.setIconTextGap(8);
        b.setFont(new Font("Comic Sans MS", Font.BOLD, 12));
        b.putClientProperty(FlatClientProperties.STYLE, ""
                + "background: #f0f0f2;"
                + "foreground: #111113;"
                + "hoverBackground: #f6f6f8;"
                + "pressedBackground: #d8d8dd;"
                + "focusColor: #f0f0f2;"
                + "arc: 10;"
                + "borderWidth: 1;"
                + "borderColor: #c8c8cd;");
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(170, 36));
        return b;
    }

    private void styleSlider(JSlider s) {
        s.putClientProperty(FlatClientProperties.STYLE, ""
                + "trackColor: #333336;"
                + "trackValueColor: #f0f0f2;"
                + "thumbColor: #f0f0f2;"
                + "focusedColor: #f0f0f2;"
                + "focusedThumbBorderColor: #f0f0f2;"
                + "trackWidth: 6;"
                + "thumbSize: 16,16");
        s.setOpaque(false);
    }

    private void setupDragAndDrop() {
        new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, new java.awt.dnd.DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                    java.util.List<File> files = (java.util.List<File>)
                            evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        setFolder(files.get(0));
                    }
                    evt.getDropTargetContext().dropComplete(true);
                } catch (Exception ex) {
                    evt.rejectDrop();
                }
            }
        });
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select soundpack folder");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setFolder(chooser.getSelectedFile());
        }
    }

    private static final String[] SOUND_EXT = {
        ".wav", ".ogg", ".aiff", ".aif", ".flac", ".mp3", ".m4a", ".aac", ".wma", ".opus"
    };

    private boolean hasSoundFiles(File folder) {
        if (folder == null || !folder.isDirectory()) return false;
        return hasSoundFilesRecursive(folder);
    }

    private boolean hasSoundFilesRecursive(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isDirectory()) {
                if (hasSoundFilesRecursive(f)) return true;
            } else if (f.isFile()) {
                String name = f.getName().toLowerCase();
                for (String ext : SOUND_EXT) {
                    if (name.endsWith(ext)) return true;
                }
            }
        }
        return false;
    }

    private void setFolder(File folder) {
        this.selectedFolder = folder;
        File packJson = new File(folder, "pack.json");
        if (folder != null && packJson.exists()) {
            statusLabel.setText("  " + folder.getAbsolutePath());
            statusLabel.setIcon(new ImageIcon(IconLoader.get("check", 14)));
            statusLabel.putClientProperty(FlatClientProperties.STYLE,
                    "foreground: #cfcfcf");
            importButton.setEnabled(true);
            try (FileReader reader = new FileReader(packJson)) {
                Soundpack sp = GSON.fromJson(reader, Soundpack.class);
                if (sp != null) {
                    nameField.setText(sp.getName());
                    authorField.setText(sp.getAuthor());
                    if (sp.getVolume() > 0) {
                        volumeSlider.setValue((int) (sp.getVolume() * 100));
                    }
                }
            } catch (Exception ignored) {
            }
        } else if (folder != null && hasSoundFiles(folder)) {
            statusLabel.setText("  " + folder.getAbsolutePath()
                    + "  (pack.json will be created)");
            statusLabel.setIcon(new ImageIcon(IconLoader.get("plus", 14)));
            statusLabel.putClientProperty(FlatClientProperties.STYLE,
                    "foreground: #b8b9c0");
            importButton.setEnabled(true);
        } else {
            statusLabel.setText("  Select a folder containing sound files (.wav, .ogg, .aiff, .flac).");
            statusLabel.setIcon(new ImageIcon(IconLoader.get("close", 14)));
            statusLabel.putClientProperty(FlatClientProperties.STYLE,
                    "foreground: #bbbbbb");
            importButton.setEnabled(false);
        }
    }

    private Map<String, String> buildSoundsFromFolder(File folder) {
        LinkedHashMap<String, String> sounds = new LinkedHashMap<>();
        if (folder == null || !folder.isDirectory()) return sounds;
        collectSounds(folder, folder, sounds);
        return sounds;
    }

    private void collectSounds(File root, File dir, LinkedHashMap<String, String> sounds) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectSounds(root, f, sounds);
            } else if (f.isFile() && isSound(f)) {
                String id = soundIdFor(relativize(root, f));
                if (!sounds.containsKey(id)) {
                    sounds.put(id, relativize(root, f));
                }
            }
        }
    }

    private String relativize(File root, File file) {
        String rel = root.toURI().relativize(file.toURI()).getPath();
        return rel.replace('/', File.separatorChar);
    }

    private boolean isSound(File f) {
        String name = f.getName().toLowerCase();
        for (String ext : SOUND_EXT) {
            if (name.endsWith(ext)) return true;
        }
        return false;
    }

    private String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String soundIdFor(String fileName) {
        String base = stripExt(fileName);
        return base.startsWith("key_") ? base : "key_" + base;
    }

    private void importPack() {
        if (selectedFolder == null) {
            return;
        }
        try {
            Path userDir = java.nio.file.Paths.get(System.getProperty("user.home"),
                    ".betterkeys", "soundpacks");
            Files.createDirectories(userDir);

            String packName = nameField.getText().trim();
            if (packName.isBlank()) {
                packName = "Custom Pack";
            }
            Path target = userDir.resolve(packName);
            if (Files.exists(target)) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "A pack named '" + packName + "' already exists. Overwrite?",
                        "Overwrite?", JOptionPane.YES_NO_OPTION);
                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }
                deleteDirectory(target.toFile());
            }

            copyFolder(selectedFolder.toPath(), target);

            File packJson = target.resolve("pack.json").toFile();
            Soundpack sp;
            if (packJson.exists()) {
                try (FileReader reader = new FileReader(packJson)) {
                    sp = GSON.fromJson(reader, Soundpack.class);
                }
            } else {
                sp = new Soundpack();
                Map<String, String> sounds = buildSoundsFromFolder(selectedFolder);
                if (!sounds.isEmpty()) {
                    sp.setSounds(sounds);
                }
            }
            sp.setName(packName);
            sp.setAuthor(authorField.getText().trim());
            sp.setVolume(volumeSlider.getValue() / 100.0);
            try (FileWriter writer = new FileWriter(packJson)) {
                GSON.toJson(sp, writer);
            }

            manager.loadAll();
            Soundpack imported = manager.findByName(packName);
            if (imported != null) {
                app.rebuildSoundpackCombo(imported);
                app.switchSoundpack(imported);
            }
            JOptionPane.showMessageDialog(this, "Sound pack '" + packName + "' imported.");
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Import failed: " + ex.getMessage(),
                    "Import Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void copyFolder(Path source, Path target) throws java.io.IOException {
        try (var walk = Files.walk(source)) {
            walk.filter(Files::isDirectory)
                    .forEach(src -> {
                        Path dest = target.resolve(source.relativize(src));
                        try {
                            Files.createDirectories(dest);
                        } catch (java.io.IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }
        try (var walk = Files.walk(source)) {
            walk.filter(Files::isRegularFile)
                    .forEach(src -> {
                        Path dest = target.resolve(source.relativize(src));
                        try {
                            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                        } catch (java.io.IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }
    }

    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirectory(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }
}
