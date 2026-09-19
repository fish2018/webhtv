package androidx.media3.extractor;

import static org.junit.Assert.*;

import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.ts.Avs3Reader;
import androidx.media3.extractor.ts.TsPayloadReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Runs against the shipped extractor classes and independent encoder fixtures. */
public class Avs3ExtractionTest {
  private static byte[] fixture(int depth) throws IOException {
    return Files.readAllBytes(Path.of(System.getProperty("avs3.fixtures"), "baseline-" + depth + ".avs3"));
  }

  private static byte[] sequence(byte[] stream) {
    for (int i = 4; i + 3 < stream.length; i++) {
      if (stream[i] == 0 && stream[i + 1] == 0 && stream[i + 2] == 1
          && (stream[i + 3] & 0xff) == 0xb3) return Arrays.copyOf(stream, i);
    }
    throw new AssertionError("Fixture has no I-picture");
  }

  @Test public void baselineDepthsAndMp4Configuration() throws Exception {
    for (int depth : new int[] {8, 10}) {
      byte[] raw = sequence(fixture(depth));
      byte[] av3c = new byte[raw.length + 4];
      av3c[0] = 1;
      av3c[1] = (byte) (raw.length >>> 8);
      av3c[2] = (byte) raw.length;
      System.arraycopy(raw, 0, av3c, 3, raw.length);
      for (byte[] input : new byte[][] {raw, av3c}) {
        Avs3Config config = Avs3Config.parse(input);
        assertEquals(256, config.width);
        assertEquals(144, config.height);
        assertEquals(depth, config.bitdepth);
        assertEquals(25, config.frameRate, 0.0001);
        assertArrayEquals(raw, config.initializationData.get(0));
        assertEquals(MimeTypes.VIDEO_AVS3, MimeTypes.getMediaMimeType(config.codecs));
      }
    }
    assertEquals(MimeTypes.AUDIO_AV3A, MimeTypes.getMediaMimeType("av3a.1"));
    assertEquals(MimeTypes.VIDEO_H264, MimeTypes.getMediaMimeType("avc1.640028"));
  }

  @Test public void rejectsTruncatedAndMalformedConfiguration() throws Exception {
    byte[] header = sequence(fixture(10));
    for (int size = 0; size < 13; size++) {
      byte[] truncated = Arrays.copyOf(header, size);
      assertThrows(ParserException.class, () -> Avs3Config.parse(truncated));
    }
    assertThrows(ParserException.class, () -> Avs3Config.parse(new byte[] {1, 127, -1, 0}));
    header[3] = (byte) 0xb3;
    assertThrows(ParserException.class, () -> Avs3Config.parse(header));
  }

  @Test public void streamFramingSurvivesEveryStartCodeSplitAndSeek() throws Exception {
    byte[] bytes = fixture(10);
    for (int chunk : new int[] {1, 2, 3, 5, 188, 4096}) {
      Avs3Reader reader = new Avs3Reader();
      Capture capture = new Capture();
      reader.createTracks(capture, new TsPayloadReader.TrackIdGenerator(0, 1));
      for (int pass = 0; pass < 2; pass++) {
        capture.clearSamples();
        reader.seek();
        reader.packetStarted(1_000_000, 0);
        for (int pos = 0; pos < bytes.length; pos += chunk) {
          reader.consume(new ParsableByteArray(Arrays.copyOfRange(bytes, pos, Math.min(bytes.length, pos + chunk))));
        }
        reader.endOfInputReached();
        reader.endOfInputReached(); // EOS must not duplicate the delayed final sample.
        assertNotNull(capture.format);
        assertEquals(MimeTypes.VIDEO_AVS3, capture.format.sampleMimeType);
        assertEquals(16, capture.samples.size());
        assertArrayEquals(bytes, capture.joinSamples());
        assertEquals(1_000_000L, (long) capture.times.get(0));
        assertEquals(1_600_000L, (long) capture.times.get(15));
        assertTrue((capture.flags.get(0) & C.BUFFER_FLAG_KEY_FRAME) != 0);
      }
    }
  }

  private static final class Capture implements ExtractorOutput, TrackOutput {
    Format format;
    ByteArrayOutputStream written = new ByteArrayOutputStream();
    final List<byte[]> samples = new ArrayList<>();
    final List<Long> times = new ArrayList<>();
    final List<Integer> flags = new ArrayList<>();
    public TrackOutput track(int id, int type) { assertEquals(C.TRACK_TYPE_VIDEO, type); return this; }
    public void endTracks() {}
    public void seekMap(SeekMap map) {}
    public void format(Format value) { format = value; }
    public int sampleData(DataReader input, int length, boolean allowEndOfInput, int part) throws IOException {
      byte[] bytes = new byte[length];
      int count = input.read(bytes, 0, length);
      if (count > 0) written.write(bytes, 0, count);
      return count;
    }
    public void sampleData(ParsableByteArray data, int length, int part) {
      written.write(data.getData(), data.getPosition(), length);
      data.skipBytes(length);
    }
    public void sampleMetadata(long timeUs, int sampleFlags, int size, int offset, CryptoData crypto) {
      byte[] bytes = written.toByteArray();
      int end = bytes.length - offset;
      samples.add(Arrays.copyOfRange(bytes, end - size, end));
      times.add(timeUs);
      flags.add(sampleFlags);
    }
    void clearSamples() { written.reset(); samples.clear(); times.clear(); flags.clear(); }
    byte[] joinSamples() throws IOException {
      ByteArrayOutputStream all = new ByteArrayOutputStream();
      for (byte[] bytes : samples) all.write(bytes);
      return all.toByteArray();
    }
  }
}
