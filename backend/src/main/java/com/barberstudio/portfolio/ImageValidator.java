package com.barberstudio.portfolio;

import com.barberstudio.common.error.BusinessRuleException;

/** Valida en el servidor: tamano y tipo real (por firma binaria), sin confiar en el nombre ni en el Content-Type enviados. */
final class ImageValidator {
    static final long MAX_BYTES = 5L * 1024 * 1024;
    private ImageValidator() {}

    static String extensionFor(byte[] b) {
        if (b == null || b.length == 0) throw new BusinessRuleException("El archivo está vacío.");
        if (b.length > MAX_BYTES) throw new BusinessRuleException("El archivo supera el tamaño máximo (5 MB).");
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        throw new BusinessRuleException("Formato no permitido. Usá JPG, PNG o WebP.");
    }

    static String mimeFor(String ext) {
        return switch (ext) { case "jpg" -> "image/jpeg"; case "png" -> "image/png"; default -> "image/webp"; };
    }
}
