# testing.md

## Nguyên tắc và workflow

Kiểm tra theo tài liệu sở hữu; báo PASS/FAIL/BLOCKED/NOT RUN đúng thực tế. Tiến độ/commit theo [PLAN](../PLAN.md#cách-sử-dụng).

| Phần thay đổi | Checks bắt buộc |
| --- | --- |
| Frontend | npm run check, npm test, npm run build; E2E khi đổi luồng UI/gameplay |
| Backend | Maven test; verify -Pintegration khi đổi persistence/security/HTTP/WS |
| Shared map/physics/rules/adventure | Fixtures và route replay TypeScript/Java; build cả hai phía |
| Luồng online/save/lifecycle | Playwright với backend/MySQL thật và hai identities |
| Tài liệu | Source of Truth, links/anchors, examples, scope và git diff --check |

## Lệnh kiểm thử

Mỗi block chạy từ root project trong terminal riêng.

Frontend:

```powershell
cd frontend
npm run check
npm test
npm run build
npm run test:e2e
```

Backend:

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd verify -Pintegration
```

Các lệnh là contract của Maven/npm configuration và được nghiệm thu trong [PLAN](../PLAN.md). Linux/macOS dùng `./mvnw`; cài Chromium cho E2E bằng `npx playwright install chromium`.

E2E (end-to-end) dùng Playwright thao tác trình duyệt qua UI → HTTP/WS → backend/MySQL để kiểm tra cả luồng. Chạy riêng vì cần ứng dụng đang chạy và trình duyệt, khác unit tests kiểm tra từng hàm/module.

Integration dùng [Testcontainers MySQL](https://java.testcontainers.org/modules/databases/mysql/), Docker và MySQL theo [deployment](deployment.md#runtime-và-workflow). E2E dùng instance cô lập; không reset database cá nhân. Runner chỉ cleanup resources/fixtures do nó tạo, kể cả khi test lỗi.

## Scenarios và oracles

| Nhóm | Kiểm tra và kết quả mong đợi |
| --- | --- |
| Physics | Đi bộ, charge ngắn/full/auto, buffer/air grace, wall/ceiling/corner/slope, ice/snow/wind, splat/cancel; full state đúng expected mỗi tick |
| Map/camera | Toàn tuyến Main Babe từ spawn tới Finish bằng input hợp lệ; tile/biên/chuyển screen đúng, không stuck, không lọt solid |
| Solo | Offline save/Continue/New Game, profile Guest/Account, UUID/revision; sync hai thiết bị đúng owner, retry/reload hàng chờ; không upload snapshot hoặc ảnh hưởng leaderboard |
| Fly Mode | Gate mặc định/dev/build/prod cho mọi kiểu chơi; nhấn đúp/repeat/cancel không launch thừa; keyboard/touch, biên world, thoát solid/đáy world/restore; save và thành tích đúng [gameplay](gameplay.md#fly-mode) |
| Nội dung/ending | Main Babe đủ NPC/readables/items/merchant/quests/môi trường/audio; ending/Skip đúng người, không credit hoặc phát trùng; build đúng scope |
| Adventure boundary | Hai simulation cùng luật/ID, frontend tra cứu đúng nội dung; Java/snapshot không chứa presentation; pickup/mua/thưởng không nhân đôi qua restore/replay/input giả |
| Community | Account bắt buộc; tự ghép/tạo sảnh, tra/Join ID/full, transfer; lookup không lộ members/token hoặc vượt kiểm tra Join; một lượt/Account, giữ state/time và không nhân đôi membership |
| Checkpoint | Restore đủ physics/adventure/world; metrics/snapshot/revision atomic; New Run đồng thời và rollback; transfer/Finish/Abandon không mất save hoặc bị writer cũ phục hồi; một Community ACTIVE không khóa Solo |
| Pause/AFK | Hidden/blur và biên AFK theo gameplay; neutral frames/giữ phím/heartbeat/chat không reset AFK, input thật reset; Community đang rơi phải settle, hết hạn lưu/release slot; Room mở menu lâu không tự out hoặc dừng trận |
| Room | 1 người không Start được; 2/max clients, countdown/permissions; Join mới chỉ WAITING, reconnect đúng roster; Leave/Host transfer/End; Play Again từng người giữ code/settings/History, không kéo người chưa xác nhận vào match mới |
| Host Pause | State/clock/Time Limit/adventure đóng băng, chat/heartbeat/deadlines vẫn chạy; Resume không bù ticks/input cũ; Pause lặp không gia hạn, hết hạn finalize PAUSE_TIMEOUT đúng một lần; Host đóng tab/chuyển quyền, mọi người offline/quay lại, Leave hết và restart theo thiết kế |
| Modes/rank | Race first Finish; Hardcore splat respawn giữ clock/peak/falls; Time Limit biên phút/deadline; No Walk không tạo vận tốc đi bộ; comparator, DNF và đồng hạng theo gameplay |
| Leaderboard | Chỉ Community; best của một lượt/Account, không ghép peak/time khác lượt, không nhận Room/Solo hoặc client score |
| Identity/data | Schema/FK/CHECK/unique theo [data](data.md); Register/Login/Logout/Merge/Skip, session rotation/ownership/ID enumeration; rollback/revision/save status; Solo không sửa lượt online |
| Stats/History | Tổng cả ba kiểu chơi; Guest local/Room và Account đã sync không cộng trùng; History theo kind/run/match, chỉ terminal/finalized; pending Solo thay cùng ID, Merge không nhân mục; ownership/pagination/best/wins đúng data |
| Identity/chat | Ngoại hình không trùng; Community username và Room nickname đúng identity; adventure/effects không lặp qua replay; chat plain text, cooldown/history/focus |
| Mobile/browser | Multi-touch/cancel/capture, xoay màn hình, keyboard/gamepad fallback, viewport/menu, storage/audio bị chặn; refresh UI đúng route, API/asset thiếu giữ đúng lỗi |
| Assets | Manifest đủ file sử dụng, nguồn/license hợp lệ; artifact/Git không chứa tài nguyên chưa được phép hoặc dữ liệu/công cụ riêng tư |

Fixtures cần input trace/map/tuning và expected states độc lập; hai simulation giống nhau chưa đủ chứng minh đúng. Kiểm tra float32/flags/buffers/restore/pha gió, không nới tolerance che lỗi.

Chơi toàn tuyến bằng input hợp lệ để nghiệm thu platform/charge/camera/audio/Finish; bay/teleport không thay route replay hoặc chơi trực tiếp. Dùng hai browser profiles hoặc normal/incognito cho identities độc lập.

## Lỗi mạng, database và bảo mật

- Ngắt mạng lúc charge/nhảy/countdown/Pause/Finish/save; reconnect trước/sau deadline, socket cũ/seq cũ, duplicate commands và server restart.
- Community đang rơi vẫn chịu physics trước Pause; Resume không mất velocity/buffers hoặc tăng peak giả. Room disconnect không tạo release-jump hoặc reset đồng hồ.
- Trì hoãn/lặp/đảo callbacks của checkpoint/Room writer và Solo sync: revision cũ, Finish/New Run/transfer/Leave/Merge không phục hồi lượt đã kết thúc, ghi sai owner hoặc tạo thêm rows. Cùng revision khác nội dung phải CONFLICT; UUID của identity/kind khác bị từ chối.
- Tắt MySQL trong instance test: Room tick không block, Community báo lỗi/tạm dừng, không phát SAVED trước commit; retry có giới hạn và transaction atomic.
- Gửi sai payload/protocol version/mode/input, position/clock online giả, input bay khi cờ tắt, cross-origin, CSRF thiếu và request vượt giới hạn; không làm dừng phòng khác hoặc lộ secrets. Solo metrics tự khai báo không được trở thành Community hoặc đóng góp leaderboard.
- Kiểm tra cùng origin và khác origin nhưng cùng site: credentials, preflight, CSRF, WS Origin và cookies; idle/absolute expiry đóng WS, đăng nhập lại đúng Account reconnect Room PAUSED, sai owner/token bị từ chối. Internet phải nghiệm thu riêng khi deploy.
- Thử handshake/Create/Join/New Run flooding và connection/phòng hết quota, kể cả phòng PAUSED; từ chối trước cấp phát, không làm treo phòng khác.
- Chặn storage/quota/autoplay/gamepad; báo đúng giới hạn, không đánh mất input hoặc báo lưu thành công giả.

## Thực nghiệm và bàn giao

Theo [yêu cầu học phần](course-materials/final-project/final-project/Instruction.md), mỗi thực nghiệm có giả thuyết, baseline, workload, môi trường, số mẫu và phân tích giới hạn.

So sánh snapshot-only với prediction/reconciliation bằng cùng map/tuning và workload. Đo input-to-display latency, correction magnitude/frequency, snapshot jitter, tick time/overrun, messages/bytes, CPU/RAM và độ trễ/lỗi persistence. Dùng 1/2/max clients, thêm nhiều phòng để tìm giới hạn; báo p50/p95/p99 khi phù hợp. Không có clock synchronization thì đo RTT, không suy one-way latency.

Nghiệm thu Local, LAN/ZeroTier bằng hai thiết bị và điện thoại thật; kiểm tra clean clone, setup/config, build/run, restart, backup/restore và cập nhật tương thích. Thiếu thiết bị/Docker hoặc check thất bại phải ghi BLOCKED/NOT RUN, không đánh dấu hoàn tất.

Đối chiếu README với build configuration; ghi checks/commit vào PLAN. Raw traces/screenshots/logs ở `.local/reports/` hoặc reports được ignore. Git giữ tests/fixtures/scripts tái tạo và báo cáo bàn giao, không dữ liệu riêng tư.

Báo cáo giải thích networking/concurrency/protocol, đóng góp có số đo, results/discussion/limitations và trích dẫn nguồn; đáp ứng Compilatio và demo theo yêu cầu học phần.
