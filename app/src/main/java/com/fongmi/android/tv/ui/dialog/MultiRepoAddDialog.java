package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.MultiRepo;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.MultiRepoStore;
import com.fongmi.android.tv.utils.QRCode;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class MultiRepoAddDialog extends DialogFragment {

    public interface Callback {
        void onAdded(MultiRepo repo);
    }

    private TextInputEditText nameEdit;
    private TextInputEditText urlEdit;
    private Callback callback;

    public static MultiRepoAddDialog show(@NonNull FragmentActivity activity, @Nullable Callback callback) {
        MultiRepoAddDialog dialog = new MultiRepoAddDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), MultiRepoAddDialog.class.getSimpleName());
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
        // 横屏TV 0.55f；手机竖屏0.85f
        if (land) {
            params.width = (int) (ResUtil.getScreenWidth(requireContext()) * (Util.isLeanback() ? 0.65f : 0.55f));
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

        if (Util.isLeanback()) {
            EventBus.getDefault().register(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.isLeanback() && EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.shape_shell_proxy_dialog);
        int horizontal = ResUtil.dp2px(24);
        int vertical = ResUtil.dp2px(20);
        root.setPadding(horizontal, vertical, horizontal, vertical);

        // 标题
        MaterialTextView title = new MaterialTextView(requireContext());
        title.setText(R.string.multi_repo_add_title);
        title.setTextColor(Color.parseColor("#202124"));
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (Util.isLeanback()) {
            buildTvContent(root);
        } else {
            buildMobileContent(root);
        }

        return root;
    }

    /**
     * TV端：左侧二维码 + 右侧输入框。支持手机扫码远程输入仓库名称和链接。
     */
    private void buildTvContent(LinearLayout root) {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = ResUtil.dp2px(12);
        content.setLayoutParams(cp);

        // 左侧：二维码
        int qrSize = ResUtil.dp2px(180);
        ImageView qrImage = new ImageView(requireContext());
        qrImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap qrBitmap = QRCode.getLightBitmap(Server.get().getAddress(4), 200, 0);
        if (qrBitmap != null) qrImage.setImageBitmap(qrBitmap);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        content.addView(qrImage, qrParams);

        // 右侧：提示信息 + 输入框 + 按钮
        LinearLayout rightPane = new LinearLayout(requireContext());
        rightPane.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rp.leftMargin = ResUtil.dp2px(12);
        rightPane.setLayoutParams(rp);

        MaterialTextView info = new MaterialTextView(requireContext());
        info.setText(ResUtil.getString(R.string.multi_repo_push_info, Server.get().getAddress()).replace("\uff0c", "\n"));
        info.setTextColor(Color.parseColor("#3C4043"));
        info.setTextSize(13);
        info.setLineSpacing(ResUtil.dp2px(4), 1f);
        info.setMaxLines(3);
        rightPane.addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        nameEdit = addInput(rightPane, R.string.multi_repo_name, R.string.multi_repo_name_hint);
        urlEdit = addInput(rightPane, R.string.multi_repo_url, R.string.multi_repo_url_hint);

        addButtonRow(rightPane);

        content.addView(rightPane);
        root.addView(content);
    }

    /**
     * 手机端：保持现状，纯输入框 + 按钮。
     */
    private void buildMobileContent(LinearLayout root) {
        nameEdit = addInput(root, R.string.multi_repo_name, R.string.multi_repo_name_hint);
        urlEdit = addInput(root, R.string.multi_repo_url, R.string.multi_repo_url_hint);
        addButtonRow(root);
    }

    private void addButtonRow(LinearLayout parent) {
        LinearLayout buttonRow = new LinearLayout(requireContext());
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = ResUtil.dp2px(18);

        MaterialButton cancelBtn = makeButton(getString(R.string.dialog_negative), false, v -> dismiss());
        MaterialButton okBtn = makeButton(getString(R.string.dialog_positive), true, v -> {
            String name = nameEdit.getText() == null ? "" : nameEdit.getText().toString().trim();
            String url = urlEdit.getText() == null ? "" : urlEdit.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                nameEdit.setError(getString(R.string.multi_repo_name_empty));
                return;
            }
            if (TextUtils.isEmpty(url)) {
                urlEdit.setError(getString(R.string.multi_repo_url_empty));
                return;
            }
            MultiRepo repo = new MultiRepo(name, url);
            MultiRepoStore.add(repo);
            if (callback != null) callback.onAdded(repo);
            dismiss();
        });
        buttonRow.addView(cancelBtn);
        LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        okParams.leftMargin = ResUtil.dp2px(12);
        buttonRow.addView(okBtn, okParams);
        parent.addView(buttonRow, rowParams);
    }

    private TextInputEditText addInput(LinearLayout parent, int labelRes, int hintRes) {
        MaterialTextView label = new MaterialTextView(requireContext());
        label.setText(labelRes);
        label.setTextColor(Color.parseColor("#5F6368"));
        label.setTextSize(14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ResUtil.dp2px(12);
        parent.addView(label, lp);

        TextInputEditText edit = new TextInputEditText(requireContext());
        edit.setHint(hintRes);
        edit.setTextColor(Color.parseColor("#202124"));
        edit.setHintTextColor(Color.parseColor("#9AA0A6"));
        edit.setTextSize(15);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.topMargin = ResUtil.dp2px(4);
        edit.setBackgroundResource(android.R.drawable.edit_text);
        edit.setPadding(ResUtil.dp2px(4), ResUtil.dp2px(10), ResUtil.dp2px(4), ResUtil.dp2px(10));
        edit.setSingleLine(true);
        parent.addView(edit, ep);
        return edit;
    }

    private MaterialButton makeButton(String text, boolean primary, View.OnClickListener listener) {
        MaterialButton btn = new MaterialButton(requireContext());
        btn.setAllCaps(false);
        btn.setText(text);
        btn.setGravity(Gravity.CENTER);
        btn.setMinHeight(ResUtil.dp2px(40));
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        btn.setCornerRadius(ResUtil.dp2px(6));
        btn.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = ResUtil.dp2px(6);
        btn.setLayoutParams(params);
        if (primary) {
            btn.setTextColor(Color.WHITE);
            btn.setBackgroundColor(Color.parseColor("#1A73E8"));
            btn.setStrokeWidth(0);
        } else {
            btn.setTextColor(Color.parseColor("#5F6368"));
            btn.setBackgroundColor(Color.TRANSPARENT);
            btn.setStrokeWidth(ResUtil.dp2px(1));
            btn.setStrokeColorResource(R.color.dialog_outlined_button_stroke);
        }
        return btn;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() != ServerEvent.Type.SETTING) return;
        if (nameEdit != null && !TextUtils.isEmpty(event.name())) {
            nameEdit.setText(event.name());
        }
        if (urlEdit != null && !TextUtils.isEmpty(event.text())) {
            urlEdit.setText(event.text());
            urlEdit.setSelection(urlEdit.getText().length());
        }
    }
}
