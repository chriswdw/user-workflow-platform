package com.platform.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MdcFilterTest {

    private final MdcFilter filter = new MdcFilter();

    @AfterEach
    void cleanup() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void mdc_populated_from_tenant_aware_authentication() throws Exception {
        setAuth(new StubTenantAuth("user-42", "ANALYST", "tenant-acme"));

        filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> {
                    assertThat(MDC.get("userId")).isEqualTo("user-42");
                    assertThat(MDC.get("tenantId")).isEqualTo("tenant-acme");
                    assertThat(MDC.get("role")).isEqualTo("ANALYST");
                });
    }

    @Test
    void mdc_cleared_after_filter() throws Exception {
        setAuth(new StubTenantAuth("user-1", "ANALYST", "tenant-1"));

        filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(),
                mock(FilterChain.class));

        assertThat(MDC.get("userId")).isNull();
        assertThat(MDC.get("tenantId")).isNull();
        assertThat(MDC.get("role")).isNull();
    }

    @Test
    void no_mdc_keys_set_for_unauthenticated_request() throws Exception {
        // SecurityContext has no authentication

        filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> {
                    assertThat(MDC.get("userId")).isNull();
                    assertThat(MDC.get("tenantId")).isNull();
                    assertThat(MDC.get("role")).isNull();
                });
    }

    @Test
    void non_tenant_aware_authentication_does_not_set_mdc() throws Exception {
        setAuth(mock(Authentication.class));

        filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> {
                    assertThat(MDC.get("userId")).isNull();
                    assertThat(MDC.get("tenantId")).isNull();
                });
    }

    private void setAuth(Authentication auth) {
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private record StubTenantAuth(String name, String role, String tenantId)
            implements Authentication, TenantAwareAuthentication {
        @Override public String getName() { return name; }
        @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
        @Override public Object getCredentials() { return null; }
        @Override public Object getDetails() { return null; }
        @Override public Object getPrincipal() { return this; }
        @Override public boolean isAuthenticated() { return true; }
        @Override public void setAuthenticated(boolean b) {}
    }
}
