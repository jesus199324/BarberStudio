package com.barberstudio.common.security;

import com.barberstudio.auth.AppUser;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final Duration ttl;

    public JwtService(JwtEncoder encoder, @Value("${app.jwt.expiration-minutes:120}") long minutes) {
        this.encoder = encoder;
        this.ttl = Duration.ofMinutes(minutes);
    }

    public Duration ttl() { return ttl; }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(String.valueOf(user.getId()))
            .issuedAt(now).expiresAt(now.plus(ttl))
            .claim("shop", user.getBarbershopId())
            .claim("role", user.getRole().name())
            .claim("email", user.getEmail())
            .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
