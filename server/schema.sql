-- Heart Rate Monitor - Database Schema
-- Run: mysql -u root < schema.sql

CREATE DATABASE IF NOT EXISTS heart_rate_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE heart_rate_db;

-- Nabız verileri
CREATE TABLE IF NOT EXISTS `heart_rate_logs` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `device_id` varchar(50) NOT NULL COMMENT 'Cihaz MAC adresi',
    `user_id` int(10) unsigned DEFAULT NULL COMMENT 'İlişkili kullanıcı (opsiyonel)',
    `heart_rate` smallint(5) unsigned NOT NULL COMMENT 'Nabız (BPM)',
    `rr_intervals` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'RR interval değerleri (ms)' CHECK (json_valid(`rr_intervals`)),
    `sensor_contact` tinyint(1) DEFAULT 1 COMMENT 'Sensör teması var mı',
    `battery_level` tinyint(3) unsigned DEFAULT NULL COMMENT 'Pil seviyesi (%)',
    `recorded_at` datetime(3) NOT NULL COMMENT 'Ölçüm zamanı (ms hassasiyet)',
    `created_at` timestamp NULL DEFAULT current_timestamp(),
    PRIMARY KEY (`id`),
    KEY `idx_device_time` (`device_id`, `recorded_at`),
    KEY `idx_user_time` (`user_id`, `recorded_at`),
    KEY `idx_recorded` (`recorded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Nabız verileri';

-- Nabız alarmları
CREATE TABLE IF NOT EXISTS `heart_rate_alerts` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `device_id` varchar(50) NOT NULL,
    `user_id` int(10) unsigned DEFAULT NULL,
    `alert_type` enum('low','high','no_signal') NOT NULL,
    `heart_rate` smallint(5) unsigned DEFAULT NULL,
    `message` text DEFAULT NULL,
    `acknowledged` tinyint(1) DEFAULT 0,
    `created_at` timestamp NULL DEFAULT current_timestamp(),
    `acknowledged_at` timestamp NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_device_time` (`device_id`, `created_at`),
    KEY `idx_unacknowledged` (`acknowledged`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Nabız alarmları';

-- Saatlik nabız istatistikleri
CREATE TABLE IF NOT EXISTS `heart_rate_stats` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `device_id` varchar(50) NOT NULL,
    `user_id` int(10) unsigned DEFAULT NULL,
    `stat_date` date NOT NULL,
    `stat_hour` tinyint(3) unsigned NOT NULL COMMENT '0-23 saat',
    `min_hr` smallint(5) unsigned DEFAULT NULL,
    `max_hr` smallint(5) unsigned DEFAULT NULL,
    `avg_hr` decimal(5,2) DEFAULT NULL,
    `sample_count` int(10) unsigned DEFAULT 0,
    `created_at` timestamp NULL DEFAULT current_timestamp(),
    `updated_at` timestamp NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_date_hour` (`device_id`, `stat_date`, `stat_hour`),
    KEY `idx_user_date` (`user_id`, `stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Saatlik nabız istatistikleri';
