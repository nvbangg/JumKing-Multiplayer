# protocol.md

## Phạm vi

HTTP JSON `/api`, raw WebSocket `/ws/game`, contract version 1. Solo chỉ dùng HTTP sync; Community/Room nhận map/tuning từ server. Luật/lifecycle theo [gameplay](gameplay.md), ownership/persistence theo [data](data.md).

## Identity và security

Account dùng Spring Security server-side session, không JWT; Guest dùng cookie opaque `JK_GUEST`, hạn 365 ngày. Cookies HttpOnly, SameSite=Lax, Path=/, host-only; Secure theo [deployment](deployment.md#cấu-hình).

HTTP dùng `credentials:include`. Lấy CSRF trước mutation; Login/Register đổi session ID và CSRF token, Logout hủy session. CORS chỉ nhận origins cụ thể, credentials và headers/methods cần thiết; xử lý preflight trước authentication. WS handshake kiểm tra identity/Origin, từ chối Origin thiếu/null hoặc ngoài allowlist. Không wildcard origins hoặc đưa secrets vào URL/logs/broadcast.

Tạo Guest khi cần chơi Room; GET me không tạo identity. Đổi identity hoặc Register/Merge khi còn membership trả CONFLICT, cần Leave trước. Đăng nhập lại đúng Account được phép để reconnect membership đang giữ; không đổi roster/owner hoặc bỏ kiểm tra token. Logout/session hết hạn thu hồi session/WS và xử lý disconnect theo gameplay.

Session mặc định idle 30 phút, absolute 24 giờ; backend kiểm tra cả HTTP/WS. Hoạt động hợp lệ làm mới idle, heartbeat đơn thuần không làm mới; absolute không gia hạn. Hết session không xóa Room PAUSED trước hạn của Room; đăng nhập lại rồi reconnect theo identity/token cũ.

Guest/Register/Login/Merge: 20 requests/phút theo IP và identity, đổi session không reset bucket IP. Password BCrypt; Guest token ngẫu nhiên 256 bit, lưu SHA-256 hash. Lỗi credentials không tiết lộ Account tồn tại. Reconnect token ngẫu nhiên 256 bit, hết hiệu lực khi membership kết thúc; kiểm tra owner/token bằng constant-time và thu hồi socket cũ.

Client giữ context reconnect (roomId/playerId/token) trong IndexedDB theo backend origin và identity để quay lại sau đóng tab; xóa khi Leave đã xác nhận hoặc server báo hết hạn. Không chia sẻ token qua room code.

Tham chiếu: [Spring CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [OWASP session](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html), [OWASP WebSocket](https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html).

## HTTP

Các path có prefix `/api`. Auth/private responses dùng Cache-Control:no-store. Không body request thì để trống.

| Method/path | Request → response |
| --- | --- |
| GET /auth/csrf | → {token,headerName} |
| GET /auth/me | → {authenticated,guest,account:null hoặc {id,username,email}}; id là chuỗi ID của chính Account |
| POST /auth/guest | → cùng shape me |
| POST /auth/register | {email,username,password} → me |
| POST /auth/login | {email,password} → me + guestHistoryAvailable |
| POST /auth/logout | → {ok:true} |
| POST /auth/guest-history/merge | → {merged:true}; bỏ qua bằng cách không gọi |
| GET /game-config | → {physics,rules,appearances,features:{flyEnabled:boolean}} |
| GET /map | → GameMap |
| GET /rooms/{roomId} | Public → {roomId,kind,joinable}; chỉ tra loại/khả năng Join, không trả members hoặc tokens |
| GET /rooms | Public → {rooms:[{roomId,mode,mapLevel,playerCount,capacity}]}; chỉ Public/WAITING còn chỗ, không tokens |
| GET /community/current | Account → {run:null hoặc RunSummary của lượt ACTIVE,updatedAt,saveStatus}; không trả snapshot nội bộ |
| GET /leaderboard?limit=100 | Public → {entries:[LeaderboardEntry]} |
| PUT /solo/runs/{id} | Account → nhận SoloMetrics, trả {revision,syncStatus:SAVED}; bỏ bản cũ theo data |
| GET /history?page=0&size=20 | Identity → {items,page,size,hasNext}; History đã kết thúc theo data |
| GET /history/{kind}/{id} | Owner → History detail; kind=SOLO/COMMUNITY/ROOM, id là run ID hoặc match ID |
| GET /profile/me | Identity → {username,total}; Guest có username=null, total chỉ gồm Room trên server |

HTTP JSON body tối đa 16 KiB. Register: username 3–32 letters/numbers/_/-, email hợp lệ ≤254 ký tự, password ≥10 Unicode code points và ≤72 UTF-8 bytes. Nickname Room trim, 1–16 letters/marks/numbers/space/_/-, cho phép trùng; playerId phân biệt người chơi.

GameMap={width,screenHeight,tileSize,progressOriginY,spawn,finish,screens}; spawn={x,y}, finish={screen,minCenterX,requiresGrounded}, screen={tiles:string[],wind:boolean}.

game-config trả tuning/rules từ shared; appearances=[{colorSlot,color}] ánh xạ slot sang màu hex. features do server quyết định theo cấu hình/profile, không nhận từ client.

RunSummary={id,status,currentProgress,maxProgress,elapsedTimeMs,peakReachedTimeMs,finishTimeMs}; LeaderboardEntry thêm {rank,username}.

History summary={id,kind,startedAt,endedAt,status,playTimeMs,jumps,falls,currentProgress,maxProgress,finishTimeMs,room}; room=null cho Solo/Community, Room có {mode,mapLevel,roomCode,durationMs,rank,isWinner}. Detail Room thêm {timeLimitMinutes,endReason,participants}; detail Solo/Community dùng summary. Participant={runId,nickname,status,rank,isWinner,currentProgress,maxProgress,finishTimeMs,playTimeMs,jumps,falls}. Ownership, thứ tự và summary sau Merge theo data.

Statistics={totalPlayTimeMs,totalJumps,totalFalls,finishes,matchesPlayed,wins,bestFinishTimeMs}; matchesPlayed/wins chỉ đếm Room, bestFinishTimeMs=null nếu chưa Finish. Không nhận bộ lọc kind/mode. Guest Solo local được client tổng hợp theo data, không upload qua API.

SoloMetrics={revision,status,playTicks,jumps,falls,currentProgress,maxProgress,peakReachedTicks,finishTicks,startedAt,endedAt}.

- id là UUID; revision nguyên dương, ticks/counters nguyên không âm trong biên số an toàn và kiểu cột SQL, progress hữu hạn. status: ACTIVE/FINISHED/ABANDONED; invariant theo [data](data.md#ghi-lượt-và-đồng-bộ-solo).
- Chỉ nhận các fields trên; server gán owner từ session và kind=SOLO. Timestamps lượt do client khai báo, updated_at do server đặt. UUID thuộc owner/kind khác trả 404; revision xung đột trả CONFLICT.
- ACK trả revision request đã xử lý hoặc bỏ vì cũ; không xác nhận revision local mới hơn. Timestamps dùng độ chính xác mili giây của schema.
- Client gửi X-Account-ID để kiểm tra phiên chưa đổi Account giữa các tab; header khác Account trong session trả CONFLICT, không dùng header để cấp quyền hay chọn owner.
- Sync khi có mạng mỗi 5 giây, khi Pause/New Game/Save & Exit; tối đa 60 requests/phút/Account. Gộp hàng chờ theo lượt; retry giữ UUID/revision/nội dung.

Page 0–100000, size/limit 1–100; timestamps ISO-8601 UTC. Chỉ trả Account ID/email của chính phiên, không lộ credentials/token. Detail sai owner trả 404 như không tồn tại.

Tra room ID tối đa 60 requests/phút/IP; ID phải đúng định dạng trong gameplay. API liệt kê chỉ trả Public/WAITING còn chỗ; kết quả tra không cấp quyền Join/reconnect, server kiểm tra lại khi xử lý command.

Lỗi chung `{error:{code,message}}`: 400 validation, 401 credentials/identity, 403 quyền/CSRF, 404 not found, 409 conflict, 429 rate limit, 5xx lỗi server đã loại secrets. UI dựa code, không parse message.

## WebSocket envelope và giới hạn

```json
{"v":1,"type":"PLAYER_INPUT","payload":{"playId":"00000000-0000-4000-8000-000000000001","generation":1,"seq":1,"left":false,"right":true,"jump":true}}
```

Envelope gồm v/type/payload; control commands có requestId 1–64 ký tự, input/cancel không dùng. Response riêng giữ requestId, broadcast không mang requestId người khác. Reject version/type/field lạ, số không hữu hạn hoặc sai JSON type; không coerce string sang boolean/number.

playId là run ID Community hoặc match ID Room. generation tăng khi Join/reconnect/đổi lượt, Resume Community hoặc Resume Room; sequence tăng trong playId/generation. Snapshot/ACK chỉ có hiệu lực cho context hiện hành.

| Giới hạn | Giá trị |
| --- | --- |
| WS handshake | 30 lần/phút/IP |
| WS connections | 2/identity, 32/IP, 256/server |
| Sảnh/phòng còn tồn tại | 64/server, kể cả Room PAUSED |
| Create/Join/New Run | 20 lệnh/phút/IP và identity |
| Inbound message / rate | 16 KiB / 120 messages/giây/connection |
| Sequence | Integer 1–9007199254740991 |
| Input queue server / client | 120 / 180 frames |
| Input timeout | 500 ms |
| Outbound control queue / send deadline | 64 messages / 5 giây |
| Heartbeat / connection expiry | 10 / 30 giây |
| Client HTTP/handshake/command deadline | 8 giây |
| Chat | 200 Unicode code points; 1 tin/giây; 50 tin gần nhất/sảnh |

Giới hạn mặc định tập trung tại cấu hình network của backend, kiểm tra trước cấp phát connection/phòng; quotas vẫn tính Room PAUSED. Không mặc định coi các giới hạn này là năng lực đã đo. Vượt queue/rate thì báo lỗi hoặc ngắt connection; không âm thầm bỏ cạnh nhấn/thả Jump. Không dùng traffic/heartbeat thay input cho chính sách AFK.

## Client → server

| Type | Payload / hành vi |
| --- | --- |
| HEARTBEAT | {nonce} → PONG |
| JOIN_COMMUNITY | {newRun?:boolean,expectedRunId?}; mặc định tiếp tục/tạo lượt, newRun=true thay lượt sau xác nhận |
| CREATE_ROOM | {nickname,visibility?:PUBLIC/PRIVATE,settings?} → tạo sảnh chờ và làm Host |
| JOIN_ROOM | {roomId,nickname?} → vào đúng Community/Room theo ID |
| LEAVE_ROOM | {} → Community flush checkpoint; Room Leave/DNF |
| GET_ROOM_STATE | {} → state hiện hành hoặc null khi đã rời |
| UPDATE_ROOM_SETTINGS | {mode,timeLimitMinutes,mapLevel}; Host/WAITING |
| START_GAME / END_MATCH | {}; quyền, roster và lifecycle theo gameplay; kiểm tra khi xử lý command |
| PLAY_AGAIN | {matchId}; mỗi thành viên xác nhận sau match vừa kết thúc, kể cả khi phòng đã về WAITING |
| SET_ROOM_PAUSED | {playId,paused:boolean}; Host, PLAYING/PAUSED; sai trận bị từ chối |
| SET_COMMUNITY_PAUSED | {paused:boolean}; Pause/Resume cá nhân, server xác nhận theo gameplay |
| NEW_COMMUNITY_RUN | {expectedRunId}; ID lượt ACTIVE hoặc null nếu không có, thay lượt đã xác nhận |
| CHAT_SEND | {text}; render plain text, lọc control characters |
| PLAYER_INPUT | {playId,generation,seq,left,right,jump,up?,down?,freeFlight?}; booleans, field tùy chọn mặc định false |
| CANCEL_INPUT | {playId,generation,seq}; hủy input/charge, không launch |
| RECONNECT | {roomId,playerId,reconnectToken} → full state |

Settings Room mặc định RACE/null/mapLevel=10, visibility mặc định PRIVATE; mode/biên timeLimitMinutes theo gameplay, mode khác TIME_LIMIT phải null. Patch atomic, sai field/state từ chối toàn bộ.

JOIN_ROOM tra ID dùng chung cho cả hai kiểu chơi: Community yêu cầu Account và khôi phục lượt cá nhân; Room chỉ nhận người mới khi WAITING. Thành viên cũ dùng RECONNECT với token, không dùng ID để vượt roster.

JOIN_COMMUNITY newRun=true yêu cầu expectedRunId là ID ACTIVE hoặc null; giữ chỗ trước khi thay save theo transaction New Run trong data. Thiếu chỗ/DB lỗi giữ lượt cũ. NEW_COMMUNITY_RUN dùng khi đã ở Community; cả hai dùng cùng service thay lượt.

JOIN_ROOM từ Community sang Community khác là transfer: giữ lượt, đặt chỗ đích và flush state trước chuyển; thất bại giữ sảnh/lượt cũ. Room đầy/không ở WAITING thì báo lỗi, không chuyển sang phòng khác. Các trường hợp đã có membership khác trả ALREADY_IN_ROOM.

Community/Room không nhận position/velocity/score/clock hoặc checkpoint từ client. Input chỉ xử lý khi đang chơi; paused/countdown/finished cancel, không enqueue movement. Chat chỉ là text.

Client nhận diện nhấn đúp khi Fly Mode được bật và gửi freeFlight là trạng thái bay mong muốn, không phải lệnh toggle mỗi frame. Server chỉ chấp nhận freeFlight=true khi features.flyEnabled=true; nếu không trả INVALID_INPUT. up/down chỉ điều khiển khi bay; CANCEL_INPUT/timeout xóa movement/charge, không đảo trạng thái bay. Physics, thoát solid và kết quả vẫn do server xử lý theo gameplay.

## Server → client

| Type | Payload chính |
| --- | --- |
| ROOM_JOINED | {roomId,kind,playerId,reconnectToken,generation,playId}; token chỉ gửi owner |
| ROOM_STATE | {roomId,kind,state,hostPlayerId,settings,members,currentPlay,result,pauseExpiresAt} |
| ROOM_LEFT | {roomId}; null nếu đã rời |
| GAME_STARTED | {playId,generation,countdownMs} |
| GAME_SNAPSHOT | {roomId,serverTick,playId,generation,self,peers,elapsedTimeMs,remainingTimeMs} |
| COMMUNITY_RUN_STATE | {run:RunSummary,generation,paused,pausePending,saveStatus} |
| PLAYER_EFFECTS | {roomId,events:[{eventSeq,playerId,playId,screen,effect,terrain}]} |
| ADVENTURE_EVENTS | {roomId,playId,events:[{eventSeq,kind,type,id}]}; chỉ owner, kind=ADVENTURE/WORLD, nội dung hiển thị tra tại client |
| PLAYER_FINISHED | {playerId,playId,result,showEnding,ending:{screen,equipped,ravenDone}}; sau commit, riêng người Finish ở Community hoặc winners ở Room; result tương ứng RunSummary/MatchResult |
| GAME_FINISHED | {playId,reason,durationMs,results,saveStatus}; Room broadcast |
| SAVE_STATUS | {playId,revision,saveStatus}; chỉ owner checkpoint hoặc members của match |
| CHAT_MESSAGE / CHAT_HISTORY | {roomId,messages:[{id,playerId,nickname,text,sentAt,colorSlot}]}; client bỏ chat từ sảnh cũ |
| RECONNECT_OK | {roomId,playerId,generation,playId}; sau đó full state |
| PONG / ERROR | {nonce} / {code,message,playId?,generation?,seq?} |

kind COMMUNITY/ROOM; host/settings null cho Community. Member={playerId,nickname,username,colorSlot,connected,paused,nextMatchConfirmed}; Community có nickname=username, Room dùng nickname lúc Join. Các tên là plain text; colorSlot trong khoảng capacity ở gameplay. paused thuộc Community, nextMatchConfirmed thuộc Room; trường không áp dụng là null.

Room state là WAITING/COUNTDOWN/PLAYING/PAUSED/FINISHED; Community state=PLAYING, Pause thuộc từng lượt. pauseExpiresAt là UTC ISO-8601 chỉ khi Room PAUSED, còn lại null. currentPlay chứa metadata và kết quả nếu đã finalize; chưa có membership thì fields null, members=[]. Xác nhận/Host theo [gameplay](gameplay.md#room-và-xếp-hạng).

self gồm lastProcessedInputSeq, status, metrics và full simulation để restore/replay: position/velocity, grounded/charge, direction/buffers, splat/knocked, terrain/wind, freeFlight/phasingFall, screen/ticks/jumps/falls/airChargeTicks và adventure. Peers chỉ có public render state, ngoại hình, progress và status; không broadcast checkpoint/secrets.

adventure gồm ID/counters tiến độ hội thoại. Vương miện là ngoại hình profile tự cấp theo data, không có lệnh trang bị. Client dựng nội dung theo [architecture](architecture.md#công-nghệ-và-trách-nhiệm); thay đổi state phải do server xác nhận.

Results={playerId,nickname,status,rank,currentProgress,maxProgress,isWinner,finishTimeMs}; nickname giữ lúc Join để Results vẫn hiển thị đúng người đã Leave. Status/reason/saveStatus theo data; SAVE_STATUS Room có revision=null. Server chốt metrics và showEnding theo gameplay.

Chat giữ trong RAM của từng sảnh, mất khi sảnh đóng/restart. Effects gồm JUMP/LAND/SPLAT/BUMP và NORMAL/ICE/SNOW; lọc screen, âm lượng và dedupe ở client.

## Prediction và lỗi

Client gửi full input frame mỗi tick, kể cả không đổi phím. Server xử lý tối đa một frame/player/tick FIFO; thiếu frame dùng input cuối và không tăng ACK. Hết input timeout xóa queue/cancel charge; reconnect/resync nếu cần, không biến mất input thành release-jump.

ACK và state lấy cùng tick. Client restore snapshot mới hơn, bỏ frames đã ACK rồi replay frames còn lại; remote interpolation theo serverTick. Replay không phát lại âm thanh, hội thoại hoặc ending. Pause yêu cầu cancel/xóa queued input; pausePending không nhận movement mới. Resume Community/Room trả full state và generation mới cho players chịu tác động; Room Resume broadcast state. Server vẫn xử lý heartbeat/chat/reconnect/Leave và deadline khi PAUSED. Đổi playId/generation phải xóa prediction buffers; heartbeat không tăng active clock.

Control timeout: GET_ROOM_STATE/reconnect, không tự gửi lại Start/New Run/End. Duplicate requestId trả kết quả cũ; New Run kiểm tra thêm expectedRunId. Bỏ message socket/context cũ; đóng connection phải kết thúc pending promises.

Nhóm error codes: INVALID_MESSAGE/INVALID_INPUT/INVALID_SETTINGS/INVALID_NICKNAME, AUTH_REQUIRED/ACCOUNT_REQUIRED, ROOM_NOT_FOUND/ROOM_FULL/ROOM_NOT_JOINABLE/ALREADY_IN_ROOM/HOST_ONLY/NOT_ENOUGH_PLAYERS, STALE_PLAY/STALE_INPUT, INVALID_RECONNECT/RECONNECT_EXPIRED, SAVE_FAILED/CONFLICT/RATE_LIMITED/INTERNAL_ERROR.

## Mở rộng dữ liệu hiển thị

Account/profile có `crownUnlocked`; public render state chỉ mang trạng thái vương miện, không nhận quyền mở từ input online. Solo Finish có thể mở crown qua sync metrics nhưng không lên leaderboard. GAME_SNAPSHOT bổ sung Players ranking từ server RAM, giữ đồng hạng; Room theo mode, Community theo độ cao hiện tại. Không truy vấn DB theo tick để tạo bảng này. Leaderboard HTTP refresh tối đa một lần/10 giây khi đang hiển thị.
