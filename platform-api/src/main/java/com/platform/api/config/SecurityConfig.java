package com.platform.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${api.jwt.secret}")
    private String jwtSecret;

    @Value("${api.rate-limit.requests-per-minute:100}")
    private int requestsPerMinute;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(
                    (req, res, ex) -> res.sendError(401, "Unauthorized")))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/dev/**").permitAll()
                    .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthenticationFilter(jwtSecret),
                    UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new RateLimitingFilter(requestsPerMinute),
                    JwtAuthenticationFilter.class);
        return http.build();
    }
}
