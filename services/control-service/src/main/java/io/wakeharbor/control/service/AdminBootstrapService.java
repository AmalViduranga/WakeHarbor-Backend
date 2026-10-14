package io.wakeharbor.control.service;

import io.wakeharbor.control.domain.NotificationPreferences;
import io.wakeharbor.control.domain.User;
import io.wakeharbor.control.repository.NotificationPreferencesRepository;
import io.wakeharbor.control.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures the single bootstrap administrator account and default notification preferences
 * are seeded securely on startup if no users exist in the database.
 */
@Service
public class AdminBootstrapService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final UserRepository userRepository;
    private final NotificationPreferencesRepository preferencesRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrapService(
            UserRepository userRepository,
            NotificationPreferencesRepository preferencesRepository,
            PasswordEncoder passwordEncoder,
            @Value("${wakeharbor.admin.email:admin@wakeharbor.local}") String adminEmail,
            @Value("${wakeharbor.admin.password:ChangeThisToASecureAdminPassword123!}") String adminPassword) {
        this.userRepository = userRepository;
        this.preferencesRepository = preferencesRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail.trim().toLowerCase();
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() == 0) {
            log.info("No users found in database. Initializing bootstrap administrator account for: {}", adminEmail);

            User admin = User.builder()
                    .email(adminEmail)
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .role("ADMIN")
                    .active(true)
                    .build();

            User savedAdmin = userRepository.save(admin);

            NotificationPreferences preferences = NotificationPreferences.builder()
                    .user(savedAdmin)
                    .emailEnabled(false)
                    .recipientEmail(adminEmail)
                    .notifyOnFailure(true)
                    .notifyOnRecovery(true)
                    .notifyOnWorkerOffline(true)
                    .failureThreshold(2)
                    .cooldownMinutes(60)
                    .build();

            preferencesRepository.save(preferences);
            log.info("Bootstrap administrator account initialized successfully.");
        } else {
            log.info("Existing user accounts detected. Bootstrap initialization skipped.");
        }
    }
}
