package com.fongmi.android.tv.ui.presenter;

import android.content.res.ColorStateList;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.databinding.AdapterTmdbCastBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.card.MaterialCardView;

public class TmdbCastPresenter extends Presenter {

    private static final int CARD_BACKGROUND = 0xFF16202A;
    private static final int STROKE_NORMAL = 0x26FFFFFF;
    private static final int STROKE_FOCUSED = 0xFFFFFFFF;

    private final OnClickListener mListener;

    public TmdbCastPresenter(OnClickListener listener) {
        this.mListener = listener;
    }

    public interface OnClickListener {
        void onItemClick(TmdbPerson item);
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        return new ViewHolder(AdapterTmdbCastBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        TmdbPerson person = (TmdbPerson) item;
        ViewHolder holder = (ViewHolder) viewHolder;
        bindFocusStyle(holder.binding.getRoot());
        holder.binding.name.setText(person.getName());
        holder.binding.role.setText(person.getSubtitle());
        ImgUtil.load(person.getName(), person.getProfileUrl(), holder.binding.profile);
        setOnClickListener(holder, view -> {
            if (mListener != null) mListener.onItemClick(person);
        });
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
    }

    private void bindFocusStyle(MaterialCardView card) {
        card.setRippleColor(ColorStateList.valueOf(0x00000000));
        card.setForeground(card.getContext().getDrawable(R.drawable.selector_tmdb_cast_focus));
        card.setStateListAnimator(null);
        applyFocusStyle(card, card.hasFocus());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) card.setDefaultFocusHighlightEnabled(false);
        card.setOnFocusChangeListener((view, focused) -> applyFocusStyle(card, focused));
    }

    private void applyFocusStyle(MaterialCardView card, boolean focused) {
        card.setActivated(focused);
        card.setCardBackgroundColor(CARD_BACKGROUND);
        card.setStrokeColor(focused ? STROKE_FOCUSED : STROKE_NORMAL);
        card.setStrokeWidth(ResUtil.dp2px(focused ? 3 : 1));
        card.setCardElevation(0);
        card.setTranslationZ(0);
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterTmdbCastBinding binding;

        public ViewHolder(@NonNull AdapterTmdbCastBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
