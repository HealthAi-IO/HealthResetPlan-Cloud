INSERT INTO ai_credit_product (code, name, price_fen, credit_amount, status, sort_order)
VALUES ('ai_10_trial', '新人体验包', 190, 10, 1, 5),
       ('vip_month', 'VIP 月卡', 690, 30, 1, 30),
       ('vip_quarter', 'VIP 季卡', 1800, 90, 1, 40),
       ('vip_year', 'VIP 年卡', 5900, 360, 1, 50)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    price_fen = VALUES(price_fen),
    credit_amount = VALUES(credit_amount),
    status = VALUES(status),
    sort_order = VALUES(sort_order);
