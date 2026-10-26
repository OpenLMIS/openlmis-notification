-- WHEN COMMITTING OR REVIEWING THIS FILE: Make sure that the timestamp in the file name (that serves as a version) is the latest timestamp, and that no new migration have been added in the meanwhile.
-- Adding migrations out of order may cause this migration to never execute or behave in an unexpected way.
-- Migrations should NOT BE EDITED. Add a new migration to apply changes.

ALTER TABLE pending_notifications
  ADD COLUMN retrycount INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pending_notifications
  ADD COLUMN retryat TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now();

ALTER TABLE pending_notifications
  ADD COLUMN lasterror TEXT;

CREATE INDEX pending_notifications_due_idx
  ON pending_notifications (retrycount, retryat, createddate);
