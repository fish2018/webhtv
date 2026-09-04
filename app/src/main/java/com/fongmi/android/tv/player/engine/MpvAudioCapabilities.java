package com.fongmi.android.tv.player.engine;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.audio.AudioCapabilities;

import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

@UnstableApi
final class MpvAudioCapabilities {

    private MpvAudioCapabilities() {
    }

    static String getAudioSpdifCodecs(Context context) {
        Context appContext = context.getApplicationContext();
        AudioManager manager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        AudioCapabilities media3 = AudioCapabilities.getCapabilities(
                appContext, androidx.media3.common.AudioAttributes.DEFAULT, null);
        Set<String> media3Codecs = splitCodecs(getAudioSpdifCodecs(media3::supportsEncoding));
        Set<String> deviceCodecs = getDeviceCodecs(manager);
        Set<String> advertised = new LinkedHashSet<>(media3Codecs);
        advertised.retainAll(deviceCodecs);
        Set<String> carrierCodecs = new LinkedHashSet<>();
        for (String codec : advertised) {
            if (supportsMpvCarrier(codec)) carrierCodecs.add(codec);
        }
        String value = String.join(",", carrierCodecs);
        if (SpiderDebug.isEnabled()) {
            SpiderDebug.log("mpv-audio", "spdif codecs=%s media3=%s devices=%s carrier=%s route=%s",
                    value, media3Codecs, describeDevices(manager), carrierCodecs,
                    hasPassthroughOutputDevice(manager));
        }
        return value;
    }

    static String getAudioSpdifCodecs(IntPredicate supportsEncoding) {
        Set<String> codecs = new LinkedHashSet<>();
        if (supportsEncoding.test(C.ENCODING_AC3)) codecs.add("ac3");
        if (supportsEncoding.test(C.ENCODING_E_AC3)
                || supportsEncoding.test(C.ENCODING_E_AC3_JOC)) codecs.add("eac3");
        boolean dtsHd = supportsEncoding.test(C.ENCODING_DTS_HD)
                || supportsEncoding.test(C.ENCODING_DTS_HD_MA);
        if (supportsEncoding.test(C.ENCODING_DTS) || dtsHd) codecs.add("dts");
        if (dtsHd) codecs.add("dts-hd");
        if (supportsEncoding.test(C.ENCODING_DOLBY_TRUEHD)) codecs.add("truehd");
        return String.join(",", codecs);
    }

    static String getAudioSpdifCodecs(Set<String> advertised,
                                      Predicate<String> carrierSupported) {
        Set<String> codecs = new LinkedHashSet<>();
        addIfSupported(codecs, advertised, carrierSupported, "ac3");
        addIfSupported(codecs, advertised, carrierSupported, "eac3");
        addIfSupported(codecs, advertised, carrierSupported, "dts");
        addIfSupported(codecs, advertised, carrierSupported, "dts-hd");
        addIfSupported(codecs, advertised, carrierSupported, "truehd");
        return String.join(",", codecs);
    }

    private static void addIfSupported(Set<String> result, Set<String> advertised,
                                       Predicate<String> carrierSupported, String codec) {
        if (advertised.contains(codec) && carrierSupported.test(codec)) result.add(codec);
    }

    private static Set<String> getDeviceCodecs(AudioManager manager) {
        Set<String> codecs = new LinkedHashSet<>();
        if (manager == null || !hasPassthroughOutputDevice(manager)) return codecs;
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device == null || !device.isSink() || !isPassthroughOutputType(device.getType())) continue;
            int[] encodings = device.getEncodings();
            if (encodings == null) continue;
            for (int encoding : encodings) addEncodingCodec(codecs, encoding);
        }
        return codecs;
    }

    private static boolean supportsMpvCarrier(String codec) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true;
        for (CarrierFormat carrier : getCarrierFormats(codec, Build.VERSION.SDK_INT)) {
            try {
                AudioFormat format = new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_IEC61937)
                        .setSampleRate(carrier.sampleRate())
                        .setChannelMask(carrier.channelMask())
                        .build();
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build();
                if (!AudioTrack.isDirectPlaybackSupported(format, attributes)) return false;
            } catch (Throwable ignored) {
                return false;
            }
        }
        return true;
    }

    static List<CarrierFormat> getCarrierFormats(String codec, int sdkInt) {
        int sampleRate = "ac3".equals(codec) || "dts".equals(codec) ? 48000 : 192000;
        if ("truehd".equals(codec)) {
            return List.of(new CarrierFormat(sampleRate, AudioFormat.CHANNEL_OUT_7POINT1_SURROUND));
        }
        if ("dts-hd".equals(codec) && sdkInt >= Build.VERSION_CODES.S) {
            return List.of(
                    new CarrierFormat(sampleRate, AudioFormat.CHANNEL_OUT_STEREO),
                    new CarrierFormat(sampleRate, AudioFormat.CHANNEL_OUT_7POINT1_SURROUND));
        }
        return List.of(new CarrierFormat(sampleRate, AudioFormat.CHANNEL_OUT_STEREO));
    }

    private static boolean hasPassthroughOutputDevice(AudioManager manager) {
        if (manager == null) return false;
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device != null && device.isSink() && isPassthroughOutputType(device.getType())) return true;
        }
        return false;
    }

    private static boolean isPassthroughOutputType(int type) {
        return type == AudioDeviceInfo.TYPE_HDMI
                || type == AudioDeviceInfo.TYPE_HDMI_ARC
                || type == AudioDeviceInfo.TYPE_HDMI_EARC
                || type == AudioDeviceInfo.TYPE_USB_DEVICE;
    }

    private static void addEncodingCodec(Set<String> codecs, int encoding) {
        switch (encoding) {
            case AudioFormat.ENCODING_AC3 -> codecs.add("ac3");
            case AudioFormat.ENCODING_E_AC3, AudioFormat.ENCODING_E_AC3_JOC -> codecs.add("eac3");
            case AudioFormat.ENCODING_DTS -> codecs.add("dts");
            case AudioFormat.ENCODING_DTS_HD -> codecs.add("dts-hd");
            case AudioFormat.ENCODING_DOLBY_TRUEHD -> codecs.add("truehd");
            default -> {
            }
        }
    }

    private static Set<String> splitCodecs(String codecs) {
        Set<String> result = new LinkedHashSet<>();
        if (codecs == null || codecs.isEmpty()) return result;
        for (String codec : codecs.split(",")) {
            if (codec != null && !codec.isEmpty()) result.add(codec);
        }
        return result;
    }

    private static String describeDevices(AudioManager manager) {
        if (manager == null) return "";
        List<String> devices = new ArrayList<>();
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device == null || !device.isSink()) continue;
            devices.add(deviceTypeName(device.getType()) + ":" + encodingText(device.getEncodings()));
        }
        return String.join(",", devices);
    }

    private static String encodingText(int[] encodings) {
        if (encodings == null || encodings.length == 0) return "";
        List<String> values = new ArrayList<>();
        for (int encoding : encodings) values.add(String.valueOf(encoding));
        return String.join("/", values);
    }

    private static String deviceTypeName(int type) {
        return switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired_headphones";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired_headset";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bt_a2dp";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt_sco";
            case AudioDeviceInfo.TYPE_HDMI -> "hdmi";
            case AudioDeviceInfo.TYPE_HDMI_ARC -> "hdmi_arc";
            case AudioDeviceInfo.TYPE_HDMI_EARC -> "hdmi_earc";
            case AudioDeviceInfo.TYPE_USB_DEVICE -> "usb_device";
            case AudioDeviceInfo.TYPE_USB_HEADSET -> "usb_headset";
            default -> "type_" + type;
        };
    }

    record CarrierFormat(int sampleRate, int channelMask) {
    }
}
