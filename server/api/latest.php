<?php
/**
 * Son HR verisi API
 * Sadece en son kaydı döndürür (HR kartı için)
 */

header('Content-Type: application/json');
header('Cache-Control: no-cache');

require_once __DIR__ . '/../config.php';

try {
    $pdo = getDB();

    $sql = "SELECT heart_rate, battery_level, sensor_contact, recorded_at,
                   TIMESTAMPDIFF(SECOND, recorded_at, NOW()) as seconds_ago
            FROM heart_rate_logs
            WHERE device_id = ?
            ORDER BY recorded_at DESC
            LIMIT 1";

    $stmt = $pdo->prepare($sql);
    $stmt->execute([getDefaultDevice()]);
    $row = $stmt->fetch();

    if ($row) {
        echo json_encode([
            'success' => true,
            'data' => [
                'heart_rate' => (int)$row['heart_rate'],
                'battery_level' => $row['battery_level'],
                'sensor_contact' => (bool)$row['sensor_contact'],
                'recorded_at' => $row['recorded_at'],
                'seconds_ago' => (int)$row['seconds_ago'],
                'time' => date('H:i:s', strtotime($row['recorded_at']))
            ]
        ]);
    } else {
        echo json_encode(['success' => false, 'error' => 'No data']);
    }
} catch (Exception $e) {
    echo json_encode(['success' => false, 'error' => $e->getMessage()]);
}
