<?php
/**
 * GET /hr/api/alerts.php
 * Aktif alarmları getir
 *
 * Query params:
 *   device_id - Cihaz MAC adresi (opsiyonel)
 *   unread    - Sadece okunmamış (varsayılan: 1)
 */

require_once __DIR__ . '/../config.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    jsonResponse(['error' => 'Method not allowed'], 405);
}

validateApiKey();

$deviceId = $_GET['device_id'] ?? null;
$unreadOnly = (int)($_GET['unread'] ?? 1);

try {
    $pdo = getDB();

    $sql = "SELECT id, device_id, alert_type, heart_rate, message,
                   acknowledged, created_at
            FROM heart_rate_alerts
            WHERE 1=1";
    $params = [];

    if ($deviceId) {
        $sql .= " AND device_id = ?";
        $params[] = strtoupper($deviceId);
    }

    if ($unreadOnly) {
        $sql .= " AND acknowledged = 0";
    }

    $sql .= " ORDER BY created_at DESC LIMIT 100";

    $stmt = $pdo->prepare($sql);
    $stmt->execute($params);
    $alerts = $stmt->fetchAll();

    // Sinyal kaybı kontrolü (son 5 dakikada veri yok)
    $sqlLastData = "SELECT device_id,
                           TIMESTAMPDIFF(MINUTE, MAX(recorded_at), NOW()) as minutes_ago
                    FROM heart_rate_logs
                    GROUP BY device_id
                    HAVING minutes_ago > ?";

    $stmtSignal = $pdo->prepare($sqlLastData);
    $stmtSignal->execute([ALERT_NO_SIGNAL_MINUTES]);
    $noSignalDevices = $stmtSignal->fetchAll();

    foreach ($noSignalDevices as $device) {
        $alerts[] = [
            'id' => null,
            'device_id' => $device['device_id'],
            'alert_type' => 'no_signal',
            'heart_rate' => null,
            'message' => "Son {$device['minutes_ago']} dakikadır sinyal yok!",
            'acknowledged' => 0,
            'created_at' => date('Y-m-d H:i:s'),
        ];
    }

    jsonResponse([
        'count' => count($alerts),
        'alerts' => $alerts,
    ]);

} catch (PDOException $e) {
    error_log("HR API Error: " . $e->getMessage());
    jsonResponse(['error' => 'Database error'], 500);
}
