package com.fongmi.android.tv.ui.dialog;

final class DanmakuSearchIntent {

    private Object currentRequest;
    private String submittedKeyword = "";
    private String resultKeyword = "";

    synchronized void begin(Object request, String keyword) {
        currentRequest = request;
        submittedKeyword = clean(keyword);
        resultKeyword = "";
    }

    synchronized boolean complete(Object request) {
        if (request == null || request != currentRequest) return false;
        resultKeyword = submittedKeyword;
        return true;
    }

    synchronized boolean isCurrent(Object request) {
        return request != null && request == currentRequest;
    }

    synchronized void cancel() {
        currentRequest = null;
        submittedKeyword = "";
        resultKeyword = "";
    }

    synchronized String getResultKeyword() {
        return resultKeyword;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
