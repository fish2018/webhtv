package com.fongmi.android.tv.subtitle.provider;

import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleQuery;
import com.fongmi.android.tv.subtitle.model.SubtitleQuerySource;
import com.fongmi.android.tv.subtitle.model.SubtitleStrictness;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShooterSubtitleProviderTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void computeFileHash_usesShooterSampleOffsets() throws Exception {
        File file = folder.newFile("movie.mkv");
        byte[] data = new byte[12 * 1024];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 251);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data);
        }

        String hash = ShooterSubtitleProvider.computeFileHash(file);

        assertEquals(md5(data, 4 * 1024) + ";" + md5(data, 8 * 1024) + ";" + md5(data, 4 * 1024) + ";" + md5(data, 4 * 1024), hash);
    }

    @Test
    public void parseCandidates_mapsShooterResponseToSubtitleCandidates() {
        String body = "[{\"Desc\":\"ok\",\"Delay\":0,\"Files\":[{\"Ext\":\"srt\",\"Link\":\"https://example.com/a.srt\"},{\"Ext\":\"ass\",\"Link\":\"https://example.com/b.ass\"}]}]";
        SubtitleContext context = SubtitleContext.builder().canonicalTitle("流浪地球").mediaPath("/sdcard/Movies/movie.mkv").preferredLanguage("zh").build();
        SubtitleQuery query = new SubtitleQuery("流浪地球", "流浪地球", "zh", SubtitleQuerySource.SOURCE_TITLE, SubtitleStrictness.NORMAL, 2019, -1, -1);

        List<SubtitleCandidate> candidates = ShooterSubtitleProvider.parseCandidates(body, query, context);

        assertEquals(2, candidates.size());
        assertEquals("shooter", candidates.get(0).getProvider());
        assertEquals("https://example.com/a.srt", candidates.get(0).getCandidateId());
        assertEquals("movie.mkv | 射手 | srt", candidates.get(0).getDisplayName());
        assertEquals("zh", candidates.get(0).getLanguage());
        assertEquals("srt", candidates.get(0).getFormat());
        assertTrue(candidates.get(0).getScore() > 0);
        assertTrue(candidates.get(0).getProviderPayload().contains("https://example.com/a.srt"));
    }

    private static String md5(byte[] data, int offset) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] hash = digest.digest(java.util.Arrays.copyOfRange(data, offset, offset + 4096));
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) builder.append(String.format("%02x", b & 0xff));
        return builder.toString();
    }
}
