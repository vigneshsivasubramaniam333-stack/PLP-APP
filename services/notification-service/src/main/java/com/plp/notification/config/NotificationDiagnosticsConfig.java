package com.plp.notification.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Logs SMTP configuration and tests the connection at startup so mail
 * misconfiguration surfaces loudly instead of failing silently per-notification.
 * Ported from the LOS notification-service for parity.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class NotificationDiagnosticsConfig {

    private final EmailNotificationProperties emailProperties;
    private final JavaMailSender mailSender;

    @PostConstruct
    void logStartupDiagnostics() {
        log.info("[SMTP_INIT] starting smtp diagnostics fromAddress={} fromName={} simulationMode={}",
                emailProperties.getFromAddress(),
                emailProperties.getFromName(),
                emailProperties.isSimulationEnabled());

        if (emailProperties.isSimulationEnabled()) {
            log.info("[SMTP_INIT] simulation mode ENABLED — emails will be logged, not sent");
            return;
        }

        if (mailSender instanceof JavaMailSenderImpl impl) {
            log.info("[SMTP_INIT] host={} port={} usernamePresent={} passwordPresent={}",
                    impl.getHost(), impl.getPort(), hasText(impl.getUsername()), hasText(impl.getPassword()));
            StringBuilder missing = new StringBuilder();
            if (!hasText(impl.getHost())) missing.append("spring.mail.host ");
            if (impl.getPort() <= 0) missing.append("spring.mail.port ");
            if (!hasText(impl.getUsername())) missing.append("spring.mail.username ");
            if (!hasText(impl.getPassword())) missing.append("spring.mail.password ");
            if (!hasText(emailProperties.getFromAddress())) missing.append("plp.notification.email.from-address ");
            if (missing.length() > 0) {
                log.warn("[SMTP_INIT] SMTP not fully configured — missing: {}", missing.toString().trim());
                return;
            }
            try {
                impl.testConnection();
                log.info("[SMTP_INIT] SMTP connection successful host={} port={}", impl.getHost(), impl.getPort());
            } catch (Exception ex) {
                log.error("[SMTP_INIT] SMTP connection FAILED host={} port={} reason={}",
                        impl.getHost(), impl.getPort(), ex.getMessage(), ex);
            }
        } else {
            log.warn("[SMTP_INIT] JavaMailSenderImpl not available; cannot test SMTP connection");
        }
    }

    private static boolean hasText(String v) {
        return v != null && !v.isBlank();
    }
}
