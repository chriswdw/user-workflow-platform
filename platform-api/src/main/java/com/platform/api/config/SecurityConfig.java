package com.platform.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

  private final String jwtSecret;
  private final int requestsPerMinute;

  public SecurityConfig(
      @Value("${api.jwt.secret}") String jwtSecret,
      @Value("${api.rate-limit.requests-per-minute:100}") int requestsPerMinute) {
    this.jwtSecret = jwtSecret;
    this.requestsPerMinute = requestsPerMinute;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) {
    // CSRF protection is intentionally off: the API is stateless (no session, no cookies) and
    // authenticates every request with an Authorization: Bearer JWT header, which browsers never
    // attach cross-site automatically, so there is no ambient credential for CSRF to abuse.
    http.csrf(AbstractHttpConfigurer::disable) // NOSONAR java:S4502 - stateless bearer-token API
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401, "Unauthorized")))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/dev/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/admin/**", "/api/v1/workflow-type-submissions/all-drafts")
                    .hasRole(PLATFORM_ADMIN)
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtSecret), UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(new RateLimitingFilter(requestsPerMinute), JwtAuthenticationFilter.class);
    return http.build();
  }
}
