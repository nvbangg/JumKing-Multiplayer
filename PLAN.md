# PLAN.md

## Cách sử dụng

Thiết kế theo docs; PLAN theo dõi thứ tự, tiến độ và kiểm chứng.

Mỗi checkbox là một commit gồm implementation, tests và docs liên quan; phase chỉ nhóm công việc. Chỉ tick sau kiểm chứng/review và đưa checkbox vào chính commit đó. [x] = hoàn tất, kiểm chứng và commit; [ ] = chưa đủ.

## Phase 00 — Thiết kế

- [x] Hoàn thiện tài liệu khởi tạo và .gitignore chung; kiểm tra nguồn quyết định, links, contracts và yêu cầu học phần.

## Phase 01 — Nền tảng

- [x] Khởi tạo React/TypeScript/Phaser, lockfile, npm scripts và .gitignore frontend; kiểm tra build/trang khởi động.
- [x] Khởi tạo Spring Boot/Java, Maven Wrapper, package, health check và .gitignore backend; kiểm tra test/build/start.
- [x] Triển khai schema ba bảng và JDBC/MySQL config/validation; kiểm tra ràng buộc, indexes và transactions trên instance cô lập.
- [x] Hoàn thiện config mẫu, Vite proxy và Maven web/JAR; kiểm tra development, đóng gói và refresh route UI.

## Phase 02 — Map và simulation

- [x] Chuẩn hóa Main Babe map/spawn/Finish/tile/biên, tuning/rules/adventure; kiểm tra geometry và ranh giới với presentation.
- [x] Đóng gói shared vào hai build; kiểm tra nạp dữ liệu từ artifact.
- [x] Triển khai TypeScript simulation và state codec; kiểm tra terrain/charge/collision bằng fixtures.
- [ ] Triển khai Java simulation; kiểm tra số học từng tick và restore/replay khớp fixtures TypeScript.
- [ ] Triển khai bay/thoát solid trong hai simulation; kiểm tra input, biên và restore.
- [ ] Hoàn thiện input replay từ spawn tới Finish; kiểm tra toàn tuyến không dùng bay/teleport.

## Phase 03 — Solo và presentation

- [ ] Chuẩn hóa assets/nội dung Main Babe, manifest/license; kiểm tra ID, quyền phân phối và tải tài nguyên.
- [ ] Hoàn thiện renderer/sprite/animation/camera/môi trường; kiểm tra toàn tuyến và chuyển screen.
- [ ] Hoàn thiện frontend theo các chức năng đã thiết kế; kiểm tra navigation/focus, responsive và trạng thái lỗi.
- [ ] Triển khai Solo save/Continue/Pause/New Game/Stats local; kiểm tra profile/UUID/revision, sync queue, offline/storage và tự Pause khi hidden/blur/AFK.
- [ ] Hoàn thiện Solo History local; kiểm tra terminal summaries, Guest/Account tách biệt và không mất lịch sử khi thay save.
- [ ] Hoàn thiện keyboard/touch/gamepad; kiểm tra charge, multi-touch và cancel input.
- [ ] Hoàn thiện audio/Options; kiểm tra lựa chọn, audio unlock/cache và lưu settings.
- [ ] Triển khai collectibles/inventory/equipment/merchant/quests trong hai simulation; kiểm tra fixtures/restore.
- [ ] Hoàn thiện NPC/readables/hội thoại; kiểm tra ID/state/trigger và focus/input.
- [ ] Hoàn thiện Solo ending/Skip; kiểm tra đúng Finish, không credit/phát trùng.
- [ ] Thêm Options/điều khiển Fly Mode; kiểm tra quyền Solo dev, nhấn đúp, di chuyển và restore.

## Phase 04 — Account và contracts

- [ ] Triển khai Guest/session/CSRF/Origin và HTTP errors; kiểm tra cookies, idle/absolute, quyền và rate limits.
- [ ] Hoàn thiện Register/Login/Logout và UI Account; kiểm tra validation, session rotation và ownership.
- [ ] Hoàn thiện Merge/Skip Guest Room History; kiểm tra pointer/writer đồng thời, nhiều lượt cùng match và Stats không cộng trùng.
- [ ] Triển khai map/game-config API và HTTP/WS adapter; kiểm tra contract, credentials, endpoints và lỗi kết nối.
- [ ] Hoàn thiện Solo metrics sync; kiểm tra owner, hai thiết bị, revision/retry, pending History và local/server totals.
- [ ] Hoàn thiện WS transport/registry/executor, sequence/heartbeat/queues; kiểm tra quotas, socket cũ và slow consumers.

## Phase 05 — Community Tower

- [ ] Triển khai Account-only Community và simulation server; kiểm tra ghép/tạo sảnh, Join/Leave và state của hai clients.
- [ ] Hoàn thiện prediction/reconciliation/interpolation; kiểm tra ACK/replay, latency, thiếu frame và resync.
- [ ] Hoàn thiện checkpoint/worker/autosave/Continue/New Run; kiểm tra transaction/revision, một lượt ACTIVE và rollback.
- [ ] Hoàn thiện Pause/Resume/AFK/Save & Exit theo gameplay; kiểm tra hidden/blur, clock, đang rơi, save status và hạn giữ slot.
- [ ] Hoàn thiện Join ID/transfer/reconnect; kiểm tra capacity, membership, biên deadline và Continue sau hết hạn.
- [ ] Đồng bộ ngoại hình/nhãn tên/animation/audio peers; kiểm tra self/peers, camera/screen, slot reuse và dedupe effects.
- [ ] Hoàn thiện chat Community; kiểm tra focus, plain text, history và giới hạn.
- [ ] Hoàn thiện leaderboard/UI; kiểm tra best từng Account, comparator, đồng hạng và dữ liệu đã commit.
- [ ] Hoàn thiện thống kê tổng; kiểm tra Guest local/Room, Account cả ba kiểu chơi, best và chống cộng trùng.
- [ ] Hoàn thiện History Solo/Community và contract chung; kiểm tra ownership, pagination, pending/sync và terminal state.
- [ ] Hoàn thiện Community Finish/ending riêng; kiểm tra flush/finalize, Skip/New Run và peers tiếp tục.
- [ ] Tích hợp Fly Mode online; kiểm tra server gate, prediction/checkpoint và thành tích.

## Phase 06 — Room

- [ ] Hoàn thiện Create/Join Room và Lobby/settings/Host; kiểm tra permissions, capacity và điều kiện Start.
- [ ] Tích hợp chat Room; kiểm tra history/focus/input và giới hạn.
- [ ] Hoàn thiện Race Start/countdown/Finish/Results; kiểm tra 1/2/max người và Finish cùng tick.
- [ ] Hoàn thiện End/Leave/Host transfer/reconnect; kiểm tra deadlines, khóa Join, re-authentication và Host khi mọi người offline.
- [ ] Hoàn thiện Host Pause/Resume Room; kiểm tra clock/state/input, giữ RAM, hết hạn, chuyển Host/reconnect và restart.
- [ ] Hoàn thiện Play Again từng người; kiểm tra room code/settings, xác nhận/AFK/Join mới, roster và match/History mới.
- [ ] Triển khai Hardcore respawn; kiểm tra body reset, giữ clock/peak/counters.
- [ ] Triển khai Time Limit; kiểm tra biên phút, deadline và Finish đúng tick.
- [ ] Triển khai No Walk; kiểm tra hướng/vận tốc trên nền, nhảy/bounce và luật kết thúc.
- [ ] Hoàn thiện comparator bốn mode; kiểm tra progress/peak/time, DNF, winner và đồng hạng.
- [ ] Hoàn thiện Room writer và tích hợp History chung; kiểm tra ghi định kỳ, finalize/retry, Merge, permissions/pagination và restart.
- [ ] Tích hợp Room ending cho winners; kiểm tra người khác tới Results và không ending khi chưa Finish.
- [ ] Nghiệm thu Fly Mode trong bốn mode Room; kiểm tra respawn, Finish, History/Stats và xếp hạng.

## Phase 07 — Nghiệm thu

- [ ] Nghiệm thu tự động/chơi trực tiếp toàn tuyến Solo/Community/Room; sửa lỗi, không dùng cheat thay route.
- [ ] Nghiệm thu điện thoại, hai identities cùng máy và LAN/ZeroTier hai thiết bị.
- [ ] Hoàn thiện workload và thực nghiệm snapshot-only/prediction; ghi số đo, cải tiến và giới hạn.
- [ ] Nghiệm thu clean clone/setup/build/run, config, MySQL restart/backup/restore và lỗi mạng/DB.
- [ ] Rà soát code/dependencies/assets/license và artifact/Git trước bàn giao.
- [ ] Hoàn thiện báo cáo/demo học phần: đóng góp nhóm, results/limitations, trích dẫn và Compilatio.

## Phase 08 — Mở rộng sau nghiệm thu

Chưa thực hiện; chỉ bắt đầu khi các phase trước ổn định và có yêu cầu.

- [ ] Thay assets bằng tài nguyên tự tạo hoặc có giấy phép; cập nhật manifest/ATTRIBUTION, nghiệm thu hình ảnh/audio toàn tuyến.
- [ ] Deploy Cloudflare Pages + VPS; cấu hình domain/HTTPS/WSS và nghiệm thu Internet.
- [ ] Voice trong trận.
- [ ] Historical Ghost.
- [ ] Email verification/password reset.
- [ ] Tùy chọn: items/player collision trong Room; chốt luật trước triển khai.

## Kết quả kiểm chứng

Ghi kết quả/checks chưa chạy và commit theo checkbox; raw reports theo [testing](docs/testing.md#thực-nghiệm-và-bàn-giao).

| Phần | Kết quả |
| --- | --- |
| Phase 00 | PASS — 9 tài liệu, 67 links/anchors nội bộ; đối chiếu contracts, Source of Truth, yêu cầu học phần; git diff --check |
| Phase 01 — frontend | PASS — npm run check, npm test (1), npm run build, Chrome E2E (1); npm audit không phát hiện lỗ hổng |
| Phase 01 — backend | PASS — Maven Wrapper 3.3.4/Maven 3.9.11, JDK 25, Spring Boot 3.5.16; 2 HTTP tests với server thật, package executable JAR |
| Phase 01 — database | PASS — 5 integration tests trên MySQL 8.4.11 riêng: CHECK/NULL/JSON, FK RESTRICT, unique Community ACTIVE, indexes/validation và transaction rollback; runner dừng/xóa instance test |
| Phase 01 — cấu hình/artifact | PASS — 2 frontend tests, 6 backend tests + 6 integration tests; JAR web chạy với DB thật, SPA refresh, API/asset/WS thiếu trả 404, Vite proxy tới backend thật; Fly bị tắt trong build/preview/prod |
| Phase 02 — dữ liệu | PASS — map 43 screen/60×45 tile, đúng spawn/Finish/tuning; shared không chứa presentation, loại liên kết sang campaign ngoài Main Babe; map/tuning tests |
| Phase 02 — artifact | PASS — Vite dist/shared và JAR classpath/shared nạp cùng JSON; kiểm tra byte trong JAR, không đóng gói fixtures; SharedData defensive copy, Maven test/build và integration MySQL thật |
| Phase 02 — TypeScript | PASS — type-check, 22 tests; 11 oracle traces/2.640 ticks so sánh state chính xác, charge/auto/re-arm/cancel, collision/slope/ice/snow/wind và codec restore/replay |
| Phase 01–07 còn lại | NOT RUN — chưa triển khai và nghiệm thu hệ thống |
| Phase 08 | Chưa thực hiện |
