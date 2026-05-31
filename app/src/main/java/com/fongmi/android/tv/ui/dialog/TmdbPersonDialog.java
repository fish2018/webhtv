package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.databinding.DialogTmdbPersonBinding;
import com.fongmi.android.tv.ui.adapter.TmdbWorkAdapter;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class TmdbPersonDialog {

    private static final int FOCUS_STROKE = 0xFFFFD166;
    private static final int FOCUS_STROKE_DP = 3;
    private static final int NORMAL_STROKE_DP = 1;

    public interface Listener {
        void onWorkClick(TmdbItem item);
    }

    public static void show(Activity activity, TmdbPerson person, List<TmdbItem> works, Listener listener) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        DialogTmdbPersonBinding binding = DialogTmdbPersonBinding.inflate(LayoutInflater.from(activity));
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(binding.getRoot())
                .create();

        binding.name.setText(person.getName());
        binding.subtitle.setText(person.getSubtitle());
        binding.subtitle.setVisibility(person.getSubtitle().isEmpty() ? View.GONE : View.VISIBLE);
        binding.biography.setText(person.getBiography().isEmpty() ? activity.getString(R.string.detail_person_empty) : person.getBiography());
        ImgUtil.load(person.getName(), person.getProfileUrl(), binding.photo);

        boolean light = resolveLightTheme(activity);
        binding.recycler.setLayoutManager(new LinearLayoutManager(activity));
        binding.recycler.setNestedScrollingEnabled(false);
        TmdbWorkAdapter adapter = new TmdbWorkAdapter(item -> {
            if (listener != null) listener.onWorkClick(item);
            dialog.dismiss();
        });
        binding.recycler.setAdapter(adapter);
        adapter.setLight(light);
        adapter.setItems(works);
        binding.worksTitle.setVisibility(works.isEmpty() ? View.GONE : View.VISIBLE);
        binding.worksCount.setText(activity.getString(R.string.detail_person_work_count, works.size()));
        binding.worksCount.setVisibility(works.isEmpty() ? View.GONE : View.VISIBLE);
        binding.workHint.setVisibility(works.isEmpty() ? View.GONE : View.VISIBLE);
        binding.recycler.setVisibility(works.isEmpty() ? View.GONE : View.VISIBLE);
        binding.close.setOnClickListener(view -> dialog.dismiss());
        applyTheme(activity, binding, light);

        if (activity.isFinishing() || activity.isDestroyed()) return;
        dialog.show();
        binding.close.post(binding.close::requestFocus);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.56f);
            int width = activity.getResources().getDisplayMetrics().widthPixels;
            float ratio = width >= 1200 ? 0.78f : 0.94f;
            window.setLayout((int) (width * ratio), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private static void applyTheme(Activity activity, DialogTmdbPersonBinding binding, boolean light) {
        int panel = light ? 0xF2FFFFFF : 0xF2101821;
        int line = light ? 0x33424B57 : 0x33FFFFFF;
        int control = light ? 0xFFE7EDF3 : 0xFF263442;
        int primary = light ? 0xFF12202D : 0xFFFFFFFF;
        int secondary = light ? 0xB312202D : 0xB3FFFFFF;
        int card = light ? 0xFFF5F8FB : 0x261C2833;
        binding.panel.setCardBackgroundColor(panel);
        binding.panel.setStrokeColor(line);
        binding.biographyCard.setCardBackgroundColor(card);
        binding.biographyCard.setStrokeColor(light ? 0x22424B57 : 0x1FFFFFFF);
        tintTextTree(binding.getRoot(), primary);
        binding.subtitle.setTextColor(secondary);
        binding.infoHint.setTextColor(secondary);
        binding.workHint.setTextColor(secondary);
        binding.worksCount.setTextColor(secondary);
        binding.biography.setTextColor(light ? 0xDD12202D : 0xDDEAF2F8);
        binding.close.setTextColor(primary);
        binding.close.setBackgroundTintList(ColorStateList.valueOf(control));
        applyCloseFocus(binding, line, binding.close.hasFocus());
        binding.close.setOnFocusChangeListener((view, focused) -> applyCloseFocus(binding, line, focused));
    }

    private static void applyCloseFocus(DialogTmdbPersonBinding binding, int line, boolean focused) {
        binding.close.setStrokeColor(ColorStateList.valueOf(focused ? FOCUS_STROKE : line));
        binding.close.setStrokeWidth(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : NORMAL_STROKE_DP));
    }

    private static boolean resolveLightTheme(Activity activity) {
        int mode = Setting.getTmdbDetailTheme();
        if (mode == 1) return false;
        if (mode == 2) return true;
        return (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
    }

    private static void tintTextTree(View view, int color) {
        if (view instanceof RecyclerView) return;
        if (view instanceof TextView textView && !(view instanceof MaterialButton)) textView.setTextColor(color);
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) tintTextTree(group.getChildAt(i), color);
    }
}
