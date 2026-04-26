package com.platform.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        List<String> keysSet = new ArrayList<>();
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof TenantAwareAuthentication tenantAuth) {
                MDC.put("userId", auth.getName());
                MDC.put("tenantId", tenantAuth.tenantId());
                MDC.put("role", tenantAuth.role());
                keysSet.addAll(List.of("userId", "tenantId", "role"));
            }
            chain.doFilter(request, response);
        } finally {
            keysSet.forEach(MDC::remove);
        }
    }
}
