package master.flame.danmaku.danmaku.loader;

import java.io.InputStream;

import master.flame.danmaku.danmaku.parser.android.AndroidFileSource;

public interface ILoader {
    void load(String url);

    void load(InputStream stream);

    AndroidFileSource getDataSource();
}
