package com.barberstudio.common.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Datos del usuario autenticado, tomados SIEMPRE del JWT validado (nunca del cliente). */
public final class CurrentUser {
    private CurrentUser() {}

    private static Jwt jwt() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof Jwt j)) throw new AccessDeniedException("No autenticado");
        return j;
    }

    /** Barberia del usuario: todas las consultas privadas se filtran con este id (multi-tenant). */
    public static long shopId() {
        Number n = jwt().getClaim("shop");
        return n.longValue();
    }

    public static long userId() { return Long.parseLong(jwt().getSubject()); }
}
