package com.platform.observability;

/**
 * Implemented by Authentication objects that carry tenant and role context.
 * MdcFilter uses this to populate MDC without coupling to a specific auth implementation.
 */
public interface TenantAwareAuthentication {
    String tenantId();
    String role();
}
