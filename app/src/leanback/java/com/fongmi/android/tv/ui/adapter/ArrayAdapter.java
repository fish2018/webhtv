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
    private int nextFocusDown;
    private int nextFocusUp;

    public ArrayAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
        forward = ResUtil.getString(R.string.play_forward);
        reverse = ResUtil.getString(R.string.play_reverse);
        backward = ResUtil.getString(R.string.play_backward);
        nextFocusUp = R.id.flag;
        nextFocusDown = R.id.episode;
    }

    public void addAll(List<String> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void clear() {
        mItems.clear();
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
            return Math.max(0, start <= end ? start - 1 : (position - 2) * 40);
        } catch (Exception e) {
            return 0;
        }
    }

    public void setNextFocus(int nextFocusUp, int nextFocusDown) {
        if (this.nextFocusUp == nextFocusUp && this.nextFocusDown == nextFocusDown) return;
        this.nextFocusUp = nextFocusUp;
        this.nextFocusDown = nextFocusDown;
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
        holder.binding.text.setNextFocusUpId(nextFocusUp == 0 ? View.NO_ID : nextFocusUp);
        holder.binding.text.setNextFocusDownId(nextFocusDown == 0 ? View.NO_ID : nextFocusDown);
        if (text.equals(reverse)) holder.binding.getRoot().setOnClickListener(view -> mListener.onRevSort());
        else if (text.equals(backward) || text.equals(forward)) holder.binding.getRoot().setOnClickListener(view -> mListener.onRevPlay(holder.binding.text));
        else holder.binding.getRoot().setOnClickListener(null);
    }

    public interface OnClickListener {

        void onRevSort();

        void onRevPlay(TextView view);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterArrayBinding binding;

        ViewHolder(@NonNull AdapterArrayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
