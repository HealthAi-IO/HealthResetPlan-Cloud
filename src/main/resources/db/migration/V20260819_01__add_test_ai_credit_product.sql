INSERT INTO ai_credit_product (code, name, price_fen, credit_amount, status, sort_order)
VALUES ('ai_test_1', '支付测试包', 1, 1, 1, 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    price_fen = VALUES(price_fen),
    credit_amount = VALUES(credit_amount),
    status = VALUES(status),
    sort_order = VALUES(sort_order);
