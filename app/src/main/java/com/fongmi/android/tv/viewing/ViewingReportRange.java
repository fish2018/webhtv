package com.fongmi.android.tv.viewing;

import java.util.Calendar;

public enum ViewingReportRange {
    ALL("全部"),
    THIS_YEAR("本年"),
    FIRST_HALF("上半年"),
    SECOND_HALF("下半年"),
    THIS_QUARTER("本季度"),
    THIS_MONTH("本月"),
    THIS_WEEK("本周"),
    LAST_30_DAYS("最近30天"),
    LAST_7_DAYS("最近7天");

    private final String label;

    ViewingReportRange(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String getDisplayLabel() {
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH) + 1;
        int quarter = (month - 1) / 3 + 1;
        switch (this) {
            case THIS_YEAR: return label + " (" + year + ")";
            case THIS_QUARTER: return label + " (Q" + quarter + ")";
            case THIS_MONTH: return label + " (" + month + "月)";
            default: return label;
        }
    }

    public long[] getTimeBounds() {
        Calendar cal = Calendar.getInstance();
        long end = cal.getTimeInMillis();
        long start = 0;

        switch (this) {
            case ALL:
                start = 0;
                break;
            case THIS_YEAR:
                cal.set(Calendar.MONTH, Calendar.JANUARY);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                start = cal.getTimeInMillis();
                break;
            case FIRST_HALF:
                cal.set(Calendar.MONTH, Calendar.JANUARY);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                start = cal.getTimeInMillis();
                cal.set(Calendar.MONTH, Calendar.JUNE);
                cal.set(Calendar.DAY_OF_MONTH, 30);
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                end = cal.getTimeInMillis();
                break;
            case SECOND_HALF:
                cal.set(Calendar.MONTH, Calendar.JULY);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                start = cal.getTimeInMillis();
                break;
            case THIS_QUARTER:
                int month = cal.get(Calendar.MONTH);
                int quarterStart = (month / 3) * 3;
                cal.set(Calendar.MONTH, quarterStart);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                start = cal.getTimeInMillis();
                break;
            case THIS_MONTH:
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                start = cal.getTimeInMillis();
                break;
            case THIS_WEEK:
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                start = cal.getTimeInMillis();
                break;
            case LAST_30_DAYS:
                cal.add(Calendar.DAY_OF_MONTH, -30);
                start = cal.getTimeInMillis();
                break;
            case LAST_7_DAYS:
                cal.add(Calendar.DAY_OF_MONTH, -7);
                start = cal.getTimeInMillis();
                break;
        }
        return new long[]{start, end};
    }
}
