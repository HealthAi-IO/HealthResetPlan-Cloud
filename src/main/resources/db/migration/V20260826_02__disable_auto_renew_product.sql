UPDATE ai_credit_product
SET status = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE code = 'vip_month_auto';
