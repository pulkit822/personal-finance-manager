package com.pfm.security;

import com.pfm.exception.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolves the id of the logged-in user from the security context. */
@Component
public class CurrentUser {
    public Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p) {
            return p.getId();
        }
        throw ApiException.unauthorized("Authentication required");
    }
}
