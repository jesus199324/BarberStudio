package com.barberstudio.common.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.barberstudio.common.security.OriginCheckFilter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    public static final String AUTH_COOKIE = "access_token";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, BearerTokenResolver resolver,
                                    JwtAuthenticationConverter converter,
                                    @Value("${app.allowed-origins}") String origins) throws Exception {
        Set<String> allowed = Arrays.stream(origins.split(",")).map(String::trim)
            .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // CSRF clasico desactivado: la API es sin estado, la cookie es SameSite y
            // OriginCheckFilter rechaza origenes no permitidos en operaciones que modifican datos.
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .addFilterBefore(new OriginCheckFilter(allowed), BearerTokenAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**", "/api/auth/login", "/api/auth/logout", "/actuator/health").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())   // archivos estaticos (/uploads, /demo-images)
            .oauth2ResourceServer(o -> o.bearerTokenResolver(resolver)
                .jwt(j -> j.jwtAuthenticationConverter(converter)));
        return http.build();
    }

    /** Lee el JWT desde la cookie HttpOnly (o el header Authorization, util para Swagger/pruebas). */
    @Bean
    BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();
        return req -> {
            String path = req.getRequestURI();
            // Una cookie vencida no debe romper las paginas publicas ni el login.
            if (path.startsWith("/api/public/") || path.startsWith("/api/auth/login") || path.startsWith("/api/auth/logout")) {
                return null;
            }
            if (req.getCookies() != null) {
                for (Cookie c : req.getCookies()) {
                    if (AUTH_COOKIE.equals(c.getName())) return c.getValue();
                }
            }
            return header.resolve(req);
        };
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

    @Bean
    SecretKey jwtSecretKey(@Value("${app.jwt.secret:}") String secret) {
        if (secret == null || secret.getBytes(UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET es obligatorio y debe tener al menos 32 caracteres.");
        }
        return new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.allowed-origins}") String origins) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Content-Type", "Accept"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", c);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
