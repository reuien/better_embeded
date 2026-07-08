package com.mos.plc.service;

public final class ResultNormalizer {
    private ResultNormalizer() {
    }

    public static String normalizeResult(String value) {
        if (value == null || value.isBlank()) {
            return "normal";
        }
        String normalized = value.trim().toLowerCase();
        if (normalized.equals("defective") || normalized.equals("defect") || normalized.equals("ng")) {
            return "defect";
        }
        return "normal";
    }

    public static String folderForResult(String result) {
        return normalizeResult(result).equals("defect") ? "defect" : "normal";
    }
}
