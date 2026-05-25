package com.plp.notification.controller;

import com.plp.notification.model.entity.Notification;
import com.plp.notification.model.entity.NotificationTemplate;
import com.plp.notification.model.enums.NotificationChannel;
import com.plp.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotifications(
            @RequestParam UUID recipientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Notification> notifications = notificationService.getNotifications(recipientId, PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", notifications.getContent(),
                "totalElements", notifications.getTotalElements(),
                "totalPages", notifications.getTotalPages()
        ));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(@RequestParam UUID recipientId) {
        long count = notificationService.getUnreadCount(recipientId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", count));
    }

    @GetMapping("/by-reference")
    public ResponseEntity<Map<String, Object>> getByReference(
            @RequestParam String referenceType, @RequestParam UUID referenceId) {
        List<Notification> notifications = notificationService.getNotificationsByReference(referenceType, referenceId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", notifications));
    }

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendNotification(@RequestBody Map<String, Object> request) {
        String templateCode = (String) request.get("templateCode");
        UUID recipientId = UUID.fromString(request.get("recipientId").toString());
        String recipientEmail = (String) request.get("recipientEmail");
        String recipientPhone = (String) request.get("recipientPhone");
        @SuppressWarnings("unchecked")
        Map<String, String> variables = (Map<String, String>) request.getOrDefault("variables", Map.of());
        String referenceType = (String) request.get("referenceType");
        UUID referenceId = request.get("referenceId") != null ?
                UUID.fromString(request.get("referenceId").toString()) : null;

        Notification notification = notificationService.sendNotification(templateCode, recipientId,
                recipientEmail, recipientPhone, variables, referenceType, referenceId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", notification.getId()));
    }

    @PostMapping("/send-direct")
    public ResponseEntity<Map<String, Object>> sendDirect(@RequestBody Map<String, Object> request) {
        NotificationChannel channel = NotificationChannel.valueOf((String) request.get("channel"));
        UUID recipientId = UUID.fromString(request.get("recipientId").toString());
        String recipientEmail = (String) request.get("recipientEmail");
        String subject = (String) request.get("subject");
        String body = (String) request.get("body");
        String referenceType = (String) request.get("referenceType");
        UUID referenceId = request.get("referenceId") != null ?
                UUID.fromString(request.get("referenceId").toString()) : null;

        Notification notification = notificationService.sendDirectNotification(channel, recipientId,
                recipientEmail, subject, body, referenceType, referenceId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", notification.getId()));
    }

    @GetMapping("/templates")
    public ResponseEntity<Map<String, Object>> getTemplates() {
        List<NotificationTemplate> templates = notificationService.getAllTemplates();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", templates));
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<Map<String, Object>> updateTemplate(
            @PathVariable UUID id, @RequestBody Map<String, String> request) {
        NotificationTemplate template = notificationService.updateTemplate(id,
                request.get("subject"), request.get("bodyTemplate"));
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", template));
    }
}
