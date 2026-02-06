<?php
/**
 * Dakikalık Ortalama HR API
 * Son X saatin dakika bazında ortalamasını döndürür
 */

header('Content-Type: application/json');
header('Cache-Control: no-cache');

require_once __DIR__ . '/../config.php';

$hours = max(1, min(24, (int)($_GET['hours'] ?? 1)));
$deviceId = getDefaultDevice();

try {
    $pdo = getDB();

    $sql = "SELECT
                DATE_FORMAT(recorded_at, '%Y-%m-%d %H:%i') as minute_key,
                DATE_FORMAT(recorded_at, '%H:%i') as label,
                COUNT(*) as readings,
                ROUND(AVG(heart_rate)) as avg_hr,
                MIN(heart_rate) as min_hr,
                MAX(heart_rate) as max_hr
            FROM heart_rate_logs
            WHERE device_id = ? AND recorded_at >= DATE_SUB(NOW(), INTERVAL ? HOUR)
            GROUP BY minute_key
            ORDER BY minute_key ASC";

    $stmt = $pdo->prepare($sql);
    $stmt->execute([$deviceId, $hours]);
    $rows = $stmt->fetchAll();

    $labels = [];
    $avgHR = [];
    $minHR = [];
    $maxHR = [];
    $counts = [];

    foreach ($rows as $row) {
        $labels[] = $row['label'];
        $avgHR[] = (int)$row['avg_hr'];
        $minHR[] = (int)$row['min_hr'];
        $maxHR[] = (int)$row['max_hr'];
        $counts[] = (int)$row['readings'];
    }

    echo json_encode([
        'success' => true,
        'data' => [
            'labels' => $labels,
            'avgHR' => $avgHR,
            'minHR' => $minHR,
            'maxHR' => $maxHR,
            'counts' => $counts,
            'totalMinutes' => count($labels),
            'hours' => $hours
        ]
    ]);
} catch (Exception $e) {
    echo json_encode(['success' => false, 'error' => $e->getMessage()]);
}
