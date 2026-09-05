package master.flame.danmaku.danmaku.parser.android;

import java.io.InputStream;

public class AndroidFileSource {

    private final InputStream stream;

    public AndroidFileSource(InputStream stream) {
        this.stream = stream;
    }

    public InputStream data() {
        return stream;
    }
}
