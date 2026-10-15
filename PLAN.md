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
- [x] Chuyển Room writer/recovery; kiểm thử ghi/finalize đúng một lần.
- [x] Chuyển Stats/History/leaderboard/current sang projections/Repository; chuẩn hóa controller/service/repository/entity/dto và tests; bỏ đường JDBC và SQL ngoài repository.
- [x] Tổ chức source/client, source/server, source/common và statics theo học phần; đồng bộ build/scripts/hướng dẫn chạy, bảo toàn cấu hình riêng và hồi quy.

## Phase 03 — Tinh giản nội dung

- [x] Bỏ Inventory/items/mua bán/quest nhận đồ khỏi simulation/common/protocol/UI và nhánh ending phụ; giữ NPC/hội thoại, môi trường, một ending cơ bản và save hiện có.
- [x] Thêm crown profile tự mở bằng full Finish, Merge/local/sync; SQL nâng cấp không reset dữ liệu.

## Phase 04 — Room và map

- [x] Triển khai Public/Private và Join Public; kiểm tra state/capacity và Join cạnh tranh.
- [x] Cấu hình 10 mốc Finish từ geometry chung, match map_level và kiểm thử tất cả spawn/route hợp lệ.
- [x] Hoàn thiện Lobby/History/Results/ending và best full-map theo độ dài; SQL nâng cấp giữ dữ liệu.

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
- [ ] Chốt đóng góp kỹ thuật và baseline; đo RTT/message rate/tick/CPU/heap, so sánh snapshot-only/prediction bằng số liệu thật, phân tích ưu/nhược điểm và giới hạn.
- [ ] Hoàn thiện `report/report.pdf` theo technical paper của học phần; dùng sơ đồ trong `statics/`, kết quả chọn lọc ở `statics/results/`, trích dẫn nguồn code/assets/ý tưởng đã sử dụng và phân công thực tế.
- [ ] Đối chiếu README/bộ source với checklist học phần; diễn tập demo và giải thích protocol/concurrency; Compilatio ≤20% và nộp Drive cần người dùng xác nhận.

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
| Phase 02 — Room writer | PASS — 51 unit; 14 MySQL integration tests qua hai nhóm, gồm Race/Hardcore/Time Limit/No Walk, Host Pause, rollback/late merge, writer và restart ABORTED. Match và roster cùng transaction JPA; entity không đi vào actor. |
| Phase 02 — Layer/Read Repository | PASS — clean build, 51 unit tests; 72 MySQL integration tests gồm 71 PASS và 1 load opt-in SKIP, flakes=0; 30 Chrome PASS qua các harness, gồm JAR cùng origin. Frontend build/type-check, 68 links/anchors và diff check PASS. Không còn JdbcTemplate trong production, SQL nằm ở Repository; DTO tách khỏi Controller/Service, tests theo package mới. Không reset database/cấu hình riêng. |
| Rà soát nền tảng trước Phase 03 | PASS — 51 unit và 7 MySQL integration tests (SchemaIT, JpaPersistenceIT); JDT resource analysis không còn cảnh báo TestDatabase. Container được đóng khi khởi tạo lỗi hoặc JVM kết thúc. Nhánh Docker chưa chạy thực tế; integration dùng MySQL cô lập. Cảnh báo m2e về shared ngoài backend còn có thể xuất hiện, không làm Maven build lỗi. |
| Cấu trúc theo học phần | PASS — source/client, source/server, source/common; Maven/Vite/scripts và hướng dẫn chạy đã đồng bộ. 68 client unit tests, 51 Java unit tests, 71 MySQL integration PASS và 1 load opt-in SKIP; 57 Chrome PASS (27 client, 30 online/JAR), build và 72 links/anchors PASS. Sơ đồ architecture/protocol đã render kiểm tra; dữ liệu/cấu hình riêng và tài liệu học phần được giữ nguyên. Báo cáo, thực nghiệm mới và nghiệm thu hai thiết bị vẫn thuộc Phase 07. |

| Phase 03 — Bỏ Inventory | PASS — 66 client unit tests, 48 Java unit tests; 71 MySQL integration PASS, 1 load opt-in SKIP; 27 Chrome client PASS (renderer đã chạy lại sau cập nhật assertion) và 30 Chrome online/JAR PASS. Giữ route hợp lệ 43 screen, NPC/readables, save cũ và ending cơ bản/Skip; loại 9 assets không còn dùng, import nội dung lặp lại cho cùng dữ liệu. Build, 72 links/anchors và diff check PASS. Crown profile server/Merge/sync thuộc checkbox tiếp theo; chưa thay schema/database cá nhân. |

| Phase 03 — Crown profile | PASS — 66 client unit, 48 Java unit; 74 MySQL integration PASS và 1 load opt-in SKIP, flakes=0; 31 Chrome online/JAR, 27 client và 3 Fly dev PASS. Guest mang crown Solo sang Room; Account giữ crown trên thiết bị mới, không chuyển checkpoint. Merge/registration, retry và rollback cùng kết quả đã kiểm chứng. SQL nâng cấp đã áp dụng trên MySQL, giữ dữ liệu; build, links/anchors và diff check PASS. E2E dùng cờ Fly riêng, không sửa cấu hình cá nhân. |

| Phase 04 — Public/Private | PASS — 66 client unit, 50 Java unit; 75 MySQL integration PASS và 1 load opt-in SKIP; Chrome online/JAR và 27 client E2E PASS. Public chỉ liệt kê Lobby còn chỗ; Private vẫn Join bằng ID. Kiểm chứng đổi visibility, quyền Host, Join/Start cạnh tranh và capacity; build/type-check, links và diff check PASS. |

| Phase 04 — Mốc Finish | PASS — 77 client unit, 52 Java unit; 10 MySQL integration PASS. Replay hợp lệ từ spawn chuẩn tới cả 10 đích, không Fly/splat; đồng bộ prediction và server. Map ngắn không cấp crown/Best Finish Time/ending; lưu map_level, retry/conflict/restart và migration giữ dữ liệu đã kiểm chứng. Build/type-check và diff check PASS. Lobby/History hoàn thiện ở mốc tiếp theo; chưa chạy migration trên database cá nhân. |

| Phase 04 — Lobby/History/Results | PASS — 77 client unit, 52 Java unit; 7 Chrome online (chọn đủ 10 map, map ngắn/full ending và bốn mode Fly), 27 client E2E; nhóm MySQL History/restart 5 PASS sau sửa assertion fixture mặc định. Hai identities thấy cùng map; Results/History đúng độ dài, Play Again giữ ID/settings, không cấp nhầm crown/best full-map. Đã xem ảnh Lobby/Results; build/type-check và diff check PASS. SQL nâng cấp vẫn cần chạy trên database đang dùng trước khi chạy backend mới. |

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
