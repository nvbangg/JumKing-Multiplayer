# PLAN.md

## Cách sử dụng

Thiết kế theo docs; kế hoạch dưới đây thay thế thứ tự triển khai cũ. Mỗi checkbox là một commit gồm code, tests và docs liên quan; chỉ tick sau kiểm chứng và review. Không commit riêng bản kế hoạch trước code. Lịch sử cũ ghi riêng, không là bằng chứng nghiệm thu phiên bản mới.

## Phase 00 — Đồng bộ thiết kế

- [x] Đồng bộ architecture/data/gameplay/protocol/deployment/testing/README theo phạm vi mới; đi cùng commit nền tảng JPA đầu tiên.

## Phase 01 — Nền tảng JPA

- [x] Ánh xạ User/PlayRun/Match bằng JPA, cấu hình validation và kiểm thử MySQL; giữ nguyên dữ liệu/schema hiện hành trước mở rộng.

## Phase 02 — Chuyển persistence

- [x] Chuyển Account/Guest/session và authentication sang UserRepository; hồi quy đăng nhập.
- [x] Chuyển merge/ownership và Solo metrics; kiểm thử locking/idempotency/rollback.
- [x] Chuyển Community checkpoint/New Run/Finish và worker; hồi quy Continue/retry.
- [ ] Chuyển Room writer/recovery; kiểm thử ghi/finalize đúng một lần.
- [ ] Chuyển Stats/History/leaderboard/current sang projections/Repository; bỏ đường JDBC và SQL ngoài repository.

## Phase 03 — Tinh giản nội dung

- [ ] Bỏ Inventory/items/mua bán/quest nhận đồ khỏi hai simulation/shared/protocol; giữ NPC/hội thoại phù hợp và save hiện có.
- [ ] Bỏ UI/trang bị và nhánh ending phụ; giữ một ending cơ bản.
- [ ] Thêm crown profile tự mở bằng full Finish, Merge/local/sync; SQL nâng cấp không reset dữ liệu.

## Phase 04 — Room và map

- [ ] Triển khai Public/Private và Join Public; kiểm tra state/capacity và Join cạnh tranh.
- [ ] Cấu hình 10 mốc Finish từ geometry chung, match map_level và kiểm thử tất cả spawn/route hợp lệ.
- [ ] Hoàn thiện Lobby/History/Results/ending và best full-map theo độ dài; SQL nâng cấp giữ dữ liệu.

## Phase 05 — Chat và xếp hạng

- [ ] Chat chung Enter/Esc/focus/tin mới 10 giây và touch; nghiệm thu vấn đề #5.
- [ ] Players ranking từ server RAM ở Room/Community, bảng thu gọn.
- [ ] Leaderboard Community trong trận, refresh giới hạn; nghiệm thu vấn đề #10.

## Phase 06 — Rà soát và triển khai

- [ ] Rà soát code chết/trùng, trách nhiệm lớp, comments và security/config; không thay luật ngoài scope.
- [ ] Nghiệm thu clean setup/build/JAR, Local/LAN/ZeroTier và cấu hình VPS một domain.

## Phase 07 — Nghiệm thu và học phần

- [ ] Hồi quy gameplay/auth/save/history/chat/Room, network/DB failures và 10 mốc Finish.
- [ ] Chơi trực tiếp mobile, nhiều identities và LAN/ZeroTier hai thiết bị; ghi phần cần người dùng hỗ trợ.
- [ ] Đo RTT/message rate/tick/CPU/heap và so sánh snapshot-only/prediction bằng số liệu thật.
- [ ] Hoàn thiện demo/báo cáo theo học phần, contribution và phân công thật; Compilatio/nộp Drive cần người dùng xác nhận.

## Phase 08 — Mở rộng sau nghiệm thu

- [ ] Thay assets bằng nguồn có giấy phép hoặc tự tạo.
- [ ] Deploy Internet.
- [ ] Email verification/password reset.
- [ ] Items/player collision trong Room khi có yêu cầu.

## Kiểm chứng phiên bản mới

| Mốc | Kiểm chứng |
| --- | --- |
| Phase 00–01 | PASS — 68 links/anchors; backend unit tests và 16 MySQL integration tests (IdentityIT, JpaPersistenceIT), JSON/timestamp round-trip. Schema/database cá nhân không thay đổi. Các Service JDBC chuyển theo Phase 02; gameplay mới chưa nghiệm thu. |

| Phase 02 — Identity | PASS — 51 unit tests, 18 MySQL integration tests; 22 Chrome online và 1 identity-race PASS. Account/Guest/authentication dùng UserRepository, không đổi contract/session hoặc database cá nhân. |

| Phase 02 — Merge/Solo | PASS — 51 unit và 19 MySQL integration tests (Identity, Community, Room writer). Giữ kiểm tra retry/conflict/rollback và late writer sau Merge. Fixture Room chuyển sang Spring bean để cùng datasource/transaction JPA; lượt lỗi fixture ban đầu không tính PASS. |

| Phase 02 — Community | PASS — 51 unit và 9 MySQL integration tests, gồm New Run/Continue, rollback và save failure. Flush lượt cũ trước insert để giữ generated unique ACTIVE; không sửa database cá nhân. |

## Lịch sử triển khai trước thay đổi kiến trúc

Bản JDBC/full-map/Inventory đã hoàn thành nền tảng, hai simulation, Solo, Account/merge, Community và bốn mode Room. Mã và kết quả từng mốc được giữ trong lịch sử Git; các kết quả sau chỉ mô tả bản trước, không thay nghiệm thu bản mới.

| Phạm vi | Kết quả đã ghi nhận |
| --- | --- |
| Physics/map | Fixtures Java/TypeScript, route tới Finish bằng input hợp lệ, 43 screen và camera |
| Persistence | MySQL thật, transaction/rollback, revision/merge và finalize; checkpoint/recovery, backup/restore |
| UI/online | Account, Solo sync, Community và Room nhiều identities, Fly/ending/chat, packaged JAR |
| Rà soát cuối | 68 frontend tests; 51 backend unit tests; 70 integration PASS và 1 opt-in SKIP; 30 Chrome E2E PASS |
| Security | npm audit 0; hai advisory Spring MVC có điều kiện SSE/XSLT không có trong ứng dụng; chưa coi là bảo đảm không còn lỗ hổng |
| Giới hạn | Chưa nghiệm thu điện thoại và ZeroTier hai thiết bị; chưa hoàn thành báo cáo/Compilatio/nộp bài |
| Timeout hệ thống | Probe Java và Node từng gặp lỗi TCP, chưa tìm được nguyên nhân; lượt hồi quy sau không tái hiện, không kết luận đã khắc phục hệ thống |
