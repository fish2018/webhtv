package com.fongmi.android.tv.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class OciAuthChallengeTest {

    @Test
    public void parsesDockerBearerChallenge() {
        OciAuthChallenge challenge = OciAuthChallenge.parse("Bearer realm=\"https://auth.docker.io/token\",service=\"registry.docker.io\",scope=\"repository:fish2018/webhtv-apk:pull\"");
        assertEquals("https://auth.docker.io/token", challenge.realm);
        assertEquals("registry.docker.io", challenge.service);
        assertEquals("repository:fish2018/webhtv-apk:pull", challenge.scope);
    }

    @Test
    public void rejectsMissingRealmAndBasicAuth() {
        assertThrows(IllegalArgumentException.class, () -> OciAuthChallenge.parse("Bearer service=\"registry.docker.io\""));
        assertThrows(IllegalArgumentException.class, () -> OciAuthChallenge.parse("Basic realm=\"registry\""));
    }
}
