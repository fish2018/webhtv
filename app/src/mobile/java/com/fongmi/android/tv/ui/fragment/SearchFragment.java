package com.fongmi.android.tv.ui.fragment;

import static androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.PopupWindow;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.FragmentSearchBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteGroupOrderStore;
import com.fongmi.android.tv.ui.adapter.HotWordAdapter;
import com.fongmi.android.tv.ui.adapter.RecordAdapter;
import com.fongmi.android.tv.ui.adapter.WordAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SearchSuggest;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.net.OkHttp;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.material.textview.MaterialTextView;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import okhttp3.Call;
import okhttp3.Response;

public class SearchFragment extends BaseFragment implements MenuProvider, WordAdapter.OnClickListener, RecordAdapter.OnClickListener {

    private static final int MENU_SCOPE_ALL = 1;
    private static final int MENU_SCOPE_CURRENT = 2;
    private static final int MENU_SCOPE_GROUP_OFFSET = 100;
    private static final int SCOPE_POPUP_ITEM_HEIGHT = 44;
    private static final int SCOPE_POPUP_MAX_ITEMS = 8;
    private static final int HOT_LIMIT = 10;

    private FragmentSearchBinding mBinding;
    private RecordAdapter mRecordAdapter;
    private WordAdapter mWordAdapter;
    private PopupWindow scopePopup;
    private String mGroup = "";
    private boolean mCurrentSite;
    private HotWordAdapter mHotTvAdapter;
    private HotWordAdapter mHotMovieAdapter;
    private HotWordAdapter mHotVarietyAdapter;
    private List<Word.Data> mIqiyiWords = new ArrayList<>();
    private List<Word.Data> mTencentWords = new ArrayList<>();
    private int mSuggestSeq;

    public static SearchFragment newInstance(String keyword) {
        return newInstance(keyword, null);
    }

    public static SearchFragment newInstance(String keyword, String siteKey) {
        return newInstance(keyword, siteKey, null, null);
    }

    public static SearchFragment newInstance(String keyword, String siteKey, String pic, String wallPic) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        args.putString("siteKey", siteKey);
        args.putString("pic", pic);
        args.putString("wallPic", wallPic);
        SearchFragment fragment = new SearchFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKeyword() {
        return getArguments().getString("keyword");
    }

    private String getSiteKey() {
        String siteKey = getArguments().getString("siteKey");
        return siteKey == null ? "" : siteKey;
    }

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private String getPic() {
        return getArguments().getString("pic");
    }

    private String getWallPic() {
        return getArguments().getString("wallPic");
    }

    private boolean empty() {
        return mBinding.keyword.getText().toString().trim().isEmpty();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initMenu() {
        if (isHidden()) return;
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        activity.setSupportActionBar(mBinding.toolbar);
        activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        activity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        activity.setTitle("");
    }

    @Override
    protected void initView() {
        mCurrentSite = !TextUtils.isEmpty(getSiteKey());
        mGroup = "";
        setRecyclerView();
        checkKeyword();
        search();
    }

    private void setRecyclerView() {
        setWordRecycler(mBinding.wordRecycler, mWordAdapter = new WordAdapter(this));
        setHotRecycler(mBinding.hotTvRecycler, mHotTvAdapter = new HotWordAdapter(this));
        setHotRecycler(mBinding.hotMovieRecycler, mHotMovieAdapter = new HotWordAdapter(this));
        setHotRecycler(mBinding.hotVarietyRecycler, mHotVarietyAdapter = new HotWordAdapter(this));
        mBinding.recordRecycler.setHasFixedSize(false);
        mBinding.recordRecycler.setAdapter(mRecordAdapter = new RecordAdapter(this));
        mBinding.recordRecycler.setLayoutManager(new FlexboxLayoutManager(getContext(), FlexDirection.ROW));
    }

    private void setWordRecycler(RecyclerView recyclerView, WordAdapter adapter) {
        recyclerView.setHasFixedSize(false);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new FlexboxLayoutManager(getContext(), FlexDirection.ROW));
    }

    private void setHotRecycler(RecyclerView recyclerView, HotWordAdapter adapter) {
        recyclerView.setHasFixedSize(false);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new FlexboxLayoutManager(getContext(), FlexDirection.ROW));
    }

    @Override
    protected void initEvent() {
        mBinding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) search();
            return true;
        });
        mBinding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                requireActivity().invalidateOptionsMenu();
                getWord(s.toString());
            }
        });
        getParentFragmentManager().setFragmentResultListener("result", getViewLifecycleOwner(), (requestKey, bundle) -> {
            if (bundle.getBoolean("edit", false)) Util.showKeyboard(mBinding.keyword);
        });
    }

    private void checkKeyword() {
        boolean visible = requireActivity().getSupportFragmentManager().findFragmentByTag(CollectFragment.class.getSimpleName()) != null;
        if (TextUtils.isEmpty(getKeyword()) && !visible) Util.showKeyboard(mBinding.keyword);
        setKeyword(getKeyword());
        getWord(getKeyword());
    }

    private void setKeyword(String text) {
        mBinding.keyword.setText(text);
        mBinding.keyword.setSelection(text.length());
    }

    private void search() {
        if (empty()) return;
        String keyword = mBinding.keyword.getText().toString().trim();
        App.post(() -> mRecordAdapter.add(keyword), 250);
        Util.hideKeyboard(mBinding.keyword);
        collect(keyword);
    }

    private void collect(String keyword) {
        FragmentManager fm = requireActivity().getSupportFragmentManager();
        String collectTag = CollectFragment.class.getSimpleName();
        if (fm.findFragmentByTag(collectTag) != null) return;
        String searchTag = SearchFragment.class.getSimpleName();
        FragmentTransaction ft = fm.beginTransaction().setTransition(TRANSIT_FRAGMENT_OPEN);
        ft.add(R.id.container, CollectFragment.newInstance(keyword, getSearchSiteKey(), getSearchGroup(), getPic(), getWallPic()), collectTag);
        Optional.ofNullable(fm.findFragmentByTag(searchTag)).ifPresent(ft::hide);
        ft.setReorderingAllowed(true).addToBackStack(null).commit();
    }

    private String getSearchSiteKey() {
        if (!mCurrentSite) return "";
        return TextUtils.isEmpty(getSiteKey()) ? getHome().getKey() : getSiteKey();
    }

    private String getSearchGroup() {
        return mCurrentSite ? "" : mGroup;
    }

    private void getWord(String text) {
        if (text.isEmpty()) getHot();
        else getSuggest(text);
    }

    private void getHot() {
        mSuggestSeq++;
        showHot(true);
        mHotTvAdapter.setItems(hotItems(Setting.getHotTv()));
        mHotMovieAdapter.setItems(hotItems(Setting.getHotMovie()));
        mHotVarietyAdapter.setItems(hotItems(Setting.getHotVariety()));
        OkHttp.newCall(rankUrl(3), Map.of(HttpHeaders.REFERER, "https://www.360kan.com/rank/general")).enqueue(getHotCallback(mHotTvAdapter, Setting::putHotTv));
        OkHttp.newCall(rankUrl(2), Map.of(HttpHeaders.REFERER, "https://www.360kan.com/rank/general")).enqueue(getHotCallback(mHotMovieAdapter, Setting::putHotMovie));
        OkHttp.newCall(rankUrl(4), Map.of(HttpHeaders.REFERER, "https://www.360kan.com/rank/general")).enqueue(getHotCallback(mHotVarietyAdapter, Setting::putHotVariety));
    }

    private void getSuggest(String text) {
        showHot(false);
        mBinding.word.setText(R.string.search_suggest);
        int seq = ++mSuggestSeq;
        mIqiyiWords = new ArrayList<>();
        mTencentWords = new ArrayList<>();
        OkHttp.newCall(SearchSuggest.iqiyiUrl(text)).enqueue(getSuggestCallback(seq, false));
        OkHttp.newCall(SearchSuggest.tencentUrl(text)).enqueue(getSuggestCallback(seq, true));
    }

    private String rankUrl(int cat) {
        return "https://api.web.360kan.com/v1/rank?cat=" + cat;
    }

    private void showHot(boolean show) {
        mBinding.word.setVisibility(show ? View.GONE : View.VISIBLE);
        mBinding.wordRecycler.setVisibility(show ? View.GONE : View.VISIBLE);
        mBinding.hotGroup.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private List<Word.Data> hotItems(String result) {
        List<Word.Data> data = Word.objectFrom(result).getData();
        return new ArrayList<>(data.subList(0, Math.min(data.size(), HOT_LIMIT)));
    }

    private Callback getHotCallback(HotWordAdapter adapter, HotSaver saver) {
        return new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String result = response.body().string();
                if (TextUtils.isEmpty(result)) return;
                App.post(() -> setHotAdapter(adapter, saver, result));
            }
        };
    }

    private void setHotAdapter(HotWordAdapter adapter, HotSaver saver, String result) {
        if (!mBinding.keyword.getText().toString().trim().isEmpty()) return;
        saver.save(result);
        adapter.setItems(hotItems(result));
    }

    private Callback getSuggestCallback(int seq, boolean tencent) {
        return new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String result = response.body().string();
                if (TextUtils.isEmpty(result)) return;
                App.post(() -> setSuggestAdapter(seq, result, tencent));
            }
        };
    }

    private void setSuggestAdapter(int seq, String result, boolean tencent) {
        if (seq != mSuggestSeq || mBinding.keyword.getText().toString().trim().isEmpty()) return;
        if (tencent) mTencentWords = SearchSuggest.parseTencent(result);
        else mIqiyiWords = SearchSuggest.parseIqiyi(result);
        mWordAdapter.setItems(SearchSuggest.merge(mIqiyiWords, mTencentWords));
    }

    private void onReset() {
        mBinding.keyword.setText("");
        requireActivity().invalidateOptionsMenu();
    }

    private void onSite() {
        Util.hideKeyboard(mBinding.keyword);
        mBinding.keyword.post(() -> SiteDialog.create().search().show(this));
    }

    private void onScope() {
        View anchor = mBinding.toolbar.findViewById(R.id.action_scope);
        if (anchor == null) return;
        List<String> groups = SiteGroupOrderStore.sort(Site.getGroups(VodConfig.get().getSites()));
        int width = getScopePopupWidth(groups);
        int height = getScopePopupHeight(groups.size() + 2);
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setBackground(getScopePopupBackground());
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayoutCompat content = new LinearLayoutCompat(requireContext());
        content.setOrientation(LinearLayoutCompat.VERTICAL);
        content.setPadding(0, ResUtil.dp2px(6), 0, ResUtil.dp2px(6));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addScopePopupItem(content, getString(R.string.search_scope_all), MENU_SCOPE_ALL, groups);
        addScopePopupItem(content, getString(R.string.search_scope_current), MENU_SCOPE_CURRENT, groups);
        for (int i = 0; i < groups.size(); i++) addScopePopupItem(content, groups.get(i), MENU_SCOPE_GROUP_OFFSET + i, groups);
        scopePopup = new PopupWindow(scroll, width, height, true);
        scopePopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        scopePopup.setOutsideTouchable(true);
        scopePopup.setElevation(ResUtil.dp2px(6));
        scopePopup.showAsDropDown(anchor, anchor.getWidth() - width, 0);
    }

    private GradientDrawable getScopePopupBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(ResUtil.dp2px(6));
        return drawable;
    }

    private int getScopePopupWidth(List<String> groups) {
        int width = Math.max(ResUtil.getTextWidth(getString(R.string.search_scope_all), 16), ResUtil.getTextWidth(getString(R.string.search_scope_current), 16));
        for (String group : groups) width = Math.max(width, ResUtil.getTextWidth(group, 16));
        int contentWidth = width + ResUtil.dp2px(36);
        int maxWidth = ResUtil.getScreenWidth(requireContext()) - ResUtil.dp2px(32);
        return Math.min(contentWidth, maxWidth);
    }

    private int getScopePopupHeight(int itemCount) {
        int itemHeight = ResUtil.dp2px(SCOPE_POPUP_ITEM_HEIGHT);
        int padding = ResUtil.dp2px(12);
        int contentHeight = itemCount * itemHeight + padding;
        int maxHeight = Math.min(ResUtil.getScreenHeight(requireContext()) - mBinding.toolbar.getHeight() - ResUtil.dp2px(32), SCOPE_POPUP_MAX_ITEMS * itemHeight + padding);
        return Math.min(contentHeight, Math.max(itemHeight + padding, maxHeight));
    }

    private void addScopePopupItem(LinearLayoutCompat content, String text, int itemId, List<String> groups) {
        MaterialTextView view = new MaterialTextView(requireContext());
        view.setText(text);
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        view.setTextColor(0xFF202124);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setPadding(ResUtil.dp2px(18), 0, ResUtil.dp2px(18), 0);
        view.setBackgroundResource(getSelectableItemBackground());
        view.setOnClickListener(v -> {
            if (scopePopup != null) scopePopup.dismiss();
            onScopeSelected(itemId, groups);
        });
        content.addView(view, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(SCOPE_POPUP_ITEM_HEIGHT)));
    }

    private int getSelectableItemBackground() {
        TypedValue value = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }

    private boolean onScopeSelected(int itemId, List<String> groups) {
        if (itemId == MENU_SCOPE_ALL) {
            mCurrentSite = false;
            mGroup = "";
        } else if (itemId == MENU_SCOPE_CURRENT) {
            Site site = getHome();
            if (site.isEmpty() || !site.isSearchable()) {
                Notify.show(R.string.detail_site_not_searchable);
                return true;
            }
            mCurrentSite = true;
            mGroup = "";
            Notify.show(getString(R.string.search_scope_current_hint, site.getDisplayName()));
        } else if (itemId >= MENU_SCOPE_GROUP_OFFSET) {
            int index = itemId - MENU_SCOPE_GROUP_OFFSET;
            if (index < 0 || index >= groups.size()) return true;
            mCurrentSite = false;
            mGroup = groups.get(index);
            Notify.show(getString(R.string.search_scope_group_hint, mGroup));
        }
        requireActivity().invalidateOptionsMenu();
        return true;
    }

    private interface HotSaver {

        void save(String result);
    }

    @Override
    public void onItemClick(String text) {
        setKeyword(text);
        search();
    }

    @Override
    public void onDataChanged(int size) {
        mBinding.record.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        mBinding.recordRecycler.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        mBinding.recordRecycler.postDelayed(() -> mBinding.recordRecycler.requestLayout(), 250);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.menu_search, menu);
    }

    @Override
    public void onPrepareMenu(@NonNull Menu menu) {
        menu.findItem(R.id.action_reset).setVisible(!empty());
        menu.findItem(R.id.action_scope).setTitle(mCurrentSite ? getString(R.string.search_scope_current) : TextUtils.isEmpty(mGroup) ? getString(R.string.search_scope_all) : mGroup);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) requireActivity().getOnBackPressedDispatcher().onBackPressed();
        if (menuItem.getItemId() == R.id.action_reset) onReset();
        if (menuItem.getItemId() == R.id.action_scope) onScope();
        if (menuItem.getItemId() == R.id.action_site) onSite();
        return true;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) requireActivity().removeMenuProvider(this);
        else initMenu();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (scopePopup != null) scopePopup.dismiss();
        scopePopup = null;
        requireActivity().removeMenuProvider(this);
    }
}
