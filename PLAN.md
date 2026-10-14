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
- [x] Hoàn thiện WS transport/registry/executor, sequence/heartbeat/queues; kiểm tra quotas, socket cũ và slow consumers.

## Phase 05 — Community Tower

- [x] Triển khai Account-only Community và simulation server; kiểm tra ghép/tạo sảnh, Join/Leave và state của hai clients.
- [x] Hoàn thiện prediction/reconciliation/interpolation; kiểm tra ACK/replay, latency, thiếu frame và resync.
- [x] Hoàn thiện checkpoint/worker/autosave/Continue/New Run; kiểm tra transaction/revision, một lượt ACTIVE và rollback.
- [x] Hoàn thiện Pause/Resume/AFK/Save & Exit theo gameplay; kiểm tra hidden/blur, clock, đang rơi, save status và hạn giữ slot.
- [x] Hoàn thiện Join ID/transfer/reconnect; kiểm tra capacity, membership, biên deadline và Continue sau hết hạn.
- [x] Đồng bộ ngoại hình/nhãn tên/animation/audio peers; kiểm tra self/peers, camera/screen, slot reuse và dedupe effects.
- [x] Hoàn thiện chat Community; kiểm tra focus, plain text, history và giới hạn.
- [x] Hoàn thiện leaderboard/UI; kiểm tra best từng Account, comparator, đồng hạng và dữ liệu đã commit.
- [x] Hoàn thiện thống kê tổng; kiểm tra Guest local/Room, Account cả ba kiểu chơi, best và chống cộng trùng.
- [x] Hoàn thiện History Solo/Community và contract chung; kiểm tra ownership, pagination, pending/sync và terminal state.
- [x] Hoàn thiện Community Finish/ending riêng; kiểm tra flush/finalize, Skip/New Run và peers tiếp tục.
- [x] Tích hợp Fly Mode online; kiểm tra server gate, prediction/checkpoint và thành tích.
- [x] Hoàn thiện inventory/trang bị và adventure effects online; kiểm tra quyền sở hữu, lưu/restore và dedupe khi reconciliation.

## Phase 06 — Room

- [x] Hoàn thiện Create/Join Room và Lobby/settings/Host; kiểm tra permissions và capacity.
- [x] Triển khai transaction roster/metrics và comparator Room; kiểm tra revision, rollback, Merge và đồng hạng bằng fixtures.
- [x] Tích hợp chat Room; kiểm tra history/focus/input và giới hạn.
- [x] Hoàn thiện Race Start/countdown/Finish/Results; kiểm tra 1/2/max người và Finish cùng tick.
- [x] Hoàn thiện End/Leave/Host transfer/reconnect; kiểm tra deadlines, khóa Join, re-authentication và Host khi mọi người offline.
- [x] Hoàn thiện Host Pause/Resume Room; kiểm tra clock/state/input, giữ RAM, hết hạn, chuyển Host/reconnect và restart.
- [x] Hoàn thiện Play Again từng người; kiểm tra room code/settings, xác nhận/AFK/Join mới, roster và match/History mới.
- [x] Triển khai Hardcore respawn; kiểm tra body reset, giữ clock/peak/counters.
- [x] Triển khai Time Limit; kiểm tra biên phút, deadline và Finish đúng tick.
- [x] Triển khai No Walk; kiểm tra hướng/vận tốc trên nền, nhảy/bounce và luật kết thúc.
- [x] Hoàn thiện comparator bốn mode; kiểm tra progress/peak/time, DNF, winner và đồng hạng.
- [x] Hoàn thiện Room writer và tích hợp History chung; kiểm tra ghi định kỳ, finalize/retry, Merge, permissions/pagination và restart.
- [x] Tích hợp Room ending cho winners; kiểm tra người khác tới Results và không ending khi chưa Finish.
- [x] Nghiệm thu Fly Mode trong bốn mode Room; kiểm tra respawn, Finish, History/Stats và xếp hạng.

## Phase 07 — Nghiệm thu

- [x] Sửa phản hồi identity cũ và phân biệt menu cá nhân/Host Pause; kiểm chứng hồi quy đăng nhập và route Main Babe bằng input hợp lệ.
- [ ] Nghiệm thu tự động/chơi trực tiếp toàn tuyến Solo/Community/Room và lỗi mạng/DB; sửa lỗi, không dùng cheat thay route.
- [ ] Nghiệm thu điện thoại, hai identities cùng máy và LAN/ZeroTier hai thiết bị.
- [x] Hoàn thiện workload và thực nghiệm snapshot-only/prediction; ghi số đo, cải tiến và giới hạn.
- [x] Nghiệm thu clean clone/setup/build/run, config và MySQL restart/backup/restore.
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
| Phase 04 — WS transport | PASS — type-check/build, 53 frontend/33 backend unit tests, 27 MySQL integration tests và 8 Chrome E2E online. Hai browser contexts có cookies độc lập kết nối WS thật; giới hạn hai socket/identity, lỗi payload/request ID, Logout chỉ thu hồi đúng Account. Unit tests kiểm tra FIFO/ACK/timeout, Fly gate, bounded serial executor, snapshot coalescing và slow consumer/overflow. Gameplay online và room registry theo Phase 05–06 |
| Phase 05 — Community server | PASS — type-check/build, 53 frontend/33 backend unit tests, 27 MySQL integration tests và 9 Chrome E2E online. Hai Account độc lập Join cùng sảnh qua ID, nhảy và nhận state của nhau, màu khác nhau, Save & Exit giải phóng membership, Continue giữ run ID/counters; xem screenshot thật. Server fixed-step, input FIFO, checkpoint JDBC ngoài actor; prediction và các biên save/pause/reconnect theo những mốc tiếp theo |
| Phase 05 — prediction | PASS — type-check/build, 58 frontend/34 backend unit tests, 27 MySQL integration tests và 10 Chrome E2E online. Client dự đoán trước ACK, restore/replay phần chưa xác nhận, nội suy peers theo serverTick; bỏ snapshot/context cũ và dừng khi buffer đầy. CANCEL loại input đang chờ, không release-jump. Chrome qua WS/MySQL thật chịu snapshot trễ 100–180 ms/đến sai thứ tự; Pause/Resume và hai lần nhảy vẫn đúng, không nhận pause từ generation cũ. Đo hiệu năng định lượng theo Phase 07 |
| Phase 05 — checkpoint | PASS — type-check/build, 58 frontend/38 backend unit tests, 33 MySQL/HTTP/WS integration tests và 11 Chrome E2E online. Worker gộp autosave chưa ghi, giữ flush barriers, retry cùng payload tối đa 3 lần; không báo SAVED trước commit. Kiểm tra concurrent Continue chỉ một ACTIVE, owner/revision/counters, rollback New Run bằng trigger lỗi và writer cũ không phục hồi ABANDONED. UI New Game giữ History/totals, đổi đúng run ID. Có clock test cho deadlines, không sửa shared rules để rút ngắn thời gian thật |
| Phase 05 — Pause | PASS — type-check/build, 58 frontend/38 backend unit tests, 34 MySQL/HTTP/WS integration tests và 12 Chrome E2E online. Blur hủy charge, không launch; pause trên không tiếp tục physics/clock tới grounded, Resume đổi generation và không tính thời gian nghỉ. Frame trung tính/heartbeat không reset AFK; kiểm tra đúng biên 1 phút, 15 phút và 5 phút bằng monotonic clock. Trigger gây lỗi MySQL xác minh Save & Exit giữ membership, Resume không vượt lỗi lưu, bỏ trigger rồi tiếp tục được. SAVED/PENDING/FAILED phản ánh worker |
| Phase 05 — membership/reconnect | PASS — type-check/build, unit tests, MySQL/WS integration và 13 Chrome E2E online; IndexedDB riêng được test thêm trên Chrome. Reload khôi phục đúng identity/token/run, vào lại paused; Leave xác nhận xóa token. 11 WS clients thật kiểm tra đủ 10 slot/màu, tự tạo sảnh kế tiếp, Join đầy không đổi membership, transfer giữ một ACTIVE, sai owner/token và socket cũ không chiếm quyền. Test clock xác minh Continue sau hết hạn; cleanup client bỏ CANCEL của context cũ khi chuyển sảnh |
| Phase 05 — presentation online | PASS — type-check/build, 59 frontend unit tests, backend/MySQL regression và 13 Chrome E2E online. Peers chỉ nhận pose/trang bị công khai; hai người có nhãn tên/màu riêng qua renderer chung, cùng bộ camera/animation đã test 43 screens. Server gửi effects theo screen, client dedupe eventSeq và bỏ self event của lượt cũ; Chrome xác minh một JUMP tự thân/một JUMP peer với mức âm lượng riêng, replay không phát lặp. Slot/membership đã nghiệm thu ở mốc trước |
| Phase 05 — chat | PASS — type-check/build, 59 frontend/39 backend unit tests, 36 MySQL integration tests và 13 Chrome E2E online. Hai Account gửi/nhận text và nhận lại 50 tin gần nhất khi Join; HTML không thực thi. Kiểm tra Unicode, cooldown, focus/cancel và chat không gia hạn AFK/pause; lịch sử chỉ ở RAM của sảnh |
| Phase 05 — leaderboard | PASS — type-check/build, 59 frontend unit tests, MySQL regression và 13 Chrome E2E online. SQL chọn một Community run hoàn chỉnh/Account, tính RANK trước LIMIT, đồng hạng 1,1,3; không ghép peak/time khác lượt hoặc lấy Solo. Chrome đối chiếu tên/rank/peak với API sau Save & Exit; leaderboard chỉ nằm trong Community menu |
| Phase 05 — thống kê | PASS — frontend unit/type-check/build và MySQL regression. Tổng ticks đổi sang ms sau khi cộng; SQL gộp ba kiểu chơi, best chỉ từ Finish, Room matches/wins đếm DISTINCT sau Merge. Test dữ liệu Room bằng fixtures; writer gameplay Room theo Phase 06. Chrome kiểm tra tổng Community + Solo đã sync sau reload không cộng lại local |
| Phase 05 — Finish/ending | PASS — 60 frontend unit tests, type-check/build, 37 MySQL integration tests và 16 Chrome E2E online. Checkpoint trước Finish được tạo bằng toàn tuyến input không cheat; server Finish ở tick 16.957, commit một kết quả/xóa snapshot rồi mới gửi ending riêng. Chrome kiểm tra Skip/ảnh kết, New Game xóa camera/animation cũ và peer vẫn nhảy; đã xem screenshot. Notice giữ trang phục trước Crown và dedupe theo lượt |
| Phase 05 — History | PASS — type-check/build, frontend units, MySQL regression và Chrome online. Phân trang 20+1 lượt, detail qua API đúng owner; local Solo chưa sync thay theo ID khi server commit, ACTIVE không hiện History. Chrome kiểm tra offline/pending/retry, New Run giữ terminal summary và IndexedDB bị chặn vẫn đọc được History server. Room detail contract/queries có fixtures; gameplay writer theo Phase 06 |
| Phase 05 — Fly online | PASS — type-check/build, 61 frontend unit tests, MySQL/WS regression và Chrome với backend profile local. Options, nhấn đúp, WASD/arrows, tắt bay và Save/Continue giữ state; intent không mất vì ACK cũ, lượt mới reset đúng. Server mặc định từ chối freeFlight=true và tọa độ giả. Backend local lưu/restore JSON và leaderboard tính peak bình thường; browser riêng chạy 1 E2E, suite mặc định 16 E2E vẫn qua |
| Phase 05 — adventure online | PASS — 62 frontend unit tests, type-check/build, MySQL regression và Chrome. Server nhặt Cap/tự trang bị đúng luật, bật/tắt qua Inventory và Continue giữ state; pickup chỉ phát một lần. Từ chối trang bị chưa sở hữu. Cues adventure/world chỉ gửi owner bằng ID, client lọc lượt và dedupe; replay snapshot không phát lại. E2E cleanup đúng membership sau lỗi để slot reconnect không ảnh hưởng ca kế tiếp |
| Phase 06 — Lobby | PASS — backend units và RoomLobbyIT với 11 WS/Guest thật: giới hạn 10 người/màu, giải phóng slot, Host-only settings và chuyển Host. Chrome hai Guest tạo/join qua ID, đồng bộ Time Limit/No Walk, không có History khi chưa Start; Start một người bị khóa trên UI. Điều kiện Start phía server/countdown theo mốc Race |
| Phase 06 — dữ liệu/xếp hạng | PASS — RoomRanking tests cho bốn mode: Finish/time, current/peak, 1,1,3, DNF và không tạo winner khi chưa Finish. RoomRunsIT trên MySQL: match/roster atomic, rollback bằng trigger lỗi, revision/retry không nhân rows; writer cũ resolve Guest đã Merge. Khóa owners tăng dần cho cả roster. Tích hợp gameplay/History/restart theo các mốc tiếp theo |
| Phase 06 — chat | PASS — type-check/build, 63 frontend unit tests, 42 integration tests và 18 Chrome E2E online. Hai Guest chat trong Lobby bằng nickname, HTML chỉ là text; history/cooldown/Unicode dùng chung ChatLog. Packet gắn room ID, client bỏ state/chat/save/result của room/play cũ; actor kiểm tra lại membership trước xử lý command sau transfer |
| Phase 06 — Race | PASS — type-check/build, 63 frontend/44 backend unit tests, 45 integration tests và 19 Chrome E2E online; chạy thêm RoomRaceIT/RoomLobbyIT sau review config. Một người/không phải Host không Start; countdown khóa Join/settings, hủy không ghi History. Hai browser Start/nhảy/Host End/Results/History; 10 WS players cùng chạy/lưu đủ roster. Checkpoint native xác minh hai Finish cùng tick 16.957, đồng hạng nhất/finalize một lần. Room dùng checkpoint interval chung; cấu hình 25 ms được kiểm tra autosave khi chưa End. Lifecycle, modes và writer/restart theo các mốc còn lại |
| Phase 06 — lifecycle | PASS — 63 frontend/44 backend unit tests, type-check/build; 6 integration tests cho lifecycle/Race và hai Chrome identities Start/End/Leave/History. Reconnect giữ trận, tăng generation, không giành lại Host; deadline đúng 5 phút, tất cả offline đóng băng clock và finalize một lần. Thu hồi session rồi Login đúng Account khôi phục roster; menu làm mới identity khi session hết hạn. Flush metrics lúc mọi người offline; Host Pause và Play Again theo mốc riêng |
| Phase 06 — Host Pause | PASS — type-check/build, 64 frontend/44 backend unit tests, 52 integration tests và 19 Chrome E2E online. Pause đóng băng clock/physics, hủy charge và input chưa ACK; Resume không replay hoặc cộng giờ nghỉ. Kiểm tra quyền, stale play, deadline 24 giờ không gia hạn, mọi người offline, chuyển Host, Login/reconnect khi session bị thu hồi và grace 5 phút sau Resume. Restart backend thật chốt ABORTED từ metrics đã commit; timeout chốt UNFINISHED không winner và giải phóng Room. Fixtures identity cleanup đúng rows của test |
| Phase 06 — Play Again | PASS — type-check/build, 64 frontend/44 backend unit tests; RoomAgain/Race/Lobby/Pause integration và 19 Chrome E2E online. Hai ván giữ code/settings nhưng tạo UUID/History mới; Host và ít nhất một người phải xác nhận, người mới được xác nhận và người AFK không vào roster. Kiểm tra stale match, khóa Join trước xác nhận đầu, revalidate Join trong actor; lỗi finalize có Retry Save giữ nguyên payload/UUID và không cho Start trước commit |
| Phase 06 — Hardcore | PASS — type-check/build, 65 frontend/46 backend unit tests, 7 integration tests và 20 Chrome E2E online. Native splat respawn đúng player, giữ active clock/peak/jumps/falls và lưu MySQL; prediction/replay khớp server. Respawn trước adventure/Finish nên splat tại đích không nhận Crown hoặc thắng. Chrome hai Guest chọn Hardcore, Start/nhảy/End và đọc đúng mode trong History |
| Phase 06 — Time Limit | PASS — type-check/build, 65 frontend/46 backend unit tests; 8 integration tests cho deadline/Race/Pause và 21 Chrome E2E online. Hết 1 phút chốt đúng tick 3.600, không tiếp tục tick hoặc tạo winner khi chưa Finish; hai Finish tại deadline 5 phút cùng thắng trước timeout. Snapshot công bố thời gian còn lại, HUD vẫn hiện khi tắt Display Timer; History giữ mode và settings |
| Phase 06 — No Walk | PASS — type-check/build, 65 frontend/48 backend unit tests, MySQL regression các mode và 22 Chrome E2E online. Input ngang trên nền giữ x/vx, charge vẫn nhảy ngang; bật tường hai hướng khớp fixtures độc lập, không đổi hướng giữa không trung. WS kiểm tra jump/bounce/History thực tế; Chrome đọc snapshot khi giữ Right xác minh đứng yên, nhảy và lưu Results. Đã xem screenshot màn chơi |
| Phase 06 — comparator tích hợp | PASS — 48 backend unit tests và RoomRankingIT qua WS/MySQL. Cả bốn mode Finish ở tick native 16.957, lưu hai hạng nhất/winners cùng tick. Kiểm tra current bằng nhau: Time Limit dùng peak, ba mode còn lại đồng hạng; Leave chốt DNF/metrics trong khi người khác tiếp tục. Host End không tạo winner; thứ tự UUID không phá đồng hạng |
| Phase 06 — writer/History | PASS — 49 backend unit tests, 63 MySQL integration tests, 22 Chrome E2E online và 1 Fly E2E riêng; JAR web/Vite proxy. Queue quá tải không mất callback/cleanup; Join bị từ chối giải phóng membership/color. Tất cả Leave khi final write lỗi vẫn retry cùng kết quả, giải phóng phòng sau commit. Shutdown flush trước đóng DB; restart chốt ABORTED từ metrics đã commit, không nhân rows/ticks. History/Merge, ownership và pagination đạt regression |
| Phase 06 — ending Room | PASS — 66 frontend/49 backend unit tests, kiểm thử MySQL ending/ranking bốn mode và Chrome hai identities. Chỉ winners nhận ending sau commit; lỗi lưu không phát ending, retry/reconnect giữ cùng kết quả. Peers vào Results/xác nhận ván tiếp, winner Skip/View Results/Play Again không bị broadcast cắt cảnh hoặc phát ending cũ. Đã xem screenshot; không ending khi Host End/timeout chưa Finish |
| Phase 06 — Fly Room | PASS — 49 backend unit tests, 5 MySQL integration tests và 4 Chrome E2E dùng hai identities. Cả bốn mode bật Fly từ Options, nhấn đúp Space bật/tắt, di chuyển lên và lưu History bình thường. WS kiểm tra xuyên solid rồi rơi thẳng/thoát solid, giữ counters; Finish sau Fly vẫn lưu winner, Stats/History và comparator bình thường |
| Phase 07 — thực nghiệm | PASS — workload HTTP/WS/MySQL thật trên Windows 11/JDK 25, 16 logical CPUs: Community 1 người, Room 2/10 người và 2 Room × 3 người; mỗi mẫu 5 giây. Snapshot ~20 Hz, simulation ~60 Hz, RTT p95 1,75–6,28 ms; 10 người dùng ~155% một CPU core gồm driver, không phải capacity production. Compute tick 10 người p95 0,250 ms/p99 0,391 ms, 0/1.800 vượt 16,67 ms; không gồm I/O/scheduler. Chrome mô hình delay một chiều 0/50/100 ms: prediction thấy nhảy sau 350 ms charge, snapshot-only 400/483/567 ms; sai lệch vị trí p95 0 so với 9/29/43 px. Mô hình không loss/drift; raw JSON được ignore, tests tái chạy theo testing |
| Phase 07 — identity và route | PASS — type-check/build, 66 frontend unit tests; Chrome hồi quy giữ phản hồi me ẩn danh đến sau Register, Account không bị ghi đè. Nhóm online riêng 22/22 và regression identity riêng 1/1. Route Chrome/Phaser khớp từng checkpoint: 43 screen, 16.957 ticks, 228 jumps, 0 falls, Finish không Fly/teleport. Room phân biệt Game Menu vẫn chạy clock với Host Pause đóng băng clock |
| Phase 07 — setup và phục hồi | PASS — Windows, clone sạch từ commit local 6f6fecc: npm ci, 66 frontend tests/build và Maven web package/49 unit tests; không mang theo config riêng. Nhóm Schema/RoomPause có 9 integration tests và Chrome trực tiếp từ JAR: Account, Community save/Continue, Room hai người Start/Pause/End/Leave/History. Restart MySQL test 8.4.11 và restore sang database mới khớp số dòng/checksum ba bảng. Sửa cleanup socket đã bị server đóng, giới hạn bốn Spring test contexts. Đã tạo lại jumpking rỗng trên MySQL chính thức 9.6 theo xác nhận của người dùng, cấp quyền DML cho app và khởi chạy JAR với config riêng được ignore |
| Phase 07 — regression còn lại | Chưa đạt toàn bộ: các lượt chạy dài còn lỗi TCP loopback rải rác. Probe Node gọi health trực tiếp gặp 2/200 timeout khi tạo kết nối mới; Java HTTP echo tối giản cũng tái hiện 3/500. IPv4-only chưa loại bỏ được lỗi; chưa xác định nguyên nhân hệ thống, không thay bằng retry hoặc bỏ assertions |
| Phase 08 | Chưa thực hiện |
