package com.justsamyog.betterkeys.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class SoundpackManager {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Path SOUNDPACKS_DIR = Paths.get(System.getProperty("user.home"),
            ".betterkeys", "soundpacks");

    private final List<Soundpack> soundpacks = new ArrayList<>();
    private final List<String> issues = new ArrayList<>();
    private Soundpack active;

    public SoundpackManager() {
        try {
            Files.createDirectories(SOUNDPACKS_DIR);
        } catch (IOException ex) {
            System.err.println("Failed to create soundpacks directory: " + ex.getMessage());
        }
    }

    public List<String> getIssues() {
        return new ArrayList<>(issues);
    }

    public List<Soundpack> loadAll() {
        soundpacks.clear();
        issues.clear();
        File dir = SOUNDPACKS_DIR.toFile();
        File[] folders = dir.listFiles(File::isDirectory);
        boolean anyPackJson = false;
        if (folders != null) {
            for (File folder : folders) {
                if (new File(folder, "pack.json").isFile()) {
                    anyPackJson = true;
                    break;
                }
            }
        }
        if (!anyPackJson) {
            seedDefaultFromResources();
            folders = dir.listFiles(File::isDirectory);
        }
        if (folders != null) {
            for (File folder : folders) {
                List<String> converted = PackConverter.convertIfNeeded(folder);
                if (converted != null) {
                    for (String issue : converted) {
                        issues.add(folder.getName() + ": " + issue);
                    }
                }
                Soundpack sp = readPackFolder(folder);
                if (sp != null) {
                    soundpacks.add(sp);
                } else if (hasSoundFilesButNoPackJson(folder)) {
                    issues.add(folder.getName() + ": sound files found, but pack.json is missing");
                }
            }
        }
        if (soundpacks.isEmpty()) {
            Soundpack fallback = new Soundpack();
            fallback.setName("Cherry MX Red ABS");
            fallback.setAuthor("Better Keys Mod");
            fallback.setVolume(0.85);
            soundpacks.add(fallback);
        }
        return new ArrayList<>(soundpacks);
    }

    private Soundpack readPackFolder(File folder) {
        File packJson = new File(folder, "pack.json");
        if (!packJson.exists()) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(packJson), StandardCharsets.UTF_8)) {
            Soundpack sp = GSON.fromJson(reader, Soundpack.class);
            if (sp != null) {
                if (sp.getSounds() == null) {
                    sp.setSounds(new java.util.LinkedHashMap<>());
                }
                resolveSoundPaths(folder, sp);
            }
            return sp;
        } catch (Exception ex) {
            issues.add(folder.getName() + ": pack.json could not be read");
            System.err.println("Failed to load soundpack '" + folder.getName() + "': " + ex.getMessage());
            return null;
        }
    }

    private boolean hasSoundFilesButNoPackJson(File folder) {
        File[] children = folder.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            if (child.isFile() && isSoundFile(child.getName())) {
                return true;
            }
        }
        File soundsDir = new File(folder, "sounds");
        File[] soundFiles = soundsDir.listFiles();
        if (soundFiles != null) {
            for (File child : soundFiles) {
                if (child.isFile() && isSoundFile(child.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSoundFile(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".aiff")
                || lower.endsWith(".aif") || lower.endsWith(".flac") || lower.endsWith(".mp3");
    }

    private void seedDefaultFromResources() {
        String[] packNames = { "Cherry MX Red ABS" };
        try {
            for (String packName : packNames) {
                Path targetDir = SOUNDPACKS_DIR.resolve(packName);
                if (Files.exists(targetDir)) {
                    continue;
                }
                InputStream packStream = getClass()
                        .getResourceAsStream("/soundpacks/" + packName + "/pack.json");
                if (packStream == null) {
                    System.err.println("Bundled soundpack not found in resources: " + packName);
                    continue;
                }
                String packJson = new String(packStream.readAllBytes(), StandardCharsets.UTF_8);
                if (packJson.startsWith("\uFEFF")) {
                    packJson = packJson.substring(1);
                }
                Soundpack sp = GSON.fromJson(packJson, Soundpack.class);
                if (sp == null || sp.getSounds() == null || sp.getSounds().isEmpty()) {
                    continue;
                }
                Files.createDirectories(targetDir.resolve("sounds"));
                Files.writeString(targetDir.resolve("pack.json"), packJson);
                for (String relative : sp.getSounds().values()) {
                    if (relative == null) {
                        continue;
                    }
                    String fileName = new File(relative).getName();
                    try (InputStream soundStream = getClass()
                            .getResourceAsStream("/soundpacks/" + packName + "/sounds/" + fileName)) {
                        if (soundStream != null) {
                            Files.copy(soundStream,
                                    targetDir.resolve("sounds").resolve(fileName),
                                    StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
                System.out.println("Seeded soundpack '" + packName + "' to " + targetDir);
            }
        } catch (IOException ex) {
            System.err.println("Failed to seed soundpacks: " + ex.getMessage());
        }
    }

    private void resolveSoundPaths(File folder, Soundpack sp) {
        sp.getSounds().replaceAll((key, relative) -> {
            if (relative == null) {
                return null;
            }
            File direct = new File(folder, relative);
            if (direct.exists()) {
                return direct.getAbsolutePath();
            }
            File inSounds = new File(folder, "sounds/" + new File(relative).getName());
            if (inSounds.exists()) {
                return inSounds.getAbsolutePath();
            }
            return relative;
        });
    }

    public Soundpack getActive() {
        return active;
    }

    public void setActive(Soundpack active) {
        this.active = active;
    }

    public List<Soundpack> getSoundpacks() {
        return new ArrayList<>(soundpacks);
    }

    public Soundpack findByName(String name) {
        if (name == null) {
            return null;
        }
        return soundpacks.stream()
                .filter(s -> name.equalsIgnoreCase(s.getName()))
                .findFirst()
                .orElse(null);
    }
}
