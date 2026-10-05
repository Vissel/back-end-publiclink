package com.qrpublic.apartment.export;

/**
 * Normalizes frontend i18n language codes for Excel export labels.
 */
public final class ExportLocale {

    public static final String ENGLISH = "en";
    public static final String VIETNAMESE = "vi";

    private ExportLocale() {
    }

    public static String resolve(String locale) {
        if (locale == null || locale.isBlank()) {
            return ENGLISH;
        }
        String normalized = locale.trim().toLowerCase();
        if (normalized.startsWith(VIETNAMESE)) {
            return VIETNAMESE;
        }
        return ENGLISH;
    }
}
