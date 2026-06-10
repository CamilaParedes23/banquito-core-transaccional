package com.banquito.core.account.infrastructure.security;

import com.banquito.core.account.api.dto.internal.AuthenticatedActor;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    public JwtAuthenticationFilter(JwtService jwtService) { this.jwtService = jwtService; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parseClaims(header.substring(7));
                List<String> roles = claims.get("roles", List.class) == null ? List.of() : claims.get("roles", List.class);
                List<String> scopes = claims.get("scopes", List.class) == null ? List.of() : claims.get("scopes", List.class);
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
                scopes.forEach(s -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + s)));
                AuthenticatedActor actor = new AuthenticatedActor(claims.getSubject(), (String) claims.get("actorType"), (String) claims.get("username"), (String) claims.get("clientId"), roles, scopes);
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, authorities));
            } catch (Exception ignored) { SecurityContextHolder.clearContext(); }
        }
        filterChain.doFilter(request, response);
    }
}
