import com.inspyresoftworks.ti84evo.model.EvoScreenCapture;
import com.inspyresoftworks.ti84evo.protocol.EvoLink;
import com.inspyresoftworks.ti84evo.transport.EvoSerialTransport;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.jcodec.api.awt.AWTSequenceEncoder;

/** Read-only, persistent-connection screen capture rate probe. Run with the CLI jar on the classpath. */
class EvoScreenBenchmark {
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final int MAX_STATS_SAMPLES = 2048;
    private static final int VIDEO_FPS = 4;

    public static void main(String[] args) throws Exception {
        int seconds = 15;
        boolean preview = false;
        boolean continuous = false;
        boolean mp4 = false;
        Path saveDir = null;
        Path encodeDir = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--seconds" -> seconds = Integer.parseInt(args[++i]);
                case "--preview" -> preview = true;
                case "--continuous" -> continuous = true;
                case "--mp4" -> mp4 = true;
                case "--save-dir" -> saveDir = Path.of(args[++i]);
                case "--encode-dir" -> encodeDir = Path.of(args[++i]);
                case "--help" -> {
                    System.out.println("Usage: java -cp build/libs/ti84-evo-cli.jar scripts/utils/EvoScreenBenchmark.java "
                        + "[--seconds 15] [--preview] [--save-dir NEW_DIRECTORY] [--mp4] [--continuous --preview] "
                        + "[--encode-dir EXISTING_RECORDING]");
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        if (encodeDir != null) {
            if (continuous || preview || saveDir != null || mp4) {
                throw new IllegalArgumentException("--encode-dir must be used by itself");
            }
            long frames;
            try (var lines = Files.lines(encodeDir.resolve("frames.csv"))) {
                frames = lines.skip(1).count();
            }
            if (frames < 1 || frames > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("The recording has no frames or is too large");
            }
            encodeMp4(encodeDir, (int) frames);
            return;
        }
        if (!continuous && (seconds < 1 || seconds > 120)) {
            throw new IllegalArgumentException("--seconds must be 1..120");
        }
        if (continuous && !preview) throw new IllegalArgumentException("--continuous requires --preview");
        if (mp4 && saveDir == null) throw new IllegalArgumentException("--mp4 requires --save-dir");
        if (saveDir != null) Files.createDirectory(saveDir);

        AtomicBoolean stopRequested = new AtomicBoolean(false);
        if (continuous) {
            Thread stopListener = new Thread(() -> {
                try {
                    if (System.in.read() >= 0) stopRequested.set(true);
                } catch (Exception ignored) {
                    // Closing the preview window still ends the run.
                }
            }, "TI-84 Evo screen capture stop listener");
            stopListener.setDaemon(true);
            stopListener.start();
        }

        JLabel display = preview ? new JLabel("Waiting for first frame", SwingConstants.CENTER) : null;
        JFrame window = preview ? new JFrame("TI-84 Evo screen capture benchmark") : null;
        if (window != null) {
            SwingUtilities.invokeAndWait(() -> {
                window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                window.add(display);
                window.setSize(660, 520);
                window.setLocationByPlatform(true);
                window.setVisible(true);
            });
        }

        List<Double> captureMs = new ArrayList<>();
        List<Double> frameGapMs = new ArrayList<>();
        long previousHash = -1;
        long previousComplete = -1;
        int changedFrames = 0;
        int frame = 0;

        try (BufferedWriter csv = saveDir == null ? null : Files.newBufferedWriter(saveDir.resolve("frames.csv"));
             EvoSerialTransport transport = EvoSerialTransport.Companion.auto(5_000)) {
            if (csv != null) {
                csv.write("frame,start_ms,complete_ms,capture_ms,render_ms,preview_ms,save_ms,changed\n");
                csv.flush();
            }
            transport.open();
            EvoLink link = new EvoLink(transport);
            // Warm up the protocol and JVM before measuring sustained capture rate.
            link.getScreenCapture();
            long started = System.nanoTime();
            long deadline = continuous ? Long.MAX_VALUE : started + seconds * 1_000_000_000L;
            long lastProgress = started;
            System.out.println(continuous
                ? "Connected; live preview continues until its window closes or Stop is pressed"
                : "Connected; measuring for " + seconds + " s");

            while (!stopRequested.get() && System.nanoTime() < deadline &&
                (window == null || window.isDisplayable())) {
                long frameStart = System.nanoTime();
                EvoScreenCapture capture = link.getScreenCapture();
                long captured = System.nanoTime();
                BufferedImage image = toImage(capture);
                long rendered = System.nanoTime();
                if (capture.getWidth() != 320 || capture.getHeight() != 240 || capture.getBitsPerPixel() != 16) {
                    throw new IllegalStateException("Unexpected screen format: " + capture.getWidth() + "x"
                        + capture.getHeight() + "x" + capture.getBitsPerPixel());
                }

                CRC32 crc = new CRC32();
                crc.update(capture.getFramebuffer());
                long hash = crc.getValue();
                boolean changed = previousHash >= 0 && hash != previousHash;
                if (changed) changedFrames++;
                previousHash = hash;

                if (window != null) {
                    final int shownFrame = frame + 1;
                    SwingUtilities.invokeAndWait(() -> {
                        Image scaled = image.getScaledInstance(640, 480, Image.SCALE_FAST);
                        display.setIcon(new ImageIcon(scaled));
                        display.setText(null);
                        window.setTitle("TI-84 Evo screen capture — frame " + shownFrame);
                    });
                }
                long saved = System.nanoTime();
                if (saveDir != null) {
                    Path target = saveDir.resolve(String.format(Locale.ROOT, "frame-%05d.png", frame + 1));
                    if (!ImageIO.write(image, "PNG", target.toFile())) {
                        throw new IllegalStateException("No PNG encoder available");
                    }
                }
                long complete = System.nanoTime();
                if (previousComplete >= 0) addSample(frameGapMs, ms(complete - previousComplete));
                previousComplete = complete;
                addSample(captureMs, ms(captured - frameStart));
                ++frame;
                if (csv != null) {
                    csv.write(String.format(Locale.ROOT, "%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%s%n",
                        frame, ms(frameStart - started), ms(complete - started), ms(captured - frameStart),
                        ms(rendered - captured), ms(saved - rendered), ms(complete - saved), changed));
                    csv.flush();
                }
                if (continuous && complete - lastProgress >= 10_000_000_000L) {
                    double elapsed = (complete - started) / NANOS_PER_SECOND;
                    System.out.printf(Locale.ROOT,
                        "Live: %d frames in %.1f s (%.2f fps); %d changed; recent median capture %.1f ms%n",
                        frame, elapsed, frame / elapsed, changedFrames, percentile(captureMs, 0.50));
                    lastProgress = complete;
                }
            }

            double elapsed = (System.nanoTime() - started) / NANOS_PER_SECOND;
            System.out.printf(Locale.ROOT, "Frames: %d in %.2f s (%.2f fps)%n", frame, elapsed, frame / elapsed);
            System.out.printf(Locale.ROOT, "Capture%s: median %.1f ms, p95 %.1f ms, max %.1f ms%n",
                continuous ? " (recent)" : "",
                percentile(captureMs, 0.50), percentile(captureMs, 0.95), percentile(captureMs, 1.0));
            System.out.printf(Locale.ROOT, "Frame gap%s: median %.1f ms, p95 %.1f ms, max %.1f ms%n",
                continuous ? " (recent)" : "",
                percentile(frameGapMs, 0.50), percentile(frameGapMs, 0.95), percentile(frameGapMs, 1.0));
            System.out.println("Changed consecutive frames: " + changedFrames + "/" + Math.max(0, frame - 1));
        } finally {
            if (saveDir != null) {
                System.out.println("Saved frame timings and PNGs to " + saveDir.toAbsolutePath());
                if (mp4 && frame > 0) encodeMp4(saveDir, frame);
            }
            if (window != null) SwingUtilities.invokeLater(window::dispose);
        }
    }

    private static double ms(long nanos) { return nanos / 1_000_000.0; }

    private static void addSample(List<Double> samples, double value) {
        if (samples.size() == MAX_STATS_SAMPLES) samples.remove(0);
        samples.add(value);
    }

    private static void encodeMp4(Path saveDir, int capturedFrames) throws Exception {
        Path partial = saveDir.resolve("capture.mp4.part");
        Path video = saveDir.resolve("capture.mp4");
        if (Files.exists(video)) throw new IllegalArgumentException("MP4 already exists: " + video);
        Files.deleteIfExists(partial);
        int encodedFrames = 0;
        System.out.println("Encoding MP4 at " + VIDEO_FPS + " fps from captured frames...");
        try (BufferedReader timings = Files.newBufferedReader(saveDir.resolve("frames.csv"))) {
            timings.readLine(); // header
            AWTSequenceEncoder encoder = AWTSequenceEncoder.createSequenceEncoder(partial.toFile(), VIDEO_FPS);
            for (int frameNumber = 1; frameNumber <= capturedFrames; frameNumber++) {
                String row = timings.readLine();
                if (row == null) throw new IllegalStateException("Missing timing for frame " + frameNumber);
                String[] columns = row.split(",", -1);
                double completeMs = Double.parseDouble(columns[2]);
                int wantedFrames = Math.max(1, (int) Math.round(completeMs * VIDEO_FPS / 1000.0));
                if (wantedFrames > encodedFrames) {
                    Path png = saveDir.resolve(String.format(Locale.ROOT, "frame-%05d.png", frameNumber));
                    BufferedImage image = ImageIO.read(png.toFile());
                    if (image == null) throw new IllegalStateException("Could not read " + png);
                    while (encodedFrames < wantedFrames) {
                        encoder.encodeImage(image);
                        encodedFrames++;
                    }
                }
                if (frameNumber % 100 == 0) {
                    System.out.println("Encoding MP4: " + frameNumber + "/" + capturedFrames + " source frames");
                }
            }
            encoder.finish();
            Files.move(partial, video);
            System.out.println("Saved MP4 to " + video.toAbsolutePath() + " (" + encodedFrames + " video frames)");
        } catch (Exception error) {
            Files.deleteIfExists(partial);
            throw error;
        }
    }

    private static BufferedImage toImage(EvoScreenCapture capture) {
        int width = capture.getWidth();
        int height = capture.getHeight();
        byte[] pixels = capture.getFramebuffer();
        if (capture.getBitsPerPixel() != 16 || pixels.length != width * height * 2) {
            throw new IllegalStateException("Expected a complete RGB565 framebuffer");
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0, offset = 0; y < height; y++) {
            for (int x = 0; x < width; x++, offset += 2) {
                int value = (pixels[offset] & 0xff) | ((pixels[offset + 1] & 0xff) << 8);
                int r5 = (value >> 11) & 31;
                int g6 = (value >> 5) & 63;
                int b5 = value & 31;
                int red = (r5 << 3) | (r5 >> 2);
                int green = (g6 << 2) | (g6 >> 4);
                int blue = (b5 << 3) | (b5 >> 2);
                image.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        return image;
    }

    private static double percentile(List<Double> values, double fraction) {
        if (values.isEmpty()) return 0;
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(sorted);
        return sorted[(int) Math.ceil(fraction * sorted.length) - 1];
    }
}
