# data.md

## Schema

Database `jumpking`, MySQL/InnoDB, utf8mb4, timestamps UTC. Schema tạo bằng `scripts/schema.sql`; Hibernate chỉ validate mapping khi start; SQL giữ constraints và indexes. Tạo/nâng cấp theo [deployment](deployment.md#mysql).

| Bảng | Trách nhiệm |
| --- | --- |
| users | Định danh Account/Guest, credentials, vương miện và liên kết Merge |
| play_runs | Một lượt của một người: metrics, kết quả Room và checkpoint Community |
| matches | Thông tin chung của một trận Room |

Solo/Community có lượt cá nhân; Room có match chung. Phạm vi save/thành tích theo [gameplay](gameplay.md#phạm-vi-và-kiểu-chơi).

```mermaid
erDiagram
    users ||--o{ play_runs : owns
    matches o|--|{ play_runs : groups
    users o|--o{ users : merged_into

    users {
        BIGINT id PK
        VARCHAR(16) type
        VARCHAR(32) username UK "nullable"
        VARCHAR(254) email UK "nullable"
        VARCHAR(255) password_hash "nullable"
        CHAR(64) guest_token_hash UK "nullable"
        BIGINT merged_into_user_id FK "nullable"
        BOOLEAN crown_unlocked
        DATETIME(3) created_at
    }
    play_runs {
        CHAR(36) id PK
        BIGINT user_id FK
        VARCHAR(16) kind
        CHAR(36) match_id FK "nullable; ROOM"
        VARCHAR(16) status
        BIGINT play_ticks
        INT jumps
        INT falls
        DOUBLE current_progress
        DOUBLE max_progress
        BIGINT peak_reached_ticks
        BIGINT finish_ticks "nullable"
        VARCHAR(16) nickname "nullable; ROOM"
        TINYINT rank "nullable; ROOM"
        BOOLEAN is_winner
        JSON snapshot "nullable; COMMUNITY ACTIVE"
        BIGINT revision
        BIGINT active_community_user_id UK "generated; nullable"
        DATETIME(3) started_at
        DATETIME(3) updated_at
        DATETIME(3) ended_at "nullable"
    }
    matches {
        CHAR(36) id PK
        CHAR(6) room_code
        VARCHAR(16) mode
        SMALLINT time_limit_minutes "nullable"
        SMALLINT map_level "1–10; 10=full"
        DATETIME(3) started_at
        DATETIME(3) ended_at "nullable"
        BIGINT duration_ticks
        VARCHAR(32) end_reason "nullable"
    }
```

users.id tự tăng; lượt online/match dùng UUID server, Solo dùng UUID local. UUID/hash/room code dùng ascii_bin, username/email dùng utf8mb4_0900_ai_ci. FK dùng RESTRICT để giữ dữ liệu thống kê.

| Trường trong play_runs | Ý nghĩa |
| --- | --- |
| kind / match_id | SOLO, COMMUNITY hoặc ROOM; chỉ ROOM có match_id |
| play_ticks / jumps / falls | Thời gian tham gia và số lần launch/splat của một lượt |
| current_progress / max_progress | Tiến độ hiện tại/lúc dừng và peak; định nghĩa theo [gameplay](gameplay.md#room-và-xếp-hạng) |
| peak_reached_ticks | play_ticks tại lần đầu đạt max_progress; chỉ thay khi peak tăng, không thay khi leo lại đúng peak cũ |
| finish_ticks | Thời gian đến đích; Solo/Community dùng clock lượt, Room dùng clock trận |
| nickname / rank / is_winner | Tên lúc tham gia, thứ hạng cuối và winner Room; không suy winner chỉ từ rank=1 |
| snapshot | State đầy đủ để Continue Community |
| revision | Số thứ tự bản ghi từ 1 |
| active_community_user_id | Cột DB tự tính để giới hạn một lượt Community ACTIVE/Account |
| started_at / updated_at / ended_at | Bắt đầu, lưu thành công gần nhất và kết thúc; không dùng hiệu thời gian lịch để xếp hạng |

Ticks theo [architecture](architecture.md#xử-lý-đồng-thời); API đổi thành ms bằng floor(ticks×1000/tickRate). Counters/ticks không âm, max_progress≥current_progress≥0, 0≤peak_reached_ticks≤play_ticks. finish_ticks bằng play_ticks với Solo/Community, không vượt duration_ticks với Room.

| status | Ý nghĩa và phạm vi |
| --- | --- |
| ACTIVE | Lượt chưa kết thúc; Community paused/offline vẫn ACTIVE |
| FINISHED | Đã đến đích; finish_ticks khác NULL |
| UNFINISHED | Room kết thúc khi người này còn tham gia nhưng chưa Finish |
| ABANDONED | Bỏ lượt Solo/Community để bắt đầu lượt mới |
| DNF | Rời Room/hết hạn reconnect trước khi trận kết thúc, hoặc trận bị abort |

Chỉ ACTIVE có ended_at=NULL; trạng thái khác có ended_at≥started_at. Chỉ FINISHED có finish_ticks khác NULL. Solo/Community không dùng UNFINISHED/DNF; Room không dùng ABANDONED. snapshot là JSON object chỉ khi kind=COMMUNITY và status=ACTIVE; còn lại NULL. match_id/nickname chỉ có ở ROOM; rank NULL đến khi finalize, sau đó trong [1,10]. Ngoài ROOM, rank=NULL và is_winner=false. Winner chỉ khi FINISHED, rank=1.

Indexes: unique username/email/guest_token_hash/active_community_user_id; play_runs(user_id,ended_at,id), play_runs(match_id), play_runs(kind,status) cho Profile/History/detail/leaderboard. Không unique(match_id,user_id): sau Merge một Account có thể sở hữu nhiều lượt cùng trận.

```sql
active_community_user_id BIGINT GENERATED ALWAYS AS
    (CASE WHEN kind = 'COMMUNITY' AND status = 'ACTIVE'
          THEN user_id ELSE NULL END) VIRTUAL,
UNIQUE KEY uq_active_community_user (active_community_user_id)
```

Cột generated tự tính, chỉ lượt Community ACTIVE có user_id; các lượt khác NULL. CHECK bảo vệ tổ hợp kind/status/NULL và biên cùng hàng, dùng IS NULL/IS NOT NULL tường minh. Service kiểm tra owner Account, roster/rank/comparator và biên thời gian giữa hai bảng trong transaction. [MySQL generated columns](https://dev.mysql.com/doc/refman/8.4/en/create-table-generated-columns.html), [unique indexes](https://dev.mysql.com/doc/refman/8.4/en/create-index.html), [CHECK constraints](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html).

## Identity và ownership

CHECK yêu cầu ACCOUNT đủ credentials, không token/pointer; GUEST không credentials, có token hoặc merge pointer nhưng không đồng thời cả hai. Email lowercase. Service kiểm tra đích Merge là ACCOUNT khác bản thân; hashing/cookie theo [protocol](protocol.md#identity-và-security).

MySQL giữ Room của Guest và cả ba kiểu chơi của Account. Solo Guest giữ local, không import qua Register/Login/Merge.

Register nâng Guest thành Account, giữ ID/Room History. Merge sang Account có sẵn khóa users theo thứ tự ID, chuyển ownership lượt Room, lưu pointer và thu hồi token trong một transaction. Writer đến sau khóa/resolve owner trước khi ghi. Merge không tạo lượt hoặc cộng counter; lặp cùng đích là no-op, không đổi sang đích khác.

Owner online chốt khi Join, không đổi theo cookie. Nickname Room giữ lúc Join; Community/leaderboard dùng username hiện tại. Đổi identity khi có membership theo protocol.

## Ghi lượt và đồng bộ Solo

New Game/New Run/Room Start tạo UUID mới; Continue/reconnect giữ ID. Ghi counters tích lũy trong transaction: revision mới hơn được cập nhật, bản cũ bỏ qua, cùng revision/nội dung là no-op, cùng revision khác nội dung là CONFLICT. Counters/peak không giảm; metrics đã kết thúc bất biến, rank/winner Room chốt ở finalize.

Solo lưu state/metrics, tóm tắt lượt đã kết thúc và hàng chờ sync trong IndexedDB, một writer/lượt. New Game chốt lượt cũ ABANDONED nếu còn ACTIVE; Finish chốt FINISHED. Chỉ thay save Continue, không xóa summaries dùng cho Stats/History. Profile tách Guest/Account; khóa Account là backend origin + Account ID từ me. Lượt chỉ sync khi đăng nhập đúng Account lúc bắt đầu; gửi metrics/revision, không snapshot. Backend lấy owner từ session, chỉ cho ghi SOLO và từ chối UUID thuộc owner/kind khác. Lượt mới trên hai thiết bị có UUID khác nhau.

Reload/lỗi mạng giữ hàng chờ; chỉ xác nhận sync sau commit. New Game giữ metrics cũ chờ sync nhưng thay save Continue. SOLO ACTIVE không giữ online membership hoặc khóa Community/Room. Metrics Solo do client khai báo chỉ dùng cho Stats/History, không dùng leaderboard. Contract/chu kỳ sync theo [protocol](protocol.md#http).

## Community checkpoint

Continue tìm COMMUNITY/ACTIVE theo Account; chưa có thì tạo spawn snapshot, revision=1. Save thuộc lượt, không gắn room code. Chuyển sảnh/Save & Exit/AFK giữ lượt.

Snapshot do server tạo: physics (freeFlight/phasingFall), hội thoại theo ID, screen events/hidden walls/pha gió và codec version. Không chứa định nghĩa shared hoặc nội dung hiển thị. Backend validate codec/metrics khi restore; xử lý quyền bay theo [gameplay](gameplay.md#fly-mode).

Autosave ghi metrics/snapshot/revision/updated_at cùng transaction trên đúng id/owner/COMMUNITY/ACTIVE, không upsert lượt đã mất. Worker tuần tự từng lượt, gộp bản chưa ghi. Chu kỳ theo [deployment](deployment.md#cấu-hình); Pause/Leave/Finish yêu cầu flush.

New Run khóa users rồi lượt bằng SELECT ... FOR UPDATE, kiểm tra expectedRunId; chốt lượt ACTIVE cũ ABANDONED/ended_at, tăng revision/updated_at, xóa snapshot và insert lượt mới trong một transaction. Rollback giữ save cũ. Finish chốt metrics/FINISHED/finish_ticks/ended_at, tăng revision/updated_at và xóa snapshot atomically. Writer cũ không phục hồi lượt. [MySQL locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html).

## Kết quả Room

Room bắt đầu gameplay tạo match và lượt ROOM của roster trong một transaction bằng IDs chốt khi Start; Lobby/countdown hủy không ghi. started_at là hết countdown. Mode/settings/map_level nằm ở matches theo [gameplay](gameplay.md#room-và-xếp-hạng); time_limit_minutes chỉ khác NULL ở TIME_LIMIT. end_reason: FINISH/TIME_LIMIT/HOST_ENDED/PAUSE_TIMEOUT/ALL_DISCONNECTED/ABORTED.

Writer lưu duration_ticks/metrics định kỳ theo deployment và flush khi Host Pause; không lưu snapshot Room. Pause/Resume không kết thúc trận hoặc tạo lượt mới; PAUSE_TIMEOUT chốt người còn tham gia UNFINISHED, không winner. Leave/hết hạn reconnect chốt DNF; finalize chốt người còn lại UNFINISHED/FINISHED. play_ticks≤duration_ticks; xếp hạng dùng clock chung theo gameplay.

Finalize ghi match và metrics/rank/winner trong một transaction, tăng revision/updated_at; giữ metrics/ended_at đã chốt của DNF. Match đang chơi có ended_at/end_reason cùng NULL, kết thúc cùng khác NULL và ended_at≥started_at. Service kiểm tra roster 2–10, đúng match/kind, rank≤số người và comparator. Retry giữ UUID: cùng kết quả no-op, khác kết quả đã chốt báo lỗi.

Room hết thành viên vẫn giữ kết quả chưa ghi trong quota phòng; retry cùng payload, chỉ giải phóng sau commit. Shutdown flush có giới hạn thời gian; không báo SAVED khi ghi lỗi.

Restart chốt match còn mở ABORTED, lượt ROOM ACTIVE thành DNF. Dùng metrics đã commit để xếp hạng, không cộng ticks offline hoặc tạo winner mới. Giới hạn phục hồi theo [architecture](architecture.md#lỗi-và-khả-năng-mở-rộng).

## History và leaderboard

| Dữ liệu | Nguồn |
| --- | --- |
| History Solo/Community | play_runs đã kết thúc; mỗi lượt một mục của owner |
| History Room | matches và play_runs ROOM; mỗi match đã finalize một mục của owner |
| Profile Stats | SUM(play_ticks/jumps/falls), số lượt FINISHED và MIN(finish_ticks) full map từ play_runs của identity |
| Số trận/thắng Room | COUNT DISTINCT match_id, riêng thắng lọc is_winner=true |
| Leaderboard | Chỉ play_runs COMMUNITY; một best run mỗi Account theo [gameplay](gameplay.md#community-tower) |

Account History gộp Solo/Community theo run ID và Room theo match ID, mới nhất trước theo ended_at rồi kind/id. Chỉ owner đọc được lượt cá nhân; owner của ít nhất một lượt đọc được match. Sau Merge, summary Room chọn lượt tốt nhất của owner, detail giữ đủ các lượt. Contract theo [protocol](protocol.md#http).

Solo Guest dùng summaries local, không gửi MySQL. Solo Account chưa sync giữ summary local ở trạng thái chờ; khi server đã lưu, thay bằng cùng kind/ID, không tạo mục thứ hai. Lượt Community ACTIVE và Room chưa finalize chưa vào History.

Thống kê Account lấy tổng đã commit của cả ba kiểu chơi. Guest cộng Solo local với Room của identity Guest; không đưa Solo Guest vào tổng Account khi Login/Register. Solo Account đã sync không cộng lại từ local; số liệu chưa sync giữ riêng đến khi commit. Không có bộ lọc kind/mode cho thống kê tổng.

Leaderboard lấy metrics Community đã commit; chọn best bằng ROW_NUMBER rồi tính RANK trước LIMIT. ID chỉ ổn định lựa chọn/hiển thị, không phá đồng hạng. Comparator/eligibility theo [gameplay](gameplay.md#community-tower). [MySQL window functions](https://dev.mysql.com/doc/refman/8.4/en/window-function-descriptions.html).

Giữ hàng metrics để bảo toàn tổng/History/best. Query có tham số và kiểm tra ownership, chỉ chọn cột cần dùng; Stats/History/leaderboard không đọc snapshot hoặc credentials/token.

Persistence online dùng PENDING/SAVED/FAILED; SAVED chỉ sau commit. Solo tách local save với sync status. Backup/nâng cấp theo [deployment](deployment.md#mysql).

## JPA và nâng cấp

User, PlayRun và Match ánh xạ ba bảng; Service sở hữu transaction, Repository sở hữu truy vấn. Không serialize Entity. Chỉ đọc snapshot khi Continue/save, dùng projection cho Stats/History/leaderboard. `revision` giữ thứ tự payload và chống retry khác nội dung, không thay bằng optimistic version của ORM. Các thao tác merge/New Run/writer khóa owner theo thứ tự rồi khóa lượt trong transaction.

`users.crown_unlocked` chỉ chuyển false → true khi có full Finish, hoặc Merge từ Guest đã mở. Guest Solo gửi riêng quyền crown khi vào Room; đây là ngoại hình tự khai báo, không gửi save/history/metrics hoặc ảnh hưởng leaderboard. Guest nhận crown online lưu lại trong profile trình duyệt.

`matches.map_level` mặc định 10 cho dữ liệu hiện có, trong [1,10]; Public/Private là metadata phòng RAM, không cần lưu khi match kết thúc. Best Finish Time bỏ Room map_level<10; counters/Finishes/Room Wins vẫn tính.

Cập nhật database có sẵn bằng SQL additive theo deployment, không reset. Hibernate `validate` không thay thế kiểm thử CHECK/unique/FK trong MySQL thật.
