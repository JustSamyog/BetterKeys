package com.justsamyog.betterkeys.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AppConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".betterkeys");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");

    private boolean mute = false;
    private double volume = 0.85;
    private String selectedSoundpack = "Cherry MX Red ABS";
    private String selectedAudioLine = null; 
    private boolean startOnBoot = false;

    private static AppConfig instance;

    public static AppConfig load() {
        if (instance != null) {
            return instance;
        }
        try {
            Files.createDirectories(CONFIG_DIR);
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE);
                if (json.startsWith("\uFEFF")) {
                    json = json.substring(1);
                }
                instance = GSON.fromJson(json, AppConfig.class);
            }
        } catch (IOException ex) {
            System.err.println("Failed to load app config: " + ex.getMessage());
        }
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            try (FileWriter writer = new FileWriter(CONFIG_FILE.toFile())) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ex) {
            System.err.println("Failed to save app config: " + ex.getMessage());
        }
    }

    public boolean isMute() {
        return mute;
    }

    public void setMute(boolean mute) {
        this.mute = mute;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = Math.max(0.0, Math.min(1.0, volume));
    }

    public String getSelectedSoundpack() {
        return selectedSoundpack;
    }

    public void setSelectedSoundpack(String selectedSoundpack) {
        this.selectedSoundpack = selectedSoundpack;
    }

    public String getSelectedAudioLine() {
        return selectedAudioLine;
    }

    public void setSelectedAudioLine(String selectedAudioLine) {
        this.selectedAudioLine = selectedAudioLine;
    }

    public boolean isStartOnBoot() {
        return startOnBoot;
    }

    public void setStartOnBoot(boolean startOnBoot) {
        this.startOnBoot = startOnBoot;
    }
}
