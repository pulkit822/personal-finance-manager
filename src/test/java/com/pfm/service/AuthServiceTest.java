package com.pfm.service;

import com.pfm.dto.Dto.*;
import com.pfm.entity.User;
import com.pfm.exception.ApiException;
import com.pfm.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @InjectMocks AuthService service;

    private RegisterRequest request() {
        return new RegisterRequest(" John@Example.com ", "password123", " John Doe ", "+1234567890");
    }

    @Test
    void registerNormalisesUsernameAndHashesPassword() {
        when(userRepository.existsByUsername("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("HASH");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(3L);
            return u;
        });

        RegisterResponse r = service.register(request());

        assertEquals(3L, r.userId());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("john@example.com", captor.getValue().getUsername());
        assertEquals("HASH", captor.getValue().getPasswordHash());
        assertEquals("John Doe", captor.getValue().getFullName());
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("john@example.com")).thenReturn(true);
        ApiException ex = assertThrows(ApiException.class, () -> service.register(request()));
        assertEquals(409, ex.getStatus().value());
        verify(userRepository, never()).save(any());
    }

    @Test
    void logoutInvalidatesSession() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        HttpSession session = req.getSession(true);
        assertEquals("Logout successful", service.logout(req).message());
        assertNull(req.getSession(false));
        assertNotNull(session);
    }

    @Test
    void logoutWithoutSessionStillSucceeds() {
        assertEquals("Logout successful", service.logout(new MockHttpServletRequest()).message());
    }
}
