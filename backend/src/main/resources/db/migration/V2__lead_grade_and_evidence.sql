ALTER TABLE lead_record ADD COLUMN customer_grade VARCHAR(8);
ALTER TABLE lead_record ADD COLUMN quantity VARCHAR(80);
ALTER TABLE lead_record ADD COLUMN destination_port VARCHAR(120);
ALTER TABLE lead_record ADD COLUMN quotation VARCHAR(200);
ALTER TABLE lead_record ADD COLUMN customer_reply VARCHAR(2000);
ALTER TABLE lead_record ADD COLUMN order_or_contract_number VARCHAR(120);
ALTER TABLE lead_record ADD COLUMN shipping_document_number VARCHAR(120);
ALTER TABLE lead_record ADD COLUMN receipt_or_arrival_note VARCHAR(500);
ALTER TABLE lead_record ADD COLUMN budget VARCHAR(200);
ALTER TABLE lead_record ADD COLUMN delivery_date VARCHAR(120);
ALTER TABLE lead_record ADD COLUMN days_since_valid_interaction INT DEFAULT 0 NOT NULL;
ALTER TABLE lead_record ADD COLUMN consecutive_unanswered INT DEFAULT 0 NOT NULL;
ALTER TABLE lead_record ADD COLUMN days_since_last_deal INT;
ALTER TABLE lead_record ADD COLUMN wake_plan_json VARCHAR(2000);

UPDATE lead_record SET customer_grade = 'C' WHERE customer_grade IS NULL;
