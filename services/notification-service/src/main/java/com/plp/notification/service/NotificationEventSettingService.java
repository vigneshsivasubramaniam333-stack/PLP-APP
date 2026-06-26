package com.plp.notification.service;

import com.plp.notification.model.entity.NotificationEventSetting;
import com.plp.notification.repository.NotificationEventSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationEventSettingService {

    private final NotificationEventSettingRepository repository;

    public List<NotificationEventSetting> listAll() {
        return repository.findAllByOrderByEventCodeAsc();
    }

    public boolean isEnabled(String eventCode) {
        return repository.findById(eventCode).map(NotificationEventSetting::isEnabled).orElse(true);
    }

    @Transactional
    public NotificationEventSetting updateEnabled(String eventCode, boolean enabled) {
        NotificationEventSetting setting = repository.findById(eventCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown notification event: " + eventCode));
        setting.setEnabled(enabled);
        return repository.save(setting);
    }
}
