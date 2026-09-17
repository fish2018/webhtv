package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogRecommendationFeedbackBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.service.RecommendationFeedbackStore;
import com.fongmi.android.tv.ui.adapter.RecommendationFeedbackAdapter;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class RecommendationFeedbackDialog implements RecommendationFeedbackAdapter.OnClickListener {

    private final DialogRecommendationFeedbackBinding binding;
    private final RecommendationFeedbackAdapter adapter;
    private final AlertDialog dialog;
    private Runnable onChanged;

    public static RecommendationFeedbackDialog create(FragmentActivity activity) {
        return new RecommendationFeedbackDialog(activity);
    }

    private RecommendationFeedbackDialog(FragmentActivity activity) {
        binding = DialogRecommendationFeedbackBinding.inflate(LayoutInflater.from(activity));
        adapter = new RecommendationFeedbackAdapter(this);
        binding.recycler.setLayoutManager(new LinearLayoutManager(activity));
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.recommendation_feedback_title)
                .setView(binding.getRoot())
                .setNegativeButton(R.string.detail_close, null)
                .setPositiveButton(R.string.recommendation_feedback_restore_all, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button restoreAll = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            restoreAll.setOnClickListener(view -> clearAll());
            renderState();
        });
    }

    public RecommendationFeedbackDialog onChanged(Runnable onChanged) {
        this.onChanged = onChanged;
        return this;
    }

    public void show() {
        adapter.reload();
        renderState();
        dialog.show();
    }

    @Override
    public void onItemClick(RecommendationFeedbackStore.Entry item) {
        if (!RecommendationFeedbackStore.remove(item)) return;
        adapter.reload();
        renderState();
        notifyChanged();
        Notify.show(R.string.recommendation_feedback_restored);
    }

    private void clearAll() {
        if (RecommendationFeedbackStore.clear() == 0) return;
        adapter.reload();
        renderState();
        notifyChanged();
        Notify.show(R.string.recommendation_feedback_restored_all);
    }

    private void renderState() {
        boolean empty = adapter.getItemCount() == 0;
        binding.recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!dialog.isShowing()) return;
        Button restoreAll = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (restoreAll != null) restoreAll.setEnabled(!empty);
    }

    private void notifyChanged() {
        RefreshEvent.history();
        if (onChanged != null) onChanged.run();
    }
}
