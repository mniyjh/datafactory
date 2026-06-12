package com.cqie.datafactory.executor.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component("executorInternalAuthFilter")
public class InternalAuthFilter extends OncePerRequestFilter {

    @Value("${security.internal-auth-key:}")
    private String internalAuthKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        String username = request.getHeader("X-User-Username");
        String rolesHeader = request.getHeader("X-User-Roles");
        String permissionsHeader = request.getHeader("X-User-Permissions");

        // Validate internal auth key to prevent header spoofing from direct service access
        String internalAuth = request.getHeader("X-Internal-Auth");
        if (StringUtils.hasText(internalAuthKey) && !internalAuthKey.equals(internalAuth)) {
            log.warn("Invalid X-Internal-Auth header from IP: {}", request.getRemoteAddr());
            filterChain.doFilter(request, response);
            return;
        }

        if (StringUtils.hasText(userId) && StringUtils.hasText(username)) {
            List<String> permissions = StringUtils.hasText(permissionsHeader)
                    ? Arrays.stream(permissionsHeader.split(","))
                            .map(String::trim)
                            .collect(Collectors.toList())
                    : Collections.emptyList();

            List<SimpleGrantedAuthority> authorities = permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
            auth.setDetails(Long.parseLong(userId));

            SecurityContextHolder.getContext().setAuthentication(auth);

            request.setAttribute("userId", Long.parseLong(userId));
            request.setAttribute("username", username);

            log.debug("Internal auth (executor): user={} userId={} permissions={}", username, userId, permissions);
        }

        filterChain.doFilter(request, response);
    }
}
