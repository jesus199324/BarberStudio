package com.barberstudio.common.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URLEncoder;

/** Enlaces wa.me con mensaje prellenado. Limitacion: solo abren el chat; no envian ni confirman nada. */
public final class WhatsAppLinks {
    private WhatsAppLinks() {}

    public static String link(String e164, String text) {
        return "https://wa.me/" + e164.replace("+", "") + "?text=" + URLEncoder.encode(text, UTF_8).replace("+", "%20");
    }
}
