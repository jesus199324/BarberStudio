package com.barberstudio.common.util;

import com.barberstudio.common.error.BusinessRuleException;
import java.util.regex.Pattern;

/** Normaliza telefonos a formato internacional E.164 (por defecto Uruguay, +598). */
public final class Phone {
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private Phone() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) throw new BusinessRuleException("Ingresá un número de WhatsApp.");
        String s = raw.trim();
        String digits = s.replaceAll("\\D", "");
        String e164;
        if (s.startsWith("+")) e164 = "+" + digits;
        else if (digits.startsWith("00")) e164 = "+" + digits.substring(2);
        else if (digits.startsWith("598")) e164 = "+" + digits;
        else if (digits.startsWith("09") && digits.length() == 9) e164 = "+598" + digits.substring(1); // 099 123 456
        else if (digits.startsWith("9") && digits.length() == 8) e164 = "+598" + digits;               // 99 123 456
        else e164 = "+" + digits;
        if (!E164.matcher(e164).matches()) {
            throw new BusinessRuleException("El WhatsApp no es válido. Usá formato internacional, por ejemplo +59899123456.");
        }
        return e164;
    }
}
