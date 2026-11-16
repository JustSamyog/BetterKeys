package com.justsamyog.betterkeys.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

public class WavGenerator {

    private static final int SAMPLE_RATE = 44100;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    private WavGenerator() {
    }

    public static Map<String, byte[]> generateDefaultPack() {
        Map<String, byte[]> pack = new LinkedHashMap<>();
        pack.put("default_press", click(800.0, 0.070, 0.002, 0.6));
        pack.put("default_release", click(600.0, 0.045, 0.001, 0.45));
        pack.put("space_press", click(400.0, 0.100, 0.003, 0.7));
        pack.put("enter_press", click(300.0, 0.120, 0.004, 0.75));
        pack.put("backspace_press", click(1200.0, 0.050, 0.001, 0.55));
        pack.put("tab_press", click(700.0, 0.065, 0.002, 0.5));
        pack.put("escape_press", click(500.0, 0.055, 0.002, 0.4));
        pack.put("mouse_click", click(1000.0, 0.035, 0.001, 0.6));
        pack.put("mouse_release", click(800.0, 0.030, 0.001, 0.4));
        return pack;
    }

    private static byte[] click(double frequencyHz, double durationSec, double attackSec, double volume) {
        int totalSamples = (int) Math.round(SAMPLE_RATE * durationSec);
        int attackSamples = (int) Math.round(SAMPLE_RATE * attackSec);
        byte[] pcm = new byte[totalSamples * 2];
        double twoPiF = 2.0 * Math.PI * frequencyHz / SAMPLE_RATE;
        double phase = 0.0;
        ByteBuffer buf = ByteBuffer.allocate(pcm.length).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < totalSamples; i++) {
            double envelope = 1.0;
            if (i < attackSamples && attackSamples > 0) {
                envelope = (double) i / attackSamples;
            } else {
                double decayProgress = (double) (i - attackSamples) / Math.max(1, totalSamples - attackSamples);
                envelope = 1.0 - decayProgress;
            }
            if (envelope < 0.0) {
                envelope = 0.0;
            }
            double sample = Math.sin(phase) * envelope * volume;
            phase += twoPiF;
            short s = (short) (sample * Short.MAX_VALUE);
            buf.putShort(s);
        }

        return wrapWav(buf.array());
    }

    private static byte[] wrapWav(byte[] pcm) {
        int dataSize = pcm.length;
        int byteRate = SAMPLE_RATE * 2; 
        int blockAlign = 2;
        int chunkSize = 36 + dataSize;
        byte[] header = new byte[44];

        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        writeIntLE(header, 4, chunkSize);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';

        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        writeIntLE(header, 16, 16);           
        writeShortLE(header, 20, 1);         
        writeShortLE(header, 22, 1);         
        writeIntLE(header, 24, SAMPLE_RATE); 
        writeIntLE(header, 28, byteRate);    
        writeShortLE(header, 32, blockAlign);
        writeShortLE(header, 34, 16);        

        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        writeIntLE(header, 40, dataSize);

        byte[] out = new byte[header.length + pcm.length];
        System.arraycopy(header, 0, out, 0, header.length);
        System.arraycopy(pcm, 0, out, header.length, pcm.length);
        return out;
    }

    private static void writeIntLE(byte[] b, int off, int val) {
        b[off] = (byte) (val & 0xFF);
        b[off + 1] = (byte) ((val >>> 8) & 0xFF);
        b[off + 2] = (byte) ((val >>> 16) & 0xFF);
        b[off + 3] = (byte) ((val >>> 24) & 0xFF);
    }

    private static void writeShortLE(byte[] b, int off, int val) {
        b[off] = (byte) (val & 0xFF);
        b[off + 1] = (byte) ((val >>> 8) & 0xFF);
    }

    public static AudioInputStream toAudioInputStream(byte[] wavBytes)
            throws IOException, javax.sound.sampled.UnsupportedAudioFileException {
        return AudioSystem.getAudioInputStream(new ByteArrayInputStream(wavBytes));
    }

    public static void main(String[] args) throws IOException {
        File outDir = new File(args.length > 0 ? args[0] : "sounds");
        outDir.mkdirs();
        Map<String, byte[]> pack = generateDefaultPack();
        for (Map.Entry<String, byte[]> e : pack.entrySet()) {
            File f = new File(outDir, e.getKey() + ".wav");
            java.nio.file.Files.write(f.toPath(), e.getValue());
            System.out.println("Wrote " + f.getAbsolutePath());
        }
    }
}
