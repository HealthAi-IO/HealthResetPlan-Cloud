CREATE TRIGGER trg_audit_log_no_update
BEFORE UPDATE ON audit_log
FOR EACH ROW SIGNAL SQLSTATE '45000'
SET MESSAGE_TEXT = 'audit_log is append-only';

CREATE TRIGGER trg_audit_log_no_delete
BEFORE DELETE ON audit_log
FOR EACH ROW SIGNAL SQLSTATE '45000'
SET MESSAGE_TEXT = 'audit_log is append-only';
