package com.barberstudio.auth;

import com.barberstudio.common.config.SecurityConfig;
import com.barberstudio.common.error.NotFoundException;
import com.barberstudio.common.security.CurrentUser;
import com.barberstudio.common.security.JwtService;
import com.barberstudio.common.util.RateLimiter;
import com.barberstudio.shop.Barbershop;
import com.barberstudio.shop.BarbershopRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppUserRepository users;
    private final BarbershopRepository shops;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RateLimiter limiter;
    private final boolean secureCookie;
    private final String sameSite;
    private final String dummyHash;

    public AuthController(AppUserRepository users, BarbershopRepository shops, PasswordEncoder encoder,
                          JwtService jwt, RateLimiter limiter,
                          @Value("${app.cookie.secure}") boolean secureCookie,
                          @Value("${app.cookie.same-site}") String sameSite) {
        this.users = users; this.shops = shops; this.encoder = encoder; this.jwt = jwt;
        this.limiter = limiter; this.secureCookie = secureCookie; this.sameSite = sameSite;
        this.dummyHash = encoder.encode("no-existe"); // iguala el tiempo de respuesta si el email no existe
    }

    public record LoginRequest(@NotBlank(message = "Ingresá tu email") @Email(message = "Email inválido") String email,
                               @NotBlank(message = "Ingresá tu contraseña") String password) {}

    public record MeResponse(Long userId, String email, String role, Long barbershopId,
                             String barbershopName, String barbershopSlug) {}

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public MeResponse login(@Valid @RequestBody LoginRequest r, HttpServletRequest req, HttpServletResponse res) {
        if (!limiter.allow("login:" + req.getRemoteAddr(), 10, Duration.ofMinutes(15))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Probá de nuevo en unos minutos.");
        }
        AppUser u = users.findByEmailIgnoreCase(r.email().trim()).filter(AppUser::isActive).orElse(null);
        boolean matches = encoder.matches(r.password(), u != null ? u.getPasswordHash() : dummyHash);
        if (u == null || !matches) throw new BadCredentialsException("Email o contraseña incorrectos.");
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(jwt.issue(u), jwt.ttl()).toString());
        return me(u);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public MeResponse me() {
        AppUser u = users.findById(CurrentUser.userId()).filter(AppUser::isActive)
            .orElseThrow(() -> new NotFoundException("Usuario no encontrado."));
        return me(u);
    }

    private MeResponse me(AppUser u) {
        Barbershop b = shops.findById(u.getBarbershopId()).orElseThrow(() -> new NotFoundException("Barbería no encontrada."));
        return new MeResponse(u.getId(), u.getEmail(), u.getRole().name(), b.getId(), b.getName(), b.getSlug());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(SecurityConfig.AUTH_COOKIE, value)
            .httpOnly(true).secure(secureCookie).sameSite(sameSite).path("/api").maxAge(maxAge).build();
    }
}
