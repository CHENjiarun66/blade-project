package com.blade.whatsapp.service;

import java.util.regex.Pattern;

final class WhatsappContextRedactor {
    private static final Pattern EMAIL = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern URL = Pattern.compile("(?i)https?://\\S+|www\\.\\S+");
    private static final Pattern JID = Pattern.compile("\\b[0-9]{5,}@(s\\.whatsapp\\.net|lid)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE = Pattern.compile("(?<![A-Za-z0-9])(?:\\+?\\d[\\d ()\\-.]{5,}\\d)(?![A-Za-z0-9])");

    private WhatsappContextRedactor() {}

    static String sanitize(String value) {
        if (value == null) return "";
        String result = JID.matcher(value).replaceAll("[CONTACT]");
        result = EMAIL.matcher(result).replaceAll("[EMAIL]");
        result = URL.matcher(result).replaceAll("[LINK]");
        return PHONE.matcher(result).replaceAll("[PHONE]");
    }

    static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
