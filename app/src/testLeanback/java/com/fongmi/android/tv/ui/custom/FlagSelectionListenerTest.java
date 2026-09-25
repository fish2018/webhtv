package com.fongmi.android.tv.ui.custom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class FlagSelectionListenerTest {

    private ActivityController<Activity> controller;
    private HorizontalGridView grid;
    private FlagAdapter adapter;
    private FlagSelectionListener listener;
    private List<Flag> flags;
    private final List<Flag> selected = new ArrayList<>();

    @Before
    public void setUp() {
        ShadowChoreographer.setPaused(true);
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
        controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar);
        grid = new HorizontalGridView(activity);
        grid.setItemAnimator(null);
        flags = Arrays.asList(flag("Drive A"), flag("Drive B"), flag("Drive C"));
        adapter = new FlagAdapter(item -> {});
        adapter.addAll(flags);
        adapter.setSelected(flags.get(0));
        grid.setAdapter(adapter);
        activity.setContentView(grid, new ViewGroup.LayoutParams(800, 120));
        controller.visible();
        layoutGrid();
        drainFrames();
        assertTrue(grid.isAttachedToWindow());
        listener = new FlagSelectionListener(grid, adapter, item -> {
            if (item.isSelected()) return;
            assertFalse("Route switching must run outside RecyclerView layout", grid.isComputingLayout());
            selected.add(item);
            adapter.setSelected(item);
            // Both indirect refreshes performed by VideoActivity's route switch.
            adapter.setNextFocusDown(R.id.array);
            adapter.toggle(item.getEpisodes().get(0));
        });
    }

    @After
    public void tearDown() {
        setComputingLayout(false);
        controller.pause().stop().destroy();
    }

    @Test
    public void synchronousFocusAndEpisodeRefreshReproduceReportedException() {
        setComputingLayout(true);
        IllegalStateException focusError = assertThrows(IllegalStateException.class,
                () -> adapter.setNextFocusDown(R.id.array));
        assertTrue(focusError.getMessage().contains("computing a layout or scrolling"));
        // Local FlagAdapter.toggle deliberately stays notify-free during layout (cb21feb422),
        // so only the focus refresh still asserts. The listener must still defer the whole
        // route switch, because that refresh runs inside the selection dispatch.
        adapter.toggle(flags.get(1).getEpisodes().get(0));
    }

    @Test
    public void selectionWaitsUntilLayoutEndsBeforeUpdatingFocusAndEpisode() {
        setComputingLayout(true);
        select(1);
        mainLooper().idle();
        assertTrue(selected.isEmpty());
        assertSame(flags.get(0), adapter.getActivated());

        setComputingLayout(false);
        drainFrames();
        assertEquals(Arrays.asList(flags.get(1)), selected);
        assertSame(flags.get(1), adapter.getActivated());
        assertTrue(flags.get(1).getEpisodes().get(0).isSelected());
        assertFalse(flags.get(0).getEpisodes().get(0).isSelected());
    }

    @Test
    public void realLeanbackLayoutDispatchDefersRouteSwitch() {
        List<Boolean> layoutCallbacks = new ArrayList<>();
        grid.addOnChildViewHolderSelectedListener(listener);
        grid.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(RecyclerView parent, RecyclerView.ViewHolder child, int position, int subposition) {
                layoutCallbacks.add(parent.isComputingLayout());
            }
        });
        grid.setSelectedPosition(1);
        adapter.notifyDataSetChanged();
        layoutGrid();
        assertTrue("Exercise an actual Leanback selection inside layout", layoutCallbacks.contains(true));
        assertTrue(selected.isEmpty());

        drainFrames();
        assertEquals(Arrays.asList(flags.get(1)), selected);
        assertTrue(flags.get(1).getEpisodes().get(0).isSelected());
    }

    @Test
    public void rapidSelectionsOnlySwitchToLatestFlag() {
        select(1);
        select(2);
        drainFrames();
        assertEquals(Arrays.asList(flags.get(2)), selected);
        assertSame(flags.get(2), adapter.getActivated());
    }

    @Test
    public void invalidPositionCancelsQueuedSelectionAndAllowsNextSelection() {
        select(1);
        listener.onChildViewHolderSelected(grid, null, RecyclerView.NO_POSITION, 0);
        listener.onChildViewHolderSelected(grid, null, adapter.getItemCount(), 0);
        drainFrames();
        assertTrue(selected.isEmpty());

        select(2);
        drainFrames();
        assertEquals(Arrays.asList(flags.get(2)), selected);
    }

    @Test
    public void replacingListWithEqualFlagsDiscardsOldSelection() {
        select(1);
        adapter.addAll(Arrays.asList(flag("Drive A"), flag("Drive B"), flag("Drive C")));
        drainFrames();
        assertTrue(selected.isEmpty());
        assertFalse(flags.get(1).getEpisodes().get(0).isSelected());
    }

    @Test
    public void replacingAdapterDiscardsQueuedSelection() {
        select(1);
        grid.setAdapter(new FlagAdapter(item -> {}));
        drainFrames();
        assertTrue(selected.isEmpty());
    }

    @Test
    public void leavingWindowDiscardsQueuedSelection() {
        select(1);
        controller.get().setContentView(new View(controller.get()));
        assertFalse(grid.isAttachedToWindow());
        drainFrames();
        assertTrue(selected.isEmpty());
    }

    private Flag flag(String name) {
        Flag flag = new Flag(name);
        flag.getEpisodes().add(new Episode());
        return flag;
    }

    private void select(int position) {
        grid.setSelectedPosition(position);
        listener.onChildViewHolderSelected(grid, null, position, 0);
    }

    private void layoutGrid() {
        grid.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY));
        grid.layout(0, 0, 800, 120);
    }

    private void setComputingLayout(boolean computing) {
        // Keep RecyclerView's real adapter observer/assertion, while controlling
        // the critical section across a queued main-thread callback deterministically.
        ReflectionHelpers.setField(grid, "mLayoutOrScrollCounter", computing ? 1 : 0);
    }

    private void drainFrames() {
        mainLooper().idleFor(Duration.ofMillis(100));
    }

    private ShadowLooper mainLooper() {
        return Shadow.extract(Looper.getMainLooper());
    }
}
