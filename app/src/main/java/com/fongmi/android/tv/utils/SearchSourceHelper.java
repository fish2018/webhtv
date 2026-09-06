package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchSourceHelper {

    public static List<SearchSourceItem> buildItems() {
        List<SearchSourceItem> items = new ArrayList<>();
        items.add(new SearchSourceItem(SearchModeStore.MODE_ALL, R.string.search_source_all, false));
        SearchSourceItem home = new SearchSourceItem(SearchModeStore.MODE_HOME, R.string.search_source_home, false);
        Site site = VodConfig.get().getHome();
        home.subtitle = site != null ? site.getName() : "";
        items.add(home);
        SearchSourceItem tag = new SearchSourceItem(SearchModeStore.MODE_TAG, R.string.search_source_tag, true);
        tag.subtitle = buildTagSubtitle();
        items.add(tag);
        SearchSourceItem white = new SearchSourceItem(SearchModeStore.MODE_WHITE, R.string.search_source_white, true);
        int wn = SearchModeStore.getWhiteList().size();
        white.subtitle = wn > 0 ? String.valueOf(wn) : "";
        items.add(white);
        SearchSourceItem black = new SearchSourceItem(SearchModeStore.MODE_BLACK, R.string.search_source_black, true);
        int bn = SearchModeStore.getBlackList().size();
        black.subtitle = bn > 0 ? String.valueOf(bn) : "";
        items.add(black);
        return items;
    }

    private static String buildTagSubtitle() {
        List<String> groups = SearchModeStore.getTagGroups();
        if (groups.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) sb.append(" ");
            sb.append(groups.get(i));
        }
        return sb.toString();
    }

    public static List<Site> buildPickerSites(boolean tagMode) {
        if (tagMode) {
            List<Site> pseudo = new ArrayList<>();
            Set<String> groups = new HashSet<>();
            for (Site s : VodConfig.get().getSites()) groups.addAll(s.getGroups());
            for (String g : groups) {
                Site ps = new Site();
                ps.setKey(g);
                ps.setName(g);
                pseudo.add(ps);
            }
            return pseudo;
        }
        return VodConfig.get().getSites();
    }
}
