package typetris.gameplay;

import java.io.ByteArrayInputStream;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;

/**
 * Procedural SFX + light BGM matching {@code web/assets/js/gameplay.js} (Web Audio API): block land,
 * row clear, typing error, power-up, chord stabs at 96 BPM, pause ducking, stop on game over
 * <p>
 * Uses short {@link Clip} bursts only — no external sound files; safe to construct once per
 * {@link GameplayPanel}; call {@link #shutdown()} when leaving gameplay
 *
 * @author CS2212 Group 26, Western University
 */
public final class GameplayAudio {

    private static final float SAMPLE_RATE = 44100f;
    private static final AudioFormat FORMAT =
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false);

    /** Half-step between chord hits in JS: {@code beat * 0.5} at 96 BPM */
    private static final long BGM_STEP_MS = Math.round(500 * 60.0 / 96.0);

    private static final double[][] BGM_CHORDS = {
        {261.63, 329.63, 392.0},
        {293.66, 369.99, 440.0},
        {329.63, 415.3, 493.88},
        {196.0, 246.94, 293.66},
    };

    private final Object startLock = new Object();
    private volatile boolean started;
    private ScheduledExecutorService bgmExec;
    private ScheduledFuture<?> bgmFuture;
    private int bgmChordIndex;

    /** 1.0 = normal; when paused, matches JS ratio {@code 0.05 / 0.28} */
    private volatile double bgmDuckFactor = 1.0;

    /** Constructs audio helper; BGM starts lazily on first SFX or {@link #ensureStarted()}. */
    public GameplayAudio() {
    }

    /** Starts BGM scheduling on first call; first chord runs without extra delay */
    public void ensureStarted() {
        synchronized (startLock) {
            if (started) {
                return;
            }
            started = true;
            bgmExec = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "typetris-bgm");
                t.setDaemon(true);
                return t;
            });
            bgmFuture =
                    bgmExec.scheduleAtFixedRate(this::playBgmChordStep, 0, BGM_STEP_MS, TimeUnit.MILLISECONDS);
        }
    }

    /** JS {@code setBgmPaused}: quieter chords while paused */
    public void setBgmPaused(boolean paused) {
        bgmDuckFactor = paused ? (0.05 / 0.28) : 1.0;
    }

    /** JS {@code stopBgm} on game over */
    public void stopBgm() {
        synchronized (startLock) {
            if (bgmFuture != null) {
                bgmFuture.cancel(false);
                bgmFuture = null;
            }
            if (bgmExec != null) {
                bgmExec.shutdown();
                bgmExec = null;
            }
            started = false;
        }
    }

    /** Cancel BGM thread when leaving the gameplay screen */
    public void shutdown() {
        stopBgm();
    }

    /** Scheduled callback: plays one short chord and advances the rotating progression. */
    private void playBgmChordStep() {
        if (!started || bgmExec == null || bgmExec.isShutdown()) {
            return;
        }
        double beat = 60.0 / 96.0;
        double chordDur = beat * 0.55;
        int samples = (int) (SAMPLE_RATE * chordDur);
        if (samples < 64) {
            return;
        }
        double[] freqs = BGM_CHORDS[Math.floorMod(bgmChordIndex++, BGM_CHORDS.length)];
        double master = 0.28 * 0.9 * bgmDuckFactor;
        byte[] pcm = renderChordSine(freqs, samples, chordDur, 0.12 * master);
        playClipAsync(pcm);
    }

    /** Low thud + bright tail when a piece locks to the stack. */
    public void playBlockLand() {
        ensureStarted();
        int n1 = msToSamples(120);
        int n2 = msToSamples(60);
        double[] a = renderSquare(140, n1, 0.22);
        double[] b = renderTriangle(320, n2, 0.08);
        byte[] pcm = mixOffset(a, b, msToSamples(10));
        playClipAsync(pcm);
    }

    /**
     * Ascending chord stinger scaled by how many lines cleared at once.
     *
     * @param clearedCount line clear count (at least 1)
     */
    public void playRowClear(int clearedCount) {
        ensureStarted();
        int c = Math.max(1, clearedCount);
        double base = 260 + (c - 1) * 60.0;
        int n38 = msToSamples(380);
        int n30 = msToSamples(300);
        int n24 = msToSamples(240);
        double[] t1 = renderTriangle(base, n38, 0.22);
        double[] t2 = renderTriangle(base * 1.25, n30, 0.16);
        double[] t3 = renderTriangle(base * 1.5, n24, 0.14);
        double[] m = mixDouble(mixDouble(t1, t2, msToSamples(30)), t3, msToSamples(60));
        playClipAsync(toPcm16(m));
    }

    /** Harsh sweep when the player mistypes. */
    public void playTypingError() {
        ensureStarted();
        int nErr = msToSamples(90);
        double[] sq = renderSquareSweep(620, 300, nErr, 0.24);
        int nSaw = msToSamples(50);
        double[] sw = renderSawSweep(820, 520, nSaw, 0.16);
        byte[] pcm = mixOffset(sq, sw, msToSamples(15));
        playClipAsync(pcm);
    }

    /** Upward gliss when a power-up activates. */
    public void playPowerUp() {
        ensureStarted();
        int n = msToSamples(220);
        byte[] pcm = toPcm16(renderTriangleGliss(330, 740, n, 0.16));
        playClipAsync(pcm);
    }

    /** @param ms duration in milliseconds */
    private static int msToSamples(int ms) {
        return Math.max(1, (int) (SAMPLE_RATE * ms / 1000.0));
    }

    /**
     * Three sines with per-sample exponential envelope (attack + decay), same spirit as JS.
     *
     * @param freqs chord partials in Hz
     * @param samples PCM length
     * @param chordDur chord duration in seconds
     * @param peakAmp peak amplitude scale
     * @return 16-bit mono PCM bytes
     */
    private static byte[] renderChordSine(double[] freqs, int samples, double chordDur, double peakAmp) {
        double[] acc = new double[samples];
        double attack = Math.min(0.03, chordDur * 0.2);
        for (double f : freqs) {
            for (int n = 0; n < samples; n++) {
                double t = n / SAMPLE_RATE;
                double env = envelopeChord(t, chordDur, attack);
                acc[n] += Math.sin(2.0 * Math.PI * f * t) * env * peakAmp;
            }
        }
        return toPcm16(acc);
    }

    /** ADSR-style envelope factor for BGM chord grains. */
    private static double envelopeChord(double t, double chordDur, double attack) {
        double a = smoothstep(t / attack);
        double d = Math.exp(-t / (chordDur * 0.35));
        return Math.max(1e-4, a * d);
    }

    /** Hermite smoothstep on [0,1]. */
    private static double smoothstep(double x) {
        if (x <= 0) {
            return 0;
        }
        if (x >= 1) {
            return 1;
        }
        return x * x * (3 - 2 * x);
    }

    /** Band-limited square-ish tone with exponential envelope. */
    private static double[] renderSquare(double freq, int samples, double peak) {
        double[] o = new double[samples];
        for (int n = 0; n < samples; n++) {
            double t = n / SAMPLE_RATE;
            double env = expEnv(t, samples / SAMPLE_RATE, peak);
            double ph = 2.0 * Math.PI * freq * t;
            o[n] = Math.signum(Math.sin(ph)) * env;
        }
        return o;
    }

    /** Triangle wave tone with exponential envelope. */
    private static double[] renderTriangle(double freq, int samples, double peak) {
        double[] o = new double[samples];
        for (int n = 0; n < samples; n++) {
            double t = n / SAMPLE_RATE;
            double env = expEnv(t, samples / SAMPLE_RATE, peak);
            double phase = frac(freq * t);
            double tri = 2.0 * Math.abs(2.0 * phase - 1.0) - 1.0;
            o[n] = tri * env;
        }
        return o;
    }

    /** Fractional part in [0,1). */
    private static double frac(double x) {
        return x - Math.floor(x);
    }

    /** Square wave with linear frequency sweep from {@code f0} to {@code f1}. */
    private static double[] renderSquareSweep(double f0, double f1, int samples, double peak) {
        double[] o = new double[samples];
        double phase = 0;
        for (int n = 0; n < samples; n++) {
            double t = n / SAMPLE_RATE;
            double tTot = samples / SAMPLE_RATE;
            double freq = f0 + (f1 - f0) * (n / (double) Math.max(1, samples - 1));
            double env = expEnv(t, tTot * 0.9, peak);
            phase += 2.0 * Math.PI * freq / SAMPLE_RATE;
            o[n] = Math.signum(Math.sin(phase)) * env;
        }
        return o;
    }

    /** Saw wave with linear frequency sweep. */
    private static double[] renderSawSweep(double f0, double f1, int samples, double peak) {
        double[] o = new double[samples];
        for (int n = 0; n < samples; n++) {
            double t = n / SAMPLE_RATE;
            double tTot = samples / SAMPLE_RATE;
            double freq = f0 + (f1 - f0) * (n / (double) Math.max(1, samples - 1));
            double env = expEnv(t, tTot * 0.85, peak);
            double phase = frac(freq * t);
            o[n] = (2.0 * phase - 1.0) * env;
        }
        return o;
    }

    /** Triangle glissando between two frequencies. */
    private static double[] renderTriangleGliss(double f0, double f1, int samples, double peak) {
        double[] o = new double[samples];
        for (int n = 0; n < samples; n++) {
            double t = n / SAMPLE_RATE;
            double tTot = samples / SAMPLE_RATE;
            double freq = f0 + (f1 - f0) * (n / (double) Math.max(1, samples - 1));
            double env = expEnv(t, tTot, peak);
            double phase = frac(freq * t);
            double tri = 2.0 * Math.abs(2.0 * phase - 1.0) - 1.0;
            o[n] = tri * env;
        }
        return o;
    }

    /** Attack/decay envelope for one-shot SFX. */
    private static double expEnv(double t, double duration, double peak) {
        double a = 1.0 - Math.exp(-t * 120);
        double d = Math.exp(-(duration - t) * 80);
        return peak * a * Math.max(1e-4, d);
    }

    /** Sample-wise sum of {@code a} and {@code b} placed at {@code offsetB}. */
    private static double[] mixDouble(double[] a, double[] b, int offsetB) {
        int len = Math.max(a.length, offsetB + b.length);
        double[] m = new double[len];
        for (int i = 0; i < a.length; i++) {
            m[i] += a[i];
        }
        for (int i = 0; i < b.length; i++) {
            int j = i + offsetB;
            if (j < len) {
                m[j] += b[i];
            }
        }
        return m;
    }

    /** {@link #mixDouble(double[], double[], int)} then {@link #toPcm16(double[])}. */
    private static byte[] mixOffset(double[] a, double[] b, int offsetB) {
        return toPcm16(mixDouble(a, b, offsetB));
    }

    /** Converts float samples in [-1,1] to little-endian 16-bit PCM bytes. */
    private static byte[] toPcm16(double[] samples) {
        byte[] out = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            double x = Math.max(-1.0, Math.min(1.0, samples[i]));
            short s = (short) (x * 32767.0);
            out[i * 2] = (byte) (s & 0xff);
            out[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
        }
        return out;
    }

    /** Plays raw PCM on a short-lived daemon thread. */
    private void playClipAsync(byte[] pcm) {
        Objects.requireNonNull(pcm);
        if (pcm.length < 4) {
            return;
        }
        new Thread(() -> playClipSync(pcm), "typetris-sfx").start();
    }

    /** Opens a {@link Clip}, plays, and closes on STOP. */
    private static void playClipSync(byte[] pcm) {
        try {
            long frames = pcm.length / FORMAT.getFrameSize();
            ByteArrayInputStream bais = new ByteArrayInputStream(pcm);
            AudioInputStream ais = new AudioInputStream(bais, FORMAT, frames);
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            clip.addLineListener(
                    ev -> {
                        if (ev.getType() == LineEvent.Type.STOP) {
                            clip.close();
                            try {
                                ais.close();
                            } catch (Exception ignored) {
                            }
                        }
                    });
            clip.start();
        } catch (Exception ignored) {
            // No audio device or unsupported line — same as web catching audio failures.
        }
    }
}
