package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogRecommendationFeedbackBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.service.RecommendationFeedbackStore;
import com.fongmi.android.tv.ui.adapter.RecommendationFeedbackAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class RecommendationFeedbackDialog extends BaseAlertDialog implements RecommendationFeedbackAdapter.OnClickListener {

    private DialogRecommendationFeedbackBinding binding;
    private RecommendationFeedbackAdapter adapter;
    private Runnable onChanged;

    public static RecommendationFeedbackDialog create() {
        return new RecommendationFeedbackDialog();
    }

    public RecommendationFeedbackDialog onChanged(Runnable onChanged) {
        this.onChanged = onChanged;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = LightDialog.create(requireContext(), getString(R.string.recommendation_feedback_title), getBinding().getRoot(), 0.48f, 0.9f, 620);
        initView();
        initEvent();
        return dialog;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogRecommendationFeedbackBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new RecommendationFeedbackAdapter(this);
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 10));
        renderState();
    }

    @Override
    protected void initEvent() {
        binding.clearAll.setOnClickListener(view -> clearAll());
        binding.close.setOnClickListener(view -> dismiss());
    }

    @Override
    public void onItemClick(RecommendationFeedbackStore.Entry item) {
        int position = adapter.indexOf(item);
        if (!RecommendationFeedbackStore.remove(item)) return;
        adapter.reload();
        renderState();
        notifyChanged();
        Notify.show(R.string.recommendation_feedback_restored);
        if (adapter.getItemCount() == 0) binding.close.requestFocus();
        else adapter.focus(binding.recycler, position);
    }

    private void clearAll() {
        if (RecommendationFeedbackStore.clear() == 0) return;
        adapter.reload();
        renderState();
        notifyChanged();
        Notify.show(R.string.recommendation_feedback_restored_all);
        binding.close.requestFocus();
    }

    private void renderState() {
        boolean empty = adapter.getItemCount() == 0;
        binding.recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.clearAll.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void notifyChanged() {
        RefreshEvent.history();
        if (onChanged != null) onChanged.run();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) binding.close.post(binding.close::requestFocus);
        else binding.recycler.post(() -> adapter.focusFirst(binding.recycler));
    }
}
