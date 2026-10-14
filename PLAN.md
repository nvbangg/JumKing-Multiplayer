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
- [x] Triển khai Java simulation; kiểm tra số học từng tick và restore/replay khớp fixtures TypeScript.
- [x] Triển khai bay/thoát solid trong hai simulation; kiểm tra input, biên và restore.
- [x] Hoàn thiện input replay từ spawn tới Finish; kiểm tra toàn tuyến không dùng bay/teleport.

## Phase 03 — Solo và presentation

- [x] Chuẩn hóa assets/nội dung Main Babe, manifest/ATTRIBUTION; kiểm tra ID, nguồn và tải tài nguyên.
- [x] Hoàn thiện renderer/sprite/animation/camera/môi trường; kiểm tra toàn tuyến và chuyển screen.
- [x] Hoàn thiện khung frontend/menu và các màn hình chức năng; kiểm tra navigation/focus, responsive và trạng thái lỗi. Tích hợp chức năng online theo Phase 04–06.
- [x] Triển khai Solo save/Continue/Pause/New Game/Stats local; kiểm tra profile/UUID/revision, sync queue, offline/storage và tự Pause khi hidden/blur/AFK.
- [x] Hoàn thiện Solo History local; kiểm tra terminal summaries, Guest/Account tách biệt và không mất lịch sử khi thay save.
- [x] Hoàn thiện keyboard/touch/gamepad; kiểm tra charge, multi-touch và cancel input.
- [x] Hoàn thiện audio/Options; kiểm tra lựa chọn, audio unlock/cache và lưu settings.
- [x] Triển khai collectibles/inventory/equipment/merchant/quests trong hai simulation; kiểm tra fixtures/restore.
- [x] Hoàn thiện NPC/readables/hội thoại; kiểm tra ID/state/trigger và focus/input.
- [x] Hoàn thiện Solo ending/Skip; kiểm tra đúng Finish, không credit/phát trùng.
- [x] Thêm Options/điều khiển Fly Mode; kiểm tra quyền Solo dev, nhấn đúp, di chuyển và restore.

## Phase 04 — Account và contracts

- [x] Triển khai Guest/session/CSRF/Origin và HTTP errors; kiểm tra cookies, idle/absolute, quyền và rate limits.
- [x] Hoàn thiện Register/Login/Logout và UI Account; kiểm tra validation, session rotation và ownership.
- [x] Hoàn thiện Merge/Skip Guest Room History; kiểm tra pointer/writer đồng thời, nhiều lượt cùng match và Stats không cộng trùng.
- [x] Triển khai map/game-config API và HTTP/WS adapter; kiểm tra contract, credentials, endpoints và lỗi kết nối.
- [x] Hoàn thiện Solo metrics sync; kiểm tra owner, hai thiết bị, revision/retry, pending History và local/server totals.
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
| Phase 02 — Java | PASS — Maven test (10); Java khớp 2.640 oracle ticks, codec restore/replay và full Main Babe route: 43 screen, 228 jumps, 0 falls, Finish hợp lệ |
| Phase 02 — Fly simulation | PASS — 26 frontend tests/13 backend tests; gate mặc định, movement/cancel, phasing thẳng xuống tới body clear, restore khi tắt quyền và fallback đáy giữ counters |
| Phase 02 — route | PASS — hai simulation khớp 265 checkpoint oracle trên tuyến 16.957 ticks, restore giữa đường; đủ 43 screen, 228 jumps, 0 falls, Finish, không Fly/teleport |
| Phase 03 — assets | PASS — manifest/hashes, 120 texture/53 âm thanh/4 NPC; 29 frontend tests và Chrome decode texture/tải audio/font; nguồn Jump King gốc ghi trong ATTRIBUTION. Giấy phép phân phối lại chưa được cung cấp |
| Phase 03 — renderer | PASS — 30 frontend tests, type-check/build; Chrome kiểm tra 43 screen/camera, opacity/depth sprite và trang bị, nickname; Solo nhảy/Pause/Resume thật và xem screenshot. Chưa có save/menu chính hoàn chỉnh/audio/điều khiển cảm ứng |
| Phase 03 — frontend/menu | PASS — 30 unit tests và 5 Chrome E2E cho menu/forms, keyboard/focus, desktop/mobile, lỗi offline và Save/Continue/refresh; kiểm tra screenshots. Adapter gọi HTTP/WS thật; backend Account/Community/Room và tích hợp online chưa triển khai, kiểm chứng ở Phase 04–06 |
| Phase 03 — Solo save | PASS — IndexedDB thật: Save/Continue/refresh, New Game giữ totals, writer lock/revision/profile, ACK cũ và sai owner, storage bị chặn, blur cancel charge và AFK 60 giây; hàng chờ local tồn tại qua mở lại DB. Đồng bộ HTTP theo Phase 04; state adventure được bổ sung cùng nội dung game |
| Phase 03 — Solo History | PASS — 33 unit tests và Chrome với IndexedDB thật: Finish dùng checkpoint oracle, terminal summary giữ UUID/counters, không còn Continue; New Game giữ best/totals/History, profile Guest không nhận dữ liệu Account; ABANDONED/ACTIVE không thành Finish |
| Phase 03 — input | PASS — build/type-check, 33 unit tests, 14 Chrome E2E; multi-touch thật qua Chrome giữ hướng/Jump, pointercancel không launch; gamepad API mô phỏng kiểm tra dead zone/held/Start/disconnect/SecurityError và keyboard fallback. Tay cầm/điện thoại vật lý chưa nghiệm thu |
| Phase 03 — audio/Options | PASS — build/type-check, 33 unit tests; Chrome giải mã/cache SFX và phát ambience thật, Pause/mute/đổi vùng dừng stream cũ; Sound/Timer lưu qua home/game/reload, Fullscreen vào/thoát, Controls/Escape đúng focus. Audio bị chặn báo lỗi nhưng Solo vẫn nhảy; kiểm tra 5 E2E liên quan và 3 audio cases |
| Phase 03 — adventure | PASS — 40 frontend tests, 20 backend tests + 6 MySQL integration/JAR web; hai engine khớp 49 checkpoint/34.365 ticks oracle pickup/Raven/merchant/NPC/readable và full route physics 16.957 ticks. Chrome 9 E2E: pickup/trang bị/wardrobe, nâng IndexedDB giữ save, Continue/History và 43 screen. Manifest còn Main Babe: 129 texture/51 âm thanh; bổ sung hidden walls, loại quest/music ngoài map. Hiển thị hội thoại/ending theo các mốc tiếp theo |
| Phase 03 — hội thoại | PASS — build/type-check, 40 unit tests; 4 Chrome E2E kiểm tra bitmap font/NPC typewriter/readable, 43 screen và input. Old Man nói trong khi người chơi vẫn nhảy; đổi screen ẩn bubble cũ. Nội dung tra bằng ID, merchant thay giá, animation theo state; không thêm pause hội thoại |
| Phase 03 — ending | PASS — build/type-check, 42 unit tests, 4 đường animation khớp tọa độ native; Chrome chạy trọn timeline Shoes/Raven và Skip/ảnh kết thúc, không credit/callback trùng. Continue đúng Finish cấp Crown trước autosave, clock giữ 16.957 ticks, một History; xem trực tiếp 2 screenshots. Renderer 43 screen phân biệt avatar với Babe |
| Phase 03 — ending fixture JSON | PASS — sửa phép so sánh qua JSON codec (JSON không giữ dấu của zero); kiểm tra riêng native −0, chạy lại type-check và toàn bộ 42 unit tests |
| Phase 03 — Fly controls | PASS — type-check/build, 44 unit tests, 25 Chrome E2E mặc định và 3 E2E riêng với cờ dev; kiểm tra gate, Options, double Space/touch, nhiều ngón, Continue và thoát solid. Build/preview luôn khóa Fly; mobile dành chỗ riêng cho touch controls |
| Phase 04 — identity/security | PASS — 22 unit tests + 13 MySQL/HTTP/JAR integration tests; Guest opaque/hash và hết hạn, CSRF/CORS/Origin, cookies Secure/HttpOnly/Lax, body thường/chunked, quyền Account, idle/absolute và rate theo IP/identity. Session authority dùng chung HTTP/WS; tích hợp thu hồi socket theo mốc WS transport |
| Phase 04 — Account | PASS — type-check/build, 46 frontend unit tests, 22 backend unit tests + 19 integration tests, gồm harness chạy 2 Chrome E2E với backend/MySQL thật. Kiểm tra BCrypt/Unicode/strict JSON, trùng credentials, lỗi Login đồng nhất, đổi session/CSRF, Logout, Guest đăng ký giữ owner Room, membership guard và hai browser identities; xem screenshot Account. Guest Solo giữ riêng; Merge theo mốc tiếp theo |
| Phase 04 — Merge | PASS — type-check/build, 46 frontend/24 backend unit tests + 23 MySQL integration tests và 3 Chrome E2E. Merge/Skip qua UI thật; UUID/counters giữ nguyên, SUM không nhân đôi và COUNT DISTINCT một trận khi Account sở hữu hai lượt. Kiểm tra retry cùng đích, chặn đổi đích, writer đồng thời/đến muộn, trigger gây lỗi để xác minh rollback; registry không giữ lock khi chờ JDBC. Tích hợp writer gameplay theo Phase 05–06 |
| Phase 04 — map/contracts | PASS — type-check/build, 52 frontend/24 backend unit tests, 24 integration tests và 4 Chrome E2E. HTTP trả đúng shared map/tuning/rules, 10 appearance slots và server flag; adapter kiểm tra envelope/size, handshake chung, pending cleanup, socket cũ, heartbeat/nonce và timeout không replay mutation. Chrome xác minh map/config và handshake bị từ chối; WS endpoint/dispatch theo mốc transport |
| Phase 04 — Solo sync | PASS — type-check/build, 53 frontend/27 backend unit tests, 27 MySQL integration tests; 25 Chrome E2E Solo và 7 E2E online. Kiểm tra offline/pending History/retry, hai browser contexts cùng Account, đổi Account không upload nhầm owner, whitelist không gửi snapshot, UUID/revision/idempotency/terminal invariants, quota 60 kể cả JSON lỗi và tổng ticks lớn không overflow. Profile/History dùng dữ liệu đã commit; queries hỗ trợ cả ba kiểu chơi, writer online nghiệm thu ở Phase 05–06. Output E2E độc lập/online tách riêng để tránh xóa trace chéo |
| Phase 04–07 còn lại | NOT RUN — chưa triển khai và nghiệm thu hệ thống |
| Phase 08 | Chưa thực hiện |
