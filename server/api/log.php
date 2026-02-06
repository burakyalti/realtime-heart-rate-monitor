<?php
/**
 * POST /hr/api/log.php
 * Nabız verisi kaydet
 *
 * Body (JSON):
 * {
 *   "device_id": "AA:BB:CC:DD:EE:FF",
 *   "heart_rate": 72,
 *   "rr_intervals": [820, 833],
 *   "sensor_contact": true,
 *   "battery_level": 85,
 *   "recorded_at": 1738764123456  // Unix timestamp (ms) veya ISO string desteklenir
 * }
 *
 * recorded_at formatları:
 * - Unix timestamp (milisaniye): 1738764123456 (13 haneli Long)
 * - ISO 8601 string: "2024-02-04T15:32:07.461" (eski format, geriye uyumluluk)
 */

require_once __DIR__ . '/../config.php';

// CORS preflight
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    header('Access-Control-Allow-Origin: *');
    header('Access-Control-Allow-Methods: POST, OPTIONS');
    header('Access-Control-Allow-Headers: Content-Type, X-API-Key');
    exit(0);
}

// Sadece POST
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    jsonResponse(['error' => 'Method not allowed'], 405);
}

// API key doğrula
validateApiKey();

// JSON body parse
$input = json_decode(file_get_contents('php://input'), true);

if (!$input) {
    jsonResponse(['error' => 'Invalid JSON body'], 400);
}

// Zorunlu alanlar
$deviceId = $input['device_id'] ?? null;
$heartRate = $input['heart_rate'] ?? null;

if (!$deviceId || !$heartRate) {
    jsonResponse(['error' => 'device_id and heart_rate are required'], 400);
}

// Cihaz doğrula
validateDevice($deviceId);

// Heart rate validasyonu
$heartRate = (int)$heartRate;
if ($heartRate < 20 || $heartRate > 250) {
    jsonResponse(['error' => 'Invalid heart_rate (20-250)'], 400);
}

// Opsiyonel alanlar
$rrIntervals = $input['rr_intervals'] ?? null;
$sensorContact = isset($input['sensor_contact']) ? (int)$input['sensor_contact'] : 1;
$batteryLevel = isset($input['battery_level']) ? (int)$input['battery_level'] : null;
$recordedAtInput = $input['recorded_at'] ?? null;

// Timestamp parse - hem Unix timestamp (ms) hem ISO 8601 string destekle
if ($recordedAtInput === null) {
    // Gönderilmemişse şimdiki zaman
    $recordedAt = date('Y-m-d H:i:s') . '.' . sprintf('%03d', (int)(microtime(true) * 1000) % 1000);
    $recordedAtUnix = time();
} elseif (is_numeric($recordedAtInput)) {
    // Unix timestamp (milisaniye) - 13 haneli Long
    // floatval kullan - büyük integer'lar için daha güvenli
    $timestampMs = floatval($recordedAtInput);
    $timestampSec = (int)floor($timestampMs / 1000);
    $milliseconds = (int)($timestampMs % 1000);
    $recordedAt = date('Y-m-d H:i:s', $timestampSec) . '.' . sprintf('%03d', $milliseconds);
    $recordedAtUnix = $timestampSec;

    // Debug log
    error_log("HR API: timestamp_ms=$timestampMs, sec=$timestampSec, ms=$milliseconds, formatted=$recordedAt");
} else {
    // ISO 8601 string (eski format - geriye uyumluluk)
    $recordedAt = str_replace('T', ' ', $recordedAtInput);
    $recordedAtUnix = strtotime($recordedAt);
}

try {
    $pdo = getDB();

    // Ana log kaydı
    $sql = "INSERT INTO heart_rate_logs
            (device_id, heart_rate, rr_intervals, sensor_contact, battery_level, recorded_at)
            VALUES (?, ?, ?, ?, ?, ?)";

    $stmt = $pdo->prepare($sql);
    $stmt->execute([
        strtoupper($deviceId),
        $heartRate,
        $rrIntervals ? json_encode($rrIntervals) : null,
        $sensorContact,
        $batteryLevel,
        $recordedAt,
    ]);

    $logId = $pdo->lastInsertId();

    // Saatlik istatistikleri güncelle
    $statDate = date('Y-m-d', $recordedAtUnix);
    $statHour = (int)date('H', $recordedAtUnix);

    $sqlStats = "INSERT INTO heart_rate_stats
                 (device_id, stat_date, stat_hour, min_hr, max_hr, avg_hr, sample_count)
                 VALUES (?, ?, ?, ?, ?, ?, 1)
                 ON DUPLICATE KEY UPDATE
                 min_hr = LEAST(min_hr, VALUES(min_hr)),
                 max_hr = GREATEST(max_hr, VALUES(max_hr)),
                 avg_hr = ((avg_hr * sample_count) + VALUES(avg_hr)) / (sample_count + 1),
                 sample_count = sample_count + 1";

    $stmtStats = $pdo->prepare($sqlStats);
    $stmtStats->execute([
        strtoupper($deviceId),
        $statDate,
        $statHour,
        $heartRate,
        $heartRate,
        $heartRate,
    ]);

    // Akıllı alarm kontrolü (pencereli + cooldown)
    $alertType = null;
    $alertMessage = null;
    $windowSeconds = ALERT_WINDOW_SECONDS;
    $minExceedCount = ALERT_MIN_EXCEED_COUNT;
    $cooldownMinutes = ALERT_COOLDOWN_MINUTES;
    $deviceIdUpper = strtoupper($deviceId);

    if ($windowSeconds == 0) {
        // Anlık kontrol - tek okuma ile karar ver
        if ($heartRate < ALERT_LOW_HR) {
            $alertType = 'low';
            $alertMessage = "Nabız çok düşük: {$heartRate} BPM";
        } elseif ($heartRate > ALERT_HIGH_HR) {
            $alertType = 'high';
            $alertMessage = "Nabız çok yüksek: {$heartRate} BPM";
        }
    } else {
        // Pencereli kontrol - son X saniyedeki aşımları say
        $windowStart = date('Y-m-d H:i:s', time() - $windowSeconds);

        // Yüksek nabız aşım sayısı
        $stmtHigh = $pdo->prepare("SELECT COUNT(*) FROM heart_rate_logs
            WHERE device_id = ? AND recorded_at >= ? AND heart_rate > ?");
        $stmtHigh->execute([$deviceIdUpper, $windowStart, ALERT_HIGH_HR]);
        $highExceedCount = (int)$stmtHigh->fetchColumn();

        // Düşük nabız aşım sayısı
        $stmtLow = $pdo->prepare("SELECT COUNT(*) FROM heart_rate_logs
            WHERE device_id = ? AND recorded_at >= ? AND heart_rate < ?");
        $stmtLow->execute([$deviceIdUpper, $windowStart, ALERT_LOW_HR]);
        $lowExceedCount = (int)$stmtLow->fetchColumn();

        if ($lowExceedCount >= $minExceedCount) {
            $alertType = 'low';
            $alertMessage = "Son {$windowSeconds}s'de {$lowExceedCount} düşük nabız: {$heartRate} BPM";
        } elseif ($highExceedCount >= $minExceedCount) {
            $alertType = 'high';
            $alertMessage = "Son {$windowSeconds}s'de {$highExceedCount} yüksek nabız: {$heartRate} BPM";
        }
    }

    if ($alertType) {
        // Cooldown kontrolü - son X dakikada aynı tipte alarm var mı
        $sqlAlertCheck = "SELECT id FROM heart_rate_alerts
                          WHERE device_id = ? AND alert_type = ?
                          AND created_at > DATE_SUB(NOW(), INTERVAL ? MINUTE)
                          LIMIT 1";
        $stmtCheck = $pdo->prepare($sqlAlertCheck);
        $stmtCheck->execute([$deviceIdUpper, $alertType, $cooldownMinutes]);

        if (!$stmtCheck->fetch()) {
            // Yeni alarm oluştur
            $sqlAlert = "INSERT INTO heart_rate_alerts (device_id, alert_type, heart_rate, message) VALUES (?, ?, ?, ?)";
            $stmtAlert = $pdo->prepare($sqlAlert);
            $stmtAlert->execute([$deviceIdUpper, $alertType, $heartRate, $alertMessage]);
        } else {
            // Cooldown içinde - alert tetiklenmedi
            $alertType = null;
        }
    }

    jsonResponse([
        'success' => true,
        'log_id' => (int)$logId,
        'heart_rate' => $heartRate,
        'recorded_at' => $recordedAt,
        'alert' => $alertType,
    ]);

} catch (PDOException $e) {
    error_log("HR API Error: " . $e->getMessage());
    jsonResponse(['error' => 'Database error'], 500);
}
