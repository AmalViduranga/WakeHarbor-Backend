package io.wakeharbor.control.service;

import io.wakeharbor.control.domain.AuditEvent;
import io.wakeharbor.control.domain.User;
import io.wakeharbor.control.dto.auth.ChangePasswordRequest;
import io.wakeharbor.control.dto.auth.LoginRequest;
import io.wakeharbor.control.dto.auth.LoginResponse;
import io.wakeharbor.control.dto.auth.UserDto;
import io.wakeharbor.control.repository.AuditEventRepository;
import io.wakeharbor.control.repository.UserRepository;
import io.wakeharbor.control.security.JwtService;
import io.wakeharbor.control.security.RateLimiterService;
import io.wakeharbor.control.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimiterService rateLimiterService;
    private final AuditEventRepository auditEventRepository;
    private final String cookieName;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final long expirationMinutes;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RateLimiterService rateLimiterService,
            AuditEventRepository auditEventRepository,
            @Value("${wakeharbor.security.cookie-name:wakeharbor_session}") String cookieName,
            @Value("${wakeharbor.security.cookie-secure:false}") boolean cookieSecure,
            @Value("${wakeharbor.security.cookie-same-site:Lax}") String cookieSameSite,
            @Value("${wakeharbor.security.jwt-expiration-minutes:1440}") long expirationMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiterService = rateLimiterService;
        this.auditEventRepository = auditEventRepository;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.expirationMinutes = expirationMinutes;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String clientIp = extractClientIp(httpRequest);
        String rateLimitKey = clientIp + ":" + request.email().toLowerCase();

        if (!rateLimiterService.isAllowed(rateLimitKey)) {
            log.warn("Login throttled due to excessive failed attempts for key: {}", rateLimitKey);
            throw new BadCredentialsException("Too many failed login attempts. Please wait 15 minutes before retrying.");
        }

        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash()) || !user.isActive()) {
            rateLimiterService.recordFailure(rateLimitKey);
            log.warn("Failed login attempt for email: {} from IP: {}", request.email(), clientIp);
            throw new BadCredentialsException("Invalid email or password");
        }

        rateLimiterService.recordSuccess(rateLimitKey);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());

        // Attach HttpOnly secure cookie
        ResponseCookie sessionCookie = ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(expirationMinutes * 60)
                .sameSite(cookieSameSite)
                .build();

        httpResponse.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.toString());

        // Record audit event
        auditEventRepository.save(AuditEvent.builder()
                .user(user)
                .action("LOGIN")
                .entityType("USER")
                .entityId(user.getId().toString())
                .ipAddress(clientIp)
                .userAgent(httpRequest.getHeader("User-Agent"))
                .details("Administrator logged in successfully")
                .build());

        log.info("Admin logged in successfully: {} from IP: {}", user.getEmail(), clientIp);
        return new LoginResponse(token, user.getId(), user.getEmail(), user.getRole());
    }

    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        ResponseCookie clearCookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite(cookieSameSite)
                .build();

        httpResponse.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(UserPrincipal principal) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return new UserDto(user.getId(), user.getEmail(), user.getRole(), user.isActive(), user.getCreatedAt());
    }

    @Transactional
    public void changePassword(UserPrincipal principal, ChangePasswordRequest request) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password does not match");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        auditEventRepository.save(AuditEvent.builder()
                .user(user)
                .action("CHANGE_PASSWORD")
                .entityType("USER")
                .entityId(user.getId().toString())
                .details("Admin password changed successfully")
                .build());

        log.info("Password updated successfully for admin user: {}", user.getEmail());
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
