package com.blade.whatsapp.service;

import java.util.LinkedHashSet;
import java.util.Set;

final class WhatsappPhoneMatcher {
    private WhatsappPhoneMatcher() {}

    static Set<String> customerPhoneVariants(String countryCode, String phone) {
        Set<String> variants = new LinkedHashSet<>();
        String number = digits(phone);
        if (number == null) return variants;
        variants.add(number);
        String callingCode = digits(countryCode);
        if (callingCode != null && !number.startsWith(callingCode)) {
            String local = number.replaceFirst("^0+", "");
            if (!local.isBlank()) variants.add(callingCode + local);
        }
        return variants;
    }

    private static String digits(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("[^0-9]", "");
        return normalized.isBlank() ? null : normalized;
    }
}
