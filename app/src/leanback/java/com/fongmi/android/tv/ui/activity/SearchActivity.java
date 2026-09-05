package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteGroupOrderStore;
import com.fongmi.android.tv.ui.adapter.HotWordAdapter;
import com.fongmi.android.tv.ui.adapter.RecordAdapter;
import com.fongmi.android.tv.ui.adapter.WordAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomKeyboard;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SearchSuggest;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.ZhuToPin;
import com.github.catvod.net.OkHttp;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Response;

public class SearchActivity extends BaseActivity implements WordAdapter.OnClickListener, RecordAdapter.OnClickListener, CustomKeyboard.Callback {

    private static final int HOT_LIMIT = 10;
    private static final int SCOPE_POPUP_ITEM_HEIGHT_DP = 52;
    private static final int SCOPE_POPUP_ITEM_GAP_DP = 2;
    private static final int SCOPE_POPUP_MAX_ITEMS = 7;
    private static final int SCOPE_POPUP_MIN_WIDTH_DP = 184;
    private static final int SCOPE_POPUP_PADDING_DP = 8;

    private ActivitySearchBinding mBinding;
    private RecordAdapter mRecordAdapter;
    private WordAdapter mWordAdapter;
    private HotWordAdapter mHotTvAdapter;
    private HotWordAdapter mHotMovieAdapter;
    private HotWordAdapter mHotVarietyAdapter;
    private List<Word.Data> mIqiyiWords = new ArrayList<>();
    private List<Word.Data> mTencentWords = new ArrayList<>();
    private String mScopeGroup = "";
    private boolean mCurrentSite;
    private int mSuggestSeq;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SearchActivity.class));
    }

    public static void start(Activity activity, String keyword) {
        start(activity, keyword, null);
    }

    public static void start(Activity activity, String keyword, String siteKey) {
        start(activity, keyword, siteKey, null, null);
    }

    public static void start(Activity activity, String keyword, String siteKey, String pic, String wallPic) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra("keyword", keyword);
        intent.putExtra("siteKey", siteKey);
        intent.putExtra("pic", pic);
        intent.putExtra("wallPic", wallPic);
        activity.startActivity(intent);
    }

    public static void directFromHistory(Activity activity, History history) {
        directFromHistory(activity, history, history.getVodName(), VodConfig.getCid());
    }

    public static void directFromHistory(Activity activity, History history, String keyword) {
        directFromHistory(activity, history, keyword, VodConfig.getCid());
    }

    public static void directFromHistory(Activity activity, History history, String keyword, int targetCid) {
        CollectActivity.startFromHistory(activity, history, keyword, targetCid);
    }

    public static void direct(Activity activity, String keyword) {
        direct(activity, keyword, null, null, null);
    }

    public static void direct(Activity activity, String keyword, String siteKey, String pic, String wallPic) {
        CollectActivity.start(activity, keyword, siteKey, null, pic, wallPic);
    }

    private String getKeyword() {
        String keyword = getIntent().getStringExtra("keyword");
        return keyword != null ? keyword : "";
    }

    private String getSiteKey() {
        String siteKey = getIntent().getStringExtra("siteKey");
        return siteKey != null ? siteKey : "";
    }

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private String getPic() {
        return getIntent().getStringExtra("pic");
    }

    private String getWallPic() {
        return getIntent().getStringExtra("wallPic");
    }

    private boolean empty() {
        return mBinding.keyword.getText().toString().trim().isEmpty();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mCurrentSite = !TextUtils.isEmpty(getSiteKey());
        CustomKeyboard.init(this, mBinding);
        setRecyclerView();
        checkKeyword();
        setSearchScope();
        onSearch();
    }

    @Override
    protected void initEvent() {
        mBinding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onSearch();
            return true;
        });
        mBinding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                getWord(s.toString());
            }
        });
        mBinding.mic.setOnClickListener(v -> mBinding.mic.start());
        mBinding.searchScope.setOnClickListener(this::showScopeMenu);
        mBinding.searchScope.setOnLongClickListener(v -> {
            showScopeMenu(v);
            return true;
        });
        mBinding.mic.setListener(this, new CustomTextListener() {
            @Override
            public void onResults(String result) {
                if (!result.isEmpty()) setKeyword(result);
                mBinding.keyword.requestFocus();
            }
        });
    }

    private void setRecyclerView() {
        setWordRecycler(mBinding.wordRecycler, mWordAdapter = new WordAdapter(this));
        setHotRecycler(mBinding.hotTvRecycler, mHotTvAdapter = new HotWordAdapter(this));
        setHotRecycler(mBinding.hotMovieRecycler, mHotMovieAdapter = new HotWordAdapter(this));
        setHotRecycler(mBinding.hotVarietyRecycler, mHotVarietyAdapter = new HotWordAdapter(this));
        mBinding.recordRecycler.setHasFixedSize(false);
        mBinding.recordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        mBinding.recordRecycler.setAdapter(mRecordAdapter = new RecordAdapter(this));
    }

    private void setWordRecycler(RecyclerView recyclerView, WordAdapter adapter) {
        recyclerView.setItemAnimator(null);
        recyclerView.setHasFixedSize(false);
        recyclerView.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        recyclerView.setAdapter(adapter);
    }

    private void setHotRecycler(RecyclerView recyclerView, HotWordAdapter adapter) {
        recyclerView.setItemAnimator(null);
        recyclerView.setHasFixedSize(false);
        recyclerView.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        recyclerView.setAdapter(adapter);
    }

    private void checkKeyword() {
        setKeyword(getKeyword());
        getWord(getKeyword());
    }

    private void setKeyword(String text) {
        mBinding.keyword.setText(text);
        mBinding.keyword.setSelection(text.length());
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
        String keyword = ZhuToPin.get(text);
        OkHttp.newCall(SearchSuggest.iqiyiUrl(keyword)).enqueue(getSuggestCallback(seq, false));
        OkHttp.newCall(SearchSuggest.tencentUrl(keyword)).enqueue(getSuggestCallback(seq, true));
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
        if (!empty()) return;
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
        if (seq != mSuggestSeq || empty()) return;
        if (tencent) mTencentWords = SearchSuggest.parseTencent(result);
        else mIqiyiWords = SearchSuggest.parseIqiyi(result);
        mWordAdapter.setItems(SearchSuggest.merge(mIqiyiWords, mTencentWords));
    }

    @Override
    public void onItemClick(String text) {
        setKeyword(text);
        onSearch();
    }

    @Override
    public void onDataChanged(int size) {
        mBinding.recordLayout.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        if (size == 0) focusFirstWord();
    }

    @Override
    public void onSearch() {
        if (empty()) return;
        String keyword = mBinding.keyword.getText().toString().trim();
        App.post(() -> mRecordAdapter.add(keyword), 250);
        Util.hideKeyboard(mBinding.keyword);
        CollectActivity.start(this, keyword, getSearchSiteKey(), mScopeGroup, getPic(), getWallPic());
    }

    private String getSearchSiteKey() {
        if (!mCurrentSite) return "";
        return TextUtils.isEmpty(getSiteKey()) ? getHome().getKey() : getSiteKey();
    }

    private void setSearchScope() {
        if (!TextUtils.isEmpty(mScopeGroup)) mBinding.searchScope.setText(mScopeGroup);
        else mBinding.searchScope.setText(mCurrentSite ? R.string.search_scope_current : R.string.search_scope_all);
    }

    private void showScopeMenu(View anchor) {
        List<String> groups = SiteGroupOrderStore.sort(Site.getGroups(VodConfig.get().getSites()));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = ResUtil.dp2px(SCOPE_POPUP_PADDING_DP);
        content.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundResource(R.drawable.shape_search_scope_popup);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int width = Math.max(ResUtil.getTextWidth(getString(R.string.search_scope_all), 18), ResUtil.getTextWidth(getString(R.string.search_scope_current), 18));
        for (String group : groups) width = Math.max(width, ResUtil.getTextWidth(group, 18));
        int itemHeight = ResUtil.dp2px(SCOPE_POPUP_ITEM_HEIGHT_DP);
        int itemGap = ResUtil.dp2px(SCOPE_POPUP_ITEM_GAP_DP);
        int popupWidth = Math.max(ResUtil.dp2px(SCOPE_POPUP_MIN_WIDTH_DP), width + ResUtil.dp2px(56));
        popupWidth = Math.min(Math.max(anchor.getWidth(), popupWidth), ResUtil.getScreenWidth() - ResUtil.dp2px(48));
        int rowHeight = itemHeight + itemGap * 2;
        int popupHeight = Math.min((groups.size() + 2) * rowHeight + padding * 2, SCOPE_POPUP_MAX_ITEMS * rowHeight + padding * 2);
        PopupWindow popup = new PopupWindow(scroll, popupWidth, popupHeight, true);
        addScopeItem(content, getString(R.string.search_scope_all), !mCurrentSite && TextUtils.isEmpty(mScopeGroup), () -> selectScope(false, "", popup));
        addScopeItem(content, getString(R.string.search_scope_current), mCurrentSite, () -> selectCurrent(popup));
        for (String group : groups) addScopeItem(content, group, !mCurrentSite && TextUtils.equals(mScopeGroup, group), () -> selectScope(false, group, popup));
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(ResUtil.dp2px(12));
        popup.setOnDismissListener(anchor::requestFocus);
        popup.showAsDropDown(anchor, anchor.getWidth() - popupWidth, ResUtil.dp2px(8), Gravity.NO_GRAVITY);
    }

    private void addScopeItem(LinearLayout parent, String text, boolean selected, Runnable action) {
        com.google.android.material.textview.MaterialTextView view = new com.google.android.material.textview.MaterialTextView(this);
        view.setText(text);
        view.setTextColor(ContextCompat.getColorStateList(this, R.color.selector_search_scope_text));
        view.setTextSize(18);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setIncludeFontPadding(false);
        view.setFocusable(true);
        view.setSelected(selected);
        view.setBackgroundResource(R.drawable.selector_search_scope_item);
        int padding = ResUtil.dp2px(20);
        view.setPadding(padding, 0, padding, 0);
        view.setOnClickListener(v -> action.run());
        int gap = ResUtil.dp2px(SCOPE_POPUP_ITEM_GAP_DP);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(SCOPE_POPUP_ITEM_HEIGHT_DP));
        params.setMargins(gap, gap, gap, gap);
        parent.addView(view, params);
        if (selected) view.post(view::requestFocus);
    }

    private void selectCurrent(PopupWindow popup) {
        Site site = getHome();
        if (site.isEmpty() || !site.isSearchable()) {
            Notify.show(R.string.detail_site_not_searchable);
            return;
        }
        Notify.show(getString(R.string.search_scope_current_hint, site.getDisplayName()));
        selectScope(true, "", popup);
    }

    private void selectScope(boolean current, String group, PopupWindow popup) {
        mCurrentSite = current;
        mScopeGroup = group;
        setSearchScope();
        popup.dismiss();
    }

    @Override
    public void showDialog() {
        SiteDialog.create().search().show(this);
    }

    @Override
    public void onRemote() {
        PushActivity.start(this, 1);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) showDialog();
        if (KeyUtil.isActionDown(event) && findFocus(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private boolean findFocus(KeyEvent event) {
        View current = getCurrentFocus();
        if (current == mBinding.keyword) return handleKeywordKey(event);
        if (current == mBinding.searchScope) return handleSearchScopeKey(event);
        View inKeyboard = mBinding.keyboard.findContainingItemView(current);
        View inWord = mBinding.wordRecycler.findContainingItemView(current);
        View inRecord = mBinding.recordRecycler.findContainingItemView(current);
        RecyclerView inHot = findHotRecycler(current);
        if (inKeyboard != null) return handleKeyboardKey(event, inKeyboard);
        if (inRecord != null) return handleRecordKey(event, inRecord);
        if (inWord != null) return handleWordKey(event, inWord);
        if (inHot != null) return handleHotWordKey(event, inHot, inHot.findContainingItemView(current));
        return false;
    }

    private View findNearestInLastRow(RecyclerView rv, int targetLeft) {
        if (rv.getChildCount() == 0) return null;
        int lastTop = rv.getChildAt(rv.getChildCount() - 1).getTop();
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == lastTop) {
                int dist = Math.abs(child.getLeft() - targetLeft);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = child;
                }
            }
        }
        return nearest;
    }

    private boolean isFirstRow(RecyclerView rv, View item) {
        View first = rv.getChildAt(0);
        return first != null && item.getTop() == first.getTop();
    }

    private boolean isLastRow(RecyclerView rv, View item) {
        View last = rv.getChildAt(rv.getChildCount() - 1);
        return last != null && item.getTop() == last.getTop();
    }

    private boolean isFirstInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int left = focused.getLeft();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == top && child.getLeft() < left) return false;
        }
        return true;
    }

    private boolean isLastInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int right = focused.getRight();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == top && child.getRight() > right) return false;
        }
        return true;
    }

    private boolean handleKeywordKey(KeyEvent event) {
        if (!KeyUtil.isRightKey(event)) return false;
        if (mBinding.keyword.getSelectionEnd() < mBinding.keyword.getText().length()) return false;
        mBinding.searchScope.requestFocus();
        return true;
    }

    private boolean handleSearchScopeKey(KeyEvent event) {
        if (KeyUtil.isLeftKey(event)) {
            mBinding.keyword.requestFocus();
            mBinding.keyword.setSelection(mBinding.keyword.length());
            return true;
        }
        if (!KeyUtil.isRightKey(event)) return false;
        boolean hasRecord = mBinding.recordLayout.getVisibility() == View.VISIBLE;
        return hasRecord ? focusFirst(mBinding.recordRecycler) : focusFirstWord();
    }

    private boolean handleKeyboardKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.keyboard, item)) {
            mBinding.keyword.requestFocus();
            return true;
        }
        if (KeyUtil.isLeftKey(event) && isFirstInRow(mBinding.keyboard, item)) return true;
        return KeyUtil.isDownKey(event) && isLastRow(mBinding.keyboard, item);
    }

    private boolean handleWordKey(KeyEvent event, View item) {
        if (KeyUtil.isRightKey(event)) return isLastInRow(mBinding.wordRecycler, item);
        if (KeyUtil.isDownKey(event)) return isLastRow(mBinding.wordRecycler, item);
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.wordRecycler, item)) {
            if (mBinding.recordLayout.getVisibility() == View.VISIBLE) {
                View child = findNearestInLastRow(mBinding.recordRecycler, item.getLeft());
                if (child != null) {
                    mBinding.scroll.smoothScrollTo(0, 0);
                    child.requestFocus();
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    private boolean handleHotWordKey(KeyEvent event, RecyclerView recyclerView, View item) {
        if (KeyUtil.isRightKey(event)) return isLastInRow(recyclerView, item);
        if (KeyUtil.isDownKey(event) && isLastRow(recyclerView, item)) {
            RecyclerView next = findHotRecycler(recyclerView, 1);
            return next == null || focusFirst(next);
        }
        if (KeyUtil.isUpKey(event) && isFirstRow(recyclerView, item)) {
            RecyclerView prev = findHotRecycler(recyclerView, -1);
            if (prev != null) {
                View child = findNearestInLastRow(prev, item.getLeft());
                if (child != null) {
                    child.requestFocus();
                    return true;
                }
                return focusFirst(prev);
            }
            if (mBinding.recordLayout.getVisibility() == View.VISIBLE) {
                View child = findNearestInLastRow(mBinding.recordRecycler, item.getLeft());
                if (child != null) {
                    mBinding.scroll.smoothScrollTo(0, 0);
                    child.requestFocus();
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    private boolean handleRecordKey(KeyEvent event, View item) {
        if (KeyUtil.isRightKey(event)) return isLastInRow(mBinding.recordRecycler, item);
        if (KeyUtil.isUpKey(event)) return isFirstRow(mBinding.recordRecycler, item);
        if (KeyUtil.isDownKey(event) && isLastRow(mBinding.recordRecycler, item)) return focusFirstWord();
        return false;
    }

    private RecyclerView findHotRecycler(View current) {
        for (RecyclerView recyclerView : getHotRecyclers()) {
            if (recyclerView.findContainingItemView(current) != null) return recyclerView;
        }
        return null;
    }

    private RecyclerView findHotRecycler(RecyclerView current, int direction) {
        RecyclerView[] recyclers = getHotRecyclers();
        for (int i = 0; i < recyclers.length; i++) {
            if (recyclers[i] != current) continue;
            for (int j = i + direction; j >= 0 && j < recyclers.length; j += direction) {
                if (recyclers[j].getChildCount() > 0) return recyclers[j];
            }
        }
        return null;
    }

    private RecyclerView[] getHotRecyclers() {
        return new RecyclerView[]{mBinding.hotTvRecycler, mBinding.hotMovieRecycler, mBinding.hotVarietyRecycler};
    }

    private boolean focusFirstWord() {
        if (mBinding.hotGroup.getVisibility() != View.VISIBLE) return focusFirst(mBinding.wordRecycler);
        for (RecyclerView recyclerView : getHotRecyclers()) {
            if (focusFirst(recyclerView)) return true;
        }
        return false;
    }

    private boolean focusFirst(RecyclerView rv) {
        View child = rv.getChildAt(0);
        if (child == null) return false;
        child.requestFocus();
        return true;
    }

    private interface HotSaver {

        void save(String result);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mBinding.mic.setFocusable(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBinding.mic.setFocusable(true);
        mBinding.keyword.requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBinding.mic.destroy();
    }
}
