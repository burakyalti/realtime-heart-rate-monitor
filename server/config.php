<?php
/**
 * Heart Rate API Konfigürasyonu
 */

// Türkiye timezone'u - tüm timestamp işlemleri için
date_default_timezone_set('Europe/Istanbul');

// .env dosyasından ortam değişkenlerini yükle
$envFile = __DIR__ . '/.env';
if (file_exists($envFile)) {
    foreach (file($envFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES) as $line) {
        if (str_starts_with(trim($line), '#')) continue;
        if (str_contains($line, '=')) {
            putenv(trim($line));
        }
    }
}

// Veritabanı (ortam değişkenlerinden oku)
define('DB_HOST', getenv('HR_DB_HOST') ?: 'localhost');
define('DB_NAME', getenv('HR_DB_NAME') ?: 'heart_rate_db');
define('DB_USER', getenv('HR_DB_USER') ?: 'root');
define('DB_PASS', getenv('HR_DB_PASS') ?: '');

// API Güvenlik (ortam değişkenlerinden oku)
define('API_KEY', getenv('HR_API_KEY') ?: 'change-me');
$_hr_devices = getenv('HR_ALLOWED_DEVICES') ?: '';
define('ALLOWED_DEVICES', $_hr_devices ? explode(',', $_hr_devices) : []);

// Alarm eşikleri
define('ALERT_LOW_HR', 50);    // 50 BPM altı alarm
define('ALERT_HIGH_HR', 120);  // 120 BPM üstü alarm
define('ALERT_NO_SIGNAL_MINUTES', 5);  // 5 dakika veri gelmezse alarm

// Akıllı bildirim ayarları
define('ALERT_WINDOW_SECONDS', 10);     // Son kaç saniyeyi kontrol et (0 = anlık)
define('ALERT_MIN_EXCEED_COUNT', 1);    // Kaç kere eşik aşılmalı
define('ALERT_COOLDOWN_MINUTES', 1);    // Bildirimler arası bekleme (dakika)

// Rate limiting
define('MAX_REQUESTS_PER_MINUTE', 120);  // Dakikada max istek (saniyede 2)

// PDO bağlantısı
function getDB(): PDO {
    static $pdo = null;

    if ($pdo === null) {
        $dsn = 'mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=utf8mb4';
        $pdo = new PDO($dsn, DB_USER, DB_PASS, [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]);
    }

    return $pdo;
}

// JSON response helper
function jsonResponse(array $data, int $status = 200): void {
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('Access-Control-Allow-Origin: *');
    header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
    header('Access-Control-Allow-Headers: Content-Type, Authorization, X-API-Key');

    echo json_encode($data, JSON_UNESCAPED_UNICODE);
    exit;
}

// API key doğrulama
function validateApiKey(): void {
    $apiKey = $_SERVER['HTTP_X_API_KEY'] ?? $_GET['api_key'] ?? null;

    if ($apiKey !== API_KEY) {
        jsonResponse(['error' => 'Invalid API key'], 401);
    }
}

// Varsayılan cihaz MAC adresi
function getDefaultDevice(): string {
    return ALLOWED_DEVICES[0] ?? '';
}

// Cihaz MAC adresi doğrulama
function validateDevice(string $deviceId): void {
    if (!in_array(strtoupper($deviceId), ALLOWED_DEVICES)) {
        jsonResponse(['error' => 'Device not authorized'], 403);
    }
}
