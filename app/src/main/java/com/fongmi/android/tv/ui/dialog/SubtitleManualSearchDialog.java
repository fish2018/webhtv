package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.service.AiSubtitleTranslationService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.subtitle.SubtitlePlaybackSession;
import com.fongmi.android.tv.subtitle.model.SubtitleAsset;
import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchResult;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchStatus;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchType;
import com.fongmi.android.tv.subtitle.model.SubtitleRequest;
import com.fongmi.android.tv.subtitle.translate.SubtitleTranslationRequest;
import com.fongmi.android.tv.subtitle.translate.SubtitleTranslationResult;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SubtitleManualSearchDialog {

    private static final String AI_PROVIDER = "AI";
    private static final String AI_CANDIDATE_PREFIX = "ai-translated:";
    private static final String MIME_SRT = "application/x-subrip";

    private SubtitleManualSearchDialog() {
    }

    public static void show(FragmentActivity activity, SubtitlePlaybackSession session, SubtitlePlaybackSession.Host host) {
        if (activity == null || session == null || host == null) return;
        showKeywordDialog(activity, session, host, session.getManualSearchKeyword(host));
    }

    private static void showKeywordDialog(FragmentActivity activity, SubtitlePlaybackSession session, SubtitlePlaybackSession.Host host, String defaultKeyword) {
        TextInputEditText input = new TextInputEditText(activity);
        input.setSingleLine(true);
        input.setHint(R.string.search_keyword);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setMaxLines(1);
        input.setText(defaultKeyword);
        input.setSelectAllOnFocus(false);
        input.setSelection(input.getText() == null ? 0 : input.getText().length());
        input.setTextColor(0xFF202124);
        input.setHintTextColor(0xFF5F6368);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.subtitle_manual_search)
                .setView(input)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.play_search, null)
                .show();
        LightDialog.apply(dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positive.setOnClickListener(view -> {
            String keyword = input.getText() == null ? "" : input.getText().toString().trim();
            if (TextUtils.isEmpty(keyword)) return;
            Util.hideKeyboard(input);
            dialog.dismiss();
            search(activity, session, host, keyword);
        });
        input.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) positive.performClick();
            return true;
        });
        input.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN || event.getAction() != KeyEvent.ACTION_DOWN) return false;
            return positive.requestFocus();
        });
        Util.showKeyboard(input);
    }

    private static void search(FragmentActivity activity, SubtitlePlaybackSession session, SubtitlePlaybackSession.Host host, String keyword) {
        Notify.show(R.string.subtitle_manual_searching);
        session.manualSearch(host, keyword, (request, result, applied) -> {
            if (!isAlive(activity)) return;
            if (!canShowCandidates(result)) {
                notifySearchResult(result);
                return;
            }
            showCandidates(activity, session, host, result.getCandidates());
        });
    }

    private static void showCandidates(FragmentActivity activity, SubtitlePlaybackSession session, SubtitlePlaybackSession.Host host, List<SubtitleCandidate> candidates) {
        showCandidates(activity, session, host, candidates, null);
    }

    private static void showCandidates(FragmentActivity activity, SubtitlePlaybackSession session, SubtitlePlaybackSession.Host host, List<SubtitleCandidate> candidates, SubtitleCandidate selected) {
        if (candidates == null || candidates.isEmpty()) return;
        String[] labels = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) labels[i] = label(candidates.get(i));
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(ResUtil.getString(R.string.subtitle_manual_select_title, candidates.size()))
                .setNegativeButton(R.string.dialog_negative, null)
                .setSingleChoiceItems(labels, indexOfCandidate(candidates, selected), (d, which) -> {
                    d.dismiss();
                    resolve(activity, session, host, candidates, candidates.get(which));
                })
                .show();
        LightDialog.apply(dialog);
    }

    private static void resolve(FragmentActivity activity, SubtitlePlaybackSession session, SubtitlePlaybackSession.Host host, List<SubtitleCandidate> candidates, SubtitleCandidate candidate) {
        if (isGeneratedAiCandidate(candidate)) {
            applyGeneratedCandidate(activity, session, host, candidate);
            return;
        }
        Notify.show(R.string.subtitle_manual_resolving);
        session.resolveManual(host, candidate, (request, result, applied) -> {
            if (!isAlive(activity)) return;
            if (result != null && result.getStatus() == SubtitleMatchStatus.MATCHED && applied) {
                Notify.show(ResUtil.getString(R.string.subtitle_manual_applied, displayName(candidate)));
            } else if (result != null && result.getStatus() == SubtitleMatchStatus.MATCHED) {
                Notify.show(R.string.subtitle_manual_apply_failed);
            } else {
                notifySearchResult(result);
            }
        });
    }

    private static void applyGeneratedCandidate(FragmentActivity activity, SubtitlePlaybackSession session, SubtitlePlaybackSession.Host host, SubtitleCandidate candidate) {
        SubtitleAsset asset = assetFromGeneratedCandidate(candidate);
        if (asset == null) {
            Notify.show(R.string.subtitle_ai_apply_failed);
            return;
        }
        boolean applied = session.applySubtitleAsset(host, asset);
        if (applied) Notify.show(ResUtil.getString(R.string.subtitle_ai_applied, asset.getDisplayName()));
        else Notify.show(R.string.subtitle_ai_apply_failed);
    }

    private static void maybeOfferAiTranslate(FragmentActivity activity, SubtitlePlaybackSession session, SubtitlePlaybackSession.Host host, SubtitleRequest request, SubtitleMatchResult result, SubtitleCandidate candidate, List<SubtitleCandidate> candidates) {
        if (!shouldOfferAiTranslate(result, candidate)) return;
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.subtitle_ai_translate_title)
                .setMessage(R.string.subtitle_ai_translate_message)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.subtitle_ai_translate_action, (d, which) -> translate(activity, session, host, request, result, candidate, candidates))
                .show();
        LightDialog.apply(dialog);
    }

    private static void translate(FragmentActivity activity, SubtitlePlaybackSession session, SubtitlePlaybackSession.Host host, SubtitleRequest request, SubtitleMatchResult matchResult, SubtitleCandidate candidate, List<SubtitleCandidate> candidates) {
        AiConfig config = AiConfig.objectFrom(Setting.getAiConfig());
        if (!config.isReady()) {
            Notify.show(R.string.subtitle_ai_config_required);
            return;
        }
        Notify.show(R.string.subtitle_ai_translating);
        SubtitleTranslationRequest translationRequest = SubtitleTranslationRequest.builder()
                .playbackKey(request == null ? "" : request.getPlaybackKey())
                .subtitleRequest(request)
                .sourceAsset(matchResult.getAsset())
                .sourceLanguage(candidate == null ? "" : candidate.getLanguage())
                .targetLanguage("zh-Hans")
                .mode(SubtitleTranslationRequest.MODE_TRANSLATED)
                .trigger(SubtitleTranslationRequest.TRIGGER_MANUAL)
                .build();
        Task.execute(() -> {
            SubtitleTranslationResult result = new AiSubtitleTranslationService(config).translate(translationRequest);
            activity.runOnUiThread(() -> onTranslated(activity, session, host, result, candidates));
        });
    }

    private static void onTranslated(FragmentActivity activity, SubtitlePlaybackSession session, SubtitlePlaybackSession.Host host, SubtitleTranslationResult result, List<SubtitleCandidate> candidates) {
        if (!isAlive(activity)) return;
        if (result != null && (result.getStatus() == SubtitleTranslationResult.Status.TRANSLATED || result.getStatus() == SubtitleTranslationResult.Status.CACHE_HIT) && result.getTranslatedAsset() != null) {
            boolean applied = session.applySubtitleAsset(host, result.getTranslatedAsset());
            if (applied) {
                Notify.show(ResUtil.getString(R.string.subtitle_ai_applied, result.getTranslatedAsset().getDisplayName()));
                List<SubtitleCandidate> updated = withTranslatedCandidate(candidates, result);
                showCandidates(activity, session, host, updated, updated.isEmpty() ? null : updated.get(0));
            } else {
                Notify.show(R.string.subtitle_ai_apply_failed);
            }
            return;
        }
        String reason = result == null ? "" : result.getReason();
        if ("ai_config_required".equals(reason)) Notify.show(R.string.subtitle_ai_config_required);
        else if ("unsupported_format".equals(reason)) Notify.show(R.string.subtitle_ai_unsupported_format);
        else Notify.show(ResUtil.getString(R.string.subtitle_ai_failed, readableAiReason(reason)));
    }

    private static boolean canShowCandidates(SubtitleMatchResult result) {
        return result != null && result.getCandidates() != null && !result.getCandidates().isEmpty();
    }

    private static boolean shouldOfferAiTranslate(SubtitleMatchResult result, SubtitleCandidate candidate) {
        if (result == null || result.getAsset() == null) return false;
        if (isChinese(candidate == null ? "" : candidate.getLanguage()) || isChinese(result.getAsset().getLanguage())) return false;
        String source = (result.getAsset().getLocalPath() + " " + result.getAsset().getDisplayName() + " " + result.getAsset().getMimeType() + " " + (candidate == null ? "" : candidate.getFormat())).toLowerCase(Locale.ROOT);
        return source.contains(".srt") || source.contains("subrip");
    }

    private static boolean isChinese(String language) {
        String value = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return false;
        return value.startsWith("zh") || value.contains("chi") || value.contains("中文") || value.contains("简体") || value.contains("繁體") || value.contains("繁体");
    }

    static List<SubtitleCandidate> withTranslatedCandidate(List<SubtitleCandidate> candidates, SubtitleTranslationResult result) {
        List<SubtitleCandidate> items = new ArrayList<>();
        SubtitleCandidate translated = translatedCandidate(result);
        if (translated != null) items.add(translated);
        if (candidates != null) {
            for (SubtitleCandidate candidate : candidates) {
                if (!sameCandidate(candidate, translated)) items.add(candidate);
            }
        }
        return items;
    }

    static int indexOfCandidate(List<SubtitleCandidate> candidates, SubtitleCandidate selected) {
        if (candidates == null || selected == null) return -1;
        for (int i = 0; i < candidates.size(); i++) if (sameCandidate(candidates.get(i), selected)) return i;
        return -1;
    }

    static boolean isGeneratedAiCandidate(SubtitleCandidate candidate) {
        return candidate != null
                && AI_PROVIDER.equals(candidate.getProvider())
                && candidate.getCandidateId().startsWith(AI_CANDIDATE_PREFIX)
                && !TextUtils.isEmpty(candidate.getProviderPayload());
    }

    static SubtitleAsset assetFromGeneratedCandidate(SubtitleCandidate candidate) {
        if (!isGeneratedAiCandidate(candidate)) return null;
        String path = candidate.getProviderPayload();
        return new SubtitleAsset(path, path, displayName(candidate), candidate.getLanguage(), MIME_SRT, 0, true, 0L);
    }

    private static SubtitleCandidate translatedCandidate(SubtitleTranslationResult result) {
        if (result == null || result.getTranslatedAsset() == null) return null;
        if (result.getStatus() != SubtitleTranslationResult.Status.TRANSLATED && result.getStatus() != SubtitleTranslationResult.Status.CACHE_HIT) return null;
        SubtitleAsset asset = result.getTranslatedAsset();
        String path = TextUtils.isEmpty(asset.getLocalPath()) ? asset.getUri() : asset.getLocalPath();
        if (TextUtils.isEmpty(path)) return null;
        String name = TextUtils.isEmpty(asset.getDisplayName()) ? "AI subtitle" : asset.getDisplayName();
        String language = TextUtils.isEmpty(asset.getLanguage()) ? "zh-Hans" : asset.getLanguage();
        return new SubtitleCandidate(AI_PROVIDER, AI_CANDIDATE_PREFIX + path, name, language, "srt", "", 100, 0, 0, 0, SubtitleMatchType.MANUAL, "", false, path);
    }

    private static boolean sameCandidate(SubtitleCandidate first, SubtitleCandidate second) {
        if (first == null || second == null) return false;
        return TextUtils.equals(first.getProvider(), second.getProvider()) && TextUtils.equals(first.getCandidateId(), second.getCandidateId());
    }

    private static void notifySearchResult(SubtitleMatchResult result) {
        if (result == null) {
            Notify.show(R.string.subtitle_manual_search_failed);
            return;
        }
        if (result.getStatus() == SubtitleMatchStatus.SKIPPED && "provider_unavailable".equals(result.getReason())) {
            Notify.show(R.string.subtitle_auto_match_provider_unavailable);
        } else if (result.getStatus() == SubtitleMatchStatus.ERROR && "inactive".equals(result.getReason())) {
            Notify.show(R.string.subtitle_manual_inactive);
        } else if (result.getStatus() == SubtitleMatchStatus.ERROR) {
            Notify.show(ResUtil.getString(R.string.subtitle_auto_match_failed, readableReason(result.getReason())));
        } else {
            Notify.show(R.string.subtitle_manual_search_empty);
        }
    }

    private static String label(SubtitleCandidate candidate) {
        StringBuilder builder = new StringBuilder(displayName(candidate));
        String meta = meta(candidate);
        if (!TextUtils.isEmpty(meta)) builder.append('\n').append(meta);
        return builder.toString();
    }

    private static String meta(SubtitleCandidate candidate) {
        if (candidate == null) return "";
        StringBuilder builder = new StringBuilder();
        append(builder, candidate.getProvider());
        append(builder, candidate.getLanguage());
        append(builder, candidate.getFormat());
        if (candidate.getScore() > 0) append(builder, String.format(Locale.US, "%d", candidate.getScore()));
        return builder.toString();
    }

    private static void append(StringBuilder builder, String value) {
        if (TextUtils.isEmpty(value)) return;
        if (builder.length() > 0) builder.append(" · ");
        builder.append(value);
    }

    private static String displayName(SubtitleCandidate candidate) {
        if (candidate == null) return "";
        if (!TextUtils.isEmpty(candidate.getDisplayName())) return candidate.getDisplayName();
        return TextUtils.isEmpty(candidate.getCandidateId()) ? candidate.getProvider() : candidate.getCandidateId();
    }

    private static String readableReason(String reason) {
        return TextUtils.isEmpty(reason) ? ResUtil.getString(R.string.subtitle_auto_match_unknown_reason) : reason;
    }

    private static String readableAiReason(String reason) {
        return TextUtils.isEmpty(reason) ? ResUtil.getString(R.string.subtitle_ai_unknown_reason) : reason;
    }

    private static boolean isAlive(FragmentActivity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }
}
