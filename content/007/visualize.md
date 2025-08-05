````sql
-- Biểu đồ: Alert Distribution
-- Mục đích: Hiển thị số lượng alert và non-alert trong log
SELECT 
    CASE 
        WHEN is_alert = true THEN 'Alert'
        ELSE 'Non-Alert'
    END AS alert_status,
    COUNT(*) AS count
FROM logs
GROUP BY alert_status;

-- Biểu đồ: Log Level Distribution
-- Mục đích: Thống kê số lượng log theo mức độ (level)
SELECT 
    level, 
    COUNT(*) AS count
FROM logs
GROUP BY level
ORDER BY count DESC;

-- Biểu đồ: Log Type Distribution
-- Mục đích: Thống kê số lượng log theo loại (type)
SELECT 
    type, 
    COUNT(*) AS count
FROM logs
GROUP BY type
ORDER BY count DESC;

-- Biểu đồ: Component Distribution
-- Mục đích: Thống kê số lượng log theo thành phần (component)
SELECT 
    component, 
    COUNT(*) AS count
FROM logs
GROUP BY component
ORDER BY count DESC;

-- Biểu đồ: Alert-Level Breakdown (có filter alert/non-alert)
-- Mục đích: Lọc theo alert/non-alert để phân tích số lượng theo level
-- Biến $__variable_alert sẽ được tạo từ Grafana dashboard (ví dụ: true / false)
SELECT 
    level, 
    COUNT(*) AS count
FROM logs
WHERE is_alert = $__variable_alert
GROUP BY level
ORDER BY count DESC;

-- Biểu đồ: Alert-Type Breakdown (có filter alert/non-alert)
-- Mục đích: Lọc theo alert/non-alert để phân tích số lượng theo type
SELECT 
    type, 
    COUNT(*) AS count
FROM logs
WHERE is_alert = $__variable_alert
GROUP BY type
ORDER BY count DESC;

-- Biểu đồ: Alert-Component Breakdown (có filter alert/non-alert)
-- Mục đích: Lọc theo alert/non-alert để phân tích số lượng theo component
SELECT 
    component, 
    COUNT(*) AS count
FROM logs
WHERE is_alert = $__variable_alert
GROUP BY component
ORDER BY count DESC;
