package com.nightpulse.sleepscope;

/** Pure-Java, on-device acoustic heuristics. NOT a medical apnea detector or speech recognition model. */
public final class SoundClassifier {
    public static final int QUIET = 0, SNORE = 1, VOICE = 2, OTHER = 3;
    private static final int RATE = 16000;
    private final int sensitivity;
    private double noiseFloor = 100.0;

    public SoundClassifier(int sensitivity) {
        this.sensitivity = Math.max(1, Math.min(3, sensitivity));
    }

    public static final class Result {
        public final int kind;
        public final double rms;
        public final double relativeLevel;
        public final boolean quiet;
        Result(int kind, double rms, double relativeLevel, boolean quiet) {
            this.kind = kind;
            this.rms = rms;
            this.relativeLevel = relativeLevel;
            this.quiet = quiet;
        }
    }

    public Result analyze(short[] samples, int length) {
        if (length < 100) return new Result(QUIET, 0, 0, true);
        double sum = 0;
        int crosses = 0;
        for (int i = 0; i < length; i++) {
            double x = samples[i];
            sum += x * x;
            if (i > 0 && ((samples[i] >= 0) != (samples[i - 1] >= 0))) crosses++;
        }
        double rms = Math.sqrt(sum / length);
        // Follow the room's quiet baseline slowly, without letting loud snoring redefine quiet.
        if (rms < noiseFloor * 1.9) {
            noiseFloor = Math.max(24, noiseFloor * .996 + rms * .004);
        }
        double factor = sensitivity == 1 ? 4.2 : sensitivity == 2 ? 2.8 : 2.1;
        double threshold = Math.max(sensitivity == 3 ? 95 : 145, noiseFloor * factor);
        double rel = Math.min(1, rms / Math.max(1, threshold * 4));
        if (rms < threshold) return new Result(QUIET, rms, rel, true);

        // Frequency features are rough indicators only; environmental noises can be mistaken for voices.
        double low = band(samples, length, 95, 155, 220, 300, 400, 530);
        double mid = band(samples, length, 700, 910, 1160, 1460);
        double high = band(samples, length, 1840, 2350, 3000);
        double total = low + mid + high + 1.e-8;
        double lowRatio = low / total;
        double midRatio = mid / total;
        double crossingRate = crosses / (double) length;
        double periodicity = periodicity(samples, length);

        if (lowRatio > 0.61 && crossingRate < 0.29 && (periodicity > 0.13 || lowRatio > 0.82)) {
            return new Result(SNORE, rms, rel, false);
        }
        if (midRatio > 0.27 && crossingRate < 0.51 && periodicity > 0.10) {
            return new Result(VOICE, rms, rel, false);
        }
        return new Result(OTHER, rms, rel, false);
    }

    private static double band(short[] samples, int n, int... hz) {
        double total = 0;
        for (int frequency : hz) {
            // Goertzel narrow-band estimates: O(n), avoids FFT dependencies and network access.
            double coeff = 2.0 * Math.cos(2.0 * Math.PI * frequency / RATE);
            double q1 = 0, q2 = 0;
            for (int i = 0; i < n; i++) {
                double q0 = samples[i] / 32768.0 + coeff * q1 - q2;
                q2 = q1;
                q1 = q0;
            }
            total += Math.max(0, q1 * q1 + q2 * q2 - coeff * q1 * q2);
        }
        return total;
    }

    private static double periodicity(short[] a, int n) {
        double base = 0;
        for (int i = 0; i < n; i++) base += (double) a[i] * a[i];
        if (base < 1) return 0;
        double max = 0;
        for (int lag = 64; lag <= 180; lag += 4) {
            double c = 0;
            for (int i = lag; i < n; i++) c += (double) a[i] * a[i - lag];
            if (c > max) max = c;
        }
        return max / base;
    }
}
