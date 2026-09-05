package com.fongmi.android.tv.subtitle;

final class SubtitleStrings {

    private SubtitleStrings() {
    }

    static boolean isEmpty(CharSequence value) {
        return value == null || value.length() == 0;
    }

    static boolean equals(CharSequence first, CharSequence second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        return first.toString().contentEquals(second);
    }
}
