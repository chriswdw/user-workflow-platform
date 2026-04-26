package com.platform.api.config;

import com.platform.observability.TenantAwareAuthentication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

public record ApiAuthentication(String userId, String role, String tenantId)
        implements Authentication, TenantAwareAuthentication {

    @Override public String getName() { return userId; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
    @Override public Object getCredentials() { return null; }
    @Override public Object getDetails() { return null; }
    @Override public Object getPrincipal() { return this; }
    @Override public boolean isAuthenticated() { return true; }
    // Intentionally a no-op: authentication state is immutable once the JWT is validated.
    // Callers cannot downgrade a validated token to unauthenticated mid-request.
    @Override public void setAuthenticated(boolean isAuthenticated) { /* immutable — see above */ }
}
