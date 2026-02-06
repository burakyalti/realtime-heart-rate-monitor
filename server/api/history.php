<?php
/**
 * GET /hr/api/history.php
 * Nabız geçmişini getir
 *
 * Query params:
 *   device_id - Cihaz MAC adresi (zorunlu)
 *   minutes   - Son X dakika (varsayılan: 60)
 *   limit     - Max kayıt sayısı (varsayılan: 1000)
 */

require_once __DIR__ . '/../config.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    jsonResponse(['error' => 'Method not allowed'], 405);
}

validateApiKey();

$deviceId = $_GET['device_id'] ?? null;
$minutes = (int)($_GET['minutes'] ?? 60);
$limit = min((int)($_GET['limit'] ?? 1000), 5000);  // Max 5000

if (!$deviceId) {
    jsonResponse(['error' => 'device_id is required'], 400);
}

try {
    $pdo = getDB();

    $sql = "SELECT heart_rate, rr_intervals, sensor_contact, recorded_at
            FROM heart_rate_logs
            WHERE device_id = ?
              AND recorded_at > DATE_SUB(NOW(), INTERVAL ? MINUTE)
            ORDER BY recorded_at DESC
            LIMIT ?";

    $stmt = $pdo->prepare($sql);
    $stmt->execute([strtoupper($deviceId), $minutes, $limit]);
    $rows = $stmt->fetchAll();

    // RR intervals decode
    foreach ($rows as &$row) {
        if ($row['rr_intervals']) {
            $row['rr_intervals'] = json_decode($row['rr_intervals']);
        }
    }

    // İstatistikler
    $stats = [
        'count' => count($rows),
        'min' => null,
        'max' => null,
        'avg' => null,
    ];

    if ($rows) {
        $hrs = array_column($rows, 'heart_rate');
        $stats['min'] = min($hrs);
        $stats['max'] = max($hrs);
        $stats['avg'] = round(array_sum($hrs) / count($hrs), 1);
    }

    jsonResponse([
        'device_id' => strtoupper($deviceId),
        'period_minutes' => $minutes,
        'stats' => $stats,
        'data' => $rows,
    ]);

} catch (PDOException $e) {
    error_log("HR API Error: " . $e->getMessage());
    jsonResponse(['error' => 'Database error'], 500);
}
