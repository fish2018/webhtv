package com.fongmi.android.tv.api.loader;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;

import java.util.Map;

final class SpiderCleanup {

    private SpiderCleanup() {
    }

    static void destroy(String tag, Map<String, Spider> spiders) {
        spiders.forEach((key, spider) -> {
            try {
                spider.destroy();
            } catch (Throwable e) {
                SpiderDebug.log(tag, "destroy failed key=%s class=%s error=%s:%s", key, spider.getClass().getName(), e.getClass().getSimpleName(), e.getMessage());
                SpiderDebug.log(tag, e);
            }
        });
    }
}
