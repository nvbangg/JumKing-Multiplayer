-- Chạy một lần trên schema trước khi có crown_unlocked; không tạo lại database.
USE jumpking;
ALTER TABLE users ADD COLUMN crown_unlocked BOOLEAN NOT NULL DEFAULT FALSE;

-- Các lượt trước nâng cấp đều dùng full map.
UPDATE users u
SET u.crown_unlocked = TRUE
WHERE EXISTS (SELECT 1 FROM play_runs r WHERE r.user_id = u.id AND r.status = 'FINISHED');

UPDATE users account_user
JOIN users guest_user ON guest_user.merged_into_user_id = account_user.id
SET account_user.crown_unlocked = TRUE
WHERE guest_user.crown_unlocked = TRUE;
