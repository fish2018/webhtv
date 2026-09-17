package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterArrayBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class ArrayAdapter extends RecyclerView.Adapter<ArrayAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<String> mItems;
    private final String backward;
    private final String forward;
    private final String reverse;
    private View.OnKeyListener keyListener;
    private int nextFocusDown;
    private int nextFocusUp;
    private int segmentSize;
    private int selectedPosition = -1;

    public ArrayAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
        forward = ResUtil.getString(R.string.play_forward);
        reverse = ResUtil.getString(R.string.play_reverse);
        backward = ResUtil.getString(R.string.play_backward);
        nextFocusUp = R.id.flag;
        nextFocusDown = R.id.episode;
        segmentSize = 40;
    }

    public void addAll(List<String> items) {
        mItems.clear();
        mItems.addAll(items);
        selectedPosition = -1;
        notifyDataSetChanged();
    }

    public void clear() {
        mItems.clear();
        selectedPosition = -1;
        notifyDataSetChanged();
    }

    public int getStart(int position) {
        if (position < 0 || position >= mItems.size()) return 0;
        String text = mItems.get(position);
        int index = text.indexOf("-");
        if (index <= 0) return 0;
        try {
            int start = Integer.parseInt(text.substring(0, index));
            int end = Integer.parseInt(text.substring(index + 1));
            return Math.max(0, start <= end ? start - 1 : (position - 2) * segmentSize);
        } catch (Exception e) {
            return 0;
        }
    }

    public void setSegmentSize(int segmentSize) {
        this.segmentSize = Math.max(1, segmentSize);
    }

    public void setSelectedPosition(int position) {
        if (selectedPosition == position) return;
        int previous = selectedPosition;
        selectedPosition = position;
        if (previous >= 0 && previous < mItems.size()) notifyItemChanged(previous);
        if (position >= 0 && position < mItems.size()) notifyItemChanged(position);
    }

    public void setNextFocus(int nextFocusUp, int nextFocusDown) {
        if (this.nextFocusUp == nextFocusUp && this.nextFocusDown == nextFocusDown) return;
        this.nextFocusUp = nextFocusUp;
        this.nextFocusDown = nextFocusDown;
        notifyDataSetChanged();
    }

    public void setOnKeyListener(View.OnKeyListener keyListener) {
        this.keyListener = keyListener;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterArrayBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String text = mItems.get(position);
        holder.binding.text.setText(text);
        holder.binding.text.setActivated(position == selectedPosition);
        holder.binding.text.setNextFocusUpId(nextFocusUp == 0 ? View.NO_ID : nextFocusUp);
        holder.binding.text.setNextFocusDownId(nextFocusDown == 0 ? View.NO_ID : nextFocusDown);
        holder.binding.text.setOnKeyListener(keyListener);
        holder.binding.text.setOnFocusChangeListener(null);
        if (text.equals(reverse)) holder.binding.getRoot().setOnClickListener(view -> mListener.onRevSort());
        else if (text.equals(backward) || text.equals(forward)) holder.binding.getRoot().setOnClickListener(view -> mListener.onRevPlay(holder.binding.text));
        else {
            holder.binding.text.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) mListener.onSegmentFocus(position);
            });
            holder.binding.getRoot().setOnClickListener(view -> mListener.onSegmentClick(position));
        }
    }

    public interface OnClickListener {

        void onRevSort();

        void onRevPlay(TextView view);

        void onSegmentClick(int position);

        void onSegmentFocus(int position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterArrayBinding binding;

        ViewHolder(@NonNull AdapterArrayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
