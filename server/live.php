<?php
/**
 * Heart Rate Live Dashboard
 * Gerçek zamanlı nabız takip paneli + Geçmiş
 */

require_once __DIR__ . '/config.php';

$activeTab = $_GET['tab'] ?? 'live';
$page = max(1, (int)($_GET['page'] ?? 1));
$perPage = 50;

// Sıralama parametreleri
$sortBy = $_GET['sort'] ?? 'recorded_at';
$sortDir = strtoupper($_GET['dir'] ?? 'DESC');

// Güvenlik: sadece izin verilen sütunlar
$allowedSorts = ['recorded_at', 'heart_rate'];
if (!in_array($sortBy, $allowedSorts)) $sortBy = 'recorded_at';
if (!in_array($sortDir, ['ASC', 'DESC'])) $sortDir = 'DESC';

// Son verileri çek
function getLatestData(): ?array {
    try {
        $pdo = getDB();
        $sql = "SELECT device_id, heart_rate, rr_intervals, sensor_contact,
                       battery_level, recorded_at,
                       TIMESTAMPDIFF(SECOND, recorded_at, NOW()) as seconds_ago
                FROM heart_rate_logs
                WHERE device_id = ?
                ORDER BY recorded_at DESC
                LIMIT 1";
        $stmt = $pdo->prepare($sql);
        $stmt->execute([getDefaultDevice()]);
        return $stmt->fetch() ?: null;
    } catch (Exception $e) {
        return null;
    }
}

// Son X dakikalık veri (grafik için)
function getRecentData(int $minutes = 5): array {
    try {
        $pdo = getDB();
        $sql = "SELECT heart_rate, recorded_at
                FROM heart_rate_logs
                WHERE device_id = ? AND recorded_at >= DATE_SUB(NOW(), INTERVAL ? MINUTE)
                ORDER BY recorded_at ASC";
        $stmt = $pdo->prepare($sql);
        $stmt->execute([getDefaultDevice(), $minutes]);
        return $stmt->fetchAll();
    } catch (Exception $e) {
        return [];
    }
}

// İstatistikler
function getStats(): array {
    try {
        $pdo = getDB();
        $sql = "SELECT
                    COUNT(*) as total_readings,
                    AVG(heart_rate) as avg_hr,
                    MIN(heart_rate) as min_hr,
                    MAX(heart_rate) as max_hr,
                    MIN(recorded_at) as first_reading,
                    MAX(recorded_at) as last_reading
                FROM heart_rate_logs
                WHERE device_id = ? AND DATE(recorded_at) = CURDATE()";
        $stmt = $pdo->prepare($sql);
        $stmt->execute([getDefaultDevice()]);
        return $stmt->fetch() ?: [];
    } catch (Exception $e) {
        return [];
    }
}

// Geçmiş verileri (pagination ve sıralama ile)
function getHistoryData(int $page, int $perPage, string $sortBy, string $sortDir): array {
    try {
        $pdo = getDB();
        $offset = ($page - 1) * $perPage;

        // Toplam sayı
        $countSql = "SELECT COUNT(*) FROM heart_rate_logs WHERE device_id = ?";
        $countStmt = $pdo->prepare($countSql);
        $countStmt->execute([getDefaultDevice()]);
        $total = (int)$countStmt->fetchColumn();

        // Veriler (sıralama ile)
        $sql = "SELECT id, heart_rate, rr_intervals, sensor_contact, battery_level, recorded_at
                FROM heart_rate_logs
                WHERE device_id = ?
                ORDER BY {$sortBy} {$sortDir}
                LIMIT ? OFFSET ?";
        $stmt = $pdo->prepare($sql);
        $stmt->execute([getDefaultDevice(), $perPage, $offset]);
        $data = $stmt->fetchAll();

        return [
            'data' => $data,
            'total' => $total,
            'pages' => ceil($total / $perPage),
            'current' => $page
        ];
    } catch (Exception $e) {
        return ['data' => [], 'total' => 0, 'pages' => 0, 'current' => 1];
    }
}

// Saatlik özet
function getHourlySummary(): array {
    try {
        $pdo = getDB();
        $sql = "SELECT
                    DATE_FORMAT(recorded_at, '%Y-%m-%d %H:00') as hour,
                    COUNT(*) as readings,
                    ROUND(AVG(heart_rate)) as avg_hr,
                    MIN(heart_rate) as min_hr,
                    MAX(heart_rate) as max_hr
                FROM heart_rate_logs
                WHERE device_id = ? AND recorded_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
                GROUP BY DATE_FORMAT(recorded_at, '%Y-%m-%d %H:00')
                ORDER BY hour DESC";
        $stmt = $pdo->prepare($sql);
        $stmt->execute([getDefaultDevice()]);
        return $stmt->fetchAll();
    } catch (Exception $e) {
        return [];
    }
}

$latest = getLatestData();
$recentData = getRecentData(5);
$stats = getStats();
$history = getHistoryData($page, $perPage, $sortBy, $sortDir);
$hourlySummary = getHourlySummary();

// Sıralama yönü değiştirme fonksiyonu (sıralama değişince sayfa 1'e döner)
function getSortUrl($column, $currentSort, $currentDir) {
    $newDir = ($currentSort === $column && $currentDir === 'DESC') ? 'ASC' : 'DESC';
    return "?tab=history&sort={$column}&dir={$newDir}&page=1";
}

function getSortIcon($column, $currentSort, $currentDir) {
    if ($currentSort !== $column) return '↕';
    return $currentDir === 'DESC' ? '↓' : '↑';
}

// Grafik verileri
$chartLabels = [];
$chartData = [];
foreach ($recentData as $row) {
    $chartLabels[] = date('H:i:s', strtotime($row['recorded_at']));
    $chartData[] = (int)$row['heart_rate'];
}

// Saatlik grafik verileri
$hourlyLabels = [];
$hourlyData = [];
foreach (array_reverse($hourlySummary) as $row) {
    $hourlyLabels[] = date('H:i', strtotime($row['hour']));
    $hourlyData[] = (int)$row['avg_hr'];
}
?>
<!DOCTYPE html>
<html lang="tr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <!-- Auto refresh artık JavaScript ile yapılıyor -->
    <title>❤️ Heart Rate <?= $activeTab === 'live' ? 'Live' : 'History' ?></title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <!-- noUiSlider for Range Selection -->
    <link href="https://cdn.jsdelivr.net/npm/nouislider@15.7.1/dist/nouislider.min.css" rel="stylesheet">
    <script src="https://cdn.jsdelivr.net/npm/nouislider@15.7.1/dist/nouislider.min.js"></script>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
            min-height: 100vh;
            color: #fff;
            padding: 20px;
            padding-bottom: 100px;
        }

        .container {
            max-width: 900px;
            margin: 0 auto;
        }

        .header {
            text-align: center;
            margin-bottom: 20px;
        }

        .header h1 {
            font-size: 1.5rem;
            font-weight: 300;
            opacity: 0.9;
        }

        /* Tab Navigation */
        .tabs {
            display: flex;
            justify-content: center;
            gap: 10px;
            margin-bottom: 25px;
        }

        .tab-btn {
            padding: 12px 30px;
            border: none;
            border-radius: 25px;
            font-size: 14px;
            font-weight: 500;
            cursor: pointer;
            transition: all 0.3s ease;
            text-decoration: none;
            color: rgba(255, 255, 255, 0.6);
            background: rgba(255, 255, 255, 0.05);
            border: 1px solid rgba(255, 255, 255, 0.1);
        }

        .tab-btn:hover {
            background: rgba(255, 255, 255, 0.1);
            color: #fff;
        }

        .tab-btn.active {
            background: linear-gradient(135deg, #ff6b6b, #ee5a5a);
            color: #fff;
            border-color: transparent;
        }

        .status-badge {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            padding: 8px 16px;
            border-radius: 20px;
            font-size: 0.85rem;
            margin-top: 10px;
        }

        .status-online {
            background: rgba(46, 213, 115, 0.2);
            color: #2ed573;
            border: 1px solid rgba(46, 213, 115, 0.3);
        }

        .status-offline {
            background: rgba(255, 71, 87, 0.2);
            color: #ff4757;
            border: 1px solid rgba(255, 71, 87, 0.3);
        }

        .status-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            animation: pulse 2s infinite;
        }

        .status-online .status-dot { background: #2ed573; }
        .status-offline .status-dot { background: #ff4757; }

        @keyframes pulse {
            0%, 100% { opacity: 1; transform: scale(1); }
            50% { opacity: 0.5; transform: scale(1.2); }
        }

        /* HR Card */
        .hr-card {
            background: rgba(255, 255, 255, 0.05);
            border-radius: 24px;
            padding: 40px;
            text-align: center;
            margin-bottom: 20px;
            backdrop-filter: blur(10px);
            border: 1px solid rgba(255, 255, 255, 0.1);
        }

        .heart-icon {
            font-size: 60px;
            animation: heartbeat 1s ease-in-out infinite;
            display: inline-block;
        }

        @keyframes heartbeat {
            0%, 100% { transform: scale(1); }
            15% { transform: scale(1.15); }
            30% { transform: scale(1); }
            45% { transform: scale(1.1); }
        }

        .hr-value {
            font-size: 120px;
            font-weight: 700;
            line-height: 1;
            margin: 20px 0 10px;
            background: linear-gradient(135deg, #ff6b6b, #ee5a5a);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
        }

        .hr-unit {
            font-size: 24px;
            opacity: 0.7;
            font-weight: 300;
        }

        .hr-status {
            display: inline-block;
            padding: 8px 20px;
            border-radius: 20px;
            font-size: 14px;
            font-weight: 500;
            margin-top: 15px;
        }

        .hr-normal { background: rgba(46, 213, 115, 0.2); color: #2ed573; }
        .hr-low { background: rgba(52, 152, 219, 0.2); color: #3498db; }
        .hr-high { background: rgba(255, 165, 2, 0.2); color: #ffa502; }

        /* Info Grid */
        .info-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 15px;
            margin-bottom: 20px;
        }

        @media (min-width: 600px) {
            .info-grid { grid-template-columns: repeat(4, 1fr); }
        }

        .info-card {
            background: rgba(255, 255, 255, 0.05);
            border-radius: 16px;
            padding: 20px;
            text-align: center;
            backdrop-filter: blur(10px);
            border: 1px solid rgba(255, 255, 255, 0.1);
        }

        .info-card .icon { font-size: 24px; margin-bottom: 8px; }
        .info-card .value { font-size: 24px; font-weight: 600; margin-bottom: 4px; }
        .info-card .label { font-size: 12px; opacity: 0.6; text-transform: uppercase; letter-spacing: 1px; }

        /* Cards */
        .card {
            background: rgba(255, 255, 255, 0.05);
            border-radius: 16px;
            padding: 20px;
            margin-bottom: 20px;
            backdrop-filter: blur(10px);
            border: 1px solid rgba(255, 255, 255, 0.1);
        }

        .card h3 {
            font-size: 14px;
            opacity: 0.7;
            margin-bottom: 15px;
            font-weight: 400;
        }

        /* RR Intervals */
        .rr-values {
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
        }

        .rr-chip {
            background: rgba(155, 89, 182, 0.2);
            color: #9b59b6;
            padding: 8px 16px;
            border-radius: 20px;
            font-size: 14px;
            font-weight: 500;
        }

        /* Chart */
        .chart-container {
            position: relative;
            height: 200px;
        }

        @media (min-width: 600px) {
            .chart-container { height: 250px; }
        }

        /* Stats Grid */
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 15px;
        }

        .stat-item { text-align: center; }
        .stat-item .value { font-size: 28px; font-weight: 600; }
        .stat-item .label { font-size: 11px; opacity: 0.5; text-transform: uppercase; letter-spacing: 1px; }
        .stat-min .value { color: #3498db; }
        .stat-avg .value { color: #2ed573; }
        .stat-max .value { color: #ff6b6b; }

        /* History Table */
        .history-table {
            width: 100%;
            border-collapse: collapse;
        }

        .history-table th,
        .history-table td {
            padding: 12px 8px;
            text-align: left;
            border-bottom: 1px solid rgba(255, 255, 255, 0.05);
        }

        .history-table th {
            font-size: 11px;
            text-transform: uppercase;
            letter-spacing: 1px;
            opacity: 0.5;
            font-weight: 500;
        }

        .history-table td {
            font-size: 14px;
        }

        .history-table tr:hover {
            background: rgba(255, 255, 255, 0.03);
        }

        .hr-badge {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 12px;
            font-weight: 600;
            font-size: 13px;
        }

        .hr-badge-normal { background: rgba(46, 213, 115, 0.2); color: #2ed573; }
        .hr-badge-low { background: rgba(52, 152, 219, 0.2); color: #3498db; }
        .hr-badge-high { background: rgba(255, 165, 2, 0.2); color: #ffa502; }

        /* Sort Links */
        .sort-link {
            color: rgba(255, 255, 255, 0.5);
            text-decoration: none;
            display: inline-flex;
            align-items: center;
            gap: 4px;
            padding: 4px 8px;
            border-radius: 6px;
            transition: all 0.2s;
        }

        .sort-link:hover {
            color: #fff;
            background: rgba(255, 255, 255, 0.1);
        }

        .sort-link.active {
            color: #ff6b6b;
        }

        /* DateTime Cell */
        .datetime-cell {
            display: flex;
            flex-direction: column;
            gap: 2px;
        }

        .datetime-cell .date {
            font-weight: 500;
        }

        .datetime-cell .time {
            font-size: 12px;
            opacity: 0.6;
        }

        /* Pagination */
        .pagination {
            display: flex;
            justify-content: center;
            gap: 8px;
            margin-top: 20px;
            flex-wrap: wrap;
        }

        .pagination a,
        .pagination span {
            padding: 10px 16px;
            border-radius: 8px;
            text-decoration: none;
            font-size: 14px;
            transition: all 0.2s;
        }

        .pagination a {
            background: rgba(255, 255, 255, 0.05);
            color: rgba(255, 255, 255, 0.7);
            border: 1px solid rgba(255, 255, 255, 0.1);
        }

        .pagination a:hover {
            background: rgba(255, 255, 255, 0.1);
            color: #fff;
        }

        .pagination .active {
            background: linear-gradient(135deg, #ff6b6b, #ee5a5a);
            color: #fff;
            border: none;
        }

        .pagination .disabled {
            opacity: 0.3;
            pointer-events: none;
        }

        /* Hourly Summary */
        .hourly-table {
            width: 100%;
            border-collapse: collapse;
        }

        .hourly-table th,
        .hourly-table td {
            padding: 10px 8px;
            text-align: center;
            border-bottom: 1px solid rgba(255, 255, 255, 0.05);
            font-size: 13px;
        }

        .hourly-table th {
            font-size: 10px;
            text-transform: uppercase;
            letter-spacing: 1px;
            opacity: 0.5;
        }

        /* No Data */
        .no-data {
            text-align: center;
            padding: 60px 20px;
            opacity: 0.5;
        }

        .no-data .icon { font-size: 60px; margin-bottom: 20px; }

        /* Footer */
        .footer {
            text-align: center;
            margin-top: 30px;
            font-size: 12px;
            opacity: 0.4;
        }

        .last-update {
            font-size: 11px;
            opacity: 0.5;
            margin-top: 10px;
        }

        /* Chart Header & Time Selector */
        .chart-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 15px;
            flex-wrap: wrap;
            gap: 10px;
        }

        .chart-header h3 {
            margin: 0;
        }

        .time-selector {
            display: flex;
            gap: 6px;
            flex-wrap: wrap;
        }

        .time-btn {
            padding: 8px 14px;
            border: 1px solid rgba(255, 255, 255, 0.2);
            background: rgba(255, 255, 255, 0.05);
            color: rgba(255, 255, 255, 0.7);
            border-radius: 20px;
            font-size: 12px;
            cursor: pointer;
            transition: all 0.2s;
        }

        .time-btn:hover {
            background: rgba(255, 255, 255, 0.1);
            color: #fff;
        }

        .time-btn.active {
            background: linear-gradient(135deg, #ff6b6b, #ee5a5a);
            color: #fff;
            border-color: transparent;
        }

        .pause-btn {
            font-size: 14px;
        }

        .pause-btn.paused {
            background: linear-gradient(135deg, #ffa502, #ff7f50);
            color: #fff;
            border-color: transparent;
            animation: pulse-pause 1.5s infinite;
        }

        @keyframes pulse-pause {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.7; }
        }

        /* Time Indicator (Swipe) */
        .time-indicator {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 12px;
            padding: 10px 15px;
            background: linear-gradient(135deg, rgba(255, 165, 2, 0.2), rgba(255, 127, 80, 0.2));
            border: 1px solid rgba(255, 165, 2, 0.3);
            border-radius: 10px;
            margin-bottom: 15px;
            flex-wrap: wrap;
        }

        .time-indicator .time-text {
            font-size: 14px;
            font-weight: 600;
            color: #ffa502;
        }

        .go-live-btn {
            padding: 6px 14px;
            background: linear-gradient(135deg, #ff6b6b, #ee5a5a);
            color: #fff;
            border: none;
            border-radius: 15px;
            font-size: 12px;
            cursor: pointer;
            transition: transform 0.2s;
        }

        .go-live-btn:hover {
            transform: scale(1.05);
        }

        .swipe-hint {
            font-size: 11px;
            opacity: 0.5;
            margin-left: auto;
        }

        /* Grafik kartı swipe için cursor */
        .card:has(#hrChart) {
            cursor: grab;
            user-select: none;
            touch-action: pan-y;
        }

        .card:has(#hrChart):active {
            cursor: grabbing;
        }

        /* Custom Time Picker */
        .custom-time-picker {
            background: rgba(0, 0, 0, 0.3);
            border-radius: 12px;
            padding: 15px;
            margin-bottom: 15px;
        }

        .time-picker-row {
            display: flex;
            align-items: center;
            gap: 12px;
            flex-wrap: wrap;
        }

        .time-picker-row label {
            font-size: 13px;
            opacity: 0.8;
        }

        .time-picker-row select {
            padding: 8px 12px;
            border-radius: 8px;
            border: 1px solid rgba(255, 255, 255, 0.2);
            background: rgba(255, 255, 255, 0.1);
            color: #fff;
            font-size: 13px;
        }

        .time-range-preview {
            font-size: 12px;
            opacity: 0.6;
            padding: 6px 12px;
            background: rgba(255, 255, 255, 0.05);
            border-radius: 6px;
        }

        .apply-btn {
            padding: 8px 16px;
            background: linear-gradient(135deg, #2ed573, #1abc9c);
            color: #fff;
            border: none;
            border-radius: 8px;
            font-size: 13px;
            cursor: pointer;
            transition: transform 0.2s;
        }

        .apply-btn:hover {
            transform: scale(1.05);
        }

        /* Chart Stats */
        .chart-stats {
            display: flex;
            gap: 20px;
            margin-bottom: 15px;
            padding: 10px 15px;
            background: rgba(0, 0, 0, 0.2);
            border-radius: 10px;
            flex-wrap: wrap;
        }

        .chart-stat {
            display: flex;
            align-items: center;
            gap: 6px;
        }

        .chart-stat .label {
            font-size: 11px;
            opacity: 0.6;
            text-transform: uppercase;
        }

        .chart-stat .value {
            font-size: 14px;
            font-weight: 600;
        }

        .chart-stat:nth-child(1) .value { color: #3498db; }
        .chart-stat:nth-child(2) .value { color: #2ed573; }
        .chart-stat:nth-child(3) .value { color: #ff6b6b; }
        .chart-stat:nth-child(4) .value { color: #9b59b6; }

        /* Minute Stats */
        .minute-stats {
            display: flex;
            gap: 20px;
            margin-bottom: 12px;
            padding: 8px 15px;
            background: rgba(0, 0, 0, 0.2);
            border-radius: 8px;
            flex-wrap: wrap;
        }

        /* EKG Container */
        .ekg-container {
            background: rgba(0, 50, 0, 0.3);
            border-radius: 8px;
            padding: 10px;
        }

        /* Range Slider Container */
        .range-slider-container {
            margin: 20px 0 10px;
            padding: 18px 24px 14px;
            background: linear-gradient(135deg, rgba(102, 126, 234, 0.1), rgba(167, 139, 250, 0.1));
            border: 1px solid rgba(102, 126, 234, 0.2);
            border-radius: 12px;
            backdrop-filter: blur(10px);
        }

        .range-slider-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 15px;
            flex-wrap: wrap;
            gap: 10px;
        }

        .range-slider-header h4 {
            font-size: 13px;
            opacity: 0.8;
            margin: 0;
        }

        .range-slider-times {
            display: flex;
            gap: 15px;
            font-size: 12px;
        }

        .range-slider-times span {
            padding: 4px 10px;
            background: rgba(255, 255, 255, 0.1);
            border-radius: 6px;
        }

        .range-slider-times .start-time { color: #667eea; }
        .range-slider-times .end-time { color: #a78bfa; }

        /* noUiSlider Modern Style - Full Override */
        .range-slider {
            height: 6px !important;
            margin: 20px 15px 25px !important;
        }

        .noUi-target {
            background: rgba(255, 255, 255, 0.12) !important;
            border: none !important;
            border-radius: 3px !important;
            box-shadow: none !important;
            height: 6px !important;
        }

        .noUi-base, .noUi-connects {
            height: 6px !important;
        }

        .noUi-connect {
            background: linear-gradient(90deg, #667eea, #a78bfa) !important;
            border-radius: 3px !important;
        }

        /* Handle - tam yuvarlak, ortalanmış */
        .noUi-horizontal .noUi-handle {
            width: 16px !important;
            height: 16px !important;
            border-radius: 50% !important;
            background: #fff !important;
            border: none !important;
            box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3) !important;
            cursor: grab !important;
            top: -5px !important;
            right: -8px !important;
        }

        .noUi-handle:before,
        .noUi-handle:after {
            display: none !important;
        }

        .noUi-handle:hover {
            transform: scale(1.2);
            box-shadow: 0 2px 8px rgba(102, 126, 234, 0.5) !important;
        }

        .noUi-handle:active {
            cursor: grabbing !important;
        }

        .noUi-handle:focus {
            outline: none !important;
        }

        /* Touch area */
        .noUi-touch-area {
            height: 100% !important;
            width: 100% !important;
        }

        /* Slider Duration Display */
        .slider-duration {
            text-align: center;
            font-size: 12px;
            color: #a78bfa;
            margin-top: 8px;
            font-weight: 500;
        }

        /* Responsive */
        @media (max-width: 500px) {
            .hr-value { font-size: 80px; }
            .history-table { font-size: 12px; }
            .history-table th, .history-table td { padding: 8px 4px; }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>Heart Rate Monitor</h1>

            <!-- Tab Navigation -->
            <div class="tabs">
                <a href="?tab=live" class="tab-btn <?= $activeTab === 'live' ? 'active' : '' ?>">
                    📡 Canlı
                </a>
                <a href="?tab=history" class="tab-btn <?= $activeTab === 'history' ? 'active' : '' ?>">
                    📜 Geçmiş
                </a>
            </div>

            <?php if ($latest): ?>
                <?php $isOnline = $latest['seconds_ago'] < 60; ?>
                <div class="status-badge <?= $isOnline ? 'status-online' : 'status-offline' ?>">
                    <span class="status-dot"></span>
                    <?= $isOnline ? 'Canlı Bağlantı' : 'Bağlantı Yok (' . floor($latest['seconds_ago'] / 60) . ' dk önce)' ?>
                </div>
            <?php else: ?>
                <div class="status-badge status-offline">
                    <span class="status-dot"></span>
                    Veri Bekleniyor
                </div>
            <?php endif; ?>
        </div>

        <?php if ($activeTab === 'live'): ?>
            <!-- ========== LIVE TAB ========== -->
            <?php if ($latest): ?>
                <!-- Ana HR Kartı -->
                <div class="hr-card">
                    <div class="heart-icon">❤️</div>
                    <div class="hr-value"><?= (int)$latest['heart_rate'] ?></div>
                    <div class="hr-unit">BPM</div>

                    <?php
                    $hr = (int)$latest['heart_rate'];
                    if ($hr < 50) { $statusClass = 'hr-low'; $statusText = 'Düşük Nabız'; }
                    elseif ($hr > 120) { $statusClass = 'hr-high'; $statusText = 'Yüksek Nabız'; }
                    else { $statusClass = 'hr-normal'; $statusText = 'Normal'; }
                    ?>
                    <div class="hr-status <?= $statusClass ?>"><?= $statusText ?></div>
                    <div class="last-update">Son güncelleme: <?= date('H:i:s', strtotime($latest['recorded_at'])) ?></div>
                </div>

                <!-- Info Grid -->
                <div class="info-grid">
                    <div class="info-card">
                        <div class="icon">🔋</div>
                        <div class="value"><?= $latest['battery_level'] ? $latest['battery_level'] . '%' : '--' ?></div>
                        <div class="label">Pil</div>
                    </div>
                    <div class="info-card">
                        <div class="icon">📡</div>
                        <div class="value"><?= $latest['sensor_contact'] ? 'Var' : 'Yok' ?></div>
                        <div class="label">Temas</div>
                    </div>
                    <div class="info-card">
                        <div class="icon">📊</div>
                        <div class="value"><?= number_format($stats['total_readings'] ?? 0) ?></div>
                        <div class="label">Bugün</div>
                    </div>
                    <div class="info-card">
                        <div class="icon">⏱️</div>
                        <div class="value"><?= $latest['seconds_ago'] < 60 ? $latest['seconds_ago'] . 's' : floor($latest['seconds_ago']/60) . 'm' ?></div>
                        <div class="label">Önce</div>
                    </div>
                </div>

                <!-- RR Intervals -->
                <?php if ($latest['rr_intervals']): ?>
                    <?php $rrIntervals = json_decode($latest['rr_intervals'], true); ?>
                    <?php if (!empty($rrIntervals)): ?>
                        <div class="card">
                            <h3>RR Intervals (HRV)</h3>
                            <div class="rr-values">
                                <?php foreach ($rrIntervals as $rr): ?>
                                    <span class="rr-chip"><?= $rr ?> ms</span>
                                <?php endforeach; ?>
                            </div>
                        </div>
                    <?php endif; ?>
                <?php endif; ?>

                <!-- Zaman Seçici + Grafikler -->
                <div class="card">
                    <div class="chart-header">
                        <h3>Nabız & EKG Grafiği</h3>
                        <div class="time-selector">
                            <button class="time-btn active" data-seconds="10">10sn</button>
                            <button class="time-btn" data-seconds="30">30sn</button>
                            <button class="time-btn" data-minutes="1">1dk</button>
                            <button class="time-btn" data-minutes="3">3dk</button>
                            <button class="time-btn" data-minutes="5">5dk</button>
                            <button class="time-btn" data-minutes="15">15dk</button>
                            <button class="time-btn" data-minutes="60">60dk</button>
                            <button class="time-btn" data-minutes="custom" id="customTimeBtn">📅 Özel</button>
                            <button class="time-btn pause-btn" id="pauseBtn" title="Canlı güncellemeyi duraklat">⏸️</button>
                        </div>
                    </div>

                    <!-- Özel Zaman Seçici (gizli) -->
                    <div id="customTimePicker" class="custom-time-picker" style="display: none;">
                        <div class="time-picker-row">
                            <label>Kaç saat önce:</label>
                            <select id="hoursAgo">
                                <option value="1">1 saat önce</option>
                                <option value="2">2 saat önce</option>
                                <option value="3">3 saat önce</option>
                                <option value="4">4 saat önce</option>
                                <option value="6">6 saat önce</option>
                                <option value="12">12 saat önce</option>
                                <option value="24">24 saat önce</option>
                            </select>
                            <span class="time-range-preview" id="timeRangePreview"></span>
                            <button class="apply-btn" id="applyCustomTime">Uygula</button>
                        </div>
                    </div>

                    <!-- Zaman Göstergesi (kaydırma için) -->
                    <div id="timeIndicator" class="time-indicator" style="display: none;">
                        <span class="time-text"></span>
                        <button id="goLiveBtn" class="go-live-btn">🔴 Canlıya Dön</button>
                        <span class="swipe-hint">← Şimdi | Geçmiş →</span>
                    </div>

                    <!-- Grafik İstatistikleri -->
                    <div class="chart-stats" id="chartStats">
                        <div class="chart-stat">
                            <span class="label">Min:</span>
                            <span class="value" id="statMin">--</span>
                        </div>
                        <div class="chart-stat">
                            <span class="label">Ort:</span>
                            <span class="value" id="statAvg">--</span>
                        </div>
                        <div class="chart-stat">
                            <span class="label">Max:</span>
                            <span class="value" id="statMax">--</span>
                        </div>
                        <div class="chart-stat">
                            <span class="label">Kayıt:</span>
                            <span class="value" id="statCount">--</span>
                        </div>
                    </div>

                    <!-- Nabız Grafiği -->
                    <div class="chart-container" style="height: 200px;">
                        <canvas id="hrChart"></canvas>
                    </div>

                    <!-- EKG Grafiği -->
                    <h4 style="margin: 20px 0 10px; font-size: 13px; opacity: 0.7;">EKG Grafiği (son 10sn)</h4>
                    <div class="chart-container ekg-container" style="height: 120px;">
                        <canvas id="ekgChart"></canvas>
                    </div>

                    <!-- Range Slider (büyük zaman dilimleri için) -->
                    <div id="rangeSliderContainer" class="range-slider-container" style="display: none;">
                        <div class="range-slider-header">
                            <h4>🎚️ Zaman Aralığı Seçimi</h4>
                            <div class="range-slider-times">
                                <span class="start-time" id="sliderStartTime">--:--</span>
                                <span>→</span>
                                <span class="end-time" id="sliderEndTime">--:--</span>
                            </div>
                        </div>
                        <div id="rangeSlider" class="range-slider"></div>
                        <div class="slider-duration" id="sliderDuration">Seçilen: -- dakika</div>
                    </div>
                </div>

                <!-- Günlük İstatistikler -->
                <?php if (!empty($stats) && $stats['total_readings'] > 0): ?>
                    <div class="card">
                        <h3>Bugünkü İstatistikler</h3>
                        <div class="stats-grid">
                            <div class="stat-item stat-min">
                                <div class="value"><?= (int)$stats['min_hr'] ?></div>
                                <div class="label">Min</div>
                            </div>
                            <div class="stat-item stat-avg">
                                <div class="value"><?= round($stats['avg_hr']) ?></div>
                                <div class="label">Ort</div>
                            </div>
                            <div class="stat-item stat-max">
                                <div class="value"><?= (int)$stats['max_hr'] ?></div>
                                <div class="label">Max</div>
                            </div>
                        </div>
                    </div>
                <?php endif; ?>

            <?php else: ?>
                <div class="no-data">
                    <div class="icon">💓</div>
                    <h2>Veri Bekleniyor</h2>
                    <p>Wahoo TICKR Fit bağlandığında veriler burada görünecek</p>
                </div>
            <?php endif; ?>

        <?php else: ?>
            <!-- ========== HISTORY TAB ========== -->

            <!-- Dakikalık Ortalama Grafiği -->
            <div class="card">
                <div class="chart-header">
                    <h3>Dakikalık Ortalama</h3>
                    <div class="time-selector">
                        <button class="time-btn minute-range-btn active" data-hours="1">1sa</button>
                        <button class="time-btn minute-range-btn" data-hours="4">4sa</button>
                        <button class="time-btn minute-range-btn" data-hours="8">8sa</button>
                        <button class="time-btn minute-range-btn" data-hours="12">12sa</button>
                        <button class="time-btn minute-range-btn" data-hours="24">24sa</button>
                    </div>
                </div>
                <div class="minute-stats" id="minuteStats">
                    <div class="chart-stat">
                        <span class="label">Min:</span>
                        <span class="value" id="minuteStatMin" style="color: #3498db;">--</span>
                    </div>
                    <div class="chart-stat">
                        <span class="label">Ort:</span>
                        <span class="value" id="minuteStatAvg" style="color: #667eea;">--</span>
                    </div>
                    <div class="chart-stat">
                        <span class="label">Max:</span>
                        <span class="value" id="minuteStatMax" style="color: #ff6b6b;">--</span>
                    </div>
                    <div class="chart-stat">
                        <span class="label">Dakika:</span>
                        <span class="value" id="minuteStatCount" style="color: #a78bfa;">--</span>
                    </div>
                </div>
                <div class="chart-container" style="height: 250px;">
                    <canvas id="minuteChart"></canvas>
                </div>
            </div>

            <!-- Son 24 Saat Grafiği (Saatlik) -->
            <?php if (!empty($hourlyData)): ?>
                <div class="card">
                    <h3>Son 24 Saat (Saatlik Ortalama)</h3>
                    <div class="chart-container">
                        <canvas id="hourlyChart"></canvas>
                    </div>
                </div>
            <?php endif; ?>

            <!-- Saatlik Özet -->
            <?php if (!empty($hourlySummary)): ?>
                <div class="card">
                    <h3>Saatlik Özet</h3>
                    <div style="overflow-x: auto;">
                        <table class="hourly-table">
                            <thead>
                                <tr>
                                    <th>Saat</th>
                                    <th>Okuma</th>
                                    <th>Min</th>
                                    <th>Ort</th>
                                    <th>Max</th>
                                </tr>
                            </thead>
                            <tbody>
                                <?php foreach ($hourlySummary as $row): ?>
                                    <tr>
                                        <td><?= date('d.m H:i', strtotime($row['hour'])) ?></td>
                                        <td><?= $row['readings'] ?></td>
                                        <td style="color: #3498db;"><?= $row['min_hr'] ?></td>
                                        <td style="color: #2ed573;"><?= $row['avg_hr'] ?></td>
                                        <td style="color: #ff6b6b;"><?= $row['max_hr'] ?></td>
                                    </tr>
                                <?php endforeach; ?>
                            </tbody>
                        </table>
                    </div>
                </div>
            <?php endif; ?>

            <!-- Tüm Kayıtlar -->
            <div class="card">
                <h3>Tüm Kayıtlar (<?= number_format($history['total']) ?> kayıt)</h3>

                <?php if (!empty($history['data'])): ?>
                    <div style="overflow-x: auto;">
                        <table class="history-table">
                            <thead>
                                <tr>
                                    <th>
                                        <a href="<?= getSortUrl('recorded_at', $sortBy, $sortDir) ?>" class="sort-link <?= $sortBy === 'recorded_at' ? 'active' : '' ?>">
                                            Tarih/Saat <?= getSortIcon('recorded_at', $sortBy, $sortDir) ?>
                                        </a>
                                    </th>
                                    <th>
                                        <a href="<?= getSortUrl('heart_rate', $sortBy, $sortDir) ?>" class="sort-link <?= $sortBy === 'heart_rate' ? 'active' : '' ?>">
                                            Nabız <?= getSortIcon('heart_rate', $sortBy, $sortDir) ?>
                                        </a>
                                    </th>
                                    <th>Pil</th>
                                </tr>
                            </thead>
                            <tbody>
                                <?php foreach ($history['data'] as $row): ?>
                                    <?php
                                    $hr = (int)$row['heart_rate'];
                                    if ($hr < 50) $badgeClass = 'hr-badge-low';
                                    elseif ($hr > 120) $badgeClass = 'hr-badge-high';
                                    else $badgeClass = 'hr-badge-normal';

                                    ?>
                                    <tr>
                                        <td>
                                            <div class="datetime-cell">
                                                <span class="date"><?= date('d.m.Y', strtotime($row['recorded_at'])) ?></span>
                                                <?php
                                                // Milisaniye dahil göster
                                                $dt = new DateTime($row['recorded_at']);
                                                $ms = $dt->format('v'); // milisaniye
                                                ?>
                                                <span class="time"><?= $dt->format('H:i:s') ?>.<?= $ms ?></span>
                                            </div>
                                        </td>
                                        <td><span class="hr-badge <?= $badgeClass ?>"><?= $hr ?></span></td>
                                        <td><?= $row['battery_level'] ? $row['battery_level'] . '%' : '-' ?></td>
                                    </tr>
                                <?php endforeach; ?>
                            </tbody>
                        </table>
                    </div>

                    <!-- Pagination -->
                    <?php if ($history['pages'] > 1): ?>
                        <?php $sortParams = "&sort={$sortBy}&dir={$sortDir}"; ?>
                        <div class="pagination">
                            <?php if ($page > 1): ?>
                                <a href="?tab=history&page=1<?= $sortParams ?>">««</a>
                                <a href="?tab=history&page=<?= $page - 1 ?><?= $sortParams ?>">«</a>
                            <?php else: ?>
                                <span class="disabled">««</span>
                                <span class="disabled">«</span>
                            <?php endif; ?>

                            <?php
                            $start = max(1, $page - 2);
                            $end = min($history['pages'], $page + 2);
                            for ($i = $start; $i <= $end; $i++):
                            ?>
                                <?php if ($i == $page): ?>
                                    <span class="active"><?= $i ?></span>
                                <?php else: ?>
                                    <a href="?tab=history&page=<?= $i ?><?= $sortParams ?>"><?= $i ?></a>
                                <?php endif; ?>
                            <?php endfor; ?>

                            <?php if ($page < $history['pages']): ?>
                                <a href="?tab=history&page=<?= $page + 1 ?><?= $sortParams ?>">»</a>
                                <a href="?tab=history&page=<?= $history['pages'] ?><?= $sortParams ?>">»»</a>
                            <?php else: ?>
                                <span class="disabled">»</span>
                                <span class="disabled">»»</span>
                            <?php endif; ?>
                        </div>
                    <?php endif; ?>

                <?php else: ?>
                    <div class="no-data">
                        <div class="icon">📜</div>
                        <p>Henüz kayıt yok</p>
                    </div>
                <?php endif; ?>
            </div>

        <?php endif; ?>

        <div class="footer">
            Wahoo TICKR Fit • <?= getDefaultDevice() ?><br>
            <?= $activeTab === 'live' ? 'Canlı modda (10sn) grafikler 5 saniyede bir güncellenir' : '' ?>
        </div>
    </div>

    <!-- Charts -->
    <?php if ($activeTab === 'live'): ?>
    <script>
    // Chart.js Crosshair Plugin (inline)
    const crosshairPlugin = {
        id: 'crosshair',
        afterDraw: (chart) => {
            if (chart.tooltip?._active?.length) {
                const ctx = chart.ctx;
                const activePoint = chart.tooltip._active[0];
                const x = activePoint.element.x;
                const y = activePoint.element.y;
                const topY = chart.scales.y.top;
                const bottomY = chart.scales.y.bottom;
                const leftX = chart.scales.x.left;
                const rightX = chart.scales.x.right;

                ctx.save();
                ctx.setLineDash([5, 5]);
                ctx.lineWidth = 1;

                // Dikey çizgi
                ctx.strokeStyle = 'rgba(255, 255, 255, 0.5)';
                ctx.beginPath();
                ctx.moveTo(x, topY);
                ctx.lineTo(x, bottomY);
                ctx.stroke();

                // Yatay çizgi
                ctx.strokeStyle = 'rgba(255, 107, 107, 0.7)';
                ctx.beginPath();
                ctx.moveTo(leftX, y);
                ctx.lineTo(rightX, y);
                ctx.stroke();

                ctx.restore();
            }
        }
    };
    Chart.register(crosshairPlugin);

    // Global değişkenler
    let hrChart = null;
    let ekgChart = null;
    let selectedSeconds = 10;  // Nabız grafiği için seçilen toplam süre (saniye)
    let selectedTimeAgo = 0;  // Kaç saniye önce (0 = şimdi) - swipe için
    let customStart = null;
    let customEnd = null;
    let isPaused = false;  // Canlı güncelleme duraklatıldı mı
    const EKG_WINDOW = 10;  // EKG her zaman son 10 saniyeyi gösterir
    let stepSize = 10;  // Kaydırma adımı (saniye) - seçilen zaman dilimine göre değişir

    // SSE değişkenleri
    let eventSource = null;

    // Range Slider değişkenleri
    let rangeSlider = null;
    let fullDataTimestamps = [];  // Tüm veri timestamp'leri (slider için)
    let fullDataHeartRate = [];   // Tüm veri HR değerleri
    let fullDataLabels = [];      // Tüm veri etiketleri
    let sliderMinTs = 0;          // Slider minimum timestamp (ms)
    let sliderMaxTs = 0;          // Slider maximum timestamp (ms)
    let sliderStartTs = 0;        // Slider seçili başlangıç (ms)
    let sliderEndTs = 0;          // Slider seçili bitiş (ms)

    // Swipe değişkenleri
    let touchStartX = 0;
    let touchStartY = 0;
    let isSwiping = false;

    // Grafikleri oluştur
    function initCharts() {
        const hrCtx = document.getElementById('hrChart')?.getContext('2d');
        const ekgCtx = document.getElementById('ekgChart')?.getContext('2d');

        if (!hrCtx || !ekgCtx) return;

        const hrGradient = hrCtx.createLinearGradient(0, 0, 0, 200);
        hrGradient.addColorStop(0, 'rgba(255, 107, 107, 0.4)');
        hrGradient.addColorStop(1, 'rgba(255, 107, 107, 0.0)');

        // Nabız Grafiği
        hrChart = new Chart(hrCtx, {
            type: 'line',
            data: { labels: [], datasets: [{ label: 'BPM', data: [], borderColor: '#ff6b6b', backgroundColor: hrGradient, borderWidth: 2, fill: true, tension: 0.3, pointRadius: 1, pointHoverRadius: 8, pointHoverBackgroundColor: '#ff6b6b' }] },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        enabled: true,
                        backgroundColor: 'rgba(0, 0, 0, 0.8)',
                        titleFont: { size: 14 },
                        bodyFont: { size: 16, weight: 'bold' },
                        padding: 12,
                        displayColors: false,
                        callbacks: {
                            title: (items) => items[0]?.label || '',
                            label: (item) => `❤️ ${item.raw} BPM`
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { color: 'rgba(255,255,255,0.1)', drawBorder: false },
                        ticks: { color: 'rgba(255,255,255,0.5)', maxTicksLimit: 8, maxRotation: 0 }
                    },
                    y: {
                        grid: { color: 'rgba(255,255,255,0.1)', drawBorder: false },
                        ticks: { color: 'rgba(255,255,255,0.5)' }
                    }
                }
            }
        });

        // EKG Grafiği - PQRST sinüs dalgası (gerçek zaman aralıklı)
        ekgChart = new Chart(ekgCtx, {
            type: 'line',
            data: { datasets: [{
                label: 'EKG',
                data: [],  // {x: ms, y: value} formatında
                borderColor: '#00ff00',
                backgroundColor: 'transparent',
                borderWidth: 1.5,
                fill: false,
                tension: 0.2,
                pointRadius: 0,
                pointHoverRadius: 4,
                pointHoverBackgroundColor: '#00ff00'
            }] },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: false,
                interaction: { mode: 'nearest', intersect: false },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        enabled: true,
                        backgroundColor: 'rgba(0, 50, 0, 0.9)',
                        titleFont: { size: 14 },
                        bodyFont: { size: 12 },
                        padding: 10,
                        displayColors: false,
                        // Sadece R peak noktalarında tooltip göster
                        filter: (item) => {
                            // y === 1.0 olan noktalar R peak
                            return item.parsed.y === 1.0;
                        },
                        callbacks: {
                            title: (items) => {
                                if (!items[0]) return '';
                                const ms = items[0].parsed.x;
                                const d = new Date(ms);
                                const h = d.getHours().toString().padStart(2, '0');
                                const m = d.getMinutes().toString().padStart(2, '0');
                                const s = d.getSeconds().toString().padStart(2, '0');
                                const mil = d.getMilliseconds().toString().padStart(3, '0');
                                return `${h}:${m}:${s}.${mil}`;
                            },
                            label: (item) => {
                                return '💓 R-Peak';
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        type: 'linear',
                        grid: { color: 'rgba(0, 255, 0, 0.12)', drawBorder: false },
                        ticks: {
                            color: 'rgba(0, 255, 0, 0.6)',
                            maxTicksLimit: 5,
                            callback: (val) => {
                                // Unix timestamp (ms) → HH:mm:ss.mmm formatı
                                const d = new Date(val);
                                const h = d.getHours().toString().padStart(2, '0');
                                const m = d.getMinutes().toString().padStart(2, '0');
                                const s = d.getSeconds().toString().padStart(2, '0');
                                const ms = d.getMilliseconds().toString().padStart(3, '0');
                                return `${h}:${m}:${s}.${ms}`;
                            }
                        },
                        title: { display: false }
                    },
                    y: {
                        min: -0.4,
                        max: 1.2,
                        grid: { color: 'rgba(0, 255, 0, 0.12)', drawBorder: false },
                        ticks: { display: false }
                    }
                }
            }
        });

        loadChartData();
    }

    // Grafik güncelleme throttle (SSE saniyede 1 veri gönderebilir)
    let lastChartLoad = 0;
    const CHART_THROTTLE = 2000; // En az 2sn arayla grafik güncelle

    // Veri yükle
    async function loadChartData() {
        const now = Date.now();
        if (now - lastChartLoad < CHART_THROTTLE) return;
        lastChartLoad = now;
        let url = '/hr/api/chart-data.php?';

        if (customStart && customEnd) {
            // Özel zaman aralığı (Unix timestamp)
            url += `start=${customStart}&end=${customEnd}`;
        } else {
            // Nabız: Seçilen tüm süreyi göster (örn: 15dk = son 15 dakika)
            // EKG: Her zaman son 10 saniyeyi gösterecek (updateCharts içinde filtrelenir)
            const now = Math.floor(Date.now() / 1000);
            const end = now - selectedTimeAgo;
            const start = end - selectedSeconds;
            url += `start=${start}&end=${end}`;

            // Debug log
            console.log(`[HR Chart] Fetching ${selectedSeconds}s of data, timeAgo=${selectedTimeAgo}`);
            console.log(`[HR Chart] Range: ${new Date(start*1000).toLocaleTimeString()} - ${new Date(end*1000).toLocaleTimeString()}`);
        }

        try {
            const response = await fetch(url);
            const json = await response.json();

            // Debug log
            console.log(`[HR Chart] API Response - count: ${json.data?.stats?.count || 0}`);

            if (json.success && json.data) {
                updateCharts(json.data);
            }
        } catch (error) {
            console.error('Veri yükleme hatası:', error);
        }
    }

    // Grafikleri güncelle
    function updateCharts(data) {
        const { labels, heartRate, timestamps, stats } = data;

        // Tüm veriyi sakla (slider için)
        fullDataTimestamps = timestamps || [];
        fullDataHeartRate = heartRate || [];
        fullDataLabels = labels || [];

        // İstatistikleri güncelle (tüm veri için)
        if (stats) {
            document.getElementById('statMin').textContent = stats.min || '--';
            document.getElementById('statAvg').textContent = stats.avg || '--';
            document.getElementById('statMax').textContent = stats.max || '--';
            document.getElementById('statCount').textContent = stats.count || '--';
        }

        // Slider kontrolü: 3dk veya daha fazla zaman diliminde slider göster
        const sliderContainer = document.getElementById('rangeSliderContainer');
        if (selectedSeconds >= 180 && fullDataTimestamps.length > 0) {
            sliderContainer.style.display = 'block';
            initRangeSlider();
        } else {
            sliderContainer.style.display = 'none';
            // Slider yok, tüm veriyi göster
            renderCharts(fullDataLabels, fullDataHeartRate, fullDataTimestamps);
        }
    }

    // Range Slider'ı initialize et
    function initRangeSlider() {
        const sliderEl = document.getElementById('rangeSlider');
        if (!sliderEl || fullDataTimestamps.length === 0) return;

        sliderMinTs = fullDataTimestamps[0];
        sliderMaxTs = fullDataTimestamps[fullDataTimestamps.length - 1];

        // Başlangıçta tüm aralık seçili
        sliderStartTs = sliderMinTs;
        sliderEndTs = sliderMaxTs;

        // Eğer slider zaten varsa, destroy et
        if (rangeSlider) {
            rangeSlider.destroy();
        }

        // noUiSlider oluştur
        rangeSlider = noUiSlider.create(sliderEl, {
            start: [sliderMinTs, sliderMaxTs],
            connect: true,
            range: {
                'min': sliderMinTs,
                'max': sliderMaxTs
            },
            step: 1000, // 1 saniye
            behaviour: 'drag-tap',
            tooltips: false
        });

        // Slider değişim eventi
        rangeSlider.on('update', function(values) {
            sliderStartTs = Math.round(values[0]);
            sliderEndTs = Math.round(values[1]);
            updateSliderDisplay();
        });

        rangeSlider.on('change', function(values) {
            sliderStartTs = Math.round(values[0]);
            sliderEndTs = Math.round(values[1]);
            filterAndRenderCharts();
        });

        // İlk render
        updateSliderDisplay();
        filterAndRenderCharts();
    }

    // Slider görüntüsünü güncelle
    function updateSliderDisplay() {
        const startDate = new Date(sliderStartTs);
        const endDate = new Date(sliderEndTs);

        const fmt = (d) => {
            const h = d.getHours().toString().padStart(2, '0');
            const m = d.getMinutes().toString().padStart(2, '0');
            const s = d.getSeconds().toString().padStart(2, '0');
            return `${h}:${m}:${s}`;
        };

        document.getElementById('sliderStartTime').textContent = fmt(startDate);
        document.getElementById('sliderEndTime').textContent = fmt(endDate);

        // Süre hesapla
        const durationMs = sliderEndTs - sliderStartTs;
        const durationSec = Math.round(durationMs / 1000);
        let durationText = '';
        if (durationSec < 60) {
            durationText = `${durationSec} saniye`;
        } else if (durationSec < 3600) {
            const min = Math.floor(durationSec / 60);
            const sec = durationSec % 60;
            durationText = sec > 0 ? `${min} dk ${sec} sn` : `${min} dakika`;
        } else {
            const hr = Math.floor(durationSec / 3600);
            const min = Math.floor((durationSec % 3600) / 60);
            durationText = min > 0 ? `${hr} sa ${min} dk` : `${hr} saat`;
        }
        document.getElementById('sliderDuration').textContent = `Seçilen: ${durationText}`;
    }

    // Slider aralığına göre grafikleri filtrele ve render et
    function filterAndRenderCharts() {
        // Seçili aralıktaki verileri filtrele
        const filteredIndices = [];
        for (let i = 0; i < fullDataTimestamps.length; i++) {
            if (fullDataTimestamps[i] >= sliderStartTs && fullDataTimestamps[i] <= sliderEndTs) {
                filteredIndices.push(i);
            }
        }

        const filteredLabels = filteredIndices.map(i => fullDataLabels[i]);
        const filteredHR = filteredIndices.map(i => fullDataHeartRate[i]);
        const filteredTS = filteredIndices.map(i => fullDataTimestamps[i]);

        // Filtrelenmiş istatistikler
        if (filteredHR.length > 0) {
            document.getElementById('statMin').textContent = Math.min(...filteredHR);
            document.getElementById('statAvg').textContent = Math.round(filteredHR.reduce((a, b) => a + b, 0) / filteredHR.length);
            document.getElementById('statMax').textContent = Math.max(...filteredHR);
            document.getElementById('statCount').textContent = filteredHR.length;
        }

        renderCharts(filteredLabels, filteredHR, filteredTS);
    }

    // Grafikleri render et (HR ve EKG)
    function renderCharts(labels, heartRate, timestamps) {
        // Nabız grafiği
        if (hrChart) {
            hrChart.data.labels = labels;
            hrChart.data.datasets[0].data = heartRate;
            if (heartRate.length > 0) {
                hrChart.options.scales.y.min = Math.min(...heartRate) - 5;
                hrChart.options.scales.y.max = Math.max(...heartRate) + 5;
            }
            hrChart.update('none');
        }

        // EKG grafiği - Seçili aralığın SON 10 saniyesi
        if (ekgChart && timestamps.length > 0) {
            const lastTimestamp = timestamps[timestamps.length - 1];
            const ekgWindowStart = lastTimestamp - (EKG_WINDOW * 1000);
            const ekgTimestamps = timestamps.filter(ts => ts >= ekgWindowStart);

            const ekgData = [];

            // PQRST dalga formu
            const pqrstWave = [
                { offset: -200, y: 0 },
                { offset: -160, y: 0.03 },
                { offset: -130, y: 0.12 },
                { offset: -100, y: 0.03 },
                { offset: -70, y: 0 },
                { offset: -40, y: -0.05 },
                { offset: 0, y: 1.0 },       // R PEAK
                { offset: 40, y: -0.12 },
                { offset: 80, y: 0 },
                { offset: 140, y: 0.05 },
                { offset: 180, y: 0.18 },
                { offset: 220, y: 0.05 },
                { offset: 260, y: 0 }
            ];

            for (let i = 0; i < ekgTimestamps.length; i++) {
                const beatTime = ekgTimestamps[i];
                pqrstWave.forEach(point => {
                    ekgData.push({ x: beatTime + point.offset, y: point.y });
                });

                if (i < ekgTimestamps.length - 1) {
                    const nextBeatTime = ekgTimestamps[i + 1];
                    const gapStart = beatTime + 260;
                    const gapEnd = nextBeatTime - 200;
                    if (gapEnd > gapStart + 50) {
                        ekgData.push({ x: gapStart + 20, y: 0 });
                        ekgData.push({ x: gapEnd - 20, y: 0 });
                    }
                }
            }

            ekgData.sort((a, b) => a.x - b.x);

            if (ekgTimestamps.length > 0) {
                ekgChart.options.scales.x.min = ekgTimestamps[0] - 300;
                ekgChart.options.scales.x.max = ekgTimestamps[ekgTimestamps.length - 1] + 350;
            }

            ekgChart.data.datasets[0].data = ekgData;
            ekgChart.update('none');
        }
    }

    // Zaman seçici event'leri
    document.querySelectorAll('.time-btn').forEach(btn => {
        btn.addEventListener('click', function() {
            const minutes = this.dataset.minutes;
            const seconds = this.dataset.seconds;

            // Active class
            document.querySelectorAll('.time-btn').forEach(b => b.classList.remove('active'));
            this.classList.add('active');

            if (minutes === 'custom') {
                document.getElementById('customTimePicker').style.display = 'block';
                updateTimePreview();
            } else {
                document.getElementById('customTimePicker').style.display = 'none';
                customStart = null;
                customEnd = null;

                // Nabız grafiği: Seçilen tüm süreyi göster
                // EKG grafiği: Her zaman son 10 saniyeyi gösterir
                if (seconds) {
                    selectedSeconds = parseInt(seconds);
                } else if (minutes) {
                    selectedSeconds = parseInt(minutes) * 60;
                }

                // stepSize: Kaydırma adımı (seçilen zaman diliminin %10'u, min 10sn)
                stepSize = Math.max(10, Math.floor(selectedSeconds / 10));

                // Canlı moda dön (selectedTimeAgo = 0)
                selectedTimeAgo = 0;
                isPaused = false;
                updatePauseButton();
                updateTimeIndicator();
                loadChartData();
            }
        });
    });

    // Özel zaman önizleme - seçilen saat dilimini göster
    function updateTimePreview() {
        const hoursAgo = parseInt(document.getElementById('hoursAgo').value);
        const now = new Date();
        // hoursAgo saat öncesinden şimdiye kadar
        const start = new Date(now.getTime() - hoursAgo * 60 * 60 * 1000);
        const end = now;

        const fmt = (d) => d.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
        document.getElementById('timeRangePreview').textContent = `${fmt(start)} - ${fmt(end)} (${hoursAgo} saat)`;
    }

    document.getElementById('hoursAgo')?.addEventListener('change', updateTimePreview);

    // Özel zaman uygula - seçilen saat diliminin tamamını göster
    document.getElementById('applyCustomTime')?.addEventListener('click', function() {
        const hoursAgo = parseInt(document.getElementById('hoursAgo').value);
        const now = new Date();
        const startTime = new Date(now.getTime() - hoursAgo * 60 * 60 * 1000);

        customStart = Math.floor(startTime.getTime() / 1000);
        customEnd = Math.floor(now.getTime() / 1000);
        selectedSeconds = hoursAgo * 3600; // EKG filtreleme için
        loadChartData();
    });

    // ===== SSE (Server-Sent Events) =====

    function startSSE() {
        if (eventSource) {
            eventSource.close();
        }

        eventSource = new EventSource('/hr/api/sse.php');

        eventSource.addEventListener('connected', (e) => {
            console.log('[SSE] Bağlandı:', JSON.parse(e.data));
        });

        eventSource.addEventListener('heartbeat', (e) => {
            const d = JSON.parse(e.data);
            console.log(`[SSE] Yeni veri: ${d.heart_rate} BPM (${d.time})`);

            // 1) HR kartını güncelle (her zaman)
            updateHRCardFromSSE(d);

            // 2) Grafikleri güncelle (canlı modda ve duraklatılmamışsa)
            if (!isPaused && selectedTimeAgo === 0 && !customStart && !customEnd) {
                loadChartData();
            }
        });

        eventSource.addEventListener('timeout', (e) => {
            console.log('[SSE] Timeout, yeniden bağlanıyor...');
            // EventSource otomatik yeniden bağlanır
        });

        eventSource.addEventListener('error', (e) => {
            console.log('[SSE] Hata veya bağlantı kesildi, yeniden bağlanıyor...');
        });

        eventSource.onerror = () => {
            // EventSource otomatik retry yapar (varsayılan ~3sn)
            console.log('[SSE] Bağlantı koptu, otomatik yeniden deneniyor...');
        };
    }

    function stopSSE() {
        if (eventSource) {
            eventSource.close();
            eventSource = null;
        }
    }

    // SSE'den gelen veriyle HR kartını güncelle
    function updateHRCardFromSSE(d) {
        // HR değeri
        const hrValueEl = document.querySelector('.hr-value');
        if (hrValueEl) hrValueEl.textContent = d.heart_rate;

        // HR durumu
        const hrStatusEl = document.querySelector('.hr-status');
        if (hrStatusEl) {
            hrStatusEl.classList.remove('hr-low', 'hr-normal', 'hr-high');
            if (d.heart_rate < 50) {
                hrStatusEl.classList.add('hr-low');
                hrStatusEl.textContent = 'Düşük Nabız';
            } else if (d.heart_rate > 120) {
                hrStatusEl.classList.add('hr-high');
                hrStatusEl.textContent = 'Yüksek Nabız';
            } else {
                hrStatusEl.classList.add('hr-normal');
                hrStatusEl.textContent = 'Normal';
            }
        }

        // Son güncelleme zamanı
        const lastUpdateEl = document.querySelector('.last-update');
        if (lastUpdateEl) lastUpdateEl.textContent = `Son güncelleme: ${d.time}`;

        // Pil seviyesi
        const batteryEl = document.querySelector('.info-card:nth-child(1) .value');
        if (batteryEl && d.battery_level) batteryEl.textContent = d.battery_level + '%';

        // Sensör teması
        const contactEl = document.querySelector('.info-card:nth-child(2) .value');
        if (contactEl) contactEl.textContent = d.sensor_contact ? 'Var' : 'Yok';

        // Ne kadar önce
        const agoEl = document.querySelector('.info-card:nth-child(4) .value');
        if (agoEl) {
            agoEl.textContent = d.seconds_ago < 60 ? d.seconds_ago + 's' : Math.floor(d.seconds_ago / 60) + 'm';
        }

        // Online/Offline durumu
        const statusBadge = document.querySelector('.status-badge');
        if (statusBadge) {
            const isOnline = d.seconds_ago < 60;
            statusBadge.classList.remove('status-online', 'status-offline');
            statusBadge.classList.add(isOnline ? 'status-online' : 'status-offline');
            statusBadge.innerHTML = `<span class="status-dot"></span>${isOnline ? 'Canlı Bağlantı' : 'Bağlantı Yok (' + Math.floor(d.seconds_ago / 60) + ' dk önce)'}`;
        }
    }

    // Pause/Play toggle
    function togglePause() {
        isPaused = !isPaused;
        const btn = document.getElementById('pauseBtn');
        if (isPaused) {
            btn.textContent = '▶️';
            btn.classList.add('paused');
            btn.title = 'Canlı güncellemeyi devam ettir';
        } else {
            btn.textContent = '⏸️';
            btn.classList.remove('paused');
            btn.title = 'Canlı güncellemeyi duraklat';
            // Devam ettirince hemen bir güncelleme yap
            if (selectedTimeAgo === 0 && !customStart && !customEnd) {
                loadChartData();
            }
        }
    }

    // Pause butonu event listener
    document.getElementById('pauseBtn')?.addEventListener('click', togglePause);

    // Swipe/Kaydırma işlevleri
    function setupSwipeHandlers() {
        const chartCard = document.querySelector('.card:has(#hrChart)');
        if (!chartCard) return;

        // Touch events
        chartCard.addEventListener('touchstart', handleTouchStart, { passive: true });
        chartCard.addEventListener('touchmove', handleTouchMove, { passive: false });
        chartCard.addEventListener('touchend', handleTouchEnd, { passive: true });

        // Mouse events (desktop için)
        chartCard.addEventListener('mousedown', handleMouseDown);
        chartCard.addEventListener('mousemove', handleMouseMove);
        chartCard.addEventListener('mouseup', handleMouseUp);
        chartCard.addEventListener('mouseleave', handleMouseUp);
    }

    function handleTouchStart(e) {
        touchStartX = e.touches[0].clientX;
        touchStartY = e.touches[0].clientY;
        isSwiping = true;
    }

    function handleTouchMove(e) {
        if (!isSwiping) return;
        const deltaX = e.touches[0].clientX - touchStartX;
        const deltaY = e.touches[0].clientY - touchStartY;
        // Yatay swipe ise sayfanın scroll'unu engelle
        if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 10) {
            e.preventDefault();
        }
    }

    function handleTouchEnd(e) {
        if (!isSwiping) return;
        isSwiping = false;
        const deltaX = e.changedTouches[0].clientX - touchStartX;
        const deltaY = e.changedTouches[0].clientY - touchStartY;
        processSwipe(deltaX, deltaY);
    }

    let mouseStartX = 0;
    let isMouseDown = false;

    function handleMouseDown(e) {
        mouseStartX = e.clientX;
        isMouseDown = true;
        e.target.style.cursor = 'grabbing';
    }

    function handleMouseMove(e) {
        if (!isMouseDown) return;
    }

    function handleMouseUp(e) {
        if (!isMouseDown) return;
        isMouseDown = false;
        e.target.style.cursor = '';
        const deltaX = e.clientX - mouseStartX;
        processSwipe(deltaX, 0);
    }

    function processSwipe(deltaX, deltaY) {
        // Sadece yatay swipe'ları işle (dikey scroll'u engelleme)
        if (Math.abs(deltaY) > Math.abs(deltaX)) return;

        const minSwipeDistance = 50; // Minimum swipe mesafesi (px)
        if (Math.abs(deltaX) < minSwipeDistance) return;

        // Özel zaman seçiliyse swipe çalışmasın
        if (customStart && customEnd) return;

        // Scroll mantığı (içeriği sürükleme):
        // Sağa kaydır (parmak soldan sağa) → Geçmişe git
        // Sola kaydır (parmak sağdan sola) → Şimdiye gel
        if (deltaX > 0) {
            // Sağa swipe → Geçmişe git
            selectedTimeAgo += stepSize;
            isPaused = true;
            updatePauseButton();
        } else {
            // Sola swipe → Şimdiye doğru gel
            selectedTimeAgo = Math.max(0, selectedTimeAgo - stepSize);
            if (selectedTimeAgo === 0) {
                isPaused = false;
                updatePauseButton();
            }
        }

        updateTimeIndicator();
        loadChartData();
    }

    function updatePauseButton() {
        const btn = document.getElementById('pauseBtn');
        if (isPaused) {
            btn.textContent = '▶️';
            btn.classList.add('paused');
            btn.title = 'Canlı güncellemeyi devam ettir';
        } else {
            btn.textContent = '⏸️';
            btn.classList.remove('paused');
            btn.title = 'Canlı güncellemeyi duraklat';
        }
    }

    function updateTimeIndicator() {
        const indicator = document.getElementById('timeIndicator');
        if (!indicator) return;

        if (selectedTimeAgo === 0 && !customStart) {
            indicator.style.display = 'none';
        } else {
            indicator.style.display = 'flex';
            let timeText = '';
            if (customStart && customEnd) {
                const startDate = new Date(customStart * 1000);
                const endDate = new Date(customEnd * 1000);
                timeText = `${startDate.toLocaleTimeString('tr-TR', {hour:'2-digit', minute:'2-digit'})} - ${endDate.toLocaleTimeString('tr-TR', {hour:'2-digit', minute:'2-digit'})}`;
            } else if (selectedTimeAgo > 0) {
                // Swipe ile geçmişe bakıyoruz
                if (selectedTimeAgo < 60) {
                    timeText = `${selectedTimeAgo}sn önce`;
                } else if (selectedTimeAgo < 3600) {
                    const min = Math.floor(selectedTimeAgo / 60);
                    const sec = selectedTimeAgo % 60;
                    timeText = sec > 0 ? `${min}dk ${sec}sn önce` : `${min}dk önce`;
                } else {
                    const hour = Math.floor(selectedTimeAgo / 3600);
                    const min = Math.floor((selectedTimeAgo % 3600) / 60);
                    timeText = min > 0 ? `${hour}sa ${min}dk önce` : `${hour}sa önce`;
                }
            }
            indicator.querySelector('.time-text').textContent = timeText;
        }
    }

    function goToLive() {
        selectedTimeAgo = 0;
        selectedSeconds = 10;  // Varsayılan 10 saniye
        customStart = null;
        customEnd = null;
        isPaused = false;

        // 10sn butonunu aktif yap
        document.querySelectorAll('.time-btn').forEach(b => b.classList.remove('active'));
        document.querySelector('.time-btn[data-seconds="10"]')?.classList.add('active');

        updatePauseButton();
        updateTimeIndicator();
        document.getElementById('customTimePicker').style.display = 'none';
        loadChartData();
    }

    // Canlıya dön butonu
    document.getElementById('goLiveBtn')?.addEventListener('click', goToLive);

    // Sayfa yüklendiğinde
    document.addEventListener('DOMContentLoaded', function() {
        initCharts();
        startSSE();  // SSE ile anlık güncelleme
        setupSwipeHandlers();
    });
    </script>
    <?php endif; ?>

    <?php if ($activeTab === 'history'): ?>
    <script>
        // ===== Dakikalık Ortalama Grafiği =====
        let minuteChart = null;

        function initMinuteChart() {
            const ctx = document.getElementById('minuteChart')?.getContext('2d');
            if (!ctx) return;

            const gradient = ctx.createLinearGradient(0, 0, 0, 250);
            gradient.addColorStop(0, 'rgba(102, 126, 234, 0.35)');
            gradient.addColorStop(1, 'rgba(102, 126, 234, 0.0)');

            minuteChart = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: [],
                    datasets: [
                        {
                            label: 'Ortalama BPM',
                            data: [],
                            borderColor: '#667eea',
                            backgroundColor: gradient,
                            borderWidth: 2,
                            fill: true,
                            tension: 0.3,
                            pointRadius: 0,
                            pointHoverRadius: 6,
                            pointHoverBackgroundColor: '#667eea'
                        },
                        {
                            label: 'Min',
                            data: [],
                            borderColor: 'rgba(52, 152, 219, 0.3)',
                            borderWidth: 1,
                            borderDash: [3, 3],
                            fill: false,
                            tension: 0.3,
                            pointRadius: 0
                        },
                        {
                            label: 'Max',
                            data: [],
                            borderColor: 'rgba(255, 107, 107, 0.3)',
                            borderWidth: 1,
                            borderDash: [3, 3],
                            fill: false,
                            tension: 0.3,
                            pointRadius: 0
                        }
                    ]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    interaction: { mode: 'index', intersect: false },
                    plugins: {
                        legend: {
                            display: true,
                            position: 'top',
                            align: 'end',
                            labels: {
                                color: 'rgba(255,255,255,0.7)',
                                usePointStyle: true,
                                pointStyle: 'line',
                                padding: 15,
                                font: { size: 12 }
                            }
                        },
                        tooltip: {
                            backgroundColor: 'rgba(0, 0, 0, 0.8)',
                            padding: 10,
                            displayColors: true,
                            callbacks: {
                                title: (items) => items[0]?.label || '',
                                label: (item) => {
                                    const names = ['Ort', 'Min', 'Max'];
                                    return ` ${names[item.datasetIndex]}: ${item.raw} BPM`;
                                }
                            }
                        }
                    },
                    scales: {
                        x: {
                            grid: { display: false },
                            ticks: {
                                color: 'rgba(255,255,255,0.4)',
                                maxTicksLimit: 15,
                                maxRotation: 0
                            }
                        },
                        y: {
                            grid: { color: 'rgba(255,255,255,0.05)' },
                            ticks: { color: 'rgba(255,255,255,0.4)' }
                        }
                    }
                }
            });
        }

        async function loadMinuteData(hours) {
            try {
                const response = await fetch(`/hr/api/minute-summary.php?hours=${hours}`);
                const json = await response.json();

                if (json.success && json.data && minuteChart) {
                    const d = json.data;

                    minuteChart.data.labels = d.labels;
                    minuteChart.data.datasets[0].data = d.avgHR;
                    minuteChart.data.datasets[1].data = d.minHR;
                    minuteChart.data.datasets[2].data = d.maxHR;

                    // Çok veri varsa point'leri gizle, az ise göster
                    minuteChart.data.datasets[0].pointRadius = d.totalMinutes > 120 ? 0 : 2;

                    // Y ekseni otomatik ayarla
                    if (d.minHR.length > 0) {
                        minuteChart.options.scales.y.min = Math.min(...d.minHR) - 5;
                        minuteChart.options.scales.y.max = Math.max(...d.maxHR) + 5;
                    }

                    minuteChart.update('none');

                    // İstatistikler
                    if (d.avgHR.length > 0) {
                        document.getElementById('minuteStatMin').textContent = Math.min(...d.minHR);
                        document.getElementById('minuteStatAvg').textContent = Math.round(d.avgHR.reduce((a, b) => a + b, 0) / d.avgHR.length);
                        document.getElementById('minuteStatMax').textContent = Math.max(...d.maxHR);
                        document.getElementById('minuteStatCount').textContent = d.totalMinutes;
                    }
                }
            } catch (error) {
                console.error('Dakikalık veri yükleme hatası:', error);
            }
        }

        // Zaman butonları
        document.querySelectorAll('.minute-range-btn').forEach(btn => {
            btn.addEventListener('click', function() {
                document.querySelectorAll('.minute-range-btn').forEach(b => b.classList.remove('active'));
                this.classList.add('active');
                loadMinuteData(parseInt(this.dataset.hours));
            });
        });

        // Sayfa yüklendiğinde
        document.addEventListener('DOMContentLoaded', function() {
            initMinuteChart();
            loadMinuteData(1); // Varsayılan: son 1 saat
        });

        // ===== Saatlik Ortalama Grafiği =====
        <?php if (!empty($hourlyData)): ?>
        (function() {
            const ctx2 = document.getElementById('hourlyChart')?.getContext('2d');
            if (!ctx2) return;
            const gradient2 = ctx2.createLinearGradient(0, 0, 0, 200);
            gradient2.addColorStop(0, 'rgba(46, 213, 115, 0.4)');
            gradient2.addColorStop(1, 'rgba(46, 213, 115, 0.0)');

            new Chart(ctx2, {
                type: 'line',
                data: {
                    labels: <?= json_encode($hourlyLabels) ?>,
                    datasets: [{
                        label: 'Ortalama BPM',
                        data: <?= json_encode($hourlyData) ?>,
                        borderColor: '#2ed573',
                        backgroundColor: gradient2,
                        borderWidth: 2,
                        fill: true,
                        tension: 0.4,
                        pointRadius: 3,
                        pointBackgroundColor: '#2ed573'
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: { legend: { display: false } },
                    scales: {
                        x: { grid: { display: false }, ticks: { color: 'rgba(255,255,255,0.4)', maxTicksLimit: 12 } },
                        y: {
                            grid: { color: 'rgba(255,255,255,0.05)' },
                            ticks: { color: 'rgba(255,255,255,0.4)' }
                        }
                    }
                }
            });
        })();
        <?php endif; ?>
    </script>
    <?php endif; ?>
</body>
</html>
