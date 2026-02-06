<?php
/**
 * Heart Rate SSE (Server-Sent Events)
 * Yeni veri geldiğinde anında client'a push eder
 */

// SSE headers
header('Content-Type: text/event-stream');
header('Cache-Control: no-cache');
header('Connection: keep-alive');
header('Access-Control-Allow-Origin: *');
header('X-Accel-Buffering: no'); // nginx buffering kapalı

// Output buffering kapalı
if (ob_get_level()) ob_end_clean();

require_once __DIR__ . '/../config.php';

$deviceId = getDefaultDevice();
$lastId = 0;
$maxRuntime = 60; // 1 dakika sonra bağlantıyı kes (client otomatik yeniden bağlanır)
$startTime = time();
$checkInterval = 1; // 1 saniye

// İlk bağlantıda son ID'yi al
try {
    $pdo = getDB();
    $stmt = $pdo->prepare("SELECT id FROM heart_rate_logs WHERE device_id = ? ORDER BY id DESC LIMIT 1");
    $stmt->execute([$deviceId]);
    $row = $stmt->fetch();
    $lastId = $row ? (int)$row['id'] : 0;
} catch (Exception $e) {
    sendSSE('error', ['message' => $e->getMessage()]);
    exit;
}

// Bağlantı bilgisi gönder
sendSSE('connected', ['lastId' => $lastId, 'time' => date('H:i:s')]);

// Ana loop
while (true) {
    // Timeout kontrolü
    if ((time() - $startTime) >= $maxRuntime) {
        sendSSE('timeout', ['message' => 'Reconnect required']);
        break;
    }

    // Client bağlantısı koptu mu?
    if (connection_aborted()) break;

    try {
        // Yeni kayıt var mı kontrol et
        $stmt = $pdo->prepare(
            "SELECT id, heart_rate, battery_level, sensor_contact, recorded_at,
                    TIMESTAMPDIFF(SECOND, recorded_at, NOW()) as seconds_ago
             FROM heart_rate_logs
             WHERE device_id = ? AND id > ?
             ORDER BY id ASC"
        );
        $stmt->execute([$deviceId, $lastId]);
        $newRows = $stmt->fetchAll();

        if (!empty($newRows)) {
            // Son kaydı HR kartı için gönder
            $latest = end($newRows);
            $lastId = (int)$latest['id'];

            sendSSE('heartbeat', [
                'heart_rate'     => (int)$latest['heart_rate'],
                'battery_level'  => $latest['battery_level'],
                'sensor_contact' => (bool)$latest['sensor_contact'],
                'recorded_at'    => $latest['recorded_at'],
                'seconds_ago'    => (int)$latest['seconds_ago'],
                'time'           => date('H:i:s', strtotime($latest['recorded_at'])),
                'new_count'      => count($newRows),
                'last_id'        => $lastId
            ]);
        }
    } catch (Exception $e) {
        // DB bağlantısı kopmuş olabilir, yeniden bağlan
        try {
            $pdo = getDB();
        } catch (Exception $e2) {
            sendSSE('error', ['message' => 'DB connection lost']);
            break;
        }
    }

    // Flush ve bekle
    sleep($checkInterval);
}

function sendSSE(string $event, array $data): void {
    echo "event: {$event}\n";
    echo "data: " . json_encode($data, JSON_UNESCAPED_UNICODE) . "\n\n";
    flush();
}
