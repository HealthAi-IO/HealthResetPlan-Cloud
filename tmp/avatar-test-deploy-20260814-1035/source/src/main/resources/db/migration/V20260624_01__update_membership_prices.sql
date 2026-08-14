UPDATE membership_plan
SET price_fen = 280, updated_at = CURRENT_TIMESTAMP(3)
WHERE code = 'monthly';

UPDATE membership_plan
SET price_fen = 2800, updated_at = CURRENT_TIMESTAMP(3)
WHERE code = 'yearly';
