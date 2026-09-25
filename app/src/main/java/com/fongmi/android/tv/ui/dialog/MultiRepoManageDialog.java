package com.fongmi.android.tv.ui.dialog;
 
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
 
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
 
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.MultiRepo;
import com.fongmi.android.tv.setting.MultiRepoStore;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
 
import java.util.List;
 
public class MultiRepoManageDialog extends DialogFragment {
 
    public interface Callback {
        void onChanged();
    }
 
    private Callback callback;
 
    public static MultiRepoManageDialog show(@NonNull FragmentActivity activity, @Nullable Callback callback) {
        MultiRepoManageDialog dialog = new MultiRepoManageDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), MultiRepoManageDialog.class.getSimpleName());
        return dialog;
    }
 
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }
 
    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(requireContext());
        // 横屏TV 0.6f；手机竖屏0.85f
        if (land) {
            params.width = (int) (ResUtil.getScreenWidth(requireContext()) * 0.55f);
        } else {
            params.width = (int) (ResUtil.getScreenWidth(requireContext()) * 0.85f);
        }
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.dimAmount = 0.58f;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
    }
 
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.shape_shell_proxy_dialog);
        int horizontal = ResUtil.dp2px(24);
        int vertical = ResUtil.dp2px(20);
        root.setPadding(horizontal, ResUtil.dp2px(24), horizontal, vertical);
 
        // 标题
        MaterialTextView title = new MaterialTextView(requireContext());
        title.setText(R.string.multi_repo_manage_title);
        title.setTextColor(Color.parseColor("#202124"));
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(title, titleParams);
 
        // 新增按钮（ChoiceDialog 风格的按钮，但有 NEW 标识）
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(54));
        rowParams.topMargin = ResUtil.dp2px(12);
        rowParams.bottomMargin = ResUtil.dp2px(8);
 
        MaterialButton addBtn = new MaterialButton(requireContext());
        addBtn.setAllCaps(false);
        addBtn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        addBtn.setMinHeight(ResUtil.dp2px(44));
        addBtn.setInsetTop(0);
        addBtn.setInsetBottom(0);
        addBtn.setStrokeWidth(ResUtil.dp2px(1));
        addBtn.setCornerRadius(ResUtil.dp2px(6));
        addBtn.setTextColor(ColorStateList.valueOf(Color.parseColor("#174EA6")));
        addBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E8F0FE")));
        addBtn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#8AB4F8")));
        addBtn.setText(R.string.multi_repo_add);
        addBtn.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                addBtn.setTextColor(ColorStateList.valueOf(Color.WHITE));
                addBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1A73E8")));
                addBtn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#174EA6")));
            } else {
                addBtn.setTextColor(ColorStateList.valueOf(Color.parseColor("#174EA6")));
                addBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E8F0FE")));
                addBtn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#8AB4F8")));
            }
        });
        addBtn.setOnClickListener(v -> MultiRepoAddDialog.show(requireActivity(), repo -> refreshList(root)));
        root.addView(addBtn, rowParams);
 
        // 仓库列表
        LinearLayout listContainer = new LinearLayout(requireContext());
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setTag("repo_list");
 
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.addView(listContainer, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollParams.topMargin = ResUtil.dp2px(8);
        int maxHeight = ResUtil.dp2px(280);
        List<MultiRepo> repos = MultiRepoStore.get();
        scrollParams.height = Math.min(maxHeight, Math.max(ResUtil.dp2px(56), repos.size() * ResUtil.dp2px(54)));
        root.addView(scrollView, scrollParams);
 
        refreshList(root);
        return root;
    }
 
    private void refreshList(LinearLayout root) {
        LinearLayout list = root.findViewWithTag("repo_list");
        if (list == null) return;
        list.removeAllViews();
        List<MultiRepo> repos = MultiRepoStore.get();
        if (repos.isEmpty()) {
            MaterialTextView empty = new MaterialTextView(requireContext());
            empty.setText(R.string.multi_repo_empty);
            empty.setTextColor(Color.parseColor("#9AA0A6"));
            empty.setTextSize(14);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ep.topMargin = ResUtil.dp2px(24);
            ep.gravity = Gravity.CENTER;
            list.addView(empty, ep);
            return;
        }
        for (int i = 0; i < repos.size(); i++) {
            MultiRepo repo = repos.get(i);
            LinearLayout row = createRepoRow(repo, i);
            list.addView(row);
        }
        // 回调
        if (callback != null) callback.onChanged();
    }
 
    private LinearLayout createRepoRow(MultiRepo repo, int index) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(ResUtil.dp2px(44));
        row.setPadding(ResUtil.dp2px(6), 0, ResUtil.dp2px(6), 0);
        row.setBackgroundColor(Color.TRANSPARENT);
 
        // 使用 ChoiceDialog 风格的 MaterialButton 作为背景（让焦点样式和播放器内核一致）
        MaterialButton nameBtn = new MaterialButton(requireContext());
        nameBtn.setAllCaps(false);
        nameBtn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        nameBtn.setSingleLine(false);
        nameBtn.setMinHeight(ResUtil.dp2px(44));
        nameBtn.setInsetTop(0);
        nameBtn.setInsetBottom(0);
        nameBtn.setStrokeWidth(ResUtil.dp2px(1));
        nameBtn.setCornerRadius(ResUtil.dp2px(6));
        nameBtn.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        nameBtn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#DADCE0")));
        nameBtn.setTextColor(ColorStateList.valueOf(Color.parseColor("#202124")));
        nameBtn.setText(repo.getName());
        nameBtn.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                nameBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1A73E8")));
                nameBtn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#174EA6")));
                nameBtn.setTextColor(ColorStateList.valueOf(Color.WHITE));
            } else {
                nameBtn.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                nameBtn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#DADCE0")));
                nameBtn.setTextColor(ColorStateList.valueOf(Color.parseColor("#202124")));
            }
        });
        nameBtn.setOnClickListener(v -> {
            // 点击仓库行也没什么动作（只做维护）
        });
 
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.rightMargin = ResUtil.dp2px(8);
        nameParams.bottomMargin = ResUtil.dp2px(8);
        nameBtn.setLayoutParams(nameParams);
        row.addView(nameBtn);
 
        // 删除按钮
        MaterialButton deleteBtn = new MaterialButton(requireContext());
        deleteBtn.setAllCaps(false);
        deleteBtn.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_action_delete));
        deleteBtn.setIconSize(ResUtil.dp2px(20));
        deleteBtn.setText("");
        deleteBtn.setMinHeight(ResUtil.dp2px(44));
        deleteBtn.setMinWidth(ResUtil.dp2px(44));
        deleteBtn.setInsetTop(0);
        deleteBtn.setInsetBottom(0);
        deleteBtn.setInsetLeft(0);
        deleteBtn.setInsetRight(0);
        deleteBtn.setIconPadding(0);
        deleteBtn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        deleteBtn.setStrokeWidth(ResUtil.dp2px(1));
        deleteBtn.setCornerRadius(ResUtil.dp2px(6));
        deleteBtn.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        deleteBtn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#DADCE0")));
        deleteBtn.setIconTint(ColorStateList.valueOf(Color.parseColor("#D93025")));
        deleteBtn.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                deleteBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1A73E8")));
                deleteBtn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#174EA6")));
                deleteBtn.setIconTint(ColorStateList.valueOf(Color.WHITE));
            } else {
                deleteBtn.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                deleteBtn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#DADCE0")));
                deleteBtn.setIconTint(ColorStateList.valueOf(Color.parseColor("#D93025")));
            }
        });
        deleteBtn.setOnClickListener(v -> {
            // 确认删除
            ChoiceDialog.showConfirm(this, R.string.multi_repo_manage_title, getString(R.string.multi_repo_manage_title) + ": " + repo.getName(), R.string.dialog_positive, () -> {
                MultiRepoStore.removeAt(index);
                // 重新构造对话框来刷新
                dismiss();
                MultiRepoManageDialog.show(requireActivity(), callback);
            });
        });
        LinearLayout.LayoutParams delParams = new LinearLayout.LayoutParams(ResUtil.dp2px(44), ResUtil.dp2px(44));
        delParams.leftMargin = ResUtil.dp2px(8);
        delParams.bottomMargin = ResUtil.dp2px(8);
        delParams.gravity = Gravity.CENTER_VERTICAL;
        deleteBtn.setLayoutParams(delParams);
        row.addView(deleteBtn);
 
        return row;
    }
}
