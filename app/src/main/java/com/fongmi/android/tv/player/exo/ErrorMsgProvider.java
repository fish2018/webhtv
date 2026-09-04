package com.fongmi.android.tv.player.exo;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;

public class ErrorMsgProvider {

    public String get(PlaybackException e) {
        return switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_TIMEOUT -> "Timeout";
            case PlaybackException.ERROR_CODE_UNSPECIFIED -> "Unspecified";
            case PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK -> "Failed Runtime Check";
            case PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "IO Unspecified";
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Bad HTTP Status";
            case PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "Invalid HTTP Content Type";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network Connection Failed";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network Connection Timeout";
            case PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> "Read Position Out Of Range";
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "Manifest Malformed";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "Container Malformed";
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "Manifest Unsupported";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Container Unsupported";
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> getDecoderInitFailed();
            case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "Decoder Query Failed";
            case PlaybackException.ERROR_CODE_DECODING_FAILED -> "Decoding Failed";
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "Decoding Format Unsupported";
            case PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED -> "Decoding Resources Reclaimed";
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "Decoding Format Exceeds Capabilities";
            case PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "Audio Track Init Failed";
            case PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "Audio Track Write Failed";
            case PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED -> "Video Output Init Failed";
            case PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED -> "Video Output Failed";
            case PlaybackException.ERROR_CODE_DRM_UNSPECIFIED -> "DRM Unspecified";
            case PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR -> "DRM System Error";
            case PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR -> "DRM Content Error";
            case PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED -> "DRM Device Revoked";
            case PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> "DRM License Expired";
            case PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED -> "DRM Provisioning Failed";
            case PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION -> "DRM Disallowed Operation";
            case PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> "DRM License Acquisition Failed";
            default -> e.getErrorCodeName();
        };
    }

    private String getDecoderInitFailed() {
        PlaybackAnalyticsListener.Snapshot snapshot = PlaybackAnalyticsListener.getSnapshot();
        Format format = snapshot.errorFormat() != null ? snapshot.errorFormat() : snapshot.videoFormat();
        StringBuilder builder = new StringBuilder(isHardwareDecoder(snapshot.errorDecoderName()) ? "硬解不可用" : "解码器初始化失败");
        if (format != null) appendFormat(builder, format);
        if (!snapshot.errorDecoderName().isEmpty()) builder.append("\nDecoder: ").append(snapshot.errorDecoderName());
        if (!snapshot.errorCause().isEmpty()) builder.append("\n原因: ").append(snapshot.errorCause());
        return builder.toString();
    }

    private void appendFormat(StringBuilder builder, Format format) {
        builder.append("\n").append(MimeTypes.isAudio(format.sampleMimeType) ? "音频: " : "视频: ");
        if (format.sampleMimeType != null) builder.append(format.sampleMimeType);
        if (format.codecs != null) builder.append(" / ").append(format.codecs);
        if (format.width > 0 && format.height > 0) builder.append(" / ").append(format.width).append("x").append(format.height);
        if (format.frameRate > 0) builder.append(" / ").append(Math.round(format.frameRate)).append("fps");
        if (format.bitrate > 0) builder.append(" / ").append(format.bitrate / 1_000_000f).append("Mbps");
        if (format.colorInfo != null) builder.append(" / ").append(format.colorInfo);
    }

    private boolean isHardwareDecoder(String decoderName) {
        if (decoderName == null) return false;
        String name = decoderName.toLowerCase();
        return !name.contains("ffmpeg") && !name.contains("google") && !name.contains("android") && !name.contains("software");
    }
}
