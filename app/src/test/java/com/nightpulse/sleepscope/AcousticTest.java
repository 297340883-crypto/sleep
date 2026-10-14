package com.nightpulse.sleepscope;

import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import static org.junit.Assert.*;

public class AcousticTest {
    @Test public void silenceIsNotSnoring() {
        SoundClassifier c = new SoundClassifier(2);
        short[] b = new short[2048];
        for (int i = 0; i < 100; i++) assertEquals(SoundClassifier.QUIET, c.analyze(b, b.length).kind);
    }

    @Test public void loudLowFrequencyTestSignalCanTriggerCandidate() {
        SoundClassifier c = new SoundClassifier(2);
        short[] b = new short[2048];
        for (int i = 0; i < b.length; i++) {
            b[i] = (short)(3500 * Math.sin(i * 2 * Math.PI * 130 / 16000)
                            + 2500 * Math.sin(i * 2 * Math.PI * 240 / 16000));
        }
        assertEquals(SoundClassifier.SNORE, c.analyze(b, b.length).kind);
    }

    @Test public void createsConsistentPcmWavHeader() throws Exception {
        File tmp = File.createTempFile("sleepscope", ".wav");
        try {
            WavClip wav = new WavClip(tmp);
            short[] samples = new short[2048];
            Arrays.fill(samples, (short) 1200);
            wav.write(samples, samples.length);
            wav.close();
            byte[] b = Files.readAllBytes(tmp.toPath());
            assertEquals(44 + samples.length * 2, b.length);
            assertEquals("RIFF", new String(b, 0, 4, "US-ASCII"));
            assertEquals("WAVE", new String(b, 8, 4, "US-ASCII"));
            assertEquals("data", new String(b, 36, 4, "US-ASCII"));
            assertEquals(0x80, b[24] & 255); // 16000 = 0x3E80 little endian
            assertEquals(0x3E, b[25] & 255);
        } finally { tmp.delete(); }
    }
}
