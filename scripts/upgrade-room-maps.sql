-- Chạy một lần trên schema chưa có map_level, sau khi dừng backend.
USE jumpking;
ALTER TABLE matches
    ADD COLUMN map_level SMALLINT NOT NULL DEFAULT 10,
    ADD CONSTRAINT ck_matches_map_level CHECK (map_level BETWEEN 1 AND 10);
