package com.fongmi.android.tv.setting;

import android.os.Build;

import java.util.Locale;
import java.util.regex.Pattern;

final class MpvHardwarePolicy {

    private static final Pattern LEGACY_HISILICON_HARDWARE = Pattern.compile("(^|[^a-z0-9])hi\\d{4}[a-z0-9]*($|[^a-z0-9])");

    private MpvHardwarePolicy() {
    }

    static boolean blocksZeroCopy() {
        String socManufacturer = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                socManufacturer = Build.SOC_MANUFACTURER;
            } catch (Throwable ignored) {
            }
        }
        return blocksZeroCopy(
                Build.MANUFACTURER,
                Build.HARDWARE,
                socManufacturer,
                Build.FINGERPRINT,
                Build.MODEL,
                Build.SUPPORTED_ABIS);
    }

    static boolean blocksZeroCopy(String manufacturer, String hardware, String socManufacturer) {
        return blocksZeroCopy(manufacturer, hardware, socManufacturer, "", "");
    }

    static boolean blocksZeroCopy(
            String manufacturer,
            String hardware,
            String socManufacturer,
            String fingerprint,
            String model) {
        return blocksZeroCopy(
                manufacturer,
                hardware,
                socManufacturer,
                fingerprint,
                model,
                new String[0]);
    }

    static boolean blocksZeroCopy(
            String manufacturer,
            String hardware,
            String socManufacturer,
            String fingerprint,
            String model,
            String[] supportedAbis) {
        String normalizedManufacturer = normalize(manufacturer);
        String normalizedHardware = normalize(hardware);
        String normalizedSoc = normalize(socManufacturer);
        String normalizedFingerprint = normalize(fingerprint);
        String normalizedModel = normalize(model);
        boolean hiSiliconChip = normalizedHardware.contains("kirin")
                || normalizedHardware.contains("hisilicon")
                || LEGACY_HISILICON_HARDWARE.matcher(normalizedHardware).find()
                || normalizedSoc.contains("hisilicon");
        boolean chipUnknown = normalizedHardware.isEmpty() || normalizedHardware.equals("unknown");
        boolean huaweiFamily = normalizedManufacturer.contains("huawei") || normalizedManufacturer.contains("honor");
        boolean emulator = normalizedHardware.contains("ranchu")
                || normalizedHardware.contains("goldfish")
                || normalizedHardware.contains("cutf_cvm")
                || normalizedFingerprint.startsWith("generic/")
                || normalizedFingerprint.contains("/sdk_gphone")
                || normalizedFingerprint.contains("emulator")
                || normalizedModel.startsWith("sdk_gphone")
                || normalizedModel.contains("android sdk built for")
                || normalizedModel.equals("emulator");
        boolean x86Abi = false;
        boolean armAbi = false;
        if (supportedAbis != null) {
            for (String abi : supportedAbis) {
                String normalizedAbi = normalize(abi);
                x86Abi |= normalizedAbi.equals("x86") || normalizedAbi.equals("x86_64");
                armAbi |= normalizedAbi.equals("armeabi-v7a") || normalizedAbi.equals("arm64-v8a");
            }
        }
        boolean nativeBridgeLikely = x86Abi && armAbi;
        // Older Android versions do not expose SOC_MANUFACTURER, and some Huawei builds
        // report only "unknown". Prefer the compatible copy path in that ambiguous case.
        return emulator || nativeBridgeLikely || hiSiliconChip || (chipUnknown && huaweiFamily);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
}
