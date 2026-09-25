package com.fongmi.android.tv.setting;
 
import android.text.TextUtils;
 
import com.fongmi.android.tv.bean.MultiRepo;
import com.github.catvod.utils.Prefers;
 
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
 
/**
 * 多仓仓库列表存储。使用 Prefers 持久化。
 */
public final class MultiRepoStore {
 
    private static final String KEY_REPOS = "multi_repo_list";
 
    private MultiRepoStore() {
    }
 
    public static List<MultiRepo> get() {
        List<MultiRepo> list = MultiRepo.arrayFrom(Prefers.getString(KEY_REPOS, ""));
        return list == null ? new ArrayList<>() : list;
    }
 
    public static void save(List<MultiRepo> list) {
        Prefers.put(KEY_REPOS, MultiRepo.toJson(list == null ? new ArrayList<>() : list));
    }
 
    public static void add(MultiRepo repo) {
        List<MultiRepo> list = get();
        for (MultiRepo item : list) {
            if (item.getUrl().equals(repo.getUrl())) return;
        }
        list.add(repo);
        save(list);
    }
 
    public static void removeAt(int index) {
        List<MultiRepo> list = get();
        if (index < 0 || index >= list.size()) return;
        list.remove(index);
        save(list);
    }
 
    public static void update(MultiRepo repo) {
        List<MultiRepo> list = get();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getUrl().equals(repo.getUrl())) {
                list.set(i, repo);
                save(list);
                return;
            }
        }
    }
 
    public static boolean isEmpty() {
        return get().isEmpty();
    }
 
    public static int size() {
        return get().size();
    }
}
