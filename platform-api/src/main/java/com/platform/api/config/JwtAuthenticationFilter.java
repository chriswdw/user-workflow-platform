package com.platform.api.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final byte[] secretKeyBytes;

    public JwtAuthenticationFilter(String base64Secret) {
        this.secretKeyBytes = Base64.getDecoder().decode(base64Secret);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(7);
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            MACVerifier verifier = new MACVerifier(secretKeyBytes);
            if (!signedJwt.verify(verifier)) {
                chain.doFilter(request, response);
                return;
            }
            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.before(new Date())) {
                chain.doFilter(request, response);
                return;
            }
            String userId = claims.getSubject();
            String role = claims.getStringClaim("role");
            String tenantId = claims.getStringClaim("tenantId");
            SecurityContextHolder.getContext()
                    .setAuthentication(new ApiAuthentication(userId, role, tenantId));
        } catch (ParseException | JOSEException ignored) {
            // invalid token — leave SecurityContext empty; Spring Security returns 401
        }
        chain.doFilter(request, response);
    }
}
