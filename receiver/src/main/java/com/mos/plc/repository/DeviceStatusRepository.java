package com.mos.plc.repository;

import com.mos.plc.entity.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceStatusRepository extends JpaRepository<DeviceStatus, String> {
}
