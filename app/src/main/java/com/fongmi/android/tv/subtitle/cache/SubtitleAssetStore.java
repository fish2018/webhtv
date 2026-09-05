package com.fongmi.android.tv.subtitle.cache;

import androidx.media3.common.C;

import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.subtitle.SubtitleUriFactory;
import com.fongmi.android.tv.subtitle.model.SubtitleAsset;
import com.fongmi.android.tv.utils.FileUtil;
import com.github.catvod.utils.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SubtitleAssetStore {

    private final SubtitleUriFactory uriFactory;

    public SubtitleAssetStore() {
        this(new SubtitleUriFactory());
    }

    public SubtitleAssetStore(SubtitleUriFactory uriFactory) {
        this.uriFactory = uriFactory;
    }

    public File root() {
        File dir = Path.cache("subtitle_asset");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public File file(String provider, String candidateId, String suffix) {
        String safeSuffix = suffix == null || suffix.isEmpty() ? ".sub" : suffix;
        return new File(root(), provider + "_" + com.github.catvod.utils.Util.md5(candidateId == null ? "" : candidateId) + safeSuffix);
    }

    public File unzipAndPick(File archive, String provider, String candidateId) {
        File folder = new File(root(), provider + "_" + com.github.catvod.utils.Util.md5(candidateId == null ? "" : candidateId) + "_zip");
        if (!folder.exists()) folder.mkdirs();
        FileUtil.zipDecompress(archive, folder);
        return pickSubtitleFile(folder);
    }

    public File pickSubtitleFile(File folder) {
        List<File> matches = new ArrayList<>();
        collect(folder, matches);
        matches.sort(Comparator.comparingInt(this::formatWeight).thenComparing(File::getName));
        return matches.isEmpty() ? null : matches.get(0);
    }

    public SubtitleAsset toAsset(File file, String displayName, String language, boolean fromCache) {
        if (file == null) return null;
        String name = displayName == null || displayName.isEmpty() ? file.getName() : displayName;
        return new SubtitleAsset(uriFactory.fromLocalFile(file), file.getAbsolutePath(), name, language, PlayerHelper.getSubtitleMimeType(file.getName()), C.SELECTION_FLAG_DEFAULT, fromCache, 0L);
    }

    private void collect(File file, List<File> matches) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return;
            for (File child : children) collect(child, matches);
            return;
        }
        String name = file.getName().toLowerCase();
        if (name.endsWith(".ass") || name.endsWith(".ssa") || name.endsWith(".srt") || name.endsWith(".vtt")) matches.add(file);
    }

    private int formatWeight(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".ass")) return 0;
        if (name.endsWith(".ssa")) return 1;
        if (name.endsWith(".srt")) return 2;
        if (name.endsWith(".vtt")) return 3;
        return 4;
    }
}
