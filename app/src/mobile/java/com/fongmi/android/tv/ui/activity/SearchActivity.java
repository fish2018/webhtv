package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.CollectFragment;
import com.fongmi.android.tv.ui.fragment.SearchFragment;

public class SearchActivity extends BaseActivity {

    public static void start(Activity activity) {
        start(activity, "");
    }

    public static void start(Activity activity, String keyword) {
        start(activity, keyword, "");
    }

    public static void start(Activity activity, String keyword, String siteKey) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra("keyword", keyword);
        if (!TextUtils.isEmpty(siteKey)) intent.putExtra("siteKey", siteKey);
        activity.startActivity(intent);
    }

    public static void direct(Activity activity, String keyword) {
        direct(activity, keyword, "");
    }

    public static void direct(Activity activity, String keyword, String siteKey) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra("keyword", keyword);
        intent.putExtra("direct", true);
        if (!TextUtils.isEmpty(siteKey)) intent.putExtra("siteKey", siteKey);
        activity.startActivity(intent);
    }

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    private String getSiteKey() {
        String siteKey = getIntent().getStringExtra("siteKey");
        return siteKey == null ? "" : siteKey;
    }

    private boolean isDirect() {
        return getIntent().getBooleanExtra("direct", false);
    }

    @Override
    protected ViewBinding getBinding() {
        return ActivitySearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            if (isDirect()) getSupportFragmentManager().beginTransaction().replace(R.id.container, CollectFragment.newInstance(getKeyword(), getSiteKey()), CollectFragment.class.getSimpleName()).commit();
            else getSupportFragmentManager().beginTransaction().replace(R.id.container, SearchFragment.newInstance(getKeyword(), getSiteKey()), SearchFragment.class.getSimpleName()).commit();
        }
    }

    @Override
    protected void onBackInvoked() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        } else {
            super.onBackInvoked();
        }
    }
}
