package com.justsamyog.betterkeys.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AudioEngine {
    private static final int CLIP_POOL_SIZE = 4;

    private final Map<String, ClipPool> pools = new ConcurrentHashMap<>();
    private final Map<String, byte[]> wavCache = new ConcurrentHashMap<>();
    private final Map<String, AudioFormat> formats = new ConcurrentHashMap<>();
    private volatile double masterVolume = 1.0;
    private volatile boolean muted = false;
    private volatile String selectedLineName = null; 
    private final Object poolLock = new Object();
    private final ScheduledExecutorService audioTasks = Executors.newSingleThreadScheduledExecutor(
            new java.util.concurrent.ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "Better Keys-AudioTasks");
                    t.setDaemon(true);
                    return t;
                }
            });

    public static java.util.List<MixerLine> getOutputLines() {
        java.util.List<MixerLine> outputs = new java.util.ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                Line.Info[] sourceLines = mixer.getSourceLineInfo();
                boolean hasClip = false;
                for (Line.Info li : sourceLines) {
                    if (li.getLineClass().isAssignableFrom(Clip.class)) {
                        hasClip = true;
                        break;
                    }
                }
                if (hasClip) {
                    outputs.add(new MixerLine(info, info.getName()));
                }
            } catch (Exception ignored) {
            }
        }
        return outputs;
    }

    public boolean load(String soundId, String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("Sound file not found: " + filePath);
            return false;
        }
        try {
            byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
            AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
            AudioFormat format = ais.getFormat();
            formats.put(soundId, format);
            wavCache.put(soundId, data);
            invalidatePool(soundId);
            scheduleWarm(soundId);
            System.out.println("Loaded sound '" + soundId + "' from " + filePath + " (" + data.length + " bytes)");
            return true;
        } catch (UnsupportedAudioFileException | IOException ex) {
            System.err.println("Failed to load sound '" + soundId + "': " + ex.getMessage());
            return false;
        }
    }

    public boolean load(String soundId, byte[] wavBytes) {
        try {
            AudioInputStream ais = WavGenerator.toAudioInputStream(wavBytes);
            AudioFormat format = ais.getFormat();
            formats.put(soundId, format);
            wavCache.put(soundId, wavBytes.clone());
            invalidatePool(soundId);
            scheduleWarm(soundId);
            System.out.println("Loaded synthetic sound '" + soundId + "' (" + wavBytes.length + " bytes)");
            return true;
        } catch (Exception ex) {
            System.err.println("Failed to load synthetic sound '" + soundId + "': " + ex.getMessage());
            return false;
        }
    }

    public void play(String soundId) {
        if (muted || soundId == null) {
            return;
        }
        byte[] data = wavCache.get(soundId);
        if (data == null) {
            return;
        }
        ClipPool pool = pools.get(poolKey(soundId));
        if (pool == null) {
            ensurePool(soundId);
            pool = pools.get(poolKey(soundId));
        }
        if (pool != null) {
            Clip clip = pool.acquire();
            if (clip != null) {
                try {
                    if (clip.isRunning()) {
                        clip.stop();
                    }
                    clip.setFramePosition(0);
                    applyGain(clip);
                    clip.start();
                } catch (Exception ex) {
                    System.err.println("Failed to play sound '" + soundId + "': " + ex.getMessage());
                }
                return;
            }
        }
        playPerOpen(soundId, data);
    }

    private void playPerOpen(String soundId, byte[] data) {
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
            AudioFormat format = ais.getFormat();
            DataLine.Info info = new DataLine.Info(Clip.class, format);
            Mixer mixer = mixerForLine();
            Clip target;
            if (mixer != null && mixer.isLineSupported(info)) {
                target = (Clip) mixer.getLine(info);
            } else {
                target = AudioSystem.getClip();
            }
            target.open(ais);
            target.setFramePosition(0);
            applyGain(target);
            target.start();
            final Clip closeMe = target;
            audioTasks.schedule(() -> {
                try {
                    closeMe.stop();
                    closeMe.close();
                } catch (Exception ignored) {
                }
            }, 3, TimeUnit.SECONDS);
        } catch (Exception ex) {
            System.err.println("Failed to play sound '" + soundId + "': " + ex.getMessage());
        }
    }

    private void applyGain(Clip clip) {
        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            double gainDb = 20.0 * Math.log10(Math.max(0.0001, masterVolume));
            gain.setValue((float) gainDb);
        } catch (IllegalArgumentException ex) {
        }
    }

    public void setMasterVolume(double volume) {
        this.masterVolume = Math.max(0.0, Math.min(1.0, volume));
    }

    public double getMasterVolume() {
        return masterVolume;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setSelectedLine(String lineName) {
        if (java.util.Objects.equals(this.selectedLineName, lineName)) {
            return;
        }
        this.selectedLineName = lineName;
        synchronized (poolLock) {
            pools.values().forEach(ClipPool::close);
            pools.clear();
        }
        for (String id : wavCache.keySet()) {
            scheduleWarm(id);
        }
    }

    public String getSelectedLine() {
        return selectedLineName;
    }

    public void stopAll() {
        pools.values().forEach(ClipPool::stopAll);
    }

    public void shutdown() {
        stopAll();
        audioTasks.shutdownNow();
        pools.values().forEach(ClipPool::close);
        pools.clear();
        wavCache.clear();
        formats.clear();
    }

    private void ensurePool(String soundId) {
        synchronized (poolLock) {
            String key = poolKey(soundId);
            if (pools.containsKey(key)) {
                return;
            }
            byte[] data = wavCache.get(soundId);
            AudioFormat format = formats.get(soundId);
            if (data == null || format == null) {
                return;
            }
            try {
                int poolSize = wavCache.size() > 64 ? 1 : CLIP_POOL_SIZE;
                pools.put(key, new ClipPool(mixerForLine(), data, format, poolSize));
            } catch (Exception ex) {
                System.err.println("Failed to open clip pool for '" + soundId + "': " + ex.getMessage());
            }
        }
    }

    private void scheduleWarm(String soundId) {
        audioTasks.execute(() -> ensurePool(soundId));
    }

    private void invalidatePool(String soundId) {
        synchronized (poolLock) {
            String suffix = "|" + soundId;
            pools.keySet().removeIf(k -> {
                if (k.endsWith(suffix)) {
                    ClipPool stale = pools.get(k);
                    if (stale != null) {
                        stale.close();
                    }
                    return true;
                }
                return false;
            });
        }
    }

    private String poolKey(String soundId) {
        String line = selectedLineName;
        return (line == null ? "" : line) + "|" + soundId;
    }

    private Mixer mixerForLine() {
        String line = selectedLineName;
        if (line == null) {
            return null;
        }
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            if (mi.getName().equals(line)) {
                return AudioSystem.getMixer(mi);
            }
        }
        return null;
    }

    private static class ClipPool {
        private final Clip[] clips;
        private int index = 0;

        ClipPool(Mixer mixer, byte[] data, AudioFormat format, int size) throws LineUnavailableException {
            DataLine.Info info = new DataLine.Info(Clip.class, format);
            Clip[] opened = new Clip[size];
            int n = 0;
            for (int i = 0; i < size; i++) {
                try {
                    Clip c;
                    if (mixer != null) {
                        if (!mixer.isLineSupported(info)) {
                            break;
                        }
                        c = (Clip) mixer.getLine(info);
                    } else {
                        c = AudioSystem.getClip();
                    }
                    c.open(AudioSystem.getAudioInputStream(new ByteArrayInputStream(data)));
                    opened[n++] = c;
                } catch (Exception ex) {
                    break;
                }
            }
            if (n == 0) {
                throw new LineUnavailableException("No clip lines available");
            }
            this.clips = java.util.Arrays.copyOf(opened, n);
        }

        Clip acquire() {
            Clip clip = clips[index];
            index = (index + 1) % clips.length;
            return clip;
        }

        void stopAll() {
            for (Clip c : clips) {
                if (c.isRunning()) {
                    c.stop();
                }
            }
        }

        void close() {
            for (Clip c : clips) {
                try {
                    c.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static final class MixerLine {
        public final Mixer.Info info;
        public final String name;
        public MixerLine(Mixer.Info info, String name) {
            this.info = info;
            this.name = name;
        }
        @Override
        public String toString() {
            return name;
        }
    }
}
