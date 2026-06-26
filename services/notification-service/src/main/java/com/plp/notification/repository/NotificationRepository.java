package com.plp.notification.repository;

import com.plp.notification.model.entity.Notification;
import com.plp.notification.model.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);
    List<Notification> findByStatusAndRetryCountLessThan(NotificationStatus status, int maxRetries);
    List<Notification> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);
    long countByRecipientIdAndStatus(UUID recipientId, NotificationStatus status);

    @Query("""
            SELECT COUNT(n) > 0 FROM Notification n
            WHERE n.referenceType = :referenceType
              AND n.referenceId = :referenceId
              AND n.template.templateCode = :templateCode
              AND n.createdAt >= :since
              AND n.status IN ('SENT', 'PENDING', 'RETRYING')
            """)
    boolean wasSentSince(
            @Param("referenceType") String referenceType,
            @Param("referenceId") UUID referenceId,
            @Param("templateCode") String templateCode,
            @Param("since") OffsetDateTime since);
}
