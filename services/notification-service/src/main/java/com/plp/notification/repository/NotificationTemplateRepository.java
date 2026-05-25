package com.plp.notification.repository;

import com.plp.notification.model.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    Optional<NotificationTemplate> findByTemplateCode(String templateCode);
}
