package com.pfm.service;

import com.pfm.dto.Dto.*;
import com.pfm.entity.User;
import com.pfm.exception.ApiException;
import com.pfm.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, session login and logout. */
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    /** Registers a new user; the email is normalised to lower case and must be unused. */
    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        String username = req.username().trim().toLowerCase();
        if (userRepository.existsByUsername(username)) {
            throw ApiException.conflict("A user with this username already exists");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName().trim());
        user.setPhoneNumber(req.phoneNumber().trim());
        User saved = userRepository.save(user);
        return new RegisterResponse("User registered successfully", saved.getId());
    }

    /** Authenticates and stores the security context in the HTTP session (JSESSIONID cookie). */
    public MessageResponse login(LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(req.username().trim().toLowerCase(), req.password()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        if (request.getSession(false) != null) {
            request.changeSessionId();   // prevent session fixation
        }
        securityContextRepository.saveContext(context, request, response);
        return new MessageResponse("Login successful");
    }

    /** Invalidates the current session. */
    public MessageResponse logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return new MessageResponse("Logout successful");
    }
}
