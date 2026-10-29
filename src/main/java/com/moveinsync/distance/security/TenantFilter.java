package com.moveinsync.distance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Filter that establishes multi-tenant context, RBAC authentication, and MDC tracing.
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    public static final String HEADER_TENANT_ID = "X-Tenant-ID";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_USER_ID = "X-User-ID";
    public static final String DEFAULT_TENANT_ID = "moveinsync-client-default";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tenantId = request.getHeader(HEADER_TENANT_ID);
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = DEFAULT_TENANT_ID;
        }

        String userRole = request.getHeader(HEADER_USER_ROLE);
        if (userRole == null || userRole.trim().isEmpty()) {
            userRole = "ROLE_OPERATOR";
        }
        if (!userRole.startsWith("ROLE_")) {
            userRole = "ROLE_" + userRole;
        }

        String userId = request.getHeader(HEADER_USER_ID);
        if (userId == null || userId.trim().isEmpty()) {
            userId = "system-user";
        }

        String traceId = UUID.randomUUID().toString().substring(0, 8);

        TenantContext.setTenantId(tenantId);
        MDC.put("tenant_id", tenantId);
        MDC.put("user_id", userId);
        MDC.put("trace_id", traceId);

        // Configure Spring Security Authentication
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(userRole));
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            response.setHeader("X-Trace-ID", traceId);
            response.setHeader("X-Tenant-ID", tenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
