package com.plp.notification.service;

import com.plp.notification.model.entity.Notification;
import com.plp.notification.model.entity.NotificationTemplate;
import com.plp.notification.model.enums.NotificationChannel;
import com.plp.notification.model.enums.NotificationStatus;
import com.plp.notification.repository.NotificationRepository;
import com.plp.notification.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_RETRIES = 3;

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final TemplateRenderer templateRenderer;

    @Transactional
    public Notification sendNotification(String templateCode, UUID recipientId,
                                          String recipientEmail, String recipientPhone,
                                          Map<String, String> variables,
                                          String referenceType, UUID referenceId) {
        NotificationTemplate template = templateRepository.findByTemplateCode(templateCode)
                .orElseThrow(() -> new RuntimeException("Template not found: " + templateCode));

        String subject = templateRenderer.render(template.getSubject(), variables);
        String body = templateRenderer.render(template.getBodyTemplate(), variables);

        Notification notification = Notification.builder()
                .template(template)
                .recipientId(recipientId)
                .recipientEmail(recipientEmail)
                .recipientPhone(recipientPhone)
                .channel(template.getChannel())
                .subject(subject)
                .body(body)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .status(NotificationStatus.PENDING)
                .build();

        notification = notificationRepository.save(notification);
        deliverNotification(notification);
        return notification;
    }

    @Transactional
    public Notification sendDirectNotification(NotificationChannel channel, UUID recipientId,
                                                String recipientEmail, String subject,
                                                String body, String referenceType, UUID referenceId) {
        Notification notification = Notification.builder()
                .recipientId(recipientId)
                .recipientEmail(recipientEmail)
                .channel(channel)
                .subject(subject)
                .body(body)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .status(NotificationStatus.PENDING)
                .build();

        notification = notificationRepository.save(notification);
        deliverNotification(notification);
        return notification;
    }

    private void deliverNotification(Notification notification) {
        try {
            switch (notification.getChannel()) {
                case EMAIL -> deliverEmail(notification);
                case SMS -> deliverSms(notification);
                case WHATSAPP -> deliverWhatsApp(notification);
                case PUSH -> deliverPush(notification);
                case IN_APP -> log.info("[IN_APP] Notification stored for recipient: {}", notification.getRecipientId());
            }
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(OffsetDateTime.now());
            log.info("Notification sent: {} to {} via {}", notification.getId(),
                    notification.getRecipientEmail(), notification.getChannel());
        } catch (Exception e) {
            notification.setRetryCount(notification.getRetryCount() + 1);
            notification.setFailureReason(e.getMessage());
            if (notification.getRetryCount() >= MAX_RETRIES) {
                notification.setStatus(NotificationStatus.FAILED);
                log.error("Notification failed after {} retries: {}", MAX_RETRIES, notification.getId());
            } else {
                notification.setStatus(NotificationStatus.RETRYING);
                log.warn("Notification retry {}/{}: {}", notification.getRetryCount(), MAX_RETRIES, notification.getId());
            }
        }
        notificationRepository.save(notification);
    }

    private void deliverEmail(Notification notification) {
        log.info("[EMAIL] To: {} Subject: {} Body: {}",
                notification.getRecipientEmail(), notification.getSubject(),
                notification.getBody() != null ? notification.getBody().substring(0, Math.min(100, notification.getBody().length())) : "");
    }

    private void deliverSms(Notification notification) {
        log.info("[SMS] To: {} Body: {}", notification.getRecipientPhone(),
                notification.getBody() != null ? notification.getBody().substring(0, Math.min(100, notification.getBody().length())) : "");
    }

    private void deliverWhatsApp(Notification notification) {
        log.info("[WHATSAPP] To: {} Body: {}", notification.getRecipientPhone(),
                notification.getBody() != null ? notification.getBody().substring(0, Math.min(100, notification.getBody().length())) : "");
    }

    private void deliverPush(Notification notification) {
        log.info("[PUSH] To: {} Body: {}", notification.getRecipientId(),
                notification.getBody() != null ? notification.getBody().substring(0, Math.min(100, notification.getBody().length())) : "");
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void retryFailedNotifications() {
        List<Notification> retryable = notificationRepository
                .findByStatusAndRetryCountLessThan(NotificationStatus.RETRYING, MAX_RETRIES);
        for (Notification notification : retryable) {
            deliverNotification(notification);
        }
        if (!retryable.isEmpty()) {
            log.info("Retried {} notifications", retryable.size());
        }
    }

    public Page<Notification> getNotifications(UUID recipientId, Pageable pageable) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
    }

    public List<Notification> getNotificationsByReference(String referenceType, UUID referenceId) {
        return notificationRepository.findByReferenceTypeAndReferenceId(referenceType, referenceId);
    }

    public long getUnreadCount(UUID recipientId) {
        return notificationRepository.countByRecipientIdAndStatus(recipientId, NotificationStatus.SENT);
    }

    public List<NotificationTemplate> getAllTemplates() {
        return templateRepository.findAll();
    }

    @Transactional
    public NotificationTemplate updateTemplate(UUID id, String subject, String bodyTemplate) {
        NotificationTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found"));
        if (subject != null) template.setSubject(subject);
        if (bodyTemplate != null) template.setBodyTemplate(bodyTemplate);
        return templateRepository.save(template);
    }
}
