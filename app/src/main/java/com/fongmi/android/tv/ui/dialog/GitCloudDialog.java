package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.gitcloud.AccountInfo;
import com.fongmi.android.tv.gitcloud.CreateRepoRequest;
import com.fongmi.android.tv.gitcloud.GitAccount;
import com.fongmi.android.tv.gitcloud.GitCloudAccountStore;
import com.fongmi.android.tv.gitcloud.GitCloudPaths;
import com.fongmi.android.tv.gitcloud.GitFile;
import com.fongmi.android.tv.gitcloud.GitFileContent;
import com.fongmi.android.tv.gitcloud.GitProviderType;
import com.fongmi.android.tv.gitcloud.GitRepo;
import com.fongmi.android.tv.gitcloud.GitRepoStore;
import com.fongmi.android.tv.gitcloud.ProviderCapabilities;
import com.fongmi.android.tv.gitcloud.SaveOptions;
import com.fongmi.android.tv.gitcloud.drive.CommitResult;
import com.fongmi.android.tv.gitcloud.drive.FileChange;
import com.fongmi.android.tv.gitcloud.drive.GitDriveConfig;
import com.fongmi.android.tv.gitcloud.drive.JGitDriveEngine;
import com.fongmi.android.tv.gitcloud.provider.GitCloudProvider;
import com.fongmi.android.tv.gitcloud.provider.GitCloudProviders;
import com.fongmi.android.tv.gitcloud.secure.GitCloudTokenStore;
import com.fongmi.android.tv.ui.custom.SafeScrollEditText;
import com.fongmi.android.tv.ui.custom.SettingClipboardOverlay;
import com.fongmi.android.tv.utils.AppBackup;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class GitCloudDialog extends BaseAlertDialog {

    private static final String FOCUS_TREE_TOGGLE = "git_cloud_tree_toggle:";
    private static final String FOCUS_TREE_CHECK = "git_cloud_tree_check:";
    private static final String FOCUS_TREE_INFO = "git_cloud_tree_info:";
    private static final int REPO_MODE_MINE = 0;
    private static final int REPO_MODE_FAVORITE = 1;
    private static final int REPO_MODE_SEARCH = 2;

    private final JGitDriveEngine driveEngine = new JGitDriveEngine();
    private final ActivityResultLauncher<Intent> filePicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
        handleFileUri(result.getData().getData());
    });
    private final ActivityResultLauncher<Intent> folderPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
        handleTreeUri(result.getData().getData());
    });
    private final ActivityResultLauncher<Intent> downloadFolderPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
        handleDownloadFolderUri(result.getData().getData());
    });
    private final ActivityResultLauncher<Intent> downloadFilePicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
        handleDownloadFileUri(result.getData().getData());
    });
    private DialogBinding binding;
    private Runnable callback;
    private GitProviderType providerType = GitProviderType.GITHUB;
    private GitAccount account;
    private GitAccount anonymousAccount;
    private GitRepo repo;
    private String currentPath = "";
    private boolean busy;
    private boolean editingAccount;
    private String progressMessage = "";
    private int progressValue;
    private boolean progressIndeterminate;
    private int repoMode = REPO_MODE_MINE;
    private String repoSearchOwner = "";
    private String reposAccountId;
    private String pendingTreeFocusTag;
    private GitFile pendingDownloadFile;
    private File downloadDirSelected;
    private LinearLayoutCompat downloadDirList;
    private MaterialTextView downloadDirPath;
    private SettingClipboardOverlay clipboardOverlay;
    private final List<GitRepo> repos = new ArrayList<>();
    private final List<GitRepo> searchResults = new ArrayList<>();
    private final Map<String, List<GitFile>> fileTree = new HashMap<>();
    private final Map<String, GitFile> selectedFiles = new HashMap<>();
    private final Set<String> expandedPaths = new HashSet<>();
    private final Set<String> expandedDownloadDirs = new HashSet<>();

    public static void show(Fragment fragment, Runnable callback) {
        show(fragment.requireActivity(), callback);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        GitCloudDialog dialog = new GitCloudDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        buildView();
        return binding;
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(requireContext());
        params.width = (int) (ResUtil.getScreenWidth(requireContext()) * (land ? 0.72f : 0.94f));
        params.height = (int) (ResUtil.getScreenHeight(requireContext()) * 0.86f);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(18);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
        if (clipboardOverlay == null) clipboardOverlay = SettingClipboardOverlay.attach(this, binding.getRoot());
        getDialog().setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK || repo == null) return false;
            if (event.getAction() == KeyEvent.ACTION_UP && !busy) changeRepo();
            return true;
        });
    }

    @Override
    protected void initView() {
        account = GitCloudAccountStore.first();
        if (account != null) providerType = account.providerType;
        editingAccount = false;
        repoMode = account == null ? REPO_MODE_FAVORITE : REPO_MODE_MINE;
        populateAccountForm(account);
        render();
        if (account != null) refreshRepos();
        else renderRepoList();
    }

    @Override
    protected void initEvent() {
        binding.close.setOnClickListener(view -> dismiss());
        binding.github.setOnClickListener(view -> selectProvider(GitProviderType.GITHUB));
        binding.cnb.setOnClickListener(view -> selectProvider(GitProviderType.CNB));
        binding.accountSummary.setOnClickListener(view -> toggleAccountManager());
        binding.tokenLink.setOnClickListener(view -> open(tokenUrl()));
        binding.helpLink.setOnClickListener(view -> open(helpUrl()));
        binding.save.setOnClickListener(view -> saveAccount(true));
        binding.repoMine.setOnClickListener(view -> switchRepoMode(REPO_MODE_MINE));
        binding.repoFavorite.setOnClickListener(view -> switchRepoMode(REPO_MODE_FAVORITE));
        binding.createRepo.setOnClickListener(view -> {
            if (requireAccount("创建仓库需要先添加账号")) showCreateRepoDialog();
        });
        binding.refresh.setOnClickListener(view -> refreshRepoMode());
        binding.searchRemote.setOnClickListener(view -> handleRepoSearch());
        binding.removeAccount.setOnClickListener(view -> removeAccount());
        binding.repoBack.setOnClickListener(view -> changeRepo());
        binding.changeRepo.setOnClickListener(view -> changeRepo());
        binding.refreshTree.setOnClickListener(view -> reloadTree());
        binding.uploadText.setOnClickListener(view -> showUploadText());
        binding.uploadFile.setOnClickListener(view -> showUploadChooser());
        binding.editFile.setOnClickListener(view -> editSelectedFile());
        binding.download.setOnClickListener(view -> downloadSelected());
        binding.deleteFiles.setOnClickListener(view -> deleteSelected());
        binding.backup.setOnClickListener(view -> backup());
        binding.restore.setOnClickListener(view -> confirmRestore());
        binding.clearCache.setOnClickListener(view -> clearCache());
        binding.repoSearch.edit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderRepoList();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    @Override
    public void onDestroyView() {
        if (clipboardOverlay != null) clipboardOverlay.detach();
        clipboardOverlay = null;
        if (callback != null) callback.run();
        super.onDestroyView();
        binding = null;
    }

    private View buildView() {
        LinearLayoutCompat root = new LinearLayoutCompat(requireContext());
        root.setOrientation(LinearLayoutCompat.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.setBackground(round(Color.WHITE, 12, Color.TRANSPARENT));

        LinearLayoutCompat header = row();
        MaterialTextView title = text("Git 云盘", 20, Color.BLACK, true);
        header.addView(title, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        binding = new DialogBinding(root);
        binding.close = iconButton("×");
        header.addView(binding.close, new LinearLayoutCompat.LayoutParams(dp(42), dp(42)));
        root.addView(header);

        binding.status = text("", 13, Color.parseColor("#5F6368"), false);
        binding.status.setPadding(0, dp(4), 0, dp(2));
        root.addView(binding.status);
        binding.progress = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        binding.progress.setMax(100);
        binding.progress.setVisibility(View.GONE);
        root.addView(binding.progress, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));
        binding.progressText = text("", 11, Color.parseColor("#5F6368"), false);
        binding.progressText.setPadding(0, dp(3), 0, dp(6));
        binding.progressText.setVisibility(View.GONE);
        root.addView(binding.progressText);

        binding.scroll = new NestedScrollView(requireContext());
        binding.scroll.setFillViewport(false);
        LinearLayoutCompat content = new LinearLayoutCompat(requireContext());
        content.setOrientation(LinearLayoutCompat.VERTICAL);
        binding.scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(binding.scroll, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayoutCompat provider = row();
        binding.github = platformButton(R.drawable.ic_repo_github);
        binding.cnb = platformButton(R.drawable.ic_repo_cnb);
        provider.addView(binding.github, new LinearLayoutCompat.LayoutParams(dp(38), dp(38)));
        LinearLayoutCompat.LayoutParams cnbParams = new LinearLayoutCompat.LayoutParams(dp(38), dp(38));
        cnbParams.leftMargin = dp(6);
        provider.addView(binding.cnb, cnbParams);
        binding.accountSummary = compact("");
        LinearLayoutCompat.LayoutParams summaryParams = new LinearLayoutCompat.LayoutParams(0, dp(36), 1);
        summaryParams.leftMargin = dp(8);
        provider.addView(binding.accountSummary, summaryParams);
        content.addView(provider);

        binding.accountCard = card();
        LinearLayoutCompat accountTop = row();
        binding.accountName = text("", 15, Color.BLACK, true);
        accountTop.addView(binding.accountName, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        binding.accountBadge = pill("");
        accountTop.addView(binding.accountBadge);
        binding.accountCard.addView(accountTop);
        binding.accountMeta = detail("");
        accountTop.addView(binding.accountMeta, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        binding.removeAccount = outline("退出");
        LinearLayoutCompat.LayoutParams removeParams = new LinearLayoutCompat.LayoutParams(dp(58), dp(30));
        removeParams.leftMargin = dp(6);
        accountTop.addView(binding.removeAccount, removeParams);
        content.addView(binding.accountCard);

        binding.loginForm = list();
        binding.alias = input("账号备注", false);
        binding.baseUrl = input("服务地址", false);
        binding.token = input("Token", true);
        binding.loginForm.addView(binding.alias.layout);
        binding.loginForm.addView(binding.baseUrl.layout);
        binding.loginForm.addView(binding.token.layout);

        LinearLayoutCompat links = row();
        binding.tokenLink = outline("获取 Token");
        binding.helpLink = outline("权限说明");
        links.addView(binding.tokenLink, new LinearLayoutCompat.LayoutParams(0, dp(34), 1));
        LinearLayoutCompat.LayoutParams helpParams = new LinearLayoutCompat.LayoutParams(0, dp(34), 1);
        helpParams.leftMargin = dp(6);
        links.addView(binding.helpLink, helpParams);
        binding.loginForm.addView(links);

        LinearLayoutCompat accountActions = row();
        binding.save = primary("保存并校验");
        accountActions.addView(binding.save, new LinearLayoutCompat.LayoutParams(0, dp(36), 1));
        binding.loginForm.addView(accountActions);
        content.addView(binding.loginForm);

        binding.repoPanel = list();
        LinearLayoutCompat repoHeader = row();
        repoHeader.addView(section("选择仓库"), new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        binding.repoMine = segment("我的");
        binding.repoFavorite = segment("收藏");
        binding.createRepo = tonal("创建");
        repoHeader.addView(binding.repoMine, new LinearLayoutCompat.LayoutParams(dp(58), dp(34)));
        LinearLayoutCompat.LayoutParams favoriteParams = new LinearLayoutCompat.LayoutParams(dp(58), dp(34));
        favoriteParams.leftMargin = dp(6);
        repoHeader.addView(binding.repoFavorite, favoriteParams);
        LinearLayoutCompat.LayoutParams createParams = new LinearLayoutCompat.LayoutParams(dp(58), dp(34));
        createParams.leftMargin = dp(6);
        repoHeader.addView(binding.createRepo, createParams);
        binding.repoPanel.addView(repoHeader);
        LinearLayoutCompat searchRow = row();
        binding.repoSearch = input("搜索仓库或 Git 地址", false);
        searchRow.addView(binding.repoSearch.layout, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        binding.repoPanel.addView(searchRow);
        LinearLayoutCompat repoTools = row();
        binding.refresh = tonal("刷新");
        repoTools.addView(binding.refresh, new LinearLayoutCompat.LayoutParams(0, dp(34), 1));
        binding.searchRemote = outline("全网搜索");
        LinearLayoutCompat.LayoutParams searchButtonParams = new LinearLayoutCompat.LayoutParams(0, dp(34), 1);
        searchButtonParams.leftMargin = dp(6);
        repoTools.addView(binding.searchRemote, searchButtonParams);
        binding.repoPanel.addView(repoTools);
        binding.repoList = list();
        binding.repoPanel.addView(binding.repoList);
        content.addView(binding.repoPanel);

        binding.filePanel = list();
        LinearLayoutCompat treeHeader = row();
        treeHeader.addView(section("目录树"), new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        binding.repoBack = headerIconButton(R.drawable.ic_git_cloud_back, "返回仓库列表");
        treeHeader.addView(binding.repoBack, new LinearLayoutCompat.LayoutParams(dp(34), dp(34)));
        binding.filePanel.addView(treeHeader);
        LinearLayoutCompat selectedRepo = card();
        LinearLayoutCompat selectedTop = row();
        binding.repoTitle = text("", 15, Color.BLACK, true);
        selectedTop.addView(binding.repoTitle, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        binding.repoBadge = pill("");
        selectedTop.addView(binding.repoBadge);
        selectedRepo.addView(selectedTop);
        binding.pathText = detail("");
        selectedRepo.addView(binding.pathText);
        LinearLayoutCompat selectedActions = row();
        binding.changeRepo = compact("仓库");
        binding.refreshTree = outline("刷新");
        binding.clearCache = outline("清缓存");
        selectedActions.addView(binding.changeRepo, new LinearLayoutCompat.LayoutParams(0, dp(32), 1));
        LinearLayoutCompat.LayoutParams refreshTreeParams = new LinearLayoutCompat.LayoutParams(0, dp(32), 1);
        refreshTreeParams.leftMargin = dp(6);
        selectedActions.addView(binding.refreshTree, refreshTreeParams);
        LinearLayoutCompat.LayoutParams cacheParams = new LinearLayoutCompat.LayoutParams(0, dp(32), 1);
        cacheParams.leftMargin = dp(6);
        selectedActions.addView(binding.clearCache, cacheParams);
        selectedRepo.addView(selectedActions);
        binding.filePanel.addView(selectedRepo);

        LinearLayoutCompat fileActions = row();
        binding.uploadText = toolButton(R.drawable.ic_git_cloud_new, "新建");
        binding.uploadFile = toolButton(R.drawable.ic_git_cloud_upload, "上传");
        binding.editFile = toolButton(R.drawable.ic_git_cloud_edit, "编辑");
        binding.download = toolButton(R.drawable.ic_git_cloud_download, "下载");
        binding.deleteFiles = toolButton(R.drawable.ic_action_delete, "删除");
        binding.backup = tonal("备份");
        binding.restore = tonal("恢复");
        binding.backup.setMinWidth(0);
        binding.restore.setMinWidth(0);
        binding.backup.setPadding(dp(2), 0, dp(2), 0);
        binding.restore.setPadding(dp(2), 0, dp(2), 0);
        fileActions.addView(binding.uploadText, new LinearLayoutCompat.LayoutParams(0, dp(32), 1));
        LinearLayoutCompat.LayoutParams uploadFileParams = new LinearLayoutCompat.LayoutParams(0, dp(32), 1);
        uploadFileParams.leftMargin = dp(5);
        fileActions.addView(binding.uploadFile, uploadFileParams);
        LinearLayoutCompat.LayoutParams editParams = new LinearLayoutCompat.LayoutParams(0, dp(32), 1);
        editParams.leftMargin = dp(5);
        fileActions.addView(binding.editFile, editParams);
        LinearLayoutCompat.LayoutParams downloadParams = new LinearLayoutCompat.LayoutParams(0, dp(32), 1);
        downloadParams.leftMargin = dp(5);
        fileActions.addView(binding.download, downloadParams);
        LinearLayoutCompat.LayoutParams deleteParams = new LinearLayoutCompat.LayoutParams(0, dp(32), 1);
        deleteParams.leftMargin = dp(5);
        fileActions.addView(binding.deleteFiles, deleteParams);
        LinearLayoutCompat.LayoutParams backupParams = new LinearLayoutCompat.LayoutParams(0, dp(32), 1);
        backupParams.leftMargin = dp(5);
        fileActions.addView(binding.backup, backupParams);
        LinearLayoutCompat.LayoutParams restoreParams = new LinearLayoutCompat.LayoutParams(0, dp(32), 1);
        restoreParams.leftMargin = dp(5);
        fileActions.addView(binding.restore, restoreParams);
        binding.filePanel.addView(fileActions);
        binding.fileList = list();
        binding.filePanel.addView(binding.fileList);
        content.addView(binding.filePanel);
        return root;
    }

    private void render() {
        if (binding == null) return;
        boolean connected = account != null && account.providerType == providerType;
        boolean mainVisible = !editingAccount;
        binding.github.setChecked(providerType == GitProviderType.GITHUB);
        binding.cnb.setChecked(providerType == GitProviderType.CNB);
        binding.baseUrl.layout.setVisibility(View.GONE);
        binding.accountSummary.setText(accountSummary());
        binding.accountCard.setVisibility(connected && editingAccount ? View.VISIBLE : View.GONE);
        binding.loginForm.setVisibility(editingAccount ? View.VISIBLE : View.GONE);
        binding.repoPanel.setVisibility(mainVisible && repo == null ? View.VISIBLE : View.GONE);
        binding.filePanel.setVisibility(mainVisible && repo != null ? View.VISIBLE : View.GONE);
        binding.repoMine.setChecked(repoMode == REPO_MODE_MINE);
        binding.repoFavorite.setChecked(repoMode == REPO_MODE_FAVORITE);
        binding.refresh.setText(repoMode == REPO_MODE_FAVORITE ? "刷新显示" : "刷新");
        binding.refresh.setEnabled(mainVisible && !busy);
        binding.searchRemote.setEnabled(mainVisible && !busy);
        binding.repoMine.setEnabled(mainVisible && !busy);
        binding.repoFavorite.setEnabled(mainVisible && !busy);
        binding.createRepo.setEnabled(mainVisible && !busy);
        binding.uploadText.setEnabled(repo != null && !busy);
        binding.uploadFile.setEnabled(repo != null && !busy);
        binding.editFile.setEnabled(repo != null && !busy && selectedEditableFile() != null);
        binding.download.setEnabled(repo != null && !busy && hasSelectedFiles());
        binding.deleteFiles.setEnabled(repo != null && !busy && !selectedFiles.isEmpty());
        boolean privateRepo = repo != null && repo.privateRepo;
        binding.backup.setVisibility(privateRepo ? View.VISIBLE : View.GONE);
        binding.restore.setVisibility(privateRepo ? View.VISIBLE : View.GONE);
        binding.backup.setEnabled(privateRepo && !busy);
        binding.restore.setEnabled(privateRepo && !busy);
        binding.clearCache.setEnabled(repo != null && !busy);
        binding.repoBack.setEnabled(!busy);
        binding.changeRepo.setEnabled(!busy);
        binding.refreshTree.setEnabled(repo != null && !busy);
        if (connected) {
            binding.accountName.setText(account.displayName());
            binding.accountBadge.setText(label(providerType));
            binding.accountMeta.setText(meta(account));
        }
        if (repo != null) {
            binding.repoTitle.setText(repo.displayName());
            binding.repoBadge.setText(repo.privateRepo ? "私有" : "公开");
            binding.pathText.setText(pathLabel());
        }
        renderProgress();
        setStatus(statusText());
    }

    private void selectProvider(GitProviderType type) {
        if (providerType == type && account != null && account.providerType == type) {
            toggleAccountManager();
        } else {
            switchProvider(type);
        }
    }

    private void switchProvider(GitProviderType type) {
        providerType = type;
        account = GitCloudAccountStore.first(type);
        anonymousAccount = null;
        repo = null;
        repoMode = account == null ? REPO_MODE_FAVORITE : REPO_MODE_MINE;
        repoSearchOwner = "";
        currentPath = "";
        repos.clear();
        searchResults.clear();
        fileTree.clear();
        selectedFiles.clear();
        expandedPaths.clear();
        reposAccountId = null;
        editingAccount = false;
        populateAccountForm(account);
        render();
        if (account != null) refreshRepos();
        else renderRepoList();
    }

    private void toggleAccountManager() {
        editingAccount = account == null || !editingAccount;
        populateAccountForm(account);
        render();
    }

    private void saveAccount(boolean loadRepos) {
        String token = value(binding.token.edit);
        if (TextUtils.isEmpty(token) && account != null) {
            try {
                token = GitCloudTokenStore.get(account.tokenKey);
            } catch (Exception e) {
                token = "";
            }
        }
        if (TextUtils.isEmpty(token)) {
            Notify.show("Token 为空");
            return;
        }
        String authToken = token;
        GitAccount target = account != null && account.providerType == providerType ? account : GitAccount.create(providerType, value(binding.baseUrl.edit), value(binding.alias.edit));
        target.baseUrl = providerType == GitProviderType.CNB ? "https://cnb.cool" : value(binding.baseUrl.edit);
        target.remark = value(binding.alias.edit);
        run("校验账号中", () -> {
            GitCloudProvider provider = provider();
            AccountInfo info = provider.validateToken(target, authToken);
            target.username = info.username;
            target.lastValidatedAt = System.currentTimeMillis();
            GitCloudTokenStore.put(target.tokenKey, authToken);
            GitCloudAccountStore.save(target);
            account = target;
            App.post(() -> {
                editingAccount = false;
                repo = null;
                repoMode = REPO_MODE_MINE;
                repoSearchOwner = "";
                currentPath = "";
                repos.clear();
                searchResults.clear();
                fileTree.clear();
                selectedFiles.clear();
                expandedPaths.clear();
                reposAccountId = null;
                render();
                if (loadRepos) refreshRepos();
            });
        });
    }

    private void refreshRepos() {
        if (account == null) {
            showAccountForm("读取我的仓库需要先添加账号");
            return;
        }
        run("读取仓库中", () -> {
            List<GitRepo> items = provider().listRepos(account, token());
            App.post(() -> showRepos(items));
        });
    }

    private void refreshRepoMode() {
        if (repoMode == REPO_MODE_SEARCH && !TextUtils.isEmpty(repoSearchOwner)) loadUserRepos(repoSearchOwner);
        else if (repoMode == REPO_MODE_SEARCH) renderRepoList();
        else if (repoMode == REPO_MODE_MINE) refreshRepos();
        else renderRepoList();
    }

    private void switchRepoMode(int mode) {
        if (mode == REPO_MODE_MINE && !requireAccount("查看我的仓库需要先添加账号")) return;
        repoMode = mode;
        if (mode != REPO_MODE_SEARCH) repoSearchOwner = "";
        renderRepoList();
    }

    private void showRepos(List<GitRepo> items) {
        repos.clear();
        selectedFiles.clear();
        repos.addAll(items);
        reposAccountId = account == null ? null : account.id;
        renderRepoList();
    }

    private void renderRepoList() {
        if (binding == null || binding.repoList == null) return;
        binding.repoList.removeAllViews();
        String keyword = binding.repoSearch == null ? "" : value(binding.repoSearch.edit).toLowerCase();
        int count = 0;
        if (repoMode == REPO_MODE_FAVORITE) count += renderRepoSection("收藏仓库", GitRepoStore.list(providerType), keyword);
        else if (repoMode == REPO_MODE_SEARCH) count += renderRepoSection(TextUtils.isEmpty(repoSearchOwner) ? "全网结果" : repoSearchOwner + " 的仓库", searchResults, keyword);
        else count += renderRepoSection("我的仓库", repos, keyword);
        if (count == 0 && currentRepoSource().isEmpty()) {
            binding.repoList.addView(empty("暂无仓库"));
            render();
            return;
        }
        if (count == 0) {
            binding.repoList.addView(empty("无匹配仓库"));
            render();
            return;
        }
        render();
    }

    private List<GitRepo> currentRepoSource() {
        if (repoMode == REPO_MODE_FAVORITE) return GitRepoStore.list(providerType);
        if (repoMode == REPO_MODE_SEARCH) return searchResults;
        return repos;
    }

    private int renderRepoSection(String title, List<GitRepo> source, String keyword) {
        List<GitRepo> visible = new ArrayList<>();
        for (GitRepo item : source) {
            if (!TextUtils.isEmpty(keyword) && !item.displayName().toLowerCase().contains(keyword)) continue;
            if (containsRepo(visible, item)) continue;
            visible.add(item);
        }
        if (visible.isEmpty()) return 0;
        binding.repoList.addView(section(title));
        for (GitRepo item : visible) binding.repoList.addView(repoRow(item));
        return visible.size();
    }

    private View repoRow(GitRepo item) {
        LinearLayoutCompat root = card();
        root.setFocusable(false);
        root.setFocusableInTouchMode(false);
        root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        root.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.selector_git_cloud_card));
        LinearLayoutCompat top = row();
        top.addView(repoTitle(item), new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (GitRepoStore.contains(item)) top.addView(pill("已收藏"));
        top.addView(pill(item.privateRepo ? "私有" : "公开"));
        root.addView(top);
        root.addView(detail((TextUtils.isEmpty(item.defaultBranch) ? "main" : item.defaultBranch) + " · " + size(item.sizeKb * 1024) + repoMetaSuffix(item)));
        LinearLayoutCompat actions = row();
        MaterialButton open = compact("打开");
        open.setOnClickListener(view -> openRepo(item));
        actions.addView(open, new LinearLayoutCompat.LayoutParams(0, dp(34), 1));
        MaterialButton favorite = outline(GitRepoStore.contains(item) ? "取消收藏" : "收藏");
        favorite.setOnClickListener(view -> toggleFavorite(item));
        LinearLayoutCompat.LayoutParams favoriteParams = new LinearLayoutCompat.LayoutParams(0, dp(34), 1);
        favoriteParams.leftMargin = dp(6);
        actions.addView(favorite, favoriteParams);
        if (providerType == GitProviderType.GITHUB && hasAccountToken() && !isOwnRepo(item)) {
            MaterialButton fork = outline("Fork");
            fork.setOnClickListener(view -> {
                if (requireAccountToken("Fork 仓库需要先添加账号")) forkRepo(item);
            });
            LinearLayoutCompat.LayoutParams forkParams = new LinearLayoutCompat.LayoutParams(dp(66), dp(34));
            forkParams.leftMargin = dp(6);
            actions.addView(fork, forkParams);
        }
        if (account != null && containsRepo(repos, item)) {
            MaterialButton delete = outline("删");
            delete.setOnClickListener(view -> confirmDeleteRepo(item));
            LinearLayoutCompat.LayoutParams deleteParams = new LinearLayoutCompat.LayoutParams(dp(52), dp(34));
            deleteParams.leftMargin = dp(6);
            actions.addView(delete, deleteParams);
        }
        root.addView(actions);
        root.setOnClickListener(view -> openRepo(item));
        return root;
    }

    private View repoTitle(GitRepo item) {
        if (!shouldLinkOwner(item)) return text(item.displayName(), 15, Color.BLACK, true);
        String owner = repoOwner(item);
        String displayName = item.displayName();
        String suffix = displayName.startsWith(owner) ? displayName.substring(owner.length()) : "/" + item.name;
        LinearLayoutCompat title = row();
        title.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        MaterialButton ownerLink = ownerLink(owner);
        ownerLink.setOnClickListener(view -> loadUserRepos(owner));
        title.addView(ownerLink, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)));
        MaterialTextView repoName = text(suffix, 15, Color.BLACK, true);
        repoName.setSingleLine(true);
        repoName.setEllipsize(TextUtils.TruncateAt.END);
        title.addView(repoName, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return title;
    }

    private boolean shouldLinkOwner(GitRepo item) {
        boolean linkableMode = repoMode == REPO_MODE_SEARCH || repoMode == REPO_MODE_FAVORITE;
        return linkableMode && providerType == GitProviderType.GITHUB && !TextUtils.isEmpty(repoOwner(item)) && !isOwnOwner(repoOwner(item));
    }

    private String repoOwner(GitRepo item) {
        if (item == null) return "";
        if (!TextUtils.isEmpty(item.owner)) return item.owner;
        if (TextUtils.isEmpty(item.fullName)) return "";
        int split = item.fullName.indexOf('/');
        return split > 0 ? item.fullName.substring(0, split) : "";
    }

    private String repoMetaSuffix(GitRepo item) {
        return TextUtils.isEmpty(item.webUrl) ? "" : " · " + item.webUrl;
    }

    private void toggleFavorite(GitRepo item) {
        if (GitRepoStore.contains(item)) {
            GitRepoStore.remove(item);
            Notify.show("已取消收藏");
        } else {
            GitRepoStore.add(item);
            Notify.show("已收藏仓库");
        }
        renderRepoList();
    }

    private void handleRepoSearch() {
        String keyword = value(binding.repoSearch.edit);
        if (TextUtils.isEmpty(keyword)) {
            Notify.show("请输入搜索关键词或 Git 地址");
            return;
        }
        String fullName = parseRepoFullName(keyword);
        if (isRepoAddress(keyword, fullName)) {
            if (providerType == GitProviderType.CNB && !requireAccountToken("CNB 浏览仓库需要先添加账号")) return;
            openRepoByFullName(fullName);
            return;
        }
        if (providerType == GitProviderType.CNB && !requireAccountToken("CNB 搜索需要先添加账号")) return;
        repoMode = REPO_MODE_SEARCH;
        repoSearchOwner = "";
        run("搜索仓库中", () -> {
            List<GitRepo> items = provider().searchRepos(activeAccount(), token(), keyword);
            App.post(() -> {
                searchResults.clear();
                searchResults.addAll(items);
                renderRepoList();
            });
        });
    }

    private void openRepoByFullName(String fullName) {
        if (TextUtils.isEmpty(fullName)) {
            Notify.show("仓库地址格式不正确");
            return;
        }
        repoMode = REPO_MODE_SEARCH;
        repoSearchOwner = "";
        run("读取仓库中", () -> {
            GitRepo found = provider().getRepo(activeAccount(), token(), fullName);
            GitRepo result = found;
            App.post(() -> {
                openRepo(result);
            });
        });
    }

    private void loadUserRepos(String owner) {
        if (TextUtils.isEmpty(owner)) return;
        repoMode = REPO_MODE_SEARCH;
        repoSearchOwner = owner;
        run("读取用户仓库中", () -> {
            List<GitRepo> items = provider().listUserRepos(activeAccount(), token(), owner);
            App.post(() -> {
                searchResults.clear();
                searchResults.addAll(items);
                binding.repoSearch.edit.setText(owner);
                renderRepoList();
            });
        });
    }

    private void forkRepo(GitRepo item) {
        run("Fork 仓库中", () -> {
            GitRepo forked = provider().forkRepo(account, token(), item);
            GitRepoStore.add(forked);
            App.post(() -> {
                if (!containsRepo(repos, forked)) repos.add(0, forked);
                Notify.show("Fork 已提交并收藏");
                renderRepoList();
                openRepo(forked);
            });
        });
    }

    private boolean containsRepo(List<GitRepo> source, GitRepo repo) {
        if (repo == null || TextUtils.isEmpty(repo.fullName)) return false;
        for (GitRepo item : source) if (item.providerType == repo.providerType && TextUtils.equals(item.fullName, repo.fullName)) return true;
        return false;
    }

    private boolean isOwnRepo(GitRepo item) {
        return containsRepo(repos, item) || isOwnOwner(item == null ? "" : item.owner);
    }

    private boolean isOwnOwner(String owner) {
        return account != null && !TextUtils.isEmpty(owner) && !TextUtils.isEmpty(account.username) && owner.equalsIgnoreCase(account.username);
    }

    private boolean isRepoAddress(String value, String fullName) {
        if (TextUtils.isEmpty(fullName) || !fullName.contains("/")) return false;
        String text = value == null ? "" : value.trim();
        if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("git@")) return true;
        return !text.contains(" ") && text.contains("/");
    }

    private String parseRepoFullName(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String text = value.trim();
        if (text.startsWith("git@")) {
            int colon = text.indexOf(':');
            if (colon >= 0) text = text.substring(colon + 1);
        } else if (text.startsWith("http://") || text.startsWith("https://")) {
            Uri uri = Uri.parse(text);
            text = uri.getPath();
        }
        text = text.replaceAll("^/+", "").replaceAll("/+$", "");
        int query = text.indexOf('?');
        if (query >= 0) text = text.substring(0, query);
        int hash = text.indexOf('#');
        if (hash >= 0) text = text.substring(0, hash);
        if (text.endsWith(".git")) text = text.substring(0, text.length() - 4);
        String[] raw = text.split("/");
        List<String> parts = new ArrayList<>();
        for (String part : raw) {
            if (TextUtils.isEmpty(part) || "-".equals(part) || "tree".equals(part) || "blob".equals(part)) break;
            parts.add(part);
            if (providerType == GitProviderType.GITHUB && parts.size() == 2) break;
        }
        return TextUtils.join("/", parts);
    }

    private void confirmDeleteRepo(GitRepo item) {
        TextInput confirm = input("输入仓库名确认", false);
        confirm.layout.setHelperText("请输入 " + item.name + " 或 " + item.fullName);
        LinearLayoutCompat content = new LinearLayoutCompat(requireContext());
        content.setOrientation(LinearLayoutCompat.VERTICAL);
        content.addView(detail("该操作会删除远端仓库，无法撤销。"));
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        content.addView(confirm.layout, params);
        final android.app.Dialog[] holder = new android.app.Dialog[1];
        holder[0] = LightDialog.create(requireContext(), "删除仓库", content, "删除", view -> {
            String value = value(confirm.edit);
            if (!TextUtils.equals(value, item.name) && !TextUtils.equals(value, item.fullName)) {
                confirm.layout.setError("仓库名不匹配");
                return;
            }
            confirm.layout.setError(null);
            holder[0].dismiss();
            deleteRepo(item);
        }, getString(R.string.dialog_negative), null);
        holder[0].show();
    }

    private void deleteRepo(GitRepo item) {
        run("删除仓库中", () -> {
            provider().deleteRepo(account, token(), item);
            App.post(() -> {
                repos.removeIf(value -> TextUtils.equals(value.fullName, item.fullName));
                if (repo != null && TextUtils.equals(repo.fullName, item.fullName)) {
                    repo = null;
                    currentPath = "";
                    fileTree.clear();
                    selectedFiles.clear();
                    expandedPaths.clear();
                }
                renderRepoList();
            });
        });
    }

    private void openRepo(GitRepo item) {
        if (providerType == GitProviderType.CNB && !requireAccountToken("CNB 浏览仓库需要先添加账号")) return;
        repo = item;
        currentPath = "";
        fileTree.clear();
        selectedFiles.clear();
        expandedPaths.clear();
        expandedPaths.add("");
        render();
        browse(item, "");
    }

    private void browse(GitRepo target, String path) {
        repo = target;
        currentPath = path == null ? "" : path;
        expandedPaths.add(currentPath);
        render();
        run("读取文件中", () -> {
            List<GitFile> files = provider().listFiles(activeAccount(), token(), target, target.defaultBranch, currentPath);
            App.post(() -> showFiles(currentPath, files));
        });
    }

    private void showFiles(String path, List<GitFile> files) {
        String key = path == null ? "" : path;
        fileTree.put(key, visibleFiles(key, files));
        if (isCoveredBySelectedDirectory(key)) selectLoadedDescendants(key);
        renderFileTree();
    }

    private void renderFileTree() {
        if (!isAdded() || binding == null || binding.fileList == null) return;
        binding.fileList.removeAllViews();
        if (repo == null) {
            restoreTreeFocus();
            return;
        }
        binding.fileList.addView(treeRootRow());
        if (!expandedPaths.contains("")) {
            restoreTreeFocus();
            return;
        }
        List<GitFile> files = fileTree.get("");
        if (files == null) {
            binding.fileList.addView(empty("目录加载中"));
            restoreTreeFocus();
            return;
        }
        if (files.isEmpty()) {
            binding.fileList.addView(empty("目录为空"));
            restoreTreeFocus();
            return;
        }
        addTreeRows(files, 1);
        restoreTreeFocus();
    }

    private View treeRootRow() {
        LinearLayoutCompat line = treeLine(0, TextUtils.isEmpty(currentPath));
        ImageButton toggle = treeToggle(expandedPaths.contains(""));
        toggle.setTag(treeFocusTag(FOCUS_TREE_TOGGLE, ""));
        toggle.setOnClickListener(view -> toggleTree("", treeFocusTag(FOCUS_TREE_TOGGLE, "")));
        line.addView(toggle, new LinearLayoutCompat.LayoutParams(dp(30), dp(30)));
        ImageView icon = treeIcon(R.drawable.ic_folder, Color.parseColor("#F9AB00"));
        line.addView(icon, new LinearLayoutCompat.LayoutParams(dp(22), dp(22)));
        MaterialTextView name = text(repo == null ? "全部文件" : repo.name, 15, Color.BLACK, true);
        name.setPadding(dp(8), 0, 0, 0);
        makeFocusable(name);
        name.setTag(treeFocusTag(FOCUS_TREE_INFO, ""));
        name.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.selector_git_cloud_inline_focus));
        name.setOnClickListener(view -> toggleTree("", treeFocusTag(FOCUS_TREE_INFO, "")));
        line.addView(name, new LinearLayoutCompat.LayoutParams(0, dp(32), 1));
        line.setOnClickListener(view -> toggleTree("", treeFocusTag(FOCUS_TREE_INFO, "")));
        return line;
    }

    private void addTreeRows(List<GitFile> files, int depth) {
        for (GitFile file : files) {
            binding.fileList.addView(fileTreeRow(file, depth));
            if (!file.directory || !expandedPaths.contains(file.path)) continue;
            List<GitFile> children = fileTree.get(file.path);
            if (children == null) {
                binding.fileList.addView(treeMessage("加载中", depth + 1));
            } else if (children.isEmpty()) {
                binding.fileList.addView(treeMessage("目录为空", depth + 1));
            } else {
                addTreeRows(children, depth + 1);
            }
        }
    }

    private View fileTreeRow(GitFile file, int depth) {
        LinearLayoutCompat line = treeLine(depth, TextUtils.equals(currentPath, file.path) || selectedFiles.containsKey(file.path));
        if (file.directory) {
            ImageButton toggle = treeToggle(expandedPaths.contains(file.path));
            toggle.setTag(treeFocusTag(FOCUS_TREE_TOGGLE, file.path));
            toggle.setOnClickListener(view -> toggleTree(file.path, treeFocusTag(FOCUS_TREE_TOGGLE, file.path)));
            line.addView(toggle, new LinearLayoutCompat.LayoutParams(dp(28), dp(30)));
        }
        MaterialCheckBox check = checkbox(file);
        check.setTag(treeFocusTag(FOCUS_TREE_CHECK, file.path));
        line.addView(check, new LinearLayoutCompat.LayoutParams(dp(30), dp(30)));
        ImageView icon = treeIcon(file.directory ? R.drawable.ic_folder : R.drawable.ic_file, file.directory ? Color.parseColor("#F9AB00") : Color.parseColor("#5F6368"));
        line.addView(icon, new LinearLayoutCompat.LayoutParams(dp(22), dp(22)));
        LinearLayoutCompat info = new LinearLayoutCompat(requireContext());
        info.setOrientation(LinearLayoutCompat.VERTICAL);
        MaterialTextView name = text(file.name, 14, Color.BLACK, true);
        MaterialTextView meta = text(file.directory ? file.path : file.path + " · " + size(file.size), 11, Color.parseColor("#5F6368"), false);
        info.addView(name);
        info.addView(meta);
        info.setPadding(dp(8), 0, 0, 0);
        info.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.selector_git_cloud_inline_focus));
        info.setTag(treeFocusTag(FOCUS_TREE_INFO, file.path));
        makeFocusable(info);
        info.setOnClickListener(view -> {
            if (file.directory) toggleTree(file.path, treeFocusTag(FOCUS_TREE_INFO, file.path));
            else copy(file.rawUrl);
        });
        line.addView(info, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        line.setOnClickListener(view -> {
            if (file.directory) toggleTree(file.path, treeFocusTag(FOCUS_TREE_INFO, file.path));
            else copy(file.rawUrl);
        });
        return line;
    }

    private void editSelectedFile() {
        if (!requireAccount("编辑文件需要先添加账号")) return;
        GitFile file = selectedEditableFile();
        if (file == null) {
            Notify.show(selectedFiles.size() == 1 ? "请选择文本文件" : "请选择一个文本文件");
            return;
        }
        editFile(file);
    }

    private void editFile(GitFile file) {
        run("读取文件中", () -> {
            GitFileContent content = provider().readFile(account, token(), repo, repo.defaultBranch, file.path);
            if (!isUtf8Text(content.data)) throw new IllegalStateException("该文件不是可编辑文本");
            App.post(() -> showTextEditor(file, content));
        });
    }

    private void showTextEditor(GitFile file, GitFileContent content) {
        LinearLayoutCompat root = new LinearLayoutCompat(requireContext());
        root.setOrientation(LinearLayoutCompat.VERTICAL);
        TextInput editPath = input("远端路径", false);
        editPath.edit.setText(file.path);
        editPath.edit.setEnabled(false);
        TextInput editContent = input("内容", false);
        editContent.edit.setText(content.text == null ? "" : content.text);
        setupTextEditor(editContent.edit, 10, 14);
        root.addView(editPath.layout);
        root.addView(editContent.layout);
        final android.app.Dialog[] holder = new android.app.Dialog[1];
        holder[0] = LightDialog.create(requireContext(), "编辑文件", root, "保存", view -> {
            saveEditedText(file.path, rawValue(editContent.edit), content.file == null ? file.sha : content.file.sha);
            holder[0].dismiss();
        }, getString(R.string.dialog_negative), null);
        holder[0].show();
    }

    private void saveEditedText(String path, String content, String sha) {
        if (!requireAccount("保存文件需要先添加账号")) return;
        run("保存文件中", () -> {
            ProviderCapabilities capabilities = provider().capabilities();
            if (capabilities.contentsWrite) {
                SaveOptions options = new SaveOptions();
                options.message = "edit: " + path;
                options.sha = sha;
                provider().saveSmallFile(account, token(), repo, repo.defaultBranch, path, content.getBytes(StandardCharsets.UTF_8), options);
            } else {
                uploadChangesSync(List.of(new FileChange(path, content.getBytes(StandardCharsets.UTF_8))));
            }
            refreshAfterWriteSync(parent(path));
        });
    }

    private void toggleTree(String path) {
        toggleTree(path, treeFocusTag(FOCUS_TREE_INFO, path));
    }

    private void toggleTree(String path, String focusTag) {
        String key = path == null ? "" : path;
        pendingTreeFocusTag = focusTag;
        if (expandedPaths.contains(key)) {
            expandedPaths.remove(key);
            renderFileTree();
            return;
        }
        expandedPaths.add(key);
        currentPath = key;
        if (fileTree.containsKey(key)) {
            render();
            renderFileTree();
        } else {
            browse(repo, key);
        }
    }

    private void showCreateRepoDialog() {
        LinearLayoutCompat root = list();
        root.setPadding(dp(18), dp(16), dp(18), dp(14));
        root.setBackground(round(Color.WHITE, 14, Color.TRANSPARENT));

        MaterialTextView title = text("创建仓库", 20, Color.BLACK, true);
        root.addView(title);

        TextInput input = input("仓库名", false);
        input.edit.setSingleLine(true);
        input.edit.setText("webhtv-backup");
        root.addView(input.layout);

        MaterialTextView modeLabel = text("可见性", 12, Color.parseColor("#5F6368"), true);
        modeLabel.setPadding(0, dp(12), 0, dp(6));
        root.addView(modeLabel);

        final boolean[] privateRepo = {true};
        LinearLayoutCompat modeRow = row();
        MaterialButton publicMode = segment("公开");
        MaterialButton privateMode = segment("私有");
        publicMode.setChecked(false);
        privateMode.setChecked(true);
        modeRow.addView(publicMode, new LinearLayoutCompat.LayoutParams(0, dp(36), 1));
        LinearLayoutCompat.LayoutParams privateModeParams = new LinearLayoutCompat.LayoutParams(0, dp(36), 1);
        privateModeParams.leftMargin = dp(8);
        modeRow.addView(privateMode, privateModeParams);
        root.addView(modeRow);

        LinearLayoutCompat actions = row();
        actions.setPadding(0, dp(16), 0, 0);
        MaterialButton cancel = outline("取消");
        MaterialButton create = primary("创建");
        actions.addView(cancel, new LinearLayoutCompat.LayoutParams(0, dp(38), 1));
        LinearLayoutCompat.LayoutParams createParams = new LinearLayoutCompat.LayoutParams(0, dp(38), 1);
        createParams.leftMargin = dp(8);
        actions.addView(create, createParams);
        root.addView(actions);

        android.app.Dialog dialog = LightDialog.create(requireContext(), null, root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        publicMode.setOnClickListener(view -> {
            privateRepo[0] = false;
            publicMode.setChecked(true);
            privateMode.setChecked(false);
        });
        privateMode.setOnClickListener(view -> {
            privateRepo[0] = true;
            publicMode.setChecked(false);
            privateMode.setChecked(true);
        });
        cancel.setOnClickListener(view -> dialog.dismiss());
        create.setOnClickListener(view -> {
            String name = value(input.edit);
            if (TextUtils.isEmpty(name)) {
                input.layout.setError("请输入仓库名");
                return;
            }
            dialog.dismiss();
            createRepo(name, privateRepo[0]);
        });
    }

    private void createRepo(String name, boolean privateRepo) {
        run("创建仓库中", () -> {
            GitRepo created = provider().createRepo(account, token(), new CreateRepoRequest(name, "WebHTV Git 云盘", privateRepo));
            List<GitFile> files;
            try {
                files = provider().listFiles(account, token(), created, created.defaultBranch, "");
            } catch (Throwable ignored) {
                files = new ArrayList<>();
            }
            final List<GitFile> createdFiles = files;
            App.post(() -> {
                repo = created;
                if (!containsRepo(repos, created)) repos.add(0, created);
                fileTree.clear();
                selectedFiles.clear();
                expandedPaths.clear();
                expandedPaths.add("");
                render();
                showFiles("", createdFiles);
            });
        });
    }

    private void showUploadText() {
        if (!requireAccount("新建文件需要先添加账号")) return;
        LinearLayoutCompat root = new LinearLayoutCompat(requireContext());
        root.setOrientation(LinearLayoutCompat.VERTICAL);
        TextInput editPath = input("远端路径", false);
        editPath.edit.setText(joinRemote(currentPath, "new-file.txt"));
        TextInput content = input("内容", false);
        setupTextEditor(content.edit, 6, 12);
        root.addView(editPath.layout);
        root.addView(content.layout);
        final android.app.Dialog[] holder = new android.app.Dialog[1];
        holder[0] = LightDialog.create(requireContext(), "新建文件", root, getString(R.string.dialog_positive), view -> {
            uploadText(value(editPath.edit), value(content.edit));
            holder[0].dismiss();
        }, getString(R.string.dialog_negative), null);
        holder[0].show();
    }

    private void uploadText(String path, String content) {
        if (TextUtils.isEmpty(path)) return;
        uploadChanges(List.of(new FileChange(path, content.getBytes(StandardCharsets.UTF_8))), "新建文件中");
    }

    private void showUploadChooser() {
        if (!requireAccount("上传文件需要先添加账号")) return;
        ChoiceDialog.showSingle(getChildFragmentManager(), "上传", new String[]{"文件", "目录"}, -1, which -> {
            if (which == 0) chooseUploadFile();
            else chooseUploadFolder();
        });
    }

    private void chooseUploadFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        filePicker.launch(Intent.createChooser(intent, "选择文件"));
    }

    private void chooseUploadFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        folderPicker.launch(intent);
    }

    private void downloadSelected() {
        if (selectedFiles.isEmpty()) {
            Notify.show("未选择文件");
            return;
        }
        List<GitFile> files = selectedDownloadFiles();
        if (files.isEmpty()) {
            Notify.show("请选择文件后下载");
            return;
        }
        showDownloadDirectoryPicker(files);
    }

    private void showDownloadDirectoryPicker(List<GitFile> files) {
        File rootDir = Path.root();
        File tvDir = Path.tv();
        new File(rootDir, "Download").mkdirs();
        downloadDirSelected = tvDir.exists() ? tvDir : rootDir;
        expandedDownloadDirs.clear();
        expandedDownloadDirs.add(fileKey(rootDir));
        expandedDownloadDirs.add(fileKey(downloadDirSelected));

        LinearLayoutCompat panel = list();
        downloadDirPath = detail("");
        panel.addView(downloadDirPath);
        LinearLayoutCompat quick = row();
        MaterialButton root = outline("根目录");
        MaterialButton tv = outline("TV");
        MaterialButton download = outline("下载");
        root.setOnClickListener(view -> selectDownloadDir(rootDir));
        tv.setOnClickListener(view -> selectDownloadDir(tvDir));
        download.setOnClickListener(view -> selectDownloadDir(new File(rootDir, "Download")));
        quick.addView(root, new LinearLayoutCompat.LayoutParams(0, dp(32), 1));
        LinearLayoutCompat.LayoutParams tvParams = new LinearLayoutCompat.LayoutParams(0, dp(32), 1);
        tvParams.leftMargin = dp(6);
        quick.addView(tv, tvParams);
        LinearLayoutCompat.LayoutParams downloadParams = new LinearLayoutCompat.LayoutParams(0, dp(32), 1);
        downloadParams.leftMargin = dp(6);
        quick.addView(download, downloadParams);
        panel.addView(quick);

        NestedScrollView scroll = new NestedScrollView(requireContext());
        downloadDirList = list();
        scroll.addView(downloadDirList, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayoutCompat.LayoutParams scrollParams = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360));
        scrollParams.topMargin = dp(8);
        panel.addView(scroll, scrollParams);
        renderDownloadDirs();

        final android.app.Dialog[] holder = new android.app.Dialog[1];
        holder[0] = LightDialog.create(requireContext(), "选择保存目录", panel, "保存到此处", view -> {
            File target = downloadDirSelected;
            if (target == null) return;
            holder[0].dismiss();
            downloadToDirectory(target, files);
        }, getString(R.string.dialog_negative), null, "新建", view -> showCreateDownloadDir());
        holder[0].show();
    }

    private void selectDownloadDir(File dir) {
        if (dir == null) return;
        if (!dir.exists()) dir.mkdirs();
        downloadDirSelected = dir;
        expandedDownloadDirs.add(fileKey(dir));
        renderDownloadDirs();
    }

    private void renderDownloadDirs() {
        if (downloadDirList == null || downloadDirPath == null) return;
        downloadDirPath.setText("当前：" + displayDownloadDir(downloadDirSelected));
        downloadDirList.removeAllViews();
        addDownloadDirRow(Path.root(), 0);
    }

    private void addDownloadDirRow(File dir, int depth) {
        if (dir == null || !dir.isDirectory()) return;
        boolean expanded = expandedDownloadDirs.contains(fileKey(dir));
        boolean selected = sameFile(dir, downloadDirSelected);
        LinearLayoutCompat line = treeLine(depth, selected);
        List<File> children = listDownloadDirs(dir);
        if (children.isEmpty()) {
            View spacer = new View(requireContext());
            line.addView(spacer, new LinearLayoutCompat.LayoutParams(dp(28), dp(30)));
        } else {
            ImageButton toggle = treeToggle(expanded);
            toggle.setOnClickListener(view -> toggleDownloadDir(dir));
            line.addView(toggle, new LinearLayoutCompat.LayoutParams(dp(28), dp(30)));
        }
        ImageView icon = treeIcon(R.drawable.ic_folder, Color.parseColor("#F9AB00"));
        line.addView(icon, new LinearLayoutCompat.LayoutParams(dp(22), dp(22)));
        LinearLayoutCompat info = new LinearLayoutCompat(requireContext());
        info.setOrientation(LinearLayoutCompat.VERTICAL);
        MaterialTextView name = text(downloadDirName(dir), 14, Color.BLACK, true);
        MaterialTextView path = text(displayDownloadDir(dir), 11, Color.parseColor("#5F6368"), false);
        info.addView(name);
        info.addView(path);
        info.setPadding(dp(8), 0, 0, 0);
        line.addView(info, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        line.setOnClickListener(view -> selectDownloadDir(dir));
        downloadDirList.addView(line);
        if (!expanded) return;
        for (File child : children) addDownloadDirRow(child, depth + 1);
    }

    private void toggleDownloadDir(File dir) {
        String key = fileKey(dir);
        if (expandedDownloadDirs.contains(key)) expandedDownloadDirs.remove(key);
        else expandedDownloadDirs.add(key);
        renderDownloadDirs();
    }

    private void showCreateDownloadDir() {
        if (downloadDirSelected == null) return;
        TextInputEditText input = new TextInputEditText(requireContext());
        input.setSingleLine(true);
        input.setText("新建文件夹");
        final android.app.Dialog[] holder = new android.app.Dialog[1];
        holder[0] = LightDialog.create(requireContext(), "新建文件夹", input, getString(R.string.dialog_positive), view -> {
            String name = cleanDownloadName(value(input));
            if (TextUtils.isEmpty(name)) return;
            File dir = new File(downloadDirSelected, name);
            if (!dir.exists() && !dir.mkdirs()) {
                Notify.show("创建文件夹失败");
                return;
            }
            holder[0].dismiss();
            selectDownloadDir(dir);
        }, getString(R.string.dialog_negative), null);
        holder[0].show();
    }

    private void downloadToDirectory(File dir, List<GitFile> files) {
        if (dir == null || files == null || files.isEmpty()) return;
        run("下载文件中", () -> {
            if (!canWriteDirectory(dir)) throw new IllegalStateException("无法写入该目录，请确认已授予所有文件访问权限");
            String auth = token();
            for (int i = 0; i < files.size(); i++) downloadFile(dir, files.get(i), i, files.size(), auth);
            App.post(() -> Notify.show("下载完成：" + files.size()));
        });
    }

    private void chooseDownloadFileTarget(GitFile file) {
        ChoiceDialog.showSingle(getChildFragmentManager(), "保存到", new String[]{"手动选择", "TV 目录", "下载目录"}, -1, which -> {
            if (which == 1) launchDownloadFile(file, "primary:TV");
            else if (which == 2) launchDownloadFile(file, "primary:Download");
            else launchDownloadFile(file, "");
        });
    }

    private void launchDownloadFile(GitFile file, String initialDocumentId) {
        pendingDownloadFile = file;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, cleanDownloadName(file.name));
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        putInitialUri(intent, initialDocumentId);
        downloadFilePicker.launch(intent);
    }

    private void putInitialUri(Intent intent, String documentId) {
        if (intent == null || TextUtils.isEmpty(documentId) || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentId));
    }

    private void handleDownloadFileUri(Uri uri) {
        GitFile file = pendingDownloadFile;
        pendingDownloadFile = null;
        if (uri == null || file == null) return;
        run("下载文件中", () -> {
            downloadFile(uri, file, token());
            App.post(() -> Notify.show("下载完成：" + file.name));
        });
    }

    private void handleDownloadFolderUri(Uri uri) {
        if (uri == null || selectedFiles.isEmpty()) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
        }
        List<GitFile> files = selectedDownloadFiles();
        if (files.isEmpty()) return;
        run("下载文件中", () -> {
            String auth = token();
            for (int i = 0; i < files.size(); i++) downloadFile(uri, files.get(i), i, files.size(), auth);
            App.post(() -> Notify.show("下载完成：" + files.size()));
        });
    }

    private void downloadFile(File directory, GitFile file, int index, int total, String token) throws Exception {
        String url = TextUtils.isEmpty(file.downloadUrl) ? file.rawUrl : file.downloadUrl;
        if (TextUtils.isEmpty(url)) throw new IllegalStateException("文件缺少下载地址：" + file.name);
        updateProgress("下载 " + (index + 1) + "/" + total + " · " + file.name, percent(index, total), true);
        Request.Builder builder = new Request.Builder().url(url);
        if (!TextUtils.isEmpty(token)) builder.header("Authorization", "Bearer " + token);
        try (Response response = OkHttp.client().newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) throw new IllegalStateException("下载失败：" + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IllegalStateException("下载内容为空：" + file.name);
            File target = uniqueDownloadFile(directory, file.name);
            try (InputStream input = body.byteStream(); OutputStream output = new FileOutputStream(Path.create(target))) {
                copy(input, output, body.contentLength(), "下载 " + file.name);
            }
        }
        updateProgress("已下载 " + (index + 1) + "/" + total, percent(index + 1, total), false);
    }

    private void deleteSelected() {
        if (!requireAccount("删除文件需要先添加账号")) return;
        if (selectedFiles.isEmpty()) {
            Notify.show("未选择文件");
            return;
        }
        List<GitFile> targets = selectedDeleteTargets();
        int dirs = 0;
        for (GitFile file : targets) if (file.directory) dirs++;
        String message = "确定删除选中的 " + targets.size() + " 项？";
        if (dirs > 0) message += "目录会递归删除其中的文件。";
        ChoiceDialog.showConfirm(getChildFragmentManager(), "删除文件", message, "删除", this::deleteSelectedSync);
    }

    private void deleteSelectedSync() {
        List<GitFile> targets = selectedDeleteTargets();
        run("删除文件中", () -> {
            List<String> refreshPaths = deleteRefreshPaths(targets);
            if (provider().capabilities().contentsWrite) deleteByContentsApi(targets);
            else deleteByGitEngine(targets);
            App.post(() -> {
                selectedFiles.clear();
                removeDeletedTreeState(targets);
                renderFileTree();
            });
            refreshDeletePathsSync(refreshPaths, targets);
        });
    }

    private List<String> deleteRefreshPaths(List<GitFile> targets) {
        Set<String> paths = new HashSet<>();
        if (!isPathDeleted(currentPath, targets)) paths.add(currentPath == null ? "" : currentPath);
        for (GitFile file : targets) {
            if (file == null || TextUtils.isEmpty(file.path)) continue;
            paths.add(parent(file.path));
        }
        return new ArrayList<>(paths);
    }

    private void removeDeletedTreeState(List<GitFile> targets) {
        for (GitFile file : targets) {
            if (file == null || TextUtils.isEmpty(file.path)) continue;
            fileTree.remove(file.path);
            selectedFiles.remove(file.path);
            removePathPrefix(fileTree.keySet(), file.path);
            removePathPrefix(expandedPaths, file.path);
            removePathPrefix(selectedFiles.keySet(), file.path);
            removeDeletedFromCachedLists(file.path);
        }
    }

    private void removePathPrefix(Set<String> paths, String path) {
        if (paths == null || TextUtils.isEmpty(path)) return;
        List<String> remove = new ArrayList<>();
        for (String value : paths) if (TextUtils.equals(value, path) || value.startsWith(path + "/")) remove.add(value);
        for (String value : remove) paths.remove(value);
    }

    private void removeDeletedFromCachedLists(String path) {
        for (List<GitFile> files : fileTree.values()) {
            if (files == null) continue;
            files.removeIf(file -> file != null && (TextUtils.equals(file.path, path) || file.path.startsWith(path + "/")));
        }
    }

    private void deleteByContentsApi(List<GitFile> targets) throws Exception {
        List<GitFile> files = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (GitFile file : targets) collectDeleteFiles(file, files, seen);
        if (files.isEmpty()) throw new IllegalStateException("没有可删除的文件");
        for (int i = 0; i < files.size(); i++) {
            GitFile file = files.get(i);
            updateProgress("删除 " + (i + 1) + "/" + files.size() + " · " + file.path, percent(i, files.size()), false);
            provider().deleteFile(account, token(), repo, repo.defaultBranch, file, "delete: " + file.path);
            updateProgress("已删除 " + (i + 1) + "/" + files.size(), percent(i + 1, files.size()), false);
        }
    }

    private void deleteByGitEngine(List<GitFile> targets) throws Exception {
        List<FileChange> changes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (GitFile file : targets) collectDeleteChanges(file, changes, seen);
        if (changes.isEmpty()) throw new IllegalStateException("没有可删除的文件");
        updateProgress("提交删除 " + changes.size() + " 个文件", 80, true);
        driveEngine.commitAndPush(driveConfig(), changes);
    }

    private void collectDeleteChanges(GitFile file, List<FileChange> changes, Set<String> seen) throws Exception {
        if (file == null || TextUtils.isEmpty(file.path)) return;
        if (!file.directory) {
            addDeleteChange(file.path, changes, seen);
            return;
        }
        updateProgress("扫描目录 " + file.path, 0, true);
        List<GitFile> children = provider().listFiles(account, token(), repo, repo.defaultBranch, file.path);
        for (GitFile child : children) collectDeleteChanges(child, changes, seen);
    }

    private void collectDeleteFiles(GitFile file, List<GitFile> files, Set<String> seen) throws Exception {
        if (file == null || TextUtils.isEmpty(file.path)) return;
        if (!file.directory) {
            if (seen.add(file.path)) files.add(file);
            return;
        }
        updateProgress("扫描目录 " + file.path, 0, true);
        List<GitFile> children = provider().listFiles(account, token(), repo, repo.defaultBranch, file.path);
        for (GitFile child : children) collectDeleteFiles(child, files, seen);
    }

    private void addDeleteChange(String path, List<FileChange> changes, Set<String> seen) {
        if (!seen.add(path)) return;
        FileChange change = new FileChange(path, null);
        change.delete = true;
        changes.add(change);
    }

    private void downloadFile(Uri treeUri, GitFile file, int index, int total, String token) throws Exception {
        String url = TextUtils.isEmpty(file.downloadUrl) ? file.rawUrl : file.downloadUrl;
        if (TextUtils.isEmpty(url)) throw new IllegalStateException("文件缺少下载地址：" + file.name);
        updateProgress("下载 " + (index + 1) + "/" + total + " · " + file.name, percent(index, total), true);
        Request.Builder builder = new Request.Builder().url(url);
        if (!TextUtils.isEmpty(token)) builder.header("Authorization", "Bearer " + token);
        try (Response response = OkHttp.client().newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) throw new IllegalStateException("下载失败：" + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IllegalStateException("下载内容为空：" + file.name);
            Uri target = createDownloadDocument(treeUri, file);
            try (InputStream input = body.byteStream(); OutputStream output = requireContext().getContentResolver().openOutputStream(target)) {
                if (output == null) throw new IllegalStateException("无法写入文件：" + file.name);
                copy(input, output, body.contentLength(), "下载 " + file.name);
            }
        }
        updateProgress("已下载 " + (index + 1) + "/" + total, percent(index + 1, total), false);
    }

    private void downloadFile(Uri target, GitFile file, String token) throws Exception {
        String url = TextUtils.isEmpty(file.downloadUrl) ? file.rawUrl : file.downloadUrl;
        if (TextUtils.isEmpty(url)) throw new IllegalStateException("文件缺少下载地址：" + file.name);
        updateProgress("下载 " + file.name, 0, true);
        Request.Builder builder = new Request.Builder().url(url);
        if (!TextUtils.isEmpty(token)) builder.header("Authorization", "Bearer " + token);
        try (Response response = OkHttp.client().newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) throw new IllegalStateException("下载失败：" + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IllegalStateException("下载内容为空：" + file.name);
            try (InputStream input = body.byteStream(); OutputStream output = requireContext().getContentResolver().openOutputStream(target)) {
                if (output == null) throw new IllegalStateException("无法写入文件：" + file.name);
                copy(input, output, body.contentLength(), "下载 " + file.name);
            }
        }
        updateProgress("已下载", 100, false);
    }

    private Uri createDownloadDocument(Uri treeUri, GitFile file) throws Exception {
        Uri dir = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
        Uri target = DocumentsContract.createDocument(requireContext().getContentResolver(), dir, "application/octet-stream", cleanDownloadName(file.name));
        if (target == null) throw new IllegalStateException("创建下载文件失败：" + file.name);
        return target;
    }

    private void handleFileUri(Uri uri) {
        if (!requireAccount("上传文件需要先添加账号")) return;
        if (uri == null || repo == null) return;
        run("读取文件中", () -> {
            String name = displayName(uri, "upload.bin");
            byte[] data = readBytes(uri, "读取 " + name);
            uploadChangesSync(List.of(new FileChange(joinRemote(currentPath, name), data)));
            refreshAfterWriteSync(currentPath);
        });
    }

    private void handleTreeUri(Uri uri) {
        if (!requireAccount("上传目录需要先添加账号")) return;
        if (uri == null || repo == null) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
        }
        run("读取目录中", () -> {
            List<FileChange> changes = new ArrayList<>();
            String rootName = documentName(uri, "folder");
            collectTree(uri, DocumentsContract.getTreeDocumentId(uri), rootName, changes);
            if (changes.isEmpty()) throw new IllegalStateException("目录中没有可上传文件");
            uploadChangesSync(changes);
            refreshAfterWriteSync(currentPath);
        });
    }

    private void uploadChanges(List<FileChange> changes, String status) {
        if (changes == null || changes.isEmpty()) return;
        run(status, () -> {
            uploadChangesSync(changes);
            refreshAfterWriteSync(currentPath, files -> containsUploadedChildren(currentPath, changes, files));
        });
    }

    private void uploadChangesSync(List<FileChange> changes) throws Exception {
        ProviderCapabilities capabilities = provider().capabilities();
        if (capabilities.contentsWrite) {
            for (int i = 0; i < changes.size(); i++) {
                FileChange change = changes.get(i);
                updateProgress("上传 " + (i + 1) + "/" + changes.size() + " · " + change.path, percent(i, changes.size()), false);
                SaveOptions options = new SaveOptions();
                options.message = "upload: " + change.path;
                provider().saveSmallFile(account, token(), repo, repo.defaultBranch, change.path, change.data, options);
                updateProgress("已上传 " + (i + 1) + "/" + changes.size(), percent(i + 1, changes.size()), false);
            }
        } else {
            updateProgress("提交并推送 " + changes.size() + " 个文件", 80, true);
            driveEngine.commitAndPush(driveConfig(), changes);
            updateProgress("推送完成", 100, false);
        }
    }

    private void refreshAfterWriteSync(String path) throws Exception {
        refreshAfterWriteSync(path, null);
    }

    private void refreshAfterWriteSync(String path, RefreshVerifier verifier) throws Exception {
        String target = path == null ? "" : path;
        updateProgress("刷新目录", 100, true);
        List<GitFile> files = listFilesFresh(target, verifier);
        App.post(() -> {
            expandedPaths.add(target);
            currentPath = target;
            fileTree.put(target, files);
            renderFileTree();
            render();
        });
    }

    private void refreshDeletePathsSync(List<String> paths, List<GitFile> targets) throws Exception {
        if (paths == null || paths.isEmpty()) {
            refreshAfterWriteSync("");
            return;
        }
        updateProgress("刷新目录", 100, true);
        Map<String, List<GitFile>> fresh = new HashMap<>();
        for (String path : paths) {
            String target = path == null ? "" : path;
            fresh.put(target, listFilesFresh(target, files -> !containsDeletedChildren(target, targets, files)));
        }
        App.post(() -> {
            for (String path : sortedPaths(fresh.keySet())) {
                expandedPaths.add(path);
                fileTree.put(path, fresh.get(path));
            }
            if (isPathDeleted(currentPath, targets)) currentPath = firstPath(paths);
            renderFileTree();
            render();
        });
    }

    private List<String> sortedPaths(Set<String> paths) {
        List<String> result = new ArrayList<>(paths);
        result.sort((a, b) -> Integer.compare(pathDepth(a), pathDepth(b)));
        return result;
    }

    private int pathDepth(String path) {
        if (TextUtils.isEmpty(path)) return 0;
        int depth = 1;
        for (int i = 0; i < path.length(); i++) if (path.charAt(i) == '/') depth++;
        return depth;
    }

    private List<GitFile> listFilesFresh(String path, RefreshVerifier verifier) throws Exception {
        List<GitFile> files = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            files = provider().listFiles(account, token(), repo, repo.defaultBranch, path);
            if (verifier == null || verifier.isFresh(files)) return files;
            Thread.sleep(400L * (attempt + 1));
        }
        return files == null ? new ArrayList<>() : files;
    }

    private boolean containsUploadedChildren(String path, List<FileChange> changes, List<GitFile> files) {
        for (FileChange change : changes) {
            String name = directChildName(path, change.path);
            if (TextUtils.isEmpty(name)) continue;
            if (!containsChild(files, name)) return false;
        }
        return true;
    }

    private boolean containsDeletedChildren(String path, List<GitFile> targets, List<GitFile> files) {
        for (GitFile target : targets) {
            String name = directChildName(path, target == null ? "" : target.path);
            if (!TextUtils.isEmpty(name) && containsChild(files, name)) return true;
        }
        return false;
    }

    private boolean containsChild(List<GitFile> files, String name) {
        if (files == null || TextUtils.isEmpty(name)) return false;
        for (GitFile file : files) if (TextUtils.equals(file.name, name)) return true;
        return false;
    }

    private String directChildName(String parent, String path) {
        if (TextUtils.isEmpty(path)) return "";
        String dir = parent == null ? "" : parent;
        String value = path;
        if (!TextUtils.isEmpty(dir)) {
            if (!value.startsWith(dir + "/")) return "";
            value = value.substring(dir.length() + 1);
        }
        int slash = value.indexOf('/');
        return slash >= 0 ? value.substring(0, slash) : value;
    }

    private boolean isPathDeleted(String path, List<GitFile> targets) {
        if (TextUtils.isEmpty(path)) return false;
        for (GitFile file : targets) {
            if (file == null || TextUtils.isEmpty(file.path)) continue;
            if (file.directory && (TextUtils.equals(path, file.path) || path.startsWith(file.path + "/"))) return true;
            if (!file.directory && TextUtils.equals(path, file.path)) return true;
        }
        return false;
    }

    private String firstPath(List<String> paths) {
        return paths == null || paths.isEmpty() ? "" : (paths.get(0) == null ? "" : paths.get(0));
    }

    private void invalidateTree(String path) {
        String target = path == null ? "" : path;
        fileTree.remove(target);
        String prefix = TextUtils.isEmpty(target) ? "" : target + "/";
        List<String> remove = new ArrayList<>();
        for (String key : fileTree.keySet()) {
            if (TextUtils.isEmpty(prefix) || key.startsWith(prefix)) remove.add(key);
        }
        for (String key : remove) fileTree.remove(key);
        remove.clear();
        for (String key : selectedFiles.keySet()) {
            if (TextUtils.isEmpty(prefix) || key.equals(target) || key.startsWith(prefix)) remove.add(key);
        }
        for (String key : remove) selectedFiles.remove(key);
    }

    private void backup() {
        if (!requireAccount("备份需要先添加账号")) return;
        if (repo == null || !repo.privateRepo) return;
        run("生成备份中", () -> {
            AppBackup.CreatedBackup created = AppBackup.createTempResult((stage, percent, bytes, total) -> updateProgress(progressText(stage, bytes, total), percent, percent <= 0));
            File archive = created.getFile();
            String name = AppBackup.fileName(created.hasWarning());
            String zip = GitCloudPaths.backupDir() + "/" + name;
            CommitResult result;
            try {
                List<FileChange> changes = new ArrayList<>();
                changes.add(new FileChange(zip, Path.readToByte(archive)));
                try {
                    for (GitFile file : provider().listFiles(account, token(), repo, repo.defaultBranch, GitCloudPaths.backupDir())) {
                        if (!isBackupManifest(file)) continue;
                        FileChange delete = new FileChange(file.path, new byte[0]);
                        delete.delete = true;
                        changes.add(delete);
                    }
                } catch (Throwable ignored) {
                }
                updateProgress("准备 Git 云盘工作区", 0, true);
                Path.clear(GitCloudPaths.worktree(account, repo));
                updateProgress("上传完整备份 · " + size(archive.length()), 80, true);
                result = driveEngine.commitAndPush(driveConfig(), changes);
            } finally {
                Path.clear(archive);
            }
            List<GitFile> rootFiles = provider().listFiles(account, token(), repo, repo.defaultBranch, "");
            List<GitFile> appFiles = provider().listFiles(account, token(), repo, repo.defaultBranch, "apps");
            List<GitFile> webhtvFiles = provider().listFiles(account, token(), repo, repo.defaultBranch, "apps/webhtv");
            List<GitFile> backupFiles = visibleFiles(GitCloudPaths.backupDir(), provider().listFiles(account, token(), repo, repo.defaultBranch, GitCloudPaths.backupDir()));
            String message = result.pushed
                    ? (created.hasWarning() ? "不完整备份已上传：" + created.getResult().warning : "备份已上传")
                    : result.message;
            App.post(() -> {
                Notify.show(message);
                if (!isAdded() || binding == null) return;
                fileTree.clear();
                fileTree.put("", rootFiles);
                fileTree.put("apps", appFiles);
                fileTree.put("apps/webhtv", webhtvFiles);
                fileTree.put(GitCloudPaths.backupDir(), backupFiles);
                expandedPaths.clear();
                expandedPaths.add("");
                expandedPaths.add("apps");
                expandedPaths.add("apps/webhtv");
                expandedPaths.add(GitCloudPaths.backupDir());
                currentPath = GitCloudPaths.backupDir();
                renderFileTree();
            });
        });
    }

    private void confirmRestore() {
        if (!requireAccount("恢复备份需要先添加账号")) return;
        if (repo == null || !repo.privateRepo) return;
        GitFile selected = validSelectedBackup();
        String source;
        if (selected != null) source = "将恢复已勾选的备份：\n" + selected.name;
        else if (selectedFiles.isEmpty()) source = "未勾选备份，将自动使用最新一次备份。";
        else source = "当前勾选包含文件夹、非备份文件或多个项目，将忽略当前选择并自动使用最新一次备份。";
        ChoiceDialog.showConfirm(getChildFragmentManager(), "恢复备份", source + "\n\n恢复内容包括数据库、设置、登录态、MPV 配置及共享数据文件。已有同名数据会被覆盖。", "恢复", this::restoreBackup);
    }

    private void restoreBackup() {
        run("查找备份中", () -> {
            GitFile backup = validSelectedBackup();
            if (backup == null) backup = latestBackup();
            if (backup == null) throw new IllegalStateException("未找到可恢复的备份");
            File archive = File.createTempFile("webhtv-git-restore-", ".zip", Path.cache());
            try {
                downloadRemoteFile(backup, archive);
                updateProgress("恢复 " + backup.name, 100, true);
                AppBackup.RestoreResult result = AppBackup.restore(archive, (stage, percent, bytes, total) -> updateProgress(progressText(stage, bytes, total), percent, percent <= 0));
                App.post(() -> Notify.show(result.hasWarning() ? "恢复完成，但存在缺失：" + result.warning : "恢复完成：" + result.fileCount() + " 个文件及完整应用数据"));
            } finally {
                Path.clear(archive);
            }
        });
    }

    private GitFile validSelectedBackup() {
        if (selectedFiles.size() != 1) return null;
        GitFile file = selectedFiles.values().iterator().next();
        return isBackupZip(file) ? file : null;
    }

    private GitFile latestBackup() throws Exception {
        List<GitFile> files = provider().listFiles(account, token(), repo, repo.defaultBranch, GitCloudPaths.backupDir());
        GitFile latest = null;
        for (GitFile file : files) {
            if (!isBackupZip(file)) continue;
            if (latest == null || AppBackup.compareBackupNames(file.name, latest.name) > 0) latest = file;
        }
        return latest;
    }

    private boolean isBackupZip(GitFile file) {
        return file != null && !file.directory && AppBackup.isBackupZipName(file.name);
    }

    private boolean isBackupManifest(GitFile file) {
        return file != null && !file.directory && AppBackup.isBackupManifestName(file.name);
    }

    private void downloadRemoteFile(GitFile file, File target) throws Exception {
        String url = TextUtils.isEmpty(file.downloadUrl) ? file.rawUrl : file.downloadUrl;
        if (TextUtils.isEmpty(url)) throw new IllegalStateException("备份缺少下载地址：" + file.name);
        updateProgress("下载备份 " + file.name, 0, true);
        Request.Builder builder = new Request.Builder().url(url);
        String auth = token();
        if (!TextUtils.isEmpty(auth)) builder.header("Authorization", "Bearer " + auth);
        try (Response response = OkHttp.client().newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) throw new IllegalStateException("下载备份失败：" + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IllegalStateException("备份内容为空：" + file.name);
            try (InputStream input = body.byteStream(); OutputStream output = new FileOutputStream(target)) {
                copy(input, output, body.contentLength(), "下载备份");
            }
        }
    }

    private String progressText(String stage, long bytes, long total) {
        if (total > 0) return stage + " · " + size(bytes) + " / " + size(total);
        if (bytes > 0) return stage + " · " + size(bytes);
        return stage;
    }

    private List<GitFile> visibleFiles(String path, List<GitFile> files) {
        if (!TextUtils.equals(GitCloudPaths.backupDir(), path)) return files == null ? new ArrayList<>() : files;
        List<GitFile> result = new ArrayList<>();
        for (GitFile file : files == null ? List.<GitFile>of() : files) {
            if (isBackupManifest(file)) continue;
            result.add(file);
        }
        return result;
    }

    private void clearCache() {
        if (!requireAccount("清理写入缓存需要先添加账号")) return;
        if (account == null || repo == null) return;
        run("清理缓存中", () -> {
            Path.clear(GitCloudPaths.worktree(account, repo));
            App.post(() -> Notify.show("本地缓存已清理"));
        });
    }

    private GitDriveConfig driveConfig() throws Exception {
        GitDriveConfig config = new GitDriveConfig();
        config.account = account;
        config.repo = repo;
        config.token = token();
        config.branch = repo.defaultBranch;
        config.worktreeDir = GitCloudPaths.worktree(account, repo);
        config.defaultRemotePath = GitCloudPaths.backupDir();
        return config;
    }

    private void removeAccount() {
        if (account == null) return;
        GitCloudTokenStore.remove(account.tokenKey);
        GitCloudAccountStore.remove(account);
        account = null;
        anonymousAccount = null;
        repo = null;
        repoSearchOwner = "";
        currentPath = "";
        repos.clear();
        searchResults.clear();
        fileTree.clear();
        selectedFiles.clear();
        expandedPaths.clear();
        reposAccountId = null;
        repoMode = REPO_MODE_FAVORITE;
        editingAccount = false;
        populateAccountForm(null);
        render();
        renderRepoList();
    }

    private void changeRepo() {
        repo = null;
        currentPath = "";
        fileTree.clear();
        selectedFiles.clear();
        expandedPaths.clear();
        render();
        if (account != null && !TextUtils.equals(reposAccountId, account.id)) refreshRepos();
        else if (account != null) showRepos(new ArrayList<>(repos));
        else renderRepoList();
    }

    private void reloadTree() {
        if (repo == null) return;
        fileTree.remove(currentPath == null ? "" : currentPath);
        browse(repo, currentPath);
    }

    private boolean hasSelectedFiles() {
        for (GitFile file : selectedFiles.values()) if (!file.directory) return true;
        return false;
    }

    private List<GitFile> selectedDownloadFiles() {
        List<GitFile> files = new ArrayList<>();
        for (GitFile file : selectedFiles.values()) if (!file.directory) files.add(file);
        return files;
    }

    private List<GitFile> selectedDeleteTargets() {
        List<GitFile> targets = new ArrayList<>();
        for (GitFile file : selectedFiles.values()) {
            if (file == null || TextUtils.isEmpty(file.path)) continue;
            if (!hasSelectedAncestorDirectory(file.path)) targets.add(file);
        }
        return targets;
    }

    private boolean hasSelectedAncestorDirectory(String path) {
        if (TextUtils.isEmpty(path)) return false;
        for (GitFile file : selectedFiles.values()) {
            if (file == null || !file.directory || TextUtils.isEmpty(file.path)) continue;
            if (!TextUtils.equals(file.path, path) && path.startsWith(file.path + "/")) return true;
        }
        return false;
    }

    private GitFile selectedEditableFile() {
        if (selectedFiles.size() != 1) return null;
        GitFile file = selectedFiles.values().iterator().next();
        return isTextCandidate(file) ? file : null;
    }

    private void populateAccountForm(GitAccount source) {
        if (binding == null || binding.alias == null) return;
        binding.alias.edit.setText(source == null ? "" : source.remark);
        binding.baseUrl.edit.setText(source == null ? defaultBaseUrl(providerType) : source.baseUrl);
        binding.token.edit.setText("");
    }

    private String label(GitProviderType type) {
        return type == GitProviderType.CNB ? "CNB" : "GitHub";
    }

    private String defaultBaseUrl(GitProviderType type) {
        return type == GitProviderType.CNB ? "https://cnb.cool" : "";
    }

    private String meta(GitAccount value) {
        if (value == null) return "";
        return value.lastValidatedAt > 0 ? "已校验" : "";
    }

    private String accountSummary() {
        if (account == null || account.providerType != providerType) return label(providerType) + " · 添加账号";
        String suffix = editingAccount ? " · 已展开" : " · 管理";
        return label(providerType) + " · " + account.displayName() + suffix;
    }

    private String statusText() {
        if (busy) return binding.status == null ? "" : binding.status.getText().toString();
        if (editingAccount) return label(providerType) + " · 添加账号";
        String name = account == null ? label(providerType) + " · 匿名浏览" : account.displayName();
        if (repo == null) return name + " · 请选择仓库";
        return name + " · " + repo.displayName();
    }

    private boolean requireAccount(String message) {
        if (account != null && account.providerType == providerType) return true;
        showAccountForm(message);
        return false;
    }

    private boolean requireAccountToken(String message) {
        if (hasAccountToken()) return true;
        showAccountForm(message);
        return false;
    }

    private boolean hasAccountToken() {
        if (account == null || account.providerType != providerType) return false;
        try {
            return !TextUtils.isEmpty(GitCloudTokenStore.get(account.tokenKey));
        } catch (Exception e) {
            return false;
        }
    }

    private void showAccountForm(String message) {
        if (!TextUtils.isEmpty(message)) Notify.show(message);
        editingAccount = true;
        populateAccountForm(account);
        render();
    }

    private String pathLabel() {
        String branch = TextUtils.isEmpty(repo.defaultBranch) ? "main" : repo.defaultBranch;
        return branch + " · /" + (TextUtils.isEmpty(currentPath) ? "" : currentPath);
    }

    private void run(String status, CheckedRunnable runnable) {
        if (busy) return;
        busy = true;
        updateProgress(status, 0, true);
        setStatus(status);
        render();
        Task.execute(() -> {
            try {
                runnable.run();
                App.post(() -> {
                    updateProgress("完成", 100, false);
                    setStatus("完成");
                });
            } catch (Throwable e) {
                SpiderDebug.log("git-cloud", e);
                App.post(() -> {
                    updateProgress("", 0, false);
                    setStatus(e.getMessage());
                    Notify.show(e.getMessage());
                });
            } finally {
                App.post(() -> {
                    busy = false;
                    if ("完成".contentEquals(progressMessage)) updateProgress("", 0, false);
                    render();
                });
            }
        });
    }

    private GitCloudProvider provider() {
        return GitCloudProviders.get(providerType);
    }

    private GitAccount activeAccount() {
        if (account != null && account.providerType == providerType) return account;
        if (anonymousAccount == null || anonymousAccount.providerType != providerType) {
            anonymousAccount = GitAccount.create(providerType, defaultBaseUrl(providerType), "");
            anonymousAccount.username = "";
        }
        return anonymousAccount;
    }

    private String token() throws Exception {
        return account == null ? "" : GitCloudTokenStore.get(account.tokenKey);
    }

    private void setStatus(String value) {
        if (binding != null && binding.status != null) binding.status.setText(value == null ? "" : value);
    }

    private void updateProgress(String message, int value, boolean indeterminate) {
        progressMessage = message == null ? "" : message;
        progressValue = Math.max(0, Math.min(100, value));
        progressIndeterminate = indeterminate;
        App.post(this::renderProgress);
    }

    private void renderProgress() {
        if (binding == null || binding.progress == null || binding.progressText == null) return;
        boolean visible = busy && !TextUtils.isEmpty(progressMessage);
        binding.progress.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.progressText.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        binding.progress.setIndeterminate(progressIndeterminate);
        if (!progressIndeterminate) binding.progress.setProgress(progressValue);
        binding.progressText.setText(progressMessage);
    }

    private TextInput input(String hint, boolean password) {
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint(hint);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxBackgroundColor(Color.WHITE);
        layout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_stroke));
        layout.setBoxStrokeWidth(dp(1));
        layout.setBoxStrokeWidthFocused(dp(2));
        layout.setHintTextColor(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_focused}, new int[]{}},
                new int[]{Color.parseColor("#0B57D0"), Color.parseColor("#5F6368")}
        ));
        layout.setBoxCornerRadii(dp(8), dp(8), dp(8), dp(8));
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        layout.setLayoutParams(params);
        TextInputEditText edit = "内容".contentEquals(hint) ? new SafeScrollEditText(layout.getContext()) : new TextInputEditText(layout.getContext());
        edit.setSingleLine(!"内容".contentEquals(hint));
        edit.setTextSize(14);
        edit.setTextColor(Color.parseColor("#202124"));
        edit.setHintTextColor(Color.parseColor("#5F6368"));
        edit.setSelectAllOnFocus(false);
        edit.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        if (password) edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(edit, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new TextInput(layout, edit);
    }

    private void setupTextEditor(TextInputEditText edit, int minLines, int maxLines) {
        edit.setSingleLine(false);
        edit.setMinLines(minLines);
        edit.setMaxLines(maxLines);
        edit.setGravity(Gravity.TOP | Gravity.START);
        edit.setPaddingRelative(dp(14), dp(18), dp(20), dp(20));
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setOverScrollMode(View.OVER_SCROLL_NEVER);
        edit.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            view.getParent().requestDisallowInterceptTouchEvent(action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL);
            return false;
        });
    }

    private View section(String title) {
        MaterialTextView view = text(title, 12, Color.parseColor("#5F6368"), true);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);
        params.bottomMargin = dp(6);
        view.setLayoutParams(params);
        return view;
    }

    private LinearLayoutCompat list() {
        LinearLayoutCompat view = new LinearLayoutCompat(requireContext());
        view.setOrientation(LinearLayoutCompat.VERTICAL);
        return view;
    }

    private LinearLayoutCompat row() {
        LinearLayoutCompat view = new LinearLayoutCompat(requireContext());
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setOrientation(LinearLayoutCompat.HORIZONTAL);
        return view;
    }

    private LinearLayoutCompat card() {
        LinearLayoutCompat view = new LinearLayoutCompat(requireContext());
        view.setOrientation(LinearLayoutCompat.VERTICAL);
        view.setPadding(dp(10), dp(9), dp(10), dp(9));
        view.setBackground(round(Color.parseColor("#F8F9FA"), 8, Color.parseColor("#DADCE0")));
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        view.setLayoutParams(params);
        return view;
    }

    private MaterialTextView text(String value, int sp, int color, boolean bold) {
        MaterialTextView view = new MaterialTextView(requireContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setSingleLine(false);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private MaterialTextView detail(String value) {
        MaterialTextView view = text(value, 12, Color.parseColor("#5F6368"), false);
        view.setPadding(0, dp(4), 0, dp(8));
        return view;
    }

    private MaterialTextView empty(String value) {
        MaterialTextView view = text(value, 13, Color.parseColor("#5F6368"), false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(14), 0, dp(14));
        return view;
    }

    private MaterialTextView pill(String value) {
        MaterialTextView view = text(value, 11, Color.parseColor("#1967D2"), true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), dp(3), dp(8), dp(3));
        view.setBackground(round(Color.parseColor("#E8F0FE"), 16, Color.TRANSPARENT));
        return view;
    }

    private MaterialButton primary(String text) {
        MaterialButton button = baseButton(text);
        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.dialog_primary_button_bg));
        button.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.dialog_primary_button_text));
        return button;
    }

    private MaterialButton tonal(String text) {
        MaterialButton button = baseButton(text);
        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.dialog_tonal_button_bg));
        button.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.dialog_tonal_button_text));
        return button;
    }

    private MaterialButton outline(String text) {
        MaterialButton button = baseButton(text);
        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_bg));
        button.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_text));
        button.setStrokeColor(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_stroke));
        button.setStrokeWidth(dp(1));
        return button;
    }

    private MaterialButton compact(String text) {
        MaterialButton button = tonal(text);
        button.setTextSize(12);
        return button;
    }

    private MaterialButton ownerLink(String text) {
        MaterialButton button = baseButton(text);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTextColor(Color.parseColor("#1967D2"));
        button.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.selector_git_cloud_inline_focus));
        button.setMinHeight(dp(28));
        button.setMinimumHeight(dp(28));
        button.setPadding(dp(2), 0, dp(2), 0);
        return button;
    }

    private MaterialButton platformButton(int icon) {
        MaterialButton button = baseButton("");
        button.setCheckable(true);
        button.setIconResource(icon);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconPadding(0);
        button.setIconSize(dp(22));
        button.setIconTint(segmentText());
        button.setBackgroundTintList(segmentBackground());
        button.setStrokeColor(segmentStroke());
        button.setStrokeWidth(dp(1));
        button.setMinWidth(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private MaterialButton toolButton(int icon, String label) {
        MaterialButton button = tonal("");
        button.setContentDescription(label);
        button.setIconResource(icon);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconPadding(0);
        button.setIconSize(dp(20));
        button.setIconTint(ContextCompat.getColorStateList(requireContext(), R.color.dialog_tonal_button_text));
        button.setMinWidth(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private MaterialButton headerIconButton(int icon, String label) {
        MaterialButton button = outline("");
        button.setContentDescription(label);
        button.setIconResource(icon);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconPadding(0);
        button.setIconSize(dp(19));
        button.setIconTint(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_text));
        button.setMinWidth(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private ImageButton treeToggle(boolean expanded) {
        ImageButton button = new ImageButton(requireContext());
        button.setImageResource(expanded ? R.drawable.ic_detail_minus : R.drawable.ic_detail_plus);
        button.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.selector_git_cloud_inline_focus));
        button.setColorFilter(Color.parseColor("#174EA6"));
        button.setPadding(dp(6), dp(6), dp(6), dp(6));
        makeFocusable(button);
        return button;
    }

    private LinearLayoutCompat treeLine(int depth, boolean selected) {
        LinearLayoutCompat view = row();
        view.setMinimumHeight(dp(42));
        view.setPadding(dp(4 + depth * 18), dp(5), dp(6), dp(5));
        view.setSelected(selected);
        view.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.selector_git_cloud_tree_item));
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);
        view.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(2);
        view.setLayoutParams(params);
        return view;
    }

    private MaterialCheckBox checkbox(GitFile file) {
        MaterialCheckBox check = new MaterialCheckBox(requireContext());
        check.setButtonTintList(ContextCompat.getColorStateList(requireContext(), R.color.dialog_checkbox_tint));
        check.setChecked(selectedFiles.containsKey(file.path));
        check.setPadding(0, 0, 0, 0);
        check.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.selector_git_cloud_inline_focus));
        makeFocusable(check);
        check.setOnClickListener(view -> {
            pendingTreeFocusTag = treeFocusTag(FOCUS_TREE_CHECK, file.path);
            toggleSelection(file);
            render();
            renderFileTree();
        });
        return check;
    }

    private void toggleSelection(GitFile file) {
        if (file == null || TextUtils.isEmpty(file.path)) return;
        if (selectedFiles.containsKey(file.path)) deselect(file);
        else select(file);
    }

    private void select(GitFile file) {
        selectedFiles.put(file.path, file);
        if (file.directory) selectLoadedDescendants(file.path);
    }

    private void deselect(GitFile file) {
        selectedFiles.remove(file.path);
        removeSelectedAncestorDirectories(file.path);
        if (file.directory) removePathPrefix(selectedFiles.keySet(), file.path);
    }

    private void selectLoadedDescendants(String path) {
        List<GitFile> children = fileTree.get(path == null ? "" : path);
        if (children == null || children.isEmpty()) return;
        for (GitFile child : children) {
            if (child == null || TextUtils.isEmpty(child.path)) continue;
            selectedFiles.put(child.path, child);
            if (child.directory) selectLoadedDescendants(child.path);
        }
    }

    private boolean isCoveredBySelectedDirectory(String path) {
        if (TextUtils.isEmpty(path)) return false;
        List<GitFile> selected = new ArrayList<>(selectedFiles.values());
        for (GitFile file : selected) {
            if (file == null || !file.directory || TextUtils.isEmpty(file.path)) continue;
            if (TextUtils.equals(file.path, path) || path.startsWith(file.path + "/")) return true;
        }
        return false;
    }

    private void removeSelectedAncestorDirectories(String path) {
        if (TextUtils.isEmpty(path)) return;
        List<String> remove = new ArrayList<>();
        for (GitFile file : selectedFiles.values()) {
            if (file == null || !file.directory || TextUtils.isEmpty(file.path)) continue;
            if (!TextUtils.equals(file.path, path) && path.startsWith(file.path + "/")) remove.add(file.path);
        }
        for (String key : remove) selectedFiles.remove(key);
    }

    private ImageView treeIcon(int icon, int color) {
        ImageView view = new ImageView(requireContext());
        view.setImageResource(icon);
        view.setColorFilter(color);
        return view;
    }

    private View treeMessage(String value, int depth) {
        MaterialTextView view = text(value, 12, Color.parseColor("#5F6368"), false);
        view.setPadding(dp(44 + depth * 18), dp(6), dp(4), dp(6));
        return view;
    }

    private MaterialButton segment(String text) {
        MaterialButton button = outline(text);
        button.setCheckable(true);
        button.setBackgroundTintList(segmentBackground());
        button.setTextColor(segmentText());
        button.setStrokeColor(segmentStroke());
        return button;
    }

    private ColorStateList segmentBackground() {
        return new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_enabled},
                new int[]{android.R.attr.state_focused},
                new int[]{android.R.attr.state_pressed},
                new int[]{}
        }, new int[]{
                Color.parseColor("#0B57D0"),
                Color.parseColor("#F1F3F4"),
                Color.parseColor("#E8F0FE"),
                Color.parseColor("#E8F0FE"),
                Color.WHITE
        });
    }

    private ColorStateList segmentText() {
        return new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_enabled},
                new int[]{}
        }, new int[]{
                Color.WHITE,
                Color.parseColor("#9AA0A6"),
                Color.parseColor("#202124")
        });
    }

    private ColorStateList segmentStroke() {
        return new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{android.R.attr.state_focused},
                new int[]{android.R.attr.state_pressed},
                new int[]{}
        }, new int[]{
                Color.parseColor("#0B57D0"),
                Color.parseColor("#0B57D0"),
                Color.parseColor("#0B57D0"),
                Color.parseColor("#C8CDD2")
        });
    }

    private MaterialButton iconButton(String text) {
        MaterialButton button = baseButton(text);
        button.setTextSize(20);
        button.setMinWidth(0);
        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_bg));
        button.setTextColor(Color.parseColor("#5F6368"));
        return button;
    }

    private MaterialButton baseButton(String text) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setFocusableInTouchMode(false);
        button.setMinWidth(0);
        button.setMinHeight(dp(32));
        button.setMinimumHeight(dp(32));
        button.setPadding(dp(6), 0, dp(6), 0);
        return button;
    }

    private void makeFocusable(View view) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(false);
        view.setClickable(true);
    }

    private String treeFocusTag(String prefix, String path) {
        return prefix + (path == null ? "" : path);
    }

    private void restoreTreeFocus() {
        if (binding == null || binding.fileList == null || TextUtils.isEmpty(pendingTreeFocusTag)) return;
        String tag = pendingTreeFocusTag;
        pendingTreeFocusTag = null;
        binding.fileList.post(() -> {
            if (binding == null || binding.fileList == null) return;
            View target = findTaggedView(binding.fileList, tag);
            if (target == null) target = findFirstFocusable(binding.fileList);
            if (target != null && target.isShown() && target.isEnabled()) target.requestFocus();
        });
    }

    private View findTaggedView(View view, Object tag) {
        if (view == null) return null;
        if (tag.equals(view.getTag())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findTaggedView(group.getChildAt(i), tag);
            if (found != null) return found;
        }
        return null;
    }

    private View findFirstFocusable(View view) {
        if (view == null) return null;
        if (view.isFocusable() && view.isShown() && view.isEnabled()) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findFirstFocusable(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable e) {
            Notify.show(R.string.manage_page_no_browser);
        }
    }

    private void copy(String value) {
        ClipboardManager manager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || TextUtils.isEmpty(value)) return;
        manager.setPrimaryClip(ClipData.newPlainText("Git raw", value));
        SettingClipboardOverlay.record(value);
        Notify.show("已复制");
    }

    private String tokenUrl() {
        return providerType == GitProviderType.CNB ? "https://cnb.cool" : "https://github.com/settings/personal-access-tokens";
    }

    private String helpUrl() {
        return providerType == GitProviderType.CNB ? "https://docs.cnb.cool/zh/guide/git-access.html" : "https://docs.github.com/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens";
    }

    private String value(TextInputEditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    private String rawValue(TextInputEditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString();
    }

    private boolean isTextCandidate(GitFile file) {
        if (file == null || file.directory || file.size > 2L * 1024 * 1024) return false;
        String path = file.path == null ? "" : file.path.toLowerCase();
        return !isBinaryPath(path);
    }

    private boolean isBinaryPath(String path) {
        String[] suffixes = {".apk", ".apks", ".zip", ".rar", ".7z", ".tar", ".gz", ".xz", ".bz2", ".jar", ".dex", ".so", ".db", ".sqlite", ".sqlite3", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".avif", ".bmp", ".ico", ".mp3", ".aac", ".flac", ".wav", ".ogg", ".m4a", ".mp4", ".mkv", ".avi", ".mov", ".webm", ".m2ts", ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".ttf", ".otf", ".woff", ".woff2"};
        for (String suffix : suffixes) if (path.endsWith(suffix)) return true;
        return false;
    }

    private boolean isUtf8Text(byte[] data) {
        if (data == null) return true;
        int controls = 0;
        for (byte value : data) {
            int c = value & 0xff;
            if (c == 0) return false;
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') controls++;
        }
        return data.length == 0 || controls * 100 / data.length < 2;
    }

    private byte[] readBytes(Uri uri, String label) throws Exception {
        long total = querySize(uri);
        try (InputStream input = requireContext().getContentResolver().openInputStream(uri); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) return new byte[0];
            copy(input, output, total, label);
            return output.toByteArray();
        }
    }

    private void copy(InputStream input, OutputStream output, long total, String label) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        long written = 0;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
            written += read;
            if (total > 0) updateProgress(label + " · " + size(written) + " / " + size(total), (int) Math.min(100, written * 100 / total), false);
            else updateProgress(label + " · " + size(written), 0, true);
        }
    }

    private long querySize(Uri uri) {
        String[] projection = {OpenableColumns.SIZE};
        try (Cursor cursor = requireContext().getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private String displayName(Uri uri, String fallback) {
        String[] projection = {OpenableColumns.DISPLAY_NAME};
        try (Cursor cursor = requireContext().getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
                if (!TextUtils.isEmpty(value)) return cleanName(value);
            }
        } catch (Throwable ignored) {
        }
        String last = uri == null ? "" : uri.getLastPathSegment();
        return cleanName(TextUtils.isEmpty(last) ? fallback : last);
    }

    private String documentName(Uri treeUri, String fallback) {
        try {
            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
            return queryDocumentName(docUri, fallback);
        } catch (Throwable e) {
            return fallback;
        }
    }

    private String queryDocumentName(Uri docUri, String fallback) {
        String[] projection = {DocumentsContract.Document.COLUMN_DISPLAY_NAME};
        try (Cursor cursor = requireContext().getContentResolver().query(docUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                if (!TextUtils.isEmpty(value)) return cleanName(value);
            }
        } catch (Throwable ignored) {
        }
        return cleanName(fallback);
    }

    private void collectTree(Uri treeUri, String documentId, String relative, List<FileChange> changes) throws Exception {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = requireContext().getContentResolver().query(children, projection, null, null, null)) {
            if (cursor == null) return;
            int idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext()) {
                String id = cursor.getString(idColumn);
                String name = cleanName(cursor.getString(nameColumn));
                String mime = cursor.getString(mimeColumn);
                String childRelative = joinRemote(relative, name);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    collectTree(treeUri, id, childRelative, changes);
                } else {
                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                    changes.add(new FileChange(joinRemote(currentPath, childRelative), readBytes(docUri, "读取 " + childRelative)));
                }
            }
        }
    }

    private String joinRemote(String parent, String child) {
        String left = parent == null ? "" : parent.replaceAll("^/+", "").replaceAll("/+$", "");
        String right = child == null ? "" : child.replaceAll("^/+", "");
        if (TextUtils.isEmpty(left)) return right;
        if (TextUtils.isEmpty(right)) return left;
        return left + "/" + right;
    }

    private String cleanName(String value) {
        String name = value == null ? "" : value.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        return TextUtils.isEmpty(name) ? "file" : name;
    }

    private String downloadDirName(File dir) {
        if (dir == null) return "";
        if (sameFile(dir, Path.root())) return "共享存储";
        String name = dir.getName();
        return TextUtils.isEmpty(name) ? dir.getAbsolutePath() : name;
    }

    private String displayDownloadDir(File dir) {
        if (dir == null) return "/";
        try {
            File root = Path.root().getCanonicalFile();
            File file = dir.getCanonicalFile();
            String rootPath = root.getPath();
            String filePath = file.getPath();
            if (TextUtils.equals(rootPath, filePath)) return "/";
            if (filePath.startsWith(rootPath + File.separator)) return "/" + filePath.substring(rootPath.length() + 1).replace(File.separatorChar, '/');
            return filePath;
        } catch (Throwable e) {
            return dir.getAbsolutePath();
        }
    }

    private String fileKey(File file) {
        if (file == null) return "";
        try {
            return file.getCanonicalPath();
        } catch (Throwable e) {
            return file.getAbsolutePath();
        }
    }

    private boolean sameFile(File a, File b) {
        return TextUtils.equals(fileKey(a), fileKey(b));
    }

    private List<File> listDownloadDirs(File dir) {
        List<File> dirs = new ArrayList<>();
        if (dir == null) return dirs;
        try {
            File[] files = dir.listFiles(file -> file != null && file.isDirectory() && file.canRead() && !file.getName().startsWith("."));
            if (files == null) return dirs;
            for (File file : files) dirs.add(file);
            dirs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        } catch (Throwable ignored) {
        }
        return dirs;
    }

    private boolean canWriteDirectory(File dir) {
        if (dir == null) return false;
        if (!dir.exists() && !dir.mkdirs()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) return false;
        File probe = new File(dir, ".webhtv-write-test");
        try (FileOutputStream output = new FileOutputStream(Path.create(probe))) {
            output.write(1);
            return true;
        } catch (Throwable e) {
            return false;
        } finally {
            Path.clear(probe);
        }
    }

    private File uniqueDownloadFile(File dir, String name) {
        String clean = cleanDownloadName(name);
        int dot = clean.lastIndexOf('.');
        String base = dot > 0 ? clean.substring(0, dot) : clean;
        String ext = dot > 0 ? clean.substring(dot) : "";
        File target = new File(dir, clean);
        for (int i = 1; target.exists(); i++) target = new File(dir, base + "-" + i + ext);
        return target;
    }

    private String cleanDownloadName(String value) {
        return cleanName(value).replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String parent(String path) {
        if (TextUtils.isEmpty(path) || !path.contains("/")) return "";
        return path.substring(0, path.lastIndexOf('/'));
    }

    private String size(long bytes) {
        if (bytes <= 0) return "0 B";
        return com.fongmi.android.tv.utils.FileUtil.byteCountToDisplaySize(bytes);
    }

    private int percent(int value, int total) {
        return total <= 0 ? 0 : Math.max(0, Math.min(100, value * 100 / total));
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }

    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private interface RefreshVerifier {
        boolean isFresh(List<GitFile> files);
    }

    private static class TextInput {
        final TextInputLayout layout;
        final TextInputEditText edit;

        TextInput(TextInputLayout layout, TextInputEditText edit) {
            this.layout = layout;
            this.edit = edit;
        }
    }

    private static class DialogBinding implements ViewBinding {
        final View root;
        NestedScrollView scroll;
        ProgressBar progress;
        MaterialTextView status;
        MaterialTextView progressText;
        MaterialButton close;
        MaterialButton github;
        MaterialButton cnb;
        MaterialButton accountSummary;
        MaterialButton tokenLink;
        MaterialButton helpLink;
        MaterialButton save;
        MaterialButton refresh;
        MaterialButton searchRemote;
        MaterialButton removeAccount;
        MaterialButton repoBack;
        MaterialButton changeRepo;
        MaterialButton refreshTree;
        MaterialButton repoMine;
        MaterialButton repoFavorite;
        MaterialButton createRepo;
        MaterialButton uploadText;
        MaterialButton uploadFile;
        MaterialButton editFile;
        MaterialButton download;
        MaterialButton deleteFiles;
        MaterialButton backup;
        MaterialButton restore;
        MaterialButton clearCache;
        MaterialTextView accountName;
        MaterialTextView accountBadge;
        MaterialTextView accountMeta;
        MaterialTextView repoTitle;
        MaterialTextView repoBadge;
        MaterialTextView pathText;
        TextInput alias;
        TextInput baseUrl;
        TextInput token;
        TextInput repoSearch;
        LinearLayoutCompat accountCard;
        LinearLayoutCompat loginForm;
        LinearLayoutCompat repoPanel;
        LinearLayoutCompat filePanel;
        LinearLayoutCompat repoList;
        LinearLayoutCompat fileList;

        DialogBinding(View root) {
            this.root = root;
        }

        @NonNull
        @Override
        public View getRoot() {
            return root;
        }
    }
}
