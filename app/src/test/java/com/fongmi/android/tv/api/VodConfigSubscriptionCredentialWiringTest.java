package com.fongmi.android.tv.api;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VodConfigSubscriptionCredentialWiringTest {

    @Test
    public void subscriptionConfigRootIsTheOnlyCredentialSource() throws Exception {
        String config = Files.readString(Path.of("src/main/java/com/fongmi/android/tv/api/config/VodConfig.java"));
        String site = Files.readString(Path.of("src/main/java/com/fongmi/android/tv/api/SiteApi.java"));
        int load = config.indexOf("protected void load(Config config)");
        int ingress = config.indexOf("TmdbSourceCredentialIngress.extractRootAndStrip", load);
        int normalized = config.indexOf("CatSource.normalize(url, Json.parse(ingress.getSanitizedJson()))", ingress);
        int accepted = config.indexOf("acceptSubscriptionCredential(ingress.getCandidateKey(), config)", normalized);

        assertTrue(ingress > load);
        assertTrue(normalized > ingress);
        assertTrue(accepted > normalized);
        assertTrue(!site.contains("SubscriptionTmdbCredentialStore.accept("));
    }

    @Test
    public void detailRootKeyIsStillStrippedButIgnored() throws Exception {
        String site = Files.readString(Path.of("src/main/java/com/fongmi/android/tv/api/SiteApi.java"));

        assertTrue(site.contains("TmdbSourceCredentialIngress.extractRootAndStrip(detailContent)"));
        assertFalse(site.contains("getCandidateKey()"));
        assertFalse(site.contains("acceptSubscriptionCredential("));
    }
}
