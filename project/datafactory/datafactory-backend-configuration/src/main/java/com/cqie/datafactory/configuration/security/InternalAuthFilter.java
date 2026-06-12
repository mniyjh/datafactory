package com.cqie.datafactory.configuration.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        String username = request.getHeader("X-User-Username");
        String rolesHeader = request.getHeader("X-User-Roles");
        String permissionsHeader = request.getHeader("X-User-Permissions");

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

            // 将 userId 作为 request attribute 方便 Controller 获取
            request.setAttribute("userId", Long.parseLong(userId));
            request.setAttribute("username", username);

            log.debug("Internal auth: user={} userId={} permissions={}", username, userId, permissions);
        }

        filterChain.doFilter(request, response);
    }
}
