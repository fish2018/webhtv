package com.fongmi.android.tv.service;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.drive.DriveCheckItem;
import com.fongmi.android.tv.bean.drive.DriveCheckResponse;
import com.fongmi.android.tv.bean.drive.DriveCheckResult;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DriveCheckService {

    public static final String STATE_OK = "ok";
    public static final String STATE_BAD = "bad";
    public static final String STATE_LOCKED = "locked";
    public static final String STATE_UNSUPPORTED = "unsupported";
    public static final String STATE_UNCERTAIN = "uncertain";

    private static final String CACHE_KEY = "drive_check_cache";
    private static final int CACHE_LIMIT = 300;
    private static final int BATCH_SIZE = 10;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType FORM = MediaType.parse("application/x-www-form-urlencoded; charset=utf-8");

    private final Map<String, DriveCheckResult> memory;
    private final Map<String, ActiveCall> inflight;
    private final OkHttpClient client;

    private static class Loader {
        static volatile DriveCheckService INSTANCE = new DriveCheckService();
    }

    private static class ActiveCall {
        private boolean done;
        private DriveCheckResult result;
    }

    private static class HttpResult {
        private final int code;
        private final String body;

        private HttpResult(int code, String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }

    public static DriveCheckService get() {
        return Loader.INSTANCE;
    }

    public DriveCheckService() {
        this.memory = new ConcurrentHashMap<>();
        this.inflight = new HashMap<>();
        this.client = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .build();
        loadCache();
    }

    public DriveCheckResponse check(List<DriveCheckItem> items) {
        if (items == null || items.isEmpty()) return new DriveCheckResponse(new ArrayList<>());
        SpiderDebug.log("pan-check", "check count=%s batchSize=%s", items.size(), BATCH_SIZE);
        DriveCheckResult[] results = new DriveCheckResult[items.size()];
        for (int start = 0; start < items.size(); start += BATCH_SIZE) {
            checkBatch(items, results, start, Math.min(start + BATCH_SIZE, items.size()));
        }
        List<DriveCheckResult> list = new ArrayList<>(results.length);
        for (DriveCheckResult result : results) list.add(result == null ? result("", "", "", STATE_UNCERTAIN, false, "检测失败") : result);
        return new DriveCheckResponse(list);
    }

    private void checkBatch(List<DriveCheckItem> items, DriveCheckResult[] results, int start, int end) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, end - start));
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = start; index < end; index++) {
                final int itemIndex = index;
                futures.add(executor.submit(() -> results[itemIndex] = checkOneSafely(items.get(itemIndex))));
            }
            for (Future<?> future : futures) waitFuture(future);
        } finally {
            executor.shutdownNow();
        }
    }

    private void waitFuture(Future<?> future) {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ignored) {
        }
    }

    private DriveCheckResult checkOneSafely(DriveCheckItem item) {
        try {
            return checkOne(item);
        } catch (Throwable e) {
            String diskType = item == null ? "" : item.getDiskType();
            String url = item == null ? "" : item.getUrl();
            return result(diskType, url, "", STATE_UNCERTAIN, false, coalesce(e.getMessage(), "检测失败"));
        }
    }

    private DriveCheckResult checkOne(DriveCheckItem item) {
        if (item == null) return result("", "", "", STATE_UNCERTAIN, false, "检测项为空");
        String diskType = item.getDiskType();
        String normalized = normalizeShareLink(diskType, item.getUrl(), item.getPassword());
        if (diskType.isEmpty() || item.getUrl().isEmpty()) return result(diskType, item.getUrl(), normalized, STATE_UNCERTAIN, false, "type 和 url 不能为空");
        if (normalized.isEmpty()) return result(diskType, item.getUrl(), "", STATE_UNCERTAIN, false, "链接格式无效");

        String key = cacheKey(diskType, normalized);
        DriveCheckResult cached = getCached(key);
        if (cached != null) {
            SpiderDebug.log("pan-check", "cache type=%s state=%s url=%s", diskType, cached.getState(), normalized);
            return cached.cacheHit();
        }

        ActiveCall call;
        synchronized (inflight) {
            call = inflight.get(key);
            if (call == null) {
                call = new ActiveCall();
                inflight.put(key, call);
            } else {
                while (!call.done) {
                    try {
                        inflight.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return result(diskType, item.getUrl(), normalized, STATE_UNCERTAIN, false, "检测被中断");
                    }
                }
                return call.result;
            }
        }

        DriveCheckResult checked;
        boolean cacheable = true;
        try {
            checked = runCheck(item, normalized);
        } catch (Throwable e) {
            cacheable = false;
            checked = result(diskType, item.getUrl(), normalized, STATE_UNCERTAIN, false, coalesce(e.getMessage(), "检测失败"));
        }

        if (cacheable) {
            memory.put(key, checked);
            saveCache();
        }
        SpiderDebug.log("pan-check", "result type=%s state=%s cacheable=%s url=%s summary=%s", diskType, checked.getState(), cacheable, normalized, checked.getSummary());
        synchronized (inflight) {
            call.result = checked;
            call.done = true;
            inflight.remove(key);
            inflight.notifyAll();
        }
        return checked;
    }

    private DriveCheckResult runCheck(DriveCheckItem item, String normalized) throws Exception {
        return switch (item.getDiskType()) {
            case "aliyun" -> checkAliyun(item, normalized);
            case "quark" -> checkQuark(item, normalized);
            case "uc" -> checkUC(item, normalized);
            case "baidu" -> checkBaidu(item, normalized);
            case "tianyi" -> checkTianyi(item, normalized);
            case "123" -> check123(item, normalized);
            case "xunlei" -> checkXunlei(item, normalized);
            case "115" -> check115(item, normalized);
            case "mobile" -> checkMobile(item, normalized);
            default -> result(item, normalized, STATE_UNSUPPORTED, false, "当前平台暂不支持检测");
        };
    }

    private DriveCheckResult checkAliyun(DriveCheckItem item, String normalized) throws Exception {
        String shareId = extractAliyunShareID(normalized);
        if (shareId.isEmpty()) return result(item, normalized, STATE_UNCERTAIN, false, "无法解析分享地址");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("share_id", shareId);
        HttpResult http = request("POST", "https://api.aliyundrive.com/adrive/v3/share_link/get_share_by_anonymous?share_id=" + enc(shareId), App.gson().toJson(payload), headers(
                "Content-Type", "application/json",
                "Origin", "https://www.alipan.com",
                "Referer", "https://www.alipan.com/",
                "x-canary", "client=web,app=share,version=v2.3.1"
        ));
        JsonObject object = json(http.body);
        String code = str(object, "code");
        String message = coalesce(str(object, "message"), code);
        if (!code.isEmpty()) {
            String lower = code.toLowerCase(Locale.ROOT);
            if (lower.contains("sharelink") || containsAny(lower, "notfound", "cancelled", "canceled", "forbidden", "expired")) return result(item, normalized, STATE_BAD, false, message);
            if (containsAny(lower, "exceed", "frequency", "limit")) return result(item, normalized, STATE_UNCERTAIN, false, message);
            return result(item, normalized, STATE_UNCERTAIN, false, message);
        }
        String status = str(object, "share_status").toLowerCase(Locale.ROOT);
        if (!status.isEmpty() && !"enabled".equals(status) && !"normal".equals(status) && containsAny(status, "forbidden", "cancel", "expired", "illegal", "invalid", "disabled")) {
            return result(item, normalized, STATE_BAD, false, coalesce(message, "链接失效"));
        }
        boolean hasName = !str(object, "share_name").isEmpty() || !str(object, "share_title").isEmpty();
        int fileCount = integer(object, "file_count", -1);
        if (http.code == 200 && (hasName || fileCount > 0)) return result(item, normalized, STATE_OK, false, "链接有效");
        if (fileCount == 0 && !hasName) return result(item, normalized, STATE_BAD, false, "分享内容为空");
        if (http.code != 200) return result(item, normalized, STATE_UNCERTAIN, false, coalesce(message, "HTTP状态码: " + http.code));
        return result(item, normalized, STATE_UNCERTAIN, false, message);
    }

    private DriveCheckResult checkQuark(DriveCheckItem item, String normalized) throws Exception {
        String[] info = extractQuarkShareIDAndPassword(normalized);
        String resourceId = info[0];
        String password = coalesce(info[1], item.getPassword());
        if (resourceId.isEmpty()) return result(item, normalized, STATE_UNCERTAIN, false, "无法解析分享地址");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pwd_id", resourceId);
        payload.put("passcode", password);
        payload.put("support_visit_limit_private_share", true);
        HttpResult tokenHttp = request("POST", "https://drive-h.quark.cn/1/clouddrive/share/sharepage/token", App.gson().toJson(payload), headers(
                "Content-Type", "application/json",
                "Origin", "https://pan.quark.cn",
                "Referer", "https://pan.quark.cn/"
        ));
        JsonObject token = json(tokenHttp.body);
        int code = integer(token, "code", 0);
        String message = str(token, "message");
        if (code == 41008) return result(item, normalized, STATE_LOCKED, false, "需要提取码");
        if (code == 41004 || code == 41010 || code == 41011) return result(item, normalized, STATE_BAD, false, "链接失效");
        if (code != 0) {
            String lower = message.toLowerCase(Locale.ROOT);
            if (containsAny(lower, "不存在", "失效", "违规", "过期", "取消")) return result(item, normalized, STATE_BAD, false, message);
            if (containsAny(lower, "提取码", "密码")) return result(item, normalized, STATE_LOCKED, false, message);
            return result(item, normalized, STATE_UNCERTAIN, false, message);
        }

        JsonObject data = obj(token, "data");
        String stoken = str(data, "stoken");
        if (stoken.isEmpty()) return result(item, normalized, STATE_UNCERTAIN, false, "访问令牌缺失");

        String detailUrl = "https://drive-pc.quark.cn/1/clouddrive/share/sharepage/detail?pwd_id=" + enc(resourceId) + "&stoken=" + enc(stoken) + "&ver=2&pr=ucpro";
        HttpResult detailHttp = request("GET", detailUrl, null, headers(
                "Accept", "application/json, text/plain, */*",
                "Origin", "https://pan.quark.cn",
                "Referer", "https://pan.quark.cn/",
                "Cache-Control", "no-cache"
        ));
        JsonObject detail = json(detailHttp.body);
        int detailCode = integer(detail, "code", 0);
        String detailMessage = str(detail, "message");
        if (detailCode != 0) {
            String lower = detailMessage.toLowerCase(Locale.ROOT);
            if (containsAny(lower, "提取码", "密码", "passcode")) return result(item, normalized, STATE_LOCKED, false, coalesce(detailMessage, "需要提取码"));
            if (containsAny(lower, "不存在", "失效", "违规", "过期", "取消")) return result(item, normalized, STATE_BAD, false, coalesce(detailMessage, "链接失效"));
            return result(item, normalized, STATE_UNCERTAIN, false, coalesce(detailMessage, "无法确认链接状态"));
        }

        JsonObject detailData = obj(detail, "data");
        JsonObject share = obj(detailData, "share");
        int size = detailData.has("list") && detailData.get("list").isJsonArray() ? detailData.getAsJsonArray("list").size() : 0;
        int shareStatus = integer(share, "status", 0);
        boolean partialViolation = bool(share, "partial_violation");
        boolean expired = bool(detailData, "is_expire");
        if (size == 0) {
            if (shareStatus > 1 && partialViolation) return result(item, normalized, STATE_BAD, false, "分享链接部分违规已失效");
            if (shareStatus > 1 || expired) return result(item, normalized, STATE_BAD, false, "分享链接已失效");
            return result(item, normalized, STATE_BAD, false, "分享链接无效：文件列表为空");
        }
        if (shareStatus == 3 && partialViolation) return result(item, normalized, STATE_BAD, false, "分享链接因违规已失效");
        if (shareStatus > 1 && shareStatus != 3) return result(item, normalized, STATE_BAD, false, "分享链接已失效");
        return result(item, normalized, STATE_OK, false, partialViolation ? "链接有效但部分文件违规" : "链接有效");
    }

    private DriveCheckResult checkUC(DriveCheckItem item, String normalized) throws Exception {
        HttpResult http = request("GET", normalized, null, headers("User-Agent", mobileUa()));
        if (http.code == 404) return result(item, normalized, STATE_BAD, false, "链接失效");
        String text = http.body.toLowerCase(Locale.ROOT);
        if (containsAny(text, "失效", "不存在", "违规", "删除", "已过期", "被取消")) return result(item, normalized, STATE_BAD, false, "链接失效");
        if (containsAny(text, "提取码", "访问码", "请输入密码")) return result(item, normalized, STATE_LOCKED, false, "需要提取码");
        if (containsAny(text, "文件", "分享", "drive.uc.cn")) return result(item, normalized, STATE_OK, false, "链接有效");
        return result(item, normalized, STATE_UNCERTAIN, false, "无法确认链接状态");
    }

    private DriveCheckResult checkBaidu(DriveCheckItem item, String normalized) throws Exception {
        String[] info = extractBaiduShareInfo(normalized);
        String shareId = info[0];
        String shortUrl = info[1];
        String password = coalesce(info[2], item.getPassword());
        if (shareId.isEmpty() || shortUrl.isEmpty()) return result(item, normalized, STATE_UNCERTAIN, false, "无法解析分享地址");

        String bdclnd = "";
        if (!password.isEmpty()) {
            String verifyUrl = "https://pan.baidu.com/share/verify?surl=" + enc(shortUrl) + "&pwd=" + enc(password);
            HttpResult verify = request("POST", verifyUrl, "pwd=" + enc(password) + "&vcode=&vcode_str=", headers(
                    "Referer", normalized,
                    "Content-Type", "application/x-www-form-urlencoded"
            ));
            JsonObject object = json(verify.body);
            int errno = integer(object, "errno", 0);
            if (errno == 0) bdclnd = str(object, "randsk");
            else if (errno == -9 || errno == -12) return result(item, normalized, STATE_LOCKED, false, "提取码错误或缺失");
            else return result(item, normalized, STATE_UNCERTAIN, false, str(object, "errmsg"));
        }

        Map<String, String> headers = headers(
                "Accept", "application/json, text/plain, */*",
                "Referer", normalized,
                "User-Agent", pcUa()
        );
        if (!bdclnd.isEmpty()) headers.put("Cookie", "BDCLND=" + bdclnd);
        String listUrl = "https://pan.baidu.com/share/list?web=1&page=1&num=20&order=time&desc=1&showempty=0&shorturl=" + enc(shortUrl) + "&root=1&clienttype=0";
        HttpResult list = request("GET", listUrl, null, headers);
        JsonObject object = json(list.body);
        int errno = integer(object, "errno", 0);
        if (errno == 0) {
            int size = object.has("list") && object.get("list").isJsonArray() ? object.getAsJsonArray("list").size() : 0;
            return result(item, normalized, size > 0 ? STATE_OK : STATE_BAD, false, size > 0 ? "链接有效" : "链接失效");
        }
        if (errno == -9 || errno == -12) return result(item, normalized, STATE_LOCKED, false, "需要提取码");
        if (errno == -7 || errno == 105 || errno == 115 || errno == 117 || errno == 145) return result(item, normalized, STATE_BAD, false, "链接失效");
        return result(item, normalized, STATE_UNCERTAIN, false, str(object, "errmsg"));
    }

    private DriveCheckResult checkTianyi(DriveCheckItem item, String normalized) throws Exception {
        String[] info = extractTianyiShareInfo(normalized, item.getPassword());
        String shareCode = info[0];
        String password = info[1];
        String referer = info[2];
        if (shareCode.isEmpty()) return result(item, normalized, STATE_UNCERTAIN, false, "无法解析分享地址");
        if (!password.isEmpty()) shareCode = shareCode + "（访问码：" + password + "）";
        String api = "https://cloud.189.cn/api/open/share/getShareInfoByCodeV2.action?noCache=" + new Random().nextDouble() + "&shareCode=" + enc(shareCode);
        HttpResult http = request("GET", api, null, headers("Referer", referer, "sign-type", "1"));
        String body = http.body;
        String lower = body.toLowerCase(Locale.ROOT);
        if (body.contains("<shareId>") || body.contains("<fileName>") || body.contains("<needAccessCode>1</needAccessCode>")) return result(item, normalized, STATE_OK, false, "链接有效");
        if (containsAny(lower, "erroraccesscode", "needaccesscode", "访问码", "提取码", "密码")) return result(item, normalized, STATE_LOCKED, false, "需要访问码");
        String code = scanTianyiKnownErrorCode(body);
        if (!code.isEmpty()) return result(item, normalized, STATE_BAD, false, mapTianyiErrorMessage(code, ""));
        if (containsAny(lower, "shareinfonotfound", "sharenotfound", "filenotfound", "shareexpirederror", "shareauditnotpass", "foldernotfound", "不存在", "失效", "取消", "过期")) return result(item, normalized, STATE_BAD, false, "链接失效");
        JsonObject object = json(body);
        if (integer(object, "shareId", 0) > 0 || !str(object, "fileName").isEmpty() || integer(object, "needAccessCode", 0) == 1) return result(item, normalized, STATE_OK, false, "链接有效");
        String errorCode = coalesce(str(object, "error_code"), scanTianyiKnownErrorCode(str(object, "res_message")));
        if (!errorCode.isEmpty()) return result(item, normalized, STATE_BAD, false, mapTianyiErrorMessage(errorCode, str(object, "res_message")));
        return result(item, normalized, STATE_UNCERTAIN, false, "无法确认链接状态");
    }

    private DriveCheckResult check123(DriveCheckItem item, String normalized) throws Exception {
        String shareKey = extract123ShareKey(normalized);
        if (shareKey.isEmpty()) return result(item, normalized, STATE_UNCERTAIN, false, "无法解析分享地址");
        HttpResult http = request("GET", "https://www.123pan.com/api/share/info?shareKey=" + enc(shareKey), null, null);
        if (http.code == 403) return result(item, normalized, STATE_OK, false, "链接有效");
        JsonObject object = json(http.body);
        int code = integer(object, "code", -1);
        if (code == 0) return result(item, normalized, STATE_OK, false, "链接有效");
        if (bool(obj(object, "data"), "HasPwd")) return result(item, normalized, STATE_LOCKED, false, "需要提取码");
        return result(item, normalized, STATE_BAD, false, coalesce(str(object, "message"), "链接失效"));
    }

    private DriveCheckResult checkXunlei(DriveCheckItem item, String normalized) throws Exception {
        String[] info = extractXunleiShareInfo(normalized);
        String shareId = info[0];
        String password = coalesce(info[1], item.getPassword());
        if (shareId.isEmpty()) return result(item, normalized, STATE_UNCERTAIN, false, "无法解析分享地址");
        String captcha = fetchXunleiCaptchaToken();
        String api = "https://api-pan.xunlei.com/drive/v1/share?share_id=" + enc(shareId) + "&pass_code=" + enc(password) + "&limit=100&pass_code_token=&page_token=&thumbnail_size=SIZE_SMALL";
        Map<String, String> headers = headers(
                "Accept", "*/*",
                "Content-Type", "application/json",
                "Origin", "https://pan.xunlei.com",
                "Referer", "https://pan.xunlei.com/",
                "User-Agent", pcUa(),
                "Accept-Encoding", "gzip, deflate",
                "x-client-id", "ZUBzD9J_XPXfn7f7",
                "x-device-id", "5505bd0cab8c9469b98e5891d9fb3e0d"
        );
        if (!captcha.isEmpty()) headers.put("x-captcha-token", captcha);
        HttpResult http = request("GET", api, null, headers);
        if (http.code == 404 || http.code == 403) return result(item, normalized, STATE_BAD, false, "链接失效");
        JsonObject object = json(http.body);
        String status = str(object, "share_status");
        String statusText = str(object, "share_status_text");
        String error = str(object, "error");
        String errorMsg = str(object, "error_description");
        if ("OK".equals(status) || !str(object, "share_id").isEmpty() || !str(object, "share_name").isEmpty() || integer(object, "file_count", 0) > 0) return result(item, normalized, STATE_OK, false, "链接有效");
        if (containsAny(error.toLowerCase(Locale.ROOT), "pass_code") || containsAny(errorMsg.toLowerCase(Locale.ROOT), "pass_code", "提取码", "密码") || containsAny(statusText.toLowerCase(Locale.ROOT), "pass_code", "提取码", "密码")) {
            return result(item, normalized, STATE_LOCKED, false, coalesce(errorMsg, statusText, "需要提取码"));
        }
        String message = coalesce(statusText, errorMsg, error);
        if (!status.isEmpty() && !"OK".equals(status)) return result(item, normalized, STATE_BAD, false, coalesce(message, "分享状态: " + status));
        if (!message.isEmpty() && containsAny(message.toLowerCase(Locale.ROOT), "参数错误", "share_status", "不存在", "失效", "过期", "not found")) return result(item, normalized, STATE_BAD, false, message);
        return result(item, normalized, STATE_UNCERTAIN, false, coalesce(message, "无法确认链接状态"));
    }

    private DriveCheckResult check115(DriveCheckItem item, String normalized) throws Exception {
        String[] info = extract115ShareInfo(normalized, item.getPassword());
        String shareCode = info[0];
        String password = info[1];
        if (shareCode.isEmpty()) return result(item, normalized, STATE_UNCERTAIN, false, "无法解析分享地址");
        if (password.isEmpty()) return result(item, normalized, STATE_LOCKED, false, "115 需要提取码");
        String api = "https://115cdn.com/webapi/share/snap?share_code=" + enc(shareCode) + "&offset=0&limit=20&receive_code=" + enc(password) + "&cid=";
        HttpResult http = request("GET", api, null, headers(
                "Referer", "https://115cdn.com/s/" + shareCode + "?password=" + password + "&",
                "X-Requested-With", "XMLHttpRequest"
        ));
        JsonObject object = json(http.body);
        boolean state = bool(object, "state");
        int errno = integer(object, "errno", -1);
        JsonObject data = obj(object, "data");
        JsonObject shareInfo = obj(data, "shareinfo");
        if (state && errno == 0) {
            String shareState = classify115ShareState(data);
            String summary = STATE_OK.equals(shareState) ? "链接有效" : coalesce(str(shareInfo, "forbid_reason"), "链接状态异常");
            return result(item, normalized, shareState, false, summary);
        }
        String error = str(object, "error");
        if (containsAny(error.toLowerCase(Locale.ROOT), "密码", "提取码", "receive_code")) return result(item, normalized, STATE_LOCKED, false, coalesce(error, "需要提取码"));
        if (containsAny(error.toLowerCase(Locale.ROOT), "参数错误", "不存在", "失效", "share_code", "forbid", "forbidden", "违规", "删除", "取消")) return result(item, normalized, STATE_BAD, false, coalesce(error, "链接失效"));
        return result(item, normalized, error.isEmpty() ? STATE_UNCERTAIN : STATE_BAD, false, coalesce(error, "无法确认链接状态"));
    }

    static String classify115ShareState(JsonObject data) {
        JsonObject shareInfo = obj(data, "shareinfo");
        int shareState = integer(data, "share_state", integer(shareInfo, "share_state", 0));
        String reason = str(shareInfo, "forbid_reason");
        // Expired or forbidden shares may still include a file list and share metadata.
        if (shareState == 7) return STATE_BAD;
        if (!reason.isEmpty()) return containsAny(reason.toLowerCase(Locale.ROOT), "密码", "提取码") ? STATE_LOCKED : STATE_BAD;
        if (shareState == 1) return STATE_OK;
        int size = data.has("list") && data.get("list").isJsonArray() ? data.getAsJsonArray("list").size() : 0;
        if (size > 0 || integer(data, "count", 0) > 0 || !str(shareInfo, "snap_id").isEmpty() || !str(shareInfo, "share_title").isEmpty()) return STATE_OK;
        return STATE_BAD;
    }

    private DriveCheckResult checkMobile(DriveCheckItem item, String normalized) throws Exception {
        String shareId = extractMobileShareID(normalized);
        if (shareId.isEmpty()) return result(item, normalized, STATE_UNCERTAIN, false, "无法解析分享地址");
        Map<String, Object> outLink = new LinkedHashMap<>();
        outLink.put("account", "");
        outLink.put("linkID", shareId);
        outLink.put("passwd", item.getPassword());
        outLink.put("caSrt", 1);
        outLink.put("coSrt", 1);
        outLink.put("srtDr", 0);
        outLink.put("bNum", 1);
        outLink.put("pCaID", "root");
        outLink.put("eNum", 200);
        Map<String, Object> common = new LinkedHashMap<>();
        common.put("account", "");
        common.put("accountType", 1);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("getOutLinkInfoReq", outLink);
        payload.put("commonAccountInfo", common);
        String encrypted = DriveMobileCrypto.encrypt(App.gson().toJson(payload));
        HttpResult http = request("POST", "https://share-kd-njs.yun.139.com/yun-share/richlifeApp/devapp/IOutLink/getOutLinkInfoV6", App.gson().toJson(encrypted), headers(
                "Accept", "application/json, text/plain, */*",
                "Content-Type", "application/json",
                "hcy-cool-flag", "1",
                "x-deviceinfo", "||3|12.27.0|chrome|131.0.0.0|5c7c68368f048245e1ce47f1c0f8f2d0||windows 10|1536X695|zh-CN|||"
        ));
        JsonObject object = json(DriveMobileCrypto.decrypt(http.body));
        String code = str(object, "resultCode");
        String desc = str(object, "desc");
        if ("0".equals(code) && object.has("data") && !object.get("data").isJsonNull()) return result(item, normalized, STATE_OK, false, "链接有效");
        String lower = desc.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "提取码", "密码", "访问码")) return result(item, normalized, STATE_LOCKED, false, coalesce(desc, "需要提取码"));
        if (!desc.isEmpty() && containsAny(lower, "失效", "不存在", "过期", "取消")) return result(item, normalized, STATE_BAD, false, desc);
        if (!desc.isEmpty()) return result(item, normalized, STATE_UNCERTAIN, false, desc);
        return result(item, normalized, code.isEmpty() ? STATE_UNCERTAIN : STATE_BAD, false, code.isEmpty() ? "无法确认链接状态" : "错误码: " + code);
    }

    private String fetchXunleiCaptchaToken() {
        try {
            String deviceId = "5505bd0cab8c9469b98e5891d9fb3e0d";
            String clientId = "ZUBzD9J_XPXfn7f7";
            String clientVersion = "1.10.0.2633";
            String packageName = "com.xunlei.browser";
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = "1." + xunleiSignature(clientId + clientVersion + packageName + deviceId + timestamp);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("timestamp", timestamp);
            meta.put("captcha_sign", signature);
            meta.put("client_version", clientVersion);
            meta.put("package_name", packageName);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("action", "get:/drive/v1/share");
            payload.put("captcha_token", "");
            payload.put("client_id", clientId);
            payload.put("device_id", deviceId);
            payload.put("meta", meta);
            payload.put("redirect_uri", "xlaccsdk01://xunlei.com/callback?state=harbor");
            HttpResult http = request("POST", "https://xluser-ssl.xunlei.com/v1/shield/captcha/init", App.gson().toJson(payload), headers(
                    "Accept", "application/json;charset=UTF-8",
                    "Content-Type", "application/json",
                    "x-device-id", deviceId,
                    "x-client-id", clientId,
                    "x-client-version", clientVersion
            ));
            JsonObject object = json(http.body);
            return str(object, "captcha_token");
        } catch (Throwable e) {
            return "";
        }
    }

    private HttpResult request(String method, String url, String body, Map<String, String> headers) throws Exception {
        Map<String, String> nextHeaders = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
        if (!hasHeader(nextHeaders, "User-Agent")) nextHeaders.put("User-Agent", pcUa());
        if (!hasHeader(nextHeaders, "Accept-Encoding")) nextHeaders.put("Accept-Encoding", "gzip");
        RequestBody requestBody = null;
        String verb = TextUtils.isEmpty(method) ? "GET" : method.toUpperCase(Locale.ROOT);
        if (!"GET".equals(verb) && !"HEAD".equals(verb)) {
            String contentType = getHeader(nextHeaders, "Content-Type");
            requestBody = RequestBody.create(body == null ? "" : body, contentType != null && contentType.startsWith("application/x-www-form-urlencoded") ? FORM : JSON);
        }
        Request request = new Request.Builder().url(url).headers(Headers.of(nextHeaders)).method(verb, requestBody).build();
        long start = System.currentTimeMillis();
        SpiderDebug.log("pan-check-net", "%s %s", verb, url);
        try (Response response = client.newCall(request).execute()) {
            byte[] raw = response.body() == null ? new byte[0] : readAll(response.body().byteStream());
            byte[] decoded = decode(response.header("Content-Encoding"), raw);
            SpiderDebug.log("pan-check-net", "%s %s -> %s raw=%s decoded=%s in %sms", verb, url, response.code(), raw.length, decoded.length, System.currentTimeMillis() - start);
            return new HttpResult(response.code(), new String(decoded, StandardCharsets.UTF_8));
        }
    }

    static String cacheKey(String diskType, String normalized) {
        // Do not reuse 115 results cached before explicit share-state checks took priority.
        return ("115".equals(diskType) ? "115:v2" : diskType) + "|" + normalized;
    }

    private DriveCheckResult getCached(String key) {
        DriveCheckResult result = memory.get(key);
        if (result == null) return null;
        DriveCheckResult capped = capCacheResult(result);
        if (isCacheValid(capped)) {
            if (capped != result) memory.put(key, capped);
            return capped;
        }
        memory.remove(key);
        saveCache();
        return null;
    }

    private void loadCache() {
        try {
            Type type = new TypeToken<Map<String, DriveCheckResult>>() {}.getType();
            Map<String, DriveCheckResult> cache = App.gson().fromJson(Prefers.getString(CACHE_KEY), type);
            if (cache == null) return;
            for (Map.Entry<String, DriveCheckResult> entry : cache.entrySet()) {
                DriveCheckResult result = capCacheResult(entry.getValue());
                if (isCacheValid(result)) memory.put(entry.getKey(), result);
            }
        } catch (Throwable ignored) {
        }
    }

    private synchronized void saveCache() {
        try {
            List<Map.Entry<String, DriveCheckResult>> entries = new ArrayList<>(memory.entrySet());
            entries.sort((a, b) -> Long.compare(b.getValue().getCheckedAt(), a.getValue().getCheckedAt()));
            Map<String, DriveCheckResult> next = new LinkedHashMap<>();
            for (Map.Entry<String, DriveCheckResult> entry : entries) {
                if (next.size() >= CACHE_LIMIT) break;
                DriveCheckResult result = capCacheResult(entry.getValue());
                if (isCacheValid(result)) next.put(entry.getKey(), result);
            }
            Prefers.put(CACHE_KEY, App.gson().toJson(next));
        } catch (Throwable ignored) {
        }
    }

    private DriveCheckResult capCacheResult(DriveCheckResult result) {
        if (result == null) return null;
        long checkedAt = result.getCheckedAt();
        if (checkedAt <= 0) return result;
        long cappedExpiresAt = Math.min(result.getExpiresAt(), checkedAt + ttl(result.getState()));
        if (cappedExpiresAt == result.getExpiresAt()) return result;
        return new DriveCheckResult(result.getDiskType(), result.getUrl(), result.getNormalizedUrl(), result.getState(), result.isCacheHit(), checkedAt, cappedExpiresAt, result.getSummary());
    }

    private boolean isCacheValid(DriveCheckResult result) {
        return result != null && System.currentTimeMillis() <= result.getExpiresAt();
    }

    private DriveCheckResult result(DriveCheckItem item, String normalized, String state, boolean cacheHit, String summary) {
        return result(item.getDiskType(), item.getUrl(), normalized, state, cacheHit, summary);
    }

    private DriveCheckResult result(String diskType, String url, String normalized, String state, boolean cacheHit, String summary) {
        long now = System.currentTimeMillis();
        return new DriveCheckResult(diskType, url, normalized, state, cacheHit, now, now + ttl(state), summary);
    }

    private long ttl(String state) {
        return switch (state) {
            case STATE_OK -> TimeUnit.HOURS.toMillis(1);
            case STATE_UNSUPPORTED -> TimeUnit.HOURS.toMillis(24);
            case STATE_BAD -> TimeUnit.HOURS.toMillis(6);
            case STATE_LOCKED -> TimeUnit.HOURS.toMillis(12);
            default -> TimeUnit.MINUTES.toMillis(30);
        };
    }

    private String normalizeShareLink(String diskType, String rawUrl, String password) {
        String base = rawUrl == null ? "" : rawUrl.trim();
        if (base.isEmpty()) return "";
        String[] parts = base.split("#", 2);
        String noFragment = parts[0];
        int queryIndex = noFragment.indexOf('?');
        String path = queryIndex >= 0 ? noFragment.substring(0, queryIndex) : noFragment;
        String query = queryIndex >= 0 ? noFragment.substring(queryIndex + 1) : "";
        if (!password.isEmpty() && ("baidu".equals(diskType) || "quark".equals(diskType) || "uc".equals(diskType)) && !query.toLowerCase(Locale.ROOT).contains("pwd=")) {
            query = query.isEmpty() ? "pwd=" + enc(password) : query + "&pwd=" + enc(password);
        }
        return query.isEmpty() ? path : path + "?" + query;
    }

    private static byte[] decode(String encoding, byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) return bytes;
        if (hasEncoding(encoding, "gzip") || isGzip(bytes)) return gunzip(bytes);
        if (hasEncoding(encoding, "deflate")) return inflate(bytes);
        return bytes;
    }

    private static byte[] gunzip(byte[] bytes) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return readAll(gzip);
        }
    }

    private static byte[] inflate(byte[] bytes) throws Exception {
        try {
            return inflate(bytes, true);
        } catch (Throwable e) {
            return inflate(bytes, false);
        }
    }

    private static byte[] inflate(byte[] bytes, boolean nowrap) throws Exception {
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(bytes), new Inflater(nowrap))) {
            return readAll(input);
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream stream = input) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static boolean hasEncoding(String encoding, String value) {
        if (TextUtils.isEmpty(encoding)) return false;
        for (String item : encoding.split(",")) if (value.equalsIgnoreCase(item.trim())) return true;
        return false;
    }

    private static boolean isGzip(byte[] bytes) {
        return bytes.length > 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b;
    }

    private static JsonObject json(String text) {
        try {
            return App.gson().fromJson(text, JsonObject.class);
        } catch (Throwable e) {
            return new JsonObject();
        }
    }

    private static JsonObject obj(JsonObject object, String key) {
        try {
            return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : new JsonObject();
        } catch (Throwable e) {
            return new JsonObject();
        }
    }

    private static String str(JsonObject object, String key) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString().trim() : "";
        } catch (Throwable e) {
            return "";
        }
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : fallback;
        } catch (Throwable e) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject object, String key) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() && object.get(key).getAsBoolean();
        } catch (Throwable e) {
            return false;
        }
    }

    private static Map<String, String> headers(String... values) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) headers.put(values[i], values[i + 1]);
        return headers;
    }

    private static boolean hasHeader(Map<String, String> headers, String name) {
        return getHeader(headers, name) != null;
    }

    private static String getHeader(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) if (name.equalsIgnoreCase(entry.getKey())) return entry.getValue();
        return null;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String decodeParam(String value) {
        try {
            return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return value == null ? "" : value;
        }
    }

    private static String coalesce(String... values) {
        for (String value : values) if (!TextUtils.isEmpty(value == null ? "" : value.trim())) return value.trim();
        return "";
    }

    private static boolean containsAny(String content, String... keywords) {
        if (content == null) return false;
        for (String keyword : keywords) if (content.contains(keyword.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static String pcUa() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    }

    private static String mobileUa() {
        return "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    }

    private static String extractAliyunShareID(String rawUrl) {
        String path = path(rawUrl);
        String[] parts = path.split("/");
        return parts.length == 0 ? "" : parts[parts.length - 1];
    }

    private static String[] extractQuarkShareIDAndPassword(String rawUrl) {
        Matcher matcher = Pattern.compile("/s/([A-Za-z0-9]+)").matcher(rawUrl);
        return new String[]{matcher.find() ? matcher.group(1) : "", query(rawUrl, "pwd")};
    }

    private static String[] extractBaiduShareInfo(String rawUrl) {
        String path = path(rawUrl);
        String pwd = query(rawUrl, "pwd");
        if (path.startsWith("/s/")) {
            String shareId = path.substring(3);
            String shortUrl = shareId.startsWith("1") && shareId.length() > 1 ? shareId.substring(1) : shareId;
            return new String[]{shareId, shortUrl, pwd};
        }
        if (path.startsWith("/share/init")) {
            String shareId = query(rawUrl, "surl");
            String shortUrl = shareId.startsWith("1") && shareId.length() > 1 ? shareId.substring(1) : shareId;
            return new String[]{shareId, shortUrl, pwd};
        }
        return new String[]{"", "", pwd};
    }

    private static String[] extractTianyiShareInfo(String rawUrl, String fallbackPassword) {
        String shareCode = query(rawUrl, "code");
        String path = path(rawUrl);
        if (shareCode.isEmpty() && path.startsWith("/t/")) shareCode = path.substring(3);
        if (shareCode.contains("/")) shareCode = shareCode.substring(0, shareCode.indexOf('/'));
        String password = fallbackPassword;
        Matcher matcher = Pattern.compile("（访问码[：:]\\s*([a-zA-Z0-9]+)）").matcher(rawUrl);
        if (matcher.find()) password = matcher.group(1);
        return new String[]{shareCode, password, rawUrl};
    }

    private static String extract123ShareKey(String rawUrl) {
        String[] patterns = {
                "https?://(?:www\\.)?(?:123684|123685|123912|123pan|123592|123865)\\.com/s/([a-zA-Z0-9-]+)",
                "https?://(?:www\\.)?123pan\\.cn/s/([a-zA-Z0-9-]+)"
        };
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(rawUrl);
            if (matcher.find()) return matcher.group(1);
        }
        String path = path(rawUrl);
        String[] parts = path.split("/");
        return parts.length == 0 ? "" : parts[parts.length - 1];
    }

    private static String[] extractXunleiShareInfo(String rawUrl) {
        Matcher matcher = Pattern.compile("pan\\.xunlei\\.com/s/([^?/#]+)").matcher(rawUrl);
        return new String[]{matcher.find() ? matcher.group(1) : "", query(rawUrl, "pwd")};
    }

    private static String[] extract115ShareInfo(String rawUrl, String fallbackPassword) {
        String path = path(rawUrl);
        String[] parts = path.split("/");
        String shareCode = parts.length == 0 ? "" : parts[parts.length - 1];
        String password = coalesce(query(rawUrl, "password"), fallbackPassword);
        return new String[]{shareCode, password};
    }

    private static String extractMobileShareID(String rawUrl) {
        String[] patterns = {
                "https?://(?:www\\.)?yun\\.139\\.com/shareweb/#/w/i/([^&/?#]+)",
                "https?://(?:www\\.)?caiyun\\.139\\.com/w/i/([^&/?#]+)",
                "https?://(?:www\\.)?caiyun\\.139\\.com/m/i\\?([^&/?#]+)",
                "https?://caiyun\\.feixin\\.10086\\.cn/([^&/?#]+)"
        };
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(rawUrl);
            if (matcher.find()) return matcher.group(1);
        }
        return "";
    }

    private static String path(String rawUrl) {
        try {
            String text = rawUrl.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://[^/]+", "");
            int queryIndex = text.indexOf('?');
            int fragmentIndex = text.indexOf('#');
            int end = text.length();
            if (queryIndex >= 0) end = Math.min(end, queryIndex);
            if (fragmentIndex >= 0) end = Math.min(end, fragmentIndex);
            return text.substring(0, end);
        } catch (Throwable e) {
            return "";
        }
    }

    private static String query(String rawUrl, String key) {
        try {
            int queryIndex = rawUrl.indexOf('?');
            if (queryIndex < 0) return "";
            int end = rawUrl.indexOf('#', queryIndex);
            String query = rawUrl.substring(queryIndex + 1, end >= 0 ? end : rawUrl.length());
            for (String part : query.split("&")) {
                String[] pair = part.split("=", 2);
                if (pair.length > 0 && key.equals(pair[0])) return pair.length > 1 ? decodeParam(pair[1]) : "";
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String scanTianyiKnownErrorCode(String content) {
        for (String code : new String[]{"ShareInfoNotFound", "ShareNotFound", "FileNotFound", "ShareExpiredError", "ShareAuditNotPass", "FolderNotFound"}) {
            if (content != null && content.contains(code)) return code;
        }
        return "";
    }

    private static String mapTianyiErrorMessage(String code, String fallback) {
        return switch (code == null ? "" : code.trim()) {
            case "ShareInfoNotFound" -> "分享信息不存在";
            case "ShareNotFound" -> "分享链接不存在";
            case "FileNotFound" -> "分享文件不存在";
            case "ShareExpiredError" -> "分享链接已过期";
            case "ShareAuditNotPass" -> "分享因审核未通过已失效";
            case "FolderNotFound" -> "分享文件夹不存在";
            default -> coalesce(fallback, code);
        };
    }

    private static String xunleiSignature(String content) throws Exception {
        String[] parts = {
                "uWRwO7gPfdPB/0NfPtfQO+71",
                "F93x+qPluYy6jdgNpq+lwdH1ap6WOM+nfz8/V",
                "0HbpxvpXFsBK5CoTKam",
                "dQhzbhzFRcawnsZqRETT9AuPAJ+wTQso82mRv",
                "SAH98AmLZLRa6DB2u68sGhyiDh15guJpXhBzI",
                "unqfo7Z64Rie9RNHMOB",
                "7yxUdFADp3DOBvXdz0DPuKNVT35wqa5z0DEyEvf",
                "RBG",
                "ThTWPG5eC0UBqlbQ+04nZAptqGCdpv9o55A"
        };
        String value = content;
        for (String part : parts) value = md5(value + part);
        return value;
    }

    private static String md5(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte item : bytes) builder.append(String.format("%02x", item));
        return builder.toString();
    }
}
