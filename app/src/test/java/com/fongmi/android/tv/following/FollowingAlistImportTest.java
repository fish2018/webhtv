package com.fongmi.android.tv.following;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FollowingAlistImportTest {

    @Test
    public void parsesSubscriptionArrayAndProviderIdentity() {
        String json = """
                {"data":[{"name":"示例剧","season":1,"metaProvider":"tmdb","metaId":1399,
                "currentEpisodes":8,"status":"Returning Series"}]}
                """;

        List<AlistSubscriptionImporter.Candidate> candidates = AlistSubscriptionImporter.parse(json);

        assertEquals(1, candidates.size());
        AlistSubscriptionImporter.Candidate candidate = candidates.get(0);
        assertEquals("示例剧", candidate.title);
        assertEquals(1, candidate.season);
        assertEquals(1399, candidate.tmdbId);
        assertEquals(8, candidate.currentEpisodes);
        assertEquals("tv", candidate.mediaType());
    }

    @Test
    public void ignoresEntriesWithoutTitle() {
        List<AlistSubscriptionImporter.Candidate> candidates = AlistSubscriptionImporter.parse(
                "[{\"season\":1,\"tmdbId\":1},{\"name\":\"OK\",\"season\":2,\"tmdbId\":2}]");

        assertEquals(1, candidates.size());
        assertEquals("OK", candidates.get(0).title);
    }

    @Test
    public void importedSubscriptionIsEnabledForScheduledChecks() {
        AlistSubscriptionImporter.Candidate candidate = new AlistSubscriptionImporter.Candidate();
        candidate.title = "导入剧集";
        candidate.season = 1;
        candidate.tmdbId = 1399;
        candidate.currentEpisodes = 8;

        Following item = AlistSubscriptionImporter.toFollowing(candidate, 100);

        assertTrue(item.enabled);
        assertEquals("tmdb:tv:1399:s1", item.identityKey);
    }

    @Test
    public void serverlessNormalizersPreserveFollowOption() throws Exception {
        String[] files = {
                "serverless/webhtv-remote-rust/src/main.rs",
                "serverless/webhtv-remote-deno/relay.js",
                "serverless/webhtv-remote-cloudflare/src/relay.js",
                "serverless/webhtv-remote-vercel/api/relay.js",
                "serverless/webhtv-remote-go/main.go"
        };
        for (String file : files) {
            Path path = Path.of(file);
            if (!Files.exists(path)) path = Path.of("..").resolve(file);
            String source = Files.readString(path, StandardCharsets.UTF_8);
            assertTrue(file + " must normalize follow", source.contains("follow"));
        }
    }
}
