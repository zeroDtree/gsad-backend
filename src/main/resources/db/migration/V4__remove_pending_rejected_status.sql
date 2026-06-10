-- Auto-approve flow: no PENDING / REJECTED; new rows default to APPROVED.

ALTER TABLE t_application
    ALTER COLUMN audit_status SET DEFAULT 'APPROVED';

-- Historical rows: map obsolete statuses to APPROVED (operator may re-run grant manually if needed).
UPDATE t_application
SET audit_status = 'APPROVED'
WHERE audit_status IN ('PENDING', 'REJECTED');
