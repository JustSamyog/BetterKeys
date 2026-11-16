package com.justsamyog.betterkeys.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PackConverter {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final long MAX_DECODED_BYTES = 512L * 1024 * 1024;

    private PackConverter() {
    }

    public static List<String> convertIfNeeded(File folder) {
        File configJson = new File(folder, "config.json");
        if (!configJson.isFile()) {
            return null;
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(Files.readString(configJson.toPath(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception ex) {
            return List.of("config.json could not be read");
        }
        String audioName = stringAt(root, "audio_file");
        if (audioName == null || audioName.isBlank()) {
            return null;
        }
        File audioFile = new File(folder, audioName);
        if (!audioFile.isFile()) {
            return List.of("Audio file '" + audioName + "' is missing from the pack folder");
        }
        File packJson = new File(folder, "pack.json");
        Map<String, String> current = readSounds(packJson);
        if (current != null && isConverted(current, audioName)) {
            return null;
        }
        JsonElement defsEl = root.get("definitions");
        if (defsEl == null || !defsEl.isJsonObject()) {
            return List.of("config.json has no key definitions");
        }
        JsonObject definitions = defsEl.getAsJsonObject();
        String configAuthor = stringAt(root, "author");

        AudioFormat pcmFormat;
        byte[] decoded;
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(audioFile)) {
            AudioFormat src = raw.getFormat();
            pcmFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16,
                    src.getChannels(), src.getChannels() * 2, src.getSampleRate(), false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(pcmFormat, raw)) {
                byte[] read = readAll(pcm);
                if (read == null) {
                    return List.of("Audio file is too large to convert");
                }
                decoded = read;
            }
        } catch (javax.sound.sampled.UnsupportedAudioFileException ex) {
            return List.of("Audio format is not supported by Better Keys");
        } catch (Exception ex) {
            return List.of("Audio file could not be read");
        }

        List<String> issues = new ArrayList<>();
        Map<String, String> sounds = new LinkedHashMap<>();
        Map<String, List<String>> variants = new LinkedHashMap<>();
        Map<String, Integer> counters = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        int slices = 0;
        try {
            Files.createDirectories(new File(folder, "sounds").toPath());
        } catch (Exception ex) {
            return List.of("The pack folder could not be written");
        }

        for (Map.Entry<String, JsonElement> def : definitions.entrySet()) {
            String vc = vcIdFor(def.getKey());
            if (vc == null) {
                skipped.add(def.getKey());
                continue;
            }
            List<double[]> ranges = timingRanges(def.getValue());
            if (ranges.isEmpty()) {
                skipped.add(def.getKey());
                continue;
            }
            String slug = slugFor(vc);
            int base = counters.getOrDefault(slug, 0);
            List<String> ids = new ArrayList<>();
            for (double[] range : ranges) {
                base++;
                String sid = "pk_" + slug + "_" + base;
                if (writeSlice(new File(folder, "sounds/" + sid + ".wav"), decoded, pcmFormat,
                        range[0], range[1])) {
                    ids.add(sid);
                    sounds.put(sid, "sounds/" + sid + ".wav");
                    slices++;
                }
            }
            counters.put(slug, base);
            if (ids.isEmpty()) {
                skipped.add(def.getKey());
            } else {
                sounds.put(vc, ids.get(0));
                List<String> existing = variants.get(vc);
                if (existing == null) {
                    variants.put(vc, ids);
                } else {
                    existing.addAll(ids);
                }
            }
        }

        if (variants.isEmpty()) {
            return List.of("No keys in this pack are supported by Better Keys");
        }
        if (!skipped.isEmpty()) {
            issues.add(skipNote(skipped));
        }
        Soundpack pack = readExisting(packJson);
        if (pack == null) {
            pack = new Soundpack();
            pack.setName(folder.getName());
            pack.setAuthor(configAuthor != null ? configAuthor : "Unknown");
            pack.setVolume(0.85);
        }
        if (pack.getName() == null || pack.getName().isBlank()) {
            pack.setName(folder.getName());
        }
        if (pack.getAuthor() == null || pack.getAuthor().isBlank()) {
            pack.setAuthor(configAuthor != null ? configAuthor : "Unknown");
        }
        if (pack.getVolume() <= 0) {
            pack.setVolume(0.85);
        }
        pack.setSounds(sounds);
        pack.setKeyVariants(variants);
        try {
            Files.writeString(packJson.toPath(), PRETTY.toJson(pack), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return List.of("Converted audio could not be saved");
        }
        System.out.println("Converted sound pack '" + folder.getName() + "': "
                + variants.size() + " keys, " + slices + " slices");
        return issues;
    }

    private static boolean isConverted(Map<String, String> current, String audioName) {
        boolean refsAudio = false;
        for (Map.Entry<String, String> e : current.entrySet()) {
            if (e.getKey() != null && e.getKey().startsWith("pk_")) {
                return true;
            }
            String value = e.getValue();
            if (value != null && (value.equalsIgnoreCase(audioName)
                    || new File(value).getName().equalsIgnoreCase(audioName))) {
                refsAudio = true;
            }
        }
        return !refsAudio;
    }

    private static Map<String, String> readSounds(File packJson) {
        if (!packJson.isFile()) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(
                    Files.readString(packJson.toPath(), StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return null;
            }
            JsonElement sounds = root.getAsJsonObject().get("sounds");
            if (sounds == null || !sounds.isJsonObject()) {
                return null;
            }
            Map<String, String> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : sounds.getAsJsonObject().entrySet()) {
                map.put(e.getKey(), e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : null);
            }
            return map;
        } catch (Exception ex) {
            return null;
        }
    }

    private static Soundpack readExisting(File packJson) {
        if (!packJson.isFile()) {
            return null;
        }
        try {
            return GSON.fromJson(Files.readString(packJson.toPath(), StandardCharsets.UTF_8), Soundpack.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private static byte[] readAll(AudioInputStream stream) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 22);
        byte[] buffer = new byte[1 << 16];
        long total = 0;
        int n;
        while ((n = stream.read(buffer)) != -1) {
            total += n;
            if (total > MAX_DECODED_BYTES) {
                return null;
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static boolean writeSlice(File target, byte[] decoded, AudioFormat format,
                                      double startMs, double endMs) {
        int frameSize = format.getFrameSize();
        if (frameSize <= 0) {
            return false;
        }
        double rate = format.getSampleRate();
        long totalFrames = decoded.length / frameSize;
        long startFrame = Math.round(startMs * rate / 1000.0);
        long endFrame = Math.round(endMs * rate / 1000.0);
        if (startFrame < 0) {
            startFrame = 0;
        }
        if (endFrame > totalFrames) {
            endFrame = totalFrames;
        }
        long frames = endFrame - startFrame;
        if (frames <= 0 || frames * 1000.0 / rate < 5) {
            return false;
        }
        long fromLong = startFrame * (long) frameSize;
        long toLong = endFrame * (long) frameSize;
        if (toLong > decoded.length) {
            toLong = decoded.length;
        }
        if (fromLong >= toLong || toLong > Integer.MAX_VALUE) {
            return false;
        }
        byte[] slice = java.util.Arrays.copyOfRange(decoded, (int) fromLong, (int) toLong);
        try (AudioInputStream sliceStream = new AudioInputStream(
                new ByteArrayInputStream(slice), format, slice.length / (long) frameSize)) {
            AudioSystem.write(sliceStream, AudioFileFormat.Type.WAVE, target);
            return true;
        } catch (Exception ex) {
            System.err.println("Failed to write slice: " + ex.getMessage());
            return false;
        }
    }

    private static List<double[]> timingRanges(JsonElement def) {
        List<double[]> ranges = new ArrayList<>();
        if (def == null || !def.isJsonObject()) {
            return ranges;
        }
        JsonObject obj = def.getAsJsonObject();
        JsonElement timing = obj.get("timing");
        if (timing == null && obj.get("down") != null && obj.get("down").isJsonObject()) {
            timing = obj.getAsJsonObject("down").get("timing");
        }
        if (timing == null || !timing.isJsonArray()) {
            return ranges;
        }
        for (JsonElement item : timing.getAsJsonArray()) {
            if (item == null || !item.isJsonArray()) {
                continue;
            }
            JsonArray arr = item.getAsJsonArray();
            if (arr.size() < 2) {
                continue;
            }
            Double start = asDouble(arr.get(0));
            Double end = asDouble(arr.get(1));
            if (start == null || end == null || end <= start) {
                continue;
            }
            ranges.add(new double[] {start, end});
        }
        return ranges;
    }

    private static Double asDouble(JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) {
            return null;
        }
        try {
            return e.getAsDouble();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String stringAt(JsonObject root, String member) {
        JsonElement el = root.get(member);
        if (el == null || !el.isJsonPrimitive()) {
            return null;
        }
        try {
            return el.getAsString();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String skipNote(List<String> skipped) {
        int shown = Math.min(5, skipped.size());
        String names = String.join(", ", skipped.subList(0, shown));
        int extra = skipped.size() - shown;
        return "Skipped " + skipped.size() + " key(s) Better Keys does not capture: " + names
                + (extra > 0 ? " (+ " + extra + " more)" : "");
    }

    private static String slugFor(String vc) {
        String slug = vc.toLowerCase(Locale.ROOT);
        if (slug.startsWith("vc_")) {
            slug = slug.substring(3);
        }
        return slug;
    }

    private static String vcIdFor(String rawName) {
        if (rawName == null) {
            return null;
        }
        String n = rawName.trim();
        if (n.length() == 4 && n.startsWith("Key")) {
            char c = n.charAt(3);
            if (c >= 'A' && c <= 'Z') {
                return "VC_" + c;
            }
        }
        if (n.length() == 6 && n.startsWith("Digit")) {
            char c = n.charAt(5);
            if (c >= '0' && c <= '9') {
                return "VC_" + c;
            }
        }
        if (n.matches("F\\d{1,2}")) {
            int f = Integer.parseInt(n.substring(1));
            if (f >= 1 && f <= 12) {
                return "VC_" + n;
            }
        }
        switch (n) {
            case "Space" -> { return "VC_SPACE"; }
            case "Enter" -> { return "VC_ENTER"; }
            case "Backspace" -> { return "VC_BACKSPACE"; }
            case "Tab" -> { return "VC_TAB"; }
            case "Escape" -> { return "VC_ESCAPE"; }
            case "ShiftLeft", "ShiftRight" -> { return "VC_SHIFT"; }
            case "ControlLeft", "ControlRight" -> { return "VC_CONTROL"; }
            case "AltLeft", "AltRight" -> { return "VC_ALT"; }
            case "MetaLeft", "MetaRight" -> { return "VC_META"; }
            case "CapsLock" -> { return "VC_CAPS_LOCK"; }
            case "NumLock" -> { return "VC_NUM_LOCK"; }
            case "Pause" -> { return "VC_PAUSE"; }
            case "PrintScreen" -> { return "VC_PRINTSCREEN"; }
            case "ScrollLock" -> { return "VC_SCROLL_LOCK"; }
            case "ContextMenu" -> { return "VC_CONTEXT_MENU"; }
            case "Minus" -> { return "VC_MINUS"; }
            case "Equal" -> { return "VC_EQUALS"; }
            case "BracketLeft" -> { return "VC_OPEN_BRACKET"; }
            case "BracketRight" -> { return "VC_CLOSE_BRACKET"; }
            case "Semicolon" -> { return "VC_SEMICOLON"; }
            case "Quote" -> { return "VC_QUOTE"; }
            case "Backslash" -> { return "VC_BACK_SLASH"; }
            case "Comma" -> { return "VC_COMMA"; }
            case "Period" -> { return "VC_PERIOD"; }
            case "Slash" -> { return "VC_SLASH"; }
            case "Backquote" -> { return "VC_BACKQUOTE"; }
            case "Home" -> { return "VC_HOME"; }
            case "End" -> { return "VC_END"; }
            case "PageUp" -> { return "VC_PAGE_UP"; }
            case "PageDown" -> { return "VC_PAGE_DOWN"; }
            case "Insert" -> { return "VC_INSERT"; }
            case "Delete" -> { return "VC_DELETE"; }
            case "ArrowUp" -> { return "VC_UP"; }
            case "ArrowDown" -> { return "VC_DOWN"; }
            case "ArrowLeft" -> { return "VC_LEFT"; }
            case "ArrowRight" -> { return "VC_RIGHT"; }
            case "Mouse1" -> { return "mouse_click"; }
        }
        return null;
    }
}
