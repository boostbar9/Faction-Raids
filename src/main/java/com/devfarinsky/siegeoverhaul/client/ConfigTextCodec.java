package com.devfarinsky.siegeoverhaul.client;

import java.util.ArrayList;
import java.util.List;

/** Type-preserving text conversion for values edited by the in-game config screen. */
final class ConfigTextCodec {
    private ConfigTextCodec() {}

    static String format(Object value) {
        if (value instanceof List<?> values) {
            return String.join(", ", values.stream().map(String::valueOf).toList());
        }
        return String.valueOf(value);
    }

    static Object parse(String text, Object template) {
        try {
            if (template instanceof Integer) return Integer.parseInt(text.trim());
            if (template instanceof Long) return Long.parseLong(text.trim());
            if (template instanceof Double) return Double.parseDouble(text.trim());
            if (template instanceof Float) return Float.parseFloat(text.trim());
            if (template instanceof List<?>) return parseStringList(text);
            return text;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<String> parseStringList(String text) {
        String value = text.trim();
        // Accept both the screen's comma-separated format and the bracketed
        // form previously displayed by String.valueOf(List).
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.isEmpty()) return List.of();

        List<String> values = new ArrayList<>();
        for (String part : value.split(",", -1)) {
            String entry = part.trim();
            if (!entry.isEmpty()) values.add(entry);
        }
        return List.copyOf(values);
    }
}
