package com.plp.notification.repository;

import com.plp.notification.model.entity.Notification;
import com.plp.notification.model.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);
    List<Notification> findByStatusAndRetryCountLessThan(NotificationStatus status, int maxRetries);
    List<Notification> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);
    long countByRecipientIdAndStatus(UUID recipientId, NotificationStatus status);
}
