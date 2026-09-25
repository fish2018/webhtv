package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActivityLaunchTest {

    @Test
    public void recognizesViewGroupInputCancellationCrash() {
        NullPointerException error = new NullPointerException("Attempt to invoke View.dispatchCancelPendingInputEvents() on a null object reference");
        error.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("android.view.ViewGroup", "dispatchCancelPendingInputEvents", "ViewGroup.java", 4765),
                new StackTraceElement("android.app.Activity", "startActivityForResult", "Activity.java", 5608)
        });

        assertTrue(ActivityLaunch.isCancelPendingInputCrash(error));
    }

    @Test
    public void rejectsAppCrashThatOnlyHasViewGroupLowerInStack() {
        NullPointerException error = new NullPointerException("app crash");
        error.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.fongmi.android.tv.ui.activity.VideoActivity", "start", "VideoActivity.java", 704),
                new StackTraceElement("android.view.ViewGroup", "dispatchCancelPendingInputEvents", "ViewGroup.java", 4765)
        });

        assertFalse(ActivityLaunch.isCancelPendingInputCrash(error));
    }

    @Test
    public void rejectsUnrelatedNullPointerException() {
        NullPointerException error = new NullPointerException("unrelated");
        error.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.fongmi.android.tv.ui.activity.VideoActivity", "start", "VideoActivity.java", 704)
        });

        assertFalse(ActivityLaunch.isCancelPendingInputCrash(error));
    }
}
