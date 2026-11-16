package com.justsamyog.betterkeys.ui;
import com.justsamyog.betterkeys.audio.AudioEngine;
import com.justsamyog.betterkeys.input.GlobalInputListener;
import com.justsamyog.betterkeys.model.Soundpack;
import com.justsamyog.betterkeys.model.SoundpackManager;

import com.formdev.flatlaf.FlatClientProperties;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KeyMapperDialog extends JDialog implements NativeKeyListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String RANDOM_ITEM = "RANDOM (varies each press)";

    private final TrayApp app;
    private final SoundpackManager manager;
    private final AudioEngine audio;
    private final Soundpack currentPack;

    private final Map<String, String> keyMap = new LinkedHashMap<>();
    private final List<String> availableSounds = new ArrayList<>();

    private KeyboardPanel keyboard;
    private JComboBox<String> soundPicker;
    private JTextField searchField;
    private JLabel selectedLabel;
    private JLabel assignmentLabel;
    private JLabel hintLabel;

    public KeyMapperDialog(JFrame owner, AudioEngine audio,
                           SoundpackManager manager, TrayApp app) {
        super(owner, "Key Sound Mapper", true);
        this.audio = audio;
        this.manager = manager;
        this.app = app;
        this.currentPack = manager.getActive();

        setSize(1120, 700);
        setMinimumSize(new Dimension(940, 620));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        loadKeyMap();
        buildUI();
        registerLiveFeedback();
    }

    private void loadKeyMap() {
        if (currentPack == null) return;
        Map<String, String> sounds = currentPack.getSounds();
        if (sounds == null) return;

        for (Map.Entry<String, String> e : sounds.entrySet()) {
            if (e.getKey().startsWith("VC_")) continue;
            if (!availableSounds.contains(e.getKey())) {
                availableSounds.add(e.getKey());
            }
        }
        availableSounds.sort(String::compareTo);

        for (String code : knownKeyCodes()) {
            String soundId = sounds.get(code);
            keyMap.put(code, soundId != null ? soundId : "RANDOM");
        }
    }

    private List<String> knownKeyCodes() {
        List<String> codes = new ArrayList<>();
        KeyboardPanel probe = new KeyboardPanel();
        for (KeyboardPanel.KeyDef k : probe.getKeys()) {
            if (!codes.contains(k.vc)) {
                codes.add(k.vc);
            }
        }
        return codes;
    }

    private void buildUI() {
        GradientPanel root = new GradientPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(18, 18, 16, 18));

        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);

        JLabel title = new JLabel("Key Sound Mapper");
        title.setFont(new Font("Comic Sans MS", Font.BOLD, 24));
        title.putClientProperty(FlatClientProperties.STYLE, "foreground: #f0f0f2");
        header.add(title, BorderLayout.NORTH);

        hintLabel = new JLabel("Click a key to select it, then assign a sound. Press any key to see it flash.");
        hintLabel.setFont(new Font("Comic Sans MS", Font.PLAIN, 12));
        hintLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: #999999");
        header.add(hintLabel, BorderLayout.SOUTH);

        root.add(header, BorderLayout.NORTH);

        keyboard = new KeyboardPanel();
        keyboard.setKeyMap(keyMap);
        keyboard.setOnSelect(this::onKeySelected);

        JScrollPane scroll = new JScrollPane(keyboard);
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        root.add(scroll, BorderLayout.CENTER);

        JPanel controls = buildControls();
        root.add(controls, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildControls() {
        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        JPanel selectionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        selectionRow.setOpaque(false);

        selectedLabel = new JLabel("No key selected");
        selectedLabel.setFont(new Font("Comic Sans MS", Font.BOLD, 16));
        selectedLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: #f0f0f2");
        selectionRow.add(selectedLabel);

        assignmentLabel = new JLabel("Click a key on the keyboard");
        assignmentLabel.setFont(new Font("Comic Sans MS", Font.PLAIN, 13));
        assignmentLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: #999999");
        selectionRow.add(assignmentLabel);

        controls.add(selectionRow);

        JPanel assignRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        assignRow.setOpaque(false);

        JLabel soundCaption = new JLabel("Sound:");
        soundCaption.setFont(new Font("Comic Sans MS", Font.PLAIN, 13));
        soundCaption.putClientProperty(FlatClientProperties.STYLE, "foreground: #cccccc");
        assignRow.add(soundCaption);

        searchField = new JTextField(12);
        searchField.putClientProperty(FlatClientProperties.STYLE, ""
                + "foreground: #f0f0f2;"
                + "background: #1a1a1c");
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search...");
        searchField.setFont(new Font("Comic Sans MS", Font.PLAIN, 12));
        searchField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent e) {
                filterSounds();
            }
        });
        assignRow.add(searchField);

        soundPicker = new JComboBox<>();
        soundPicker.addItem(RANDOM_ITEM);
        for (String s : availableSounds) {
            soundPicker.addItem(s);
        }
        soundPicker.setPreferredSize(new Dimension(240, 32));
        soundPicker.setFont(new Font("Comic Sans MS", Font.PLAIN, 12));
        soundPicker.putClientProperty(FlatClientProperties.STYLE, ""
                + "foreground: #f0f0f2;"
                + "background: #1a1a1c;"
                + "focusColor: #f0f0f2;");
        assignRow.add(soundPicker);

        JButton previewBtn = new JButton("Preview");
        styleButton(previewBtn, false);
        previewBtn.addActionListener(e -> {
            String sound = (String) soundPicker.getSelectedItem();
            if (sound == null) return;
            if (RANDOM_ITEM.equals(sound)) {
                sound = pickRandomSound();
            }
            if (sound != null) {
                audio.play(sound);
            }
            flashSelected();
        });
        assignRow.add(previewBtn);

        JButton assignBtn = new JButton("Assign to Key");
        styleButton(assignBtn, true);
        assignBtn.addActionListener(e -> assignSound());
        assignRow.add(assignBtn);

        controls.add(assignRow);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        actionRow.setOpaque(false);

        JButton resetBtn = new JButton("Reset All");
        styleButton(resetBtn, false);
        resetBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Reset all keys to random variation sounds?",
                    "Reset All", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                keyMap.replaceAll((k, v) -> "RANDOM");
                keyboard.setKeyMap(keyMap);
                refreshSelectionLabels();
            }
        });
        actionRow.add(resetBtn);

        JButton saveBtn = new JButton("Save Mapping");
        styleButton(saveBtn, true);
        saveBtn.addActionListener(e -> {
            saveKeyMap();
            JOptionPane.showMessageDialog(this, "Key mapping saved!", "Saved",
                    JOptionPane.INFORMATION_MESSAGE);
            dispose();
        });
        actionRow.add(saveBtn);

        JButton closeBtn = new JButton("Close");
        styleButton(closeBtn, false);
        closeBtn.addActionListener(e -> dispose());
        actionRow.add(closeBtn);

        controls.add(actionRow);
        return controls;
    }

    private void onKeySelected(KeyboardPanel.KeyDef key) {
        refreshSelectionLabels();
        String sound = keyMap.getOrDefault(key.vc, "RANDOM");
        soundPicker.setSelectedItem(RANDOM_ITEM.equals(sound) ? RANDOM_ITEM : sound);
        if (!"RANDOM".equals(sound)) {
            audio.play(sound);
        }
    }

    private void refreshSelectionLabels() {
        KeyboardPanel.KeyDef key = keyboard.getSelected();
        if (key == null) {
            selectedLabel.setText("No key selected");
            assignmentLabel.setText("Click a key on the keyboard");
            return;
        }
        String sound = keyMap.getOrDefault(key.vc, "RANDOM");
        selectedLabel.setText(key.label);
        assignmentLabel.setText(key.vc + "  ->  "
                + ("RANDOM".equals(sound) ? "random variation" : sound));
    }

    private void flashSelected() {
        KeyboardPanel.KeyDef key = keyboard.getSelected();
        if (key != null) {
            keyboard.flash(key.vc);
        }
    }

    private String pickRandomSound() {
        if (availableSounds.isEmpty()) return null;
        return availableSounds.get(new java.util.Random().nextInt(availableSounds.size()));
    }

    private void assignSound() {
        KeyboardPanel.KeyDef key = keyboard.getSelected();
        if (key == null) {
            JOptionPane.showMessageDialog(this,
                    "Click a key on the keyboard first.",
                    "No Key Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String sound = (String) soundPicker.getSelectedItem();
        if (sound == null) return;

        if (RANDOM_ITEM.equals(sound)) {
            keyMap.put(key.vc, "RANDOM");
        } else {
            keyMap.put(key.vc, sound);
        }
        keyboard.setKeyMap(keyMap);
        refreshSelectionLabels();
        keyboard.flash(key.vc);
        if (RANDOM_ITEM.equals(sound)) {
            String random = pickRandomSound();
            if (random != null) audio.play(random);
        } else {
            audio.play(sound);
        }
    }

    private void filterSounds() {
        String query = searchField.getText().toLowerCase().trim();
        String current = (String) soundPicker.getSelectedItem();
        soundPicker.removeAllItems();
        soundPicker.addItem(RANDOM_ITEM);
        for (String s : availableSounds) {
            if (query.isEmpty() || s.toLowerCase().contains(query)) {
                soundPicker.addItem(s);
            }
        }
        if (current != null && soundPicker.getItemCount() > 0) {
            soundPicker.setSelectedItem(current);
        }
    }

    private void saveKeyMap() {
        if (currentPack == null) return;
        try {
            Map<String, String> sounds = currentPack.getSounds();
            if (sounds == null) sounds = new LinkedHashMap<>();

            for (Map.Entry<String, String> e : keyMap.entrySet()) {
                if ("RANDOM".equals(e.getValue())) {
                    sounds.remove(e.getKey());
                } else {
                    sounds.put(e.getKey(), e.getValue());
                }
            }
            currentPack.setSounds(sounds);

            Path packDir = Paths.get(System.getProperty("user.home"),
                    ".betterkeys", "soundpacks", currentPack.getName());
            Files.createDirectories(packDir);
            Path packJson = packDir.resolve("pack.json");
            try (FileWriter writer = new FileWriter(packJson.toFile())) {
                GSON.toJson(currentPack, writer);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void registerLiveFeedback() {
        try {
            if (GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.addNativeKeyListener(this);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        String vc = GlobalInputListener.keyCodeToVC(e.getKeyCode());
        if (vc == null || keyboard == null) return;
        SwingUtilities.invokeLater(() -> keyboard.flash(vc));
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
    }

    private void unregisterLiveFeedback() {
        try {
            GlobalScreen.removeNativeKeyListener(this);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void dispose() {
        unregisterLiveFeedback();
        super.dispose();
    }

    private void styleButton(JButton b, boolean accent) {
        b.putClientProperty(FlatClientProperties.STYLE, ""
                + "background: " + (accent ? "#f0f0f2" : "#252528") + ";"
                + "foreground: " + (accent ? "#111113" : "#eeeeee") + ";"
                + "hoverBackground: " + (accent ? "#f0f0f2" : "#35353a") + ";"
                + "pressedBackground: " + (accent ? "#c8c8cd" : "#1a1a1c") + ";"
                + "focusColor: #f0f0f2;"
                + "arc: 8;");
        b.setFont(new Font("Comic Sans MS", Font.PLAIN, 12));
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
}
