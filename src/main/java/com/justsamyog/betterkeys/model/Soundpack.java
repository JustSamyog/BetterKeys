package com.justsamyog.betterkeys.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Soundpack {
    private String name = "Default";
    private String author = "Default";
    private double volume = 0.85;
    private Map<String, String> sounds = new LinkedHashMap<>();
    private Map<String, List<String>> keyVariants;
    private final List<String> keyVariations = new ArrayList<>();
    private final transient Random random = new Random();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public Map<String, String> getSounds() {
        return sounds;
    }

    public void setSounds(Map<String, String> sounds) {
        this.sounds = sounds;
        rebuildKeyVariations();
    }

    private void rebuildKeyVariations() {
        keyVariations.clear();
        if (sounds == null) {
            return;
        }
        for (String key : sounds.keySet()) {
            if (key != null && key.matches("key_\\d+")) {
                keyVariations.add(key);
            }
        }
        keyVariations.sort(String::compareTo);
    }

    public String getSoundPath(String key) {
        if (sounds == null) {
            return null;
        }
        return sounds.get(key);
    }

    public String getRandomKeySound() {
        if (!keyVariations.isEmpty()) {
            return keyVariations.get(random.nextInt(keyVariations.size()));
        }
        return "default_press";
    }

    public Map<String, List<String>> getKeyVariants() {
        return keyVariants;
    }

    public void setKeyVariants(Map<String, List<String>> keyVariants) {
        this.keyVariants = keyVariants;
    }

    public String randomVariant(String id) {
        String mapped = getSoundPath(id);
        if (mapped == null) {
            return null;
        }
        if (keyVariants != null) {
            List<String> variants = keyVariants.get(id);
            if (variants != null && !variants.isEmpty() && variants.contains(mapped)) {
                String pick = variants.get(random.nextInt(variants.size()));
                if (getSoundPath(pick) != null) {
                    return pick;
                }
            }
        }
        return sounds.containsKey(mapped) ? mapped : id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Soundpack)) {
            return false;
        }
        Soundpack other = (Soundpack) obj;
        return name != null && other.name != null && name.equalsIgnoreCase(other.name);
    }

    @Override
    public int hashCode() {
        return name == null ? 0 : name.toLowerCase(java.util.Locale.ROOT).hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
