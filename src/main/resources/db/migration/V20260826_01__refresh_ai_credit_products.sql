INSERT INTO ai_credit_product (code, name, price_fen, credit_amount, status, sort_order)
VALUES ('ai_10_trial', '新人体验包', 290, 10, 1, 5),
       ('ai_30', 'AI 健康分析包', 990, 30, 1, 10),
       ('ai_80', 'AI 健康分析大容量包', 1990, 80, 1, 20),
       ('vip_month', 'VIP 月卡', 1990, 30, 1, 30),
       ('vip_month_auto', '连续包月', 1290, 30, 1, 40),
       ('vip_year', 'VIP 年卡', 9800, 360, 1, 50)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    price_fen = VALUES(price_fen),
    credit_amount = VALUES(credit_amount),
    status = VALUES(status),
    sort_order = VALUES(sort_order);
