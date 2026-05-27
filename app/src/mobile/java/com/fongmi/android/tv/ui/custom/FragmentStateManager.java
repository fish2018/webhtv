package com.fongmi.android.tv.ui.custom;

import static androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN;

import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.fongmi.android.tv.ui.base.BaseFragment;

import java.util.function.IntFunction;

public class FragmentStateManager {

    private final ViewGroup container;
    private final FragmentManager fm;
    private final IntFunction<Fragment> factory;

    public FragmentStateManager(ViewGroup container, FragmentManager fm, IntFunction<Fragment> factory) {
        this.container = container;
        this.factory = factory;
        this.fm = fm;
    }

    public boolean change(int position) {
        String tag = getTag(position);
        Fragment expected = factory.apply(position);
        if (expected == null) return false;
        Fragment fragment = fm.findFragmentByTag(tag);
        FragmentTransaction ft = fm.beginTransaction().setTransition(TRANSIT_FRAGMENT_OPEN);
        if (fragment != null && fragment.getClass() != expected.getClass()) {
            ft.remove(fragment).commitNowAllowingStateLoss();
            fragment = null;
            ft = fm.beginTransaction().setTransition(TRANSIT_FRAGMENT_OPEN);
        }
        if (fragment == null) {
            fragment = expected;
            ft.add(container.getId(), fragment, tag);
        }
        Fragment current = fm.getPrimaryNavigationFragment();
        if (current != null && current != fragment) ft.hide(current);
        ft.show(fragment).setPrimaryNavigationFragment(fragment).setReorderingAllowed(true).commitNowAllowingStateLoss();
        return true;
    }

    private String getTag(int position) {
        return "android:switcher:" + position;
    }

    public BaseFragment getFragment(int position) {
        return (BaseFragment) fm.findFragmentByTag(getTag(position));
    }

    public boolean isVisible(int position) {
        Fragment fragment = getFragment(position);
        return fragment != null && fragment.isVisible();
    }

    public boolean canBack(int position) {
        BaseFragment fragment = getFragment(position);
        return fragment != null && fragment.canBack();
    }
}
