package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogViewingReportRangeBinding;
import com.fongmi.android.tv.viewing.ViewingReportRange;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ViewingReportRangeDialog {

    private final DialogViewingReportRangeBinding binding;
    private final RangeAdapter adapter;
    private final AlertDialog dialog;
    private Callback callback;

    public static ViewingReportRangeDialog create(Activity activity) {
        return new ViewingReportRangeDialog(activity);
    }

    private ViewingReportRangeDialog(Activity activity) {
        binding = DialogViewingReportRangeBinding.inflate(activity.getLayoutInflater());
        adapter = new RangeAdapter();
        binding.recycler.setAdapter(adapter);
        dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.viewing_report_select_range)
                .setView(binding.getRoot())
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.viewing_report_generate, (d, which) -> {
                    if (callback != null) callback.onSelected(adapter.getSelected());
                })
                .create();
    }

    public ViewingReportRangeDialog callback(Callback callback) {
        this.callback = callback;
        return this;
    }

    public void show() {
        adapter.select(ViewingReportRange.ALL);
        dialog.show();
    }

    public interface Callback {
        void onSelected(ViewingReportRange range);
    }

    private static class RangeAdapter extends RecyclerView.Adapter<RangeAdapter.Holder> {

        private final List<ViewingReportRange> ranges;
        private ViewingReportRange selected;

        public RangeAdapter() {
            this.ranges = new ArrayList<>(Arrays.asList(ViewingReportRange.values()));
            this.selected = ViewingReportRange.ALL;
        }

        public void select(ViewingReportRange range) {
            this.selected = range;
            notifyDataSetChanged();
        }

        public ViewingReportRange getSelected() {
            return selected;
        }

        @Override
        public int getItemCount() {
            return ranges.size();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            CheckedTextView button = (CheckedTextView) LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_single_choice, parent, false);
            button.setFocusable(true);
            button.setFocusableInTouchMode(false);
            button.setClickable(true);
            TypedValue outValue = new TypedValue();
            parent.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            button.setBackgroundResource(outValue.resourceId);
            return new Holder(button);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ViewingReportRange range = ranges.get(position);
            holder.button.setText(range.getDisplayLabel());
            holder.button.setChecked(range == selected);
            holder.button.setOnClickListener(v -> select(range));
        }

        static class Holder extends RecyclerView.ViewHolder {
            final CheckedTextView button;

            Holder(CheckedTextView button) {
                super(button);
                this.button = button;
            }
        }
    }
}
