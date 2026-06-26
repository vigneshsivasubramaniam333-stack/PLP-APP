package com.plp.notification.repository;

import com.plp.notification.model.entity.NotificationEventSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationEventSettingRepository extends JpaRepository<NotificationEventSetting, String> {

    List<NotificationEventSetting> findAllByOrderByEventCodeAsc();
}
