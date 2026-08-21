ALTER TABLE payment_refund
    ADD COLUMN failure_reason VARCHAR(255) NULL AFTER reason;

UPDATE payment_refund
SET status = 'needs_manual', failure_reason = '历史退款申请待人工处理'
WHERE status = 'requested';
