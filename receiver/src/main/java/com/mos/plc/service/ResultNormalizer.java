package com.mos.plc.service;

import java.util.Locale;

public final class ResultNormalizer {
    private ResultNormalizer() {
    }

    public static String normalizeResult(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("result is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "normal", "ok", "pass", "passed", "good", "no_defect", "none" -> "normal";
            case "defective", "defect", "ng", "fail", "failed" -> "defect";
            default -> throw new IllegalArgumentException("result must be one of OK, NG, normal, defect");
        };
    }

    public static boolean isDefect(String result) {
        return normalizeResult(result).equals("defect");
    }

    public static boolean isNormal(String result) {
        return normalizeResult(result).equals("normal");
    }

    public static String folderForResult(String result) {
        if (isDefect(result)) {
            return "defect";
        }
        return "normal";
    }
}
