package com.nightpulse.sleepscope;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Incremental mono PCM-16 WAV output. Header is finalized only after last sample. */
public final class WavClip {
    public static final int SAMPLE_RATE = 16000;
    private final File file;
    private FileOutputStream out;
    private long pcmBytes;

    public WavClip(File file) throws IOException {
        this.file = file;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create clip folder");
        out = new FileOutputStream(file);
        out.write(new byte[44]);
    }

    public void write(short[] samples, int length) throws IOException {
        if (out == null) return;
        byte[] bytes = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            bytes[i * 2] = (byte) samples[i];
            bytes[i * 2 + 1] = (byte) (samples[i] >>> 8);
        }
        out.write(bytes);
        pcmBytes += bytes.length;
    }

    public void write(byte[] bytes, int length) throws IOException {
        if (out == null) return;
        out.write(bytes, 0, length);
        pcmBytes += length;
    }

    public long bytes() { return pcmBytes; }
    public File file() { return file; }

    public void close() throws IOException {
        if (out == null) return;
        out.flush();
        out.close();
        out = null;
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(0);
            ascii(raf, "RIFF");
            le(raf, 36 + pcmBytes, 4);
            ascii(raf, "WAVEfmt ");
            le(raf, 16, 4);  // PCM fmt chunk size
            le(raf, 1, 2);   // uncompressed PCM
            le(raf, 1, 2);   // mono
            le(raf, SAMPLE_RATE, 4);
            le(raf, SAMPLE_RATE * 2, 4);
            le(raf, 2, 2);
            le(raf, 16, 2);
            ascii(raf, "data");
            le(raf, pcmBytes, 4);
        }
    }

    private static void ascii(RandomAccessFile raf, String s) throws IOException { raf.write(s.getBytes("US-ASCII")); }
    private static void le(RandomAccessFile raf, long v, int byteCount) throws IOException {
        for (int i = 0; i < byteCount; i++) raf.write((int) ((v >>> (i * 8)) & 255));
    }
}
