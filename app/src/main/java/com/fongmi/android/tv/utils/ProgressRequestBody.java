package com.fongmi.android.tv.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

public class ProgressRequestBody extends RequestBody {

    private static final int SEGMENT_SIZE = 64 * 1024;

    private final Listener listener;
    private final MediaType type;
    private final File file;

    public ProgressRequestBody(File file, MediaType type, Listener listener) {
        this.listener = listener;
        this.type = type;
        this.file = file;
    }

    @Nullable
    @Override
    public MediaType contentType() {
        return type;
    }

    @Override
    public long contentLength() {
        return file.length();
    }

    @Override
    public void writeTo(@NonNull BufferedSink sink) throws IOException {
        long written = 0;
        long total = contentLength();
        try (Source source = Okio.source(new FileInputStream(file))) {
            long read;
            while ((read = source.read(sink.getBuffer(), SEGMENT_SIZE)) != -1) {
                sink.emit();
                written += read;
                if (listener != null) listener.onProgress(written, total);
            }
        }
    }

    public interface Listener {

        void onProgress(long written, long total);
    }
}
