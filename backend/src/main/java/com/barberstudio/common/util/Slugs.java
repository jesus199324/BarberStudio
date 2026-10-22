package com.barberstudio.common.util;

import java.text.Normalizer;
import java.util.Locale;

public final class Slugs {
    private Slugs() {}

    public static String slugify(String text) {
        String n = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
