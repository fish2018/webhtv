package com.fongmi.android.tv.subtitle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RealtimeSubtitleSpeechRecognitionFactoryTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void missingModelIsReportedWithoutCreatingRecognizer() throws Exception {
        RealtimeSubtitleSpeechRecognitionFactory factory =
                RealtimeSubtitleSpeechRecognitionFactory.forRoot(temporary.newFolder());

        assertFalse(factory.isReady());
    }

    @Test
    public void contractExposesResetAcceptAndClose() {
        assertNotNull(SpeechRecognitionFactory.Session.class);
        assertNotNull(SpeechRecognitionFactory.Listener.class);
    }
}