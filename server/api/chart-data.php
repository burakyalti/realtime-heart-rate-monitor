<?php
/**
 * GET /hr/api/chart-data.php
 * Grafik için nabız verisi döndürür
 *
 * Parametreler:
 * - minutes: Son X dakika (varsayılan: 3)
 * - start: Özel başlangıç zamanı (Unix timestamp veya Y-m-d H:i:s)
 * - end: Özel bitiş zamanı (Unix timestamp veya Y-m-d H:i:s)
 */

require_once __DIR__ . '/../config.php';

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');

$deviceId = getDefaultDevice();

// Parametreleri al
$seconds = isset($_GET['seconds']) ? (int)$_GET['seconds'] : null;
$minutes = isset($_GET['minutes']) ? (int)$_GET['minutes'] : null;
$startTime = $_GET['start'] ?? null;
$endTime = $_GET['end'] ?? null;

// Saniye hesapla (varsayılan 10 saniye)
if ($seconds) {
    $totalSeconds = $seconds;
} elseif ($minutes) {
    $totalSeconds = $minutes * 60;
} else {
    $totalSeconds = 10;
}

try {
    $pdo = getDB();

    // Özel zaman aralığı mı yoksa son X dakika mı?
    if ($startTime && $endTime) {
        // Özel zaman aralığı
        // Unix timestamp ise dönüştür
        if (is_numeric($startTime)) {
            $startTime = date('Y-m-d H:i:s', (int)$startTime);
        }
        if (is_numeric($endTime)) {
            $endTime = date('Y-m-d H:i:s', (int)$endTime);
        }

        $sql = "SELECT heart_rate, recorded_at, rr_intervals
                FROM heart_rate_logs
                WHERE device_id = ?
                  AND recorded_at >= ?
                  AND recorded_at <= ?
                ORDER BY recorded_at ASC";
        $stmt = $pdo->prepare($sql);
        $stmt->execute([$deviceId, $startTime, $endTime]);
    } else {
        // Son X saniye
        $sql = "SELECT heart_rate, recorded_at, rr_intervals
                FROM heart_rate_logs
                WHERE device_id = ?
                  AND recorded_at >= DATE_SUB(NOW(), INTERVAL ? SECOND)
                ORDER BY recorded_at ASC";
        $stmt = $pdo->prepare($sql);
        $stmt->execute([$deviceId, $totalSeconds]);
    }

    $rows = $stmt->fetchAll();

    // Grafik verilerini hazırla
    $hrData = [];      // Nabız grafiği için
    $ekgData = [];     // EKG grafiği için
    $labels = [];      // Zaman etiketleri
    $timestamps = [];  // Unix timestamp (ms)

    foreach ($rows as $row) {
        $dt = new DateTime($row['recorded_at']);
        $timestampMs = (int)($dt->getTimestamp() * 1000) + (int)$dt->format('v');

        $timestamps[] = $timestampMs;
        $labels[] = $dt->format('H:i:s.v');
        $hrData[] = (int)$row['heart_rate'];

        // EKG için: her veri noktası bir atım
        // RR intervals varsa kullan, yoksa varsayılan peak
        $ekgData[] = [
            'x' => $timestampMs,
            'y' => 1  // Peak noktası
        ];
    }

    // İstatistikler
    $stats = [];
    if (!empty($hrData)) {
        $stats = [
            'min' => min($hrData),
            'max' => max($hrData),
            'avg' => round(array_sum($hrData) / count($hrData)),
            'count' => count($hrData)
        ];
    }

    echo json_encode([
        'success' => true,
        'data' => [
            'timestamps' => $timestamps,
            'labels' => $labels,
            'heartRate' => $hrData,
            'ekg' => $ekgData,
            'stats' => $stats
        ],
        'range' => [
            'start' => !empty($timestamps) ? min($timestamps) : null,
            'end' => !empty($timestamps) ? max($timestamps) : null,
            'seconds' => $totalSeconds
        ]
    ], JSON_UNESCAPED_UNICODE);

} catch (PDOException $e) {
    error_log("Chart API Error: " . $e->getMessage());
    http_response_code(500);
    echo json_encode(['error' => 'Database error']);
}
