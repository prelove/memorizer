-- Study plan table for Stage B
CREATE TABLE IF NOT EXISTS study_plan (
  plan_date DATE NOT NULL,
  card_id BIGINT NOT NULL,
  deck_id BIGINT,
  kind SMALLINT NOT NULL,           -- 0=DUE,1=LEECH,2=NEW,3=CHALLENGE
  status SMALLINT NOT NULL DEFAULT 0, -- 0=PENDING,1=DONE,2=ROLLED,3=SKIPPED
  order_no INT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_study_plan_day_card
  ON study_plan(plan_date, card_id);

CREATE INDEX IF NOT EXISTS ix_study_plan_day_status_order
  ON study_plan(plan_date, status, order_no);
