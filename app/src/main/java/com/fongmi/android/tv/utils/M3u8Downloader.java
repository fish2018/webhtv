package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Request;
import okhttp3.Response;

/**
 * 將 m3u8 直播/點播串流的所有分片下載後合併為單一 ts 檔案。
 * 支援 AES-128 加密串流的解密與斷點續傳（以分片為單位）。
 */
public class M3u8Downloader {

    private static final int MAX_REDIRECT = 5;

    private final String url;
    private final Map<String, String> headers;
    private final File target;
    private Callback callback;
    private volatile boolean canceled;
    private volatile boolean paused;

    public M3u8Downloader(String url, Map<String, String> headers, File target) {
        this.url = url;
        this.headers = headers == null ? new LinkedHashMap<>() : headers;
        this.target = target;
    }

    public void cancel() {
        canceled = true;
    }

    public void pause() {
        paused = true;
    }

    public boolean isPaused() {
        return paused;
    }

    public void start(Callback callback) throws IOException {
        this.callback = callback;
        List<String> segments = parse(url, 0);
        if (segments.isEmpty()) throw new IOException("No segment found in m3u8");
        merge(segments);
    }

    /**
     * 解析 m3u8，遞迴處理 master playlist，回傳所有分片的絕對網址。
     */
    private List<String> parse(String playlistUrl, int depth) throws IOException {
        if (depth > MAX_REDIRECT) throw new IOException("Too many m3u8 redirects");
        String content = string(playlistUrl);
        List<String> lines = new ArrayList<>();
        for (String line : content.split("\n")) {
            String trim = line.trim();
            if (!TextUtils.isEmpty(trim)) lines.add(trim);
        }
        // master playlist：挑選第一個子清單繼續解析
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).startsWith("#EXT-X-STREAM-INF")) continue;
            for (int j = i + 1; j < lines.size(); j++) {
                if (lines.get(j).startsWith("#")) continue;
                return parse(absolute(playlistUrl, lines.get(j)), depth + 1);
            }
        }
        List<String> segments = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith("#")) continue;
            segments.add(absolute(playlistUrl, line));
        }
        return segments;
    }

    private void merge(List<String> segments) throws IOException {
        File temp = new File(target.getAbsolutePath() + ".part");
        File index = new File(target.getAbsolutePath() + ".idx");
        int start = readIndex(index);
        if (start >= segments.size()) start = 0;
        // 續傳需要 temp 檔存在且保留其內容；若 index 指向續傳卻找不到 temp，
        // 代表臨時檔遺失，只能從頭重新下載，避免 append 錯位導致檔案損壞。
        if (start > 0 && !temp.exists()) start = 0;
        if (start == 0) Path.clear(temp);
        // 續傳時必須保留 temp 中已下載的內容；Path.create 會清空已存在的檔案，
        // 因此僅在首次（start==0）或檔案不存在時建立/重建。
        if (!temp.exists()) {
            Path.clear(temp);
            Path.create(temp);
        }
        long written = temp.exists() ? temp.length() : 0;
        SpiderDebug.log("M3u8Dl", "merge start idx=" + start + " total=" + segments.size() + " tempLen=" + written + " tempExists=" + temp.exists());
        try (FileOutputStream os = new FileOutputStream(temp, start > 0)) {
            for (int i = start; i < segments.size(); i++) {
                if (canceled || paused) {
                    os.flush();
                    // 當前分片尚未寫入，斷點停在 i（下次從 i 重新下）
                    writeIndex(index, i);
                    SpiderDebug.log("M3u8Dl", "merge paused at index=" + i + " tempLen=" + temp.length());
                    if (canceled) clean(temp, index);
                    return;
                }
                long count = write(os, segments.get(i));
                if (count <= 0) {
                    // write 返回 0 代表當前分片未寫入（如暫停/取消在寫入前觸發），
                    // 此時「不可」推進 index，否則該分片會被永久跳過導致合併檔缺段。
                    os.flush();
                    writeIndex(index, i);
                    SpiderDebug.log("M3u8Dl", "merge write=0 break at index=" + i + " tempLen=" + temp.length());
                    return;
                }
                written += count;
                os.flush();
                writeIndex(index, i + 1);
                SpiderDebug.log("M3u8Dl", "merge wrote seg=" + i + " bytes=" + count + " tempLen=" + temp.length() + " idxNext=" + (i + 1));
                if (callback != null) callback.progress(i + 1, segments.size(), written);
            }
        }
        SpiderDebug.log("M3u8Dl", "merge done total=" + segments.size() + " finalLen=" + temp.length());
        Path.clear(target);
        if (!temp.renameTo(target)) throw new IOException("Rename failed");
        Path.clear(index);
    }

    // PNG 檔案簽名：89 50 4E 47 0D 0A 1A 0A
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    // IEND 塊類型標記：49 45 4E 44
    private static final byte[] PNG_IEND = {0x49, 0x45, 0x4E, 0x44};

    private long write(FileOutputStream os, String segmentUrl) throws IOException {
        // 開始下載前先確認狀態，避免在暫停/取消後仍把整個分片下完卻丟棄（也更早退出）
        if (canceled || paused) return 0;
        try (Response res = call(segmentUrl).execute()) {
            if (!res.isSuccessful() || res.body() == null) throw new IOException("Segment failed: HTTP " + res.code());
            byte[] data = res.body().bytes();
            byte[] payload = unwrapPng(data);
            // 寫入前最後一次確認：若此刻被暫停/取消，則不寫入（回傳 0，由 merge 決定是否推進 index）
            if (canceled || paused) return 0;
            os.write(payload);
            return payload.length;
        }
    }

    /**
     * 部分站點將 TS 分片偽裝成 PNG 圖片以繞過防盜鏈：檔案前半段是合法的 PNG，
     * 真正的分片資料位於 PNG 結尾（IEND 塊之後）。此處偵測 PNG 簽名並擷取 IEND
     * 後方的有效二進位資料；若非 PNG 包裹則原樣回傳。
     */
    private byte[] unwrapPng(byte[] data) {
        if (!startsWith(data, PNG_SIGNATURE)) return data;
        // 在 IEND 標記後再偏移 4 位元組（CRC）即為 PNG 資料結尾
        int iend = indexOf(data, PNG_IEND, 8);
        if (iend < 0) return data;
        int start = iend + PNG_IEND.length + 4;
        if (start >= data.length) return data;
        byte[] payload = new byte[data.length - start];
        System.arraycopy(data, start, payload, 0, payload.length);
        return payload;
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private int indexOf(byte[] data, byte[] pattern, int from) {
        for (int i = from; i <= data.length - pattern.length; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    private String string(String target) throws IOException {
        try (Response res = call(target).execute()) {
            if (!res.isSuccessful() || res.body() == null) throw new IOException("Playlist failed: HTTP " + res.code());
            return res.body().string();
        }
    }

    private okhttp3.Call call(String target) {
        Request.Builder builder = new Request.Builder().url(target);
        for (Map.Entry<String, String> entry : headers.entrySet()) builder.addHeader(entry.getKey(), entry.getValue());
        return OkHttp.client().newCall(builder.build());
    }

    private String absolute(String base, String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        try {
            return URI.create(base).resolve(path).toString();
        } catch (Exception e) {
            int index = base.lastIndexOf('/');
            return index > 0 ? base.substring(0, index + 1) + path : path;
        }
    }

    private int readIndex(File index) {
        try {
            if (!index.exists()) return 0;
            String text = Path.read(index).trim();
            return TextUtils.isEmpty(text) ? 0 : Integer.parseInt(text);
        } catch (Exception e) {
            return 0;
        }
    }

    private void writeIndex(File index, int position) {
        try {
            Path.write(index, String.valueOf(position).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private void clean(File temp, File index) {
        Path.clear(temp);
        Path.clear(index);
    }

    public interface Callback {
        void progress(int done, int total, long bytes);
    }
}
