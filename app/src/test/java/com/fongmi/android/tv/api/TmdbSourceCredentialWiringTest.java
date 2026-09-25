package com.fongmi.android.tv.api;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class TmdbSourceCredentialWiringTest {

    @Test
    public void siteApiStripsCredentialBeforeLoggingParsingAndCaching() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/fongmi/android/tv/api/SiteApi.java"));
        int ingress = source.indexOf("TmdbSourceCredentialIngress.extractRootAndStrip");
        int firstLog = source.indexOf("SpiderDebug.log(\"detail\", ingress.getSanitizedJson())");
        int parse = source.indexOf("Result.fromJson(ingress.getSanitizedJson())");
        int cache = source.indexOf("VodDetailCache.putContent(sourceKey, id, content)");

        assertTrue(ingress > 0);
        assertTrue(firstLog > ingress);
        assertTrue(parse > ingress);
        assertTrue(cache > parse);
        assertTrue(!source.contains("SubscriptionTmdbCredentialStore.accept("));
    }

    @Test
    public void vodConfigAcceptsCredentialOnlyAfterSubscriptionConfigLoadSucceeds() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/fongmi/android/tv/api/config/VodConfig.java"));
        int config = source.indexOf("public VodConfig config(Config config)");
        int begin = source.indexOf("SubscriptionTmdbCredentialStore.beginSubscription", config);
        int assign = source.indexOf("this.config = config", config);
        int load = source.indexOf("protected void load(Config config)");
        int ingress = source.indexOf("TmdbSourceCredentialIngress.extractRootAndStrip", load);
        int loaded = source.indexOf("if (!isLoaded()) throw new Exception", ingress);
        int accept = source.indexOf("acceptSubscriptionCredential(ingress.getCandidateKey(), config)", loaded);
        int normalize = source.indexOf("CatSource.normalize(url, Json.parse(ingress.getSanitizedJson()))", ingress);

        assertTrue(begin > config);
        assertTrue(assign > begin);
        assertTrue(ingress > load);
        assertTrue(normalize > ingress);
        assertTrue(loaded > normalize);
        assertTrue(accept > loaded);
    }
}
