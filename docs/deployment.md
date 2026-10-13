# deployment.md

## Runtime và workflow

JDK 25, Node.js 24, MySQL Server 8.4; build dùng Maven Wrapper/npm lockfile. Workbench quản trị MySQL; Docker phục vụ [integration tests](testing.md).

Solo chỉ cần frontend; Community/Room và Solo sync cần backend/MySQL. Trạng thái triển khai các lệnh dưới đây theo [PLAN](../PLAN.md).

| Thành phần | Development | Local/LAN/ZeroTier |
| --- | --- | --- |
| Frontend | Vite 127.0.0.1:5173 | Static files phục vụ từ backend:8080 |
| Backend | 127.0.0.1:8080; Vite proxy /api và /ws | Bind IP host hoặc 0.0.0.0 |
| MySQL | 127.0.0.1:3306 | Chỉ backend truy cập |

## Cấu hình

| File mẫu trong Git | File riêng được ignore |
| --- | --- |
| backend/config/application-local.properties.example | backend/config/application-local.properties |
| frontend/.env.example | frontend/.env.local |

Chạy backend từ `backend/` với profile local để đọc external config. Ví dụ properties:

```properties
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/jumpking?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
spring.datasource.username=jumpking_app
spring.datasource.password=CHANGE_ME
server.address=127.0.0.1
server.port=8080
app.allowed-origins=http://127.0.0.1:5173,http://127.0.0.1:8080
server.servlet.session.cookie.secure=false
```

Dùng password thật chỉ trong file riêng/process ENV. Cấu hình frontend:

```dotenv
BACKEND_URL=http://127.0.0.1:8080
VITE_BACKEND_ORIGIN=
FLY_ENABLED=false
```

BACKEND_URL chỉ dùng cho proxy Vite. VITE_BACKEND_ORIGIN trống nếu cùng origin, nếu tách thì điền origin backend không có path. Adapter nối /api và /ws/game, đổi http/https thành ws/wss. VITE_* công khai, cố định khi build; không chứa secrets. [Vite env](https://vite.dev/guide/env-and-mode)

| ENV tương ứng | Property / mặc định |
| --- | --- |
| SPRING_PROFILES_ACTIVE | local cho external config; prod khi deploy |
| SPRING_DATASOURCE_URL/USERNAME/PASSWORD | spring.datasource.url/username/password |
| SERVER_ADDRESS/PORT | server.address/port |
| APP_ALLOWED_ORIGINS | app.allowed-origins; origins cụ thể cách nhau dấu phẩy |
| SERVER_SERVLET_SESSION_COOKIE_SECURE | true khi HTTPS; áp dụng cả Guest cookie |
| SERVER_SERVLET_SESSION_TIMEOUT | server.servlet.session.timeout; idle theo [protocol](protocol.md#identity-và-security) |
| APP_SESSION_ABSOLUTE_TIMEOUT | app.session-absolute-timeout; hạn tối đa theo protocol |
| APP_AUTH_RATE_PER_MINUTE / APP_HTTP_BODY_LIMIT | app.auth-rate-per-minute=20; app.http-body-limit=16384 bytes |
| APP_CHECKPOINT_INTERVAL_MS | app.checkpoint-interval-ms=1000; chu kỳ checkpoint Community và metrics Room |
| APP_PERSISTENCE_RETRY_COUNT | app.persistence-retry-count=3 |
| FLY_ENABLED | app.fly-enabled=${FLY_ENABLED:false}; mặc định false |

Ưu tiên config: command-line → process ENV → external properties → JAR. Backend validate datasource/allowlist. Gameplay/rules ở shared, network bounds theo protocol, presentation ở frontend config. [Spring external config](https://docs.spring.io/spring-boot/reference/features/external-config.html)

Root .gitignore chứa quy tắc chung; mỗi module ignore config riêng, dependencies, build/test output.

## Fly Mode local

Solo: đặt `FLY_ENABLED=true` trong `frontend/.env.local`, restart `npm run dev`. Vite config đọc bằng loadEnv; chỉ dev server cho bay, mọi build đều tắt. [Vite conditional config](https://vite.dev/config/#conditional-config)

Community/Room: đặt `$env:FLY_ENABLED='true'` trong terminal backend rồi chạy Maven/JAR profile local; đổi cờ cần restart. Backend đọc process ENV qua property ở trên, không đọc frontend/.env.local. UI nhận `features.flyEnabled` từ game-config; quyền áp dụng mọi player kể cả LAN/ZeroTier.

Mặc định false; profile prod luôn ép false dù ENV=true. Khi deploy vẫn đặt false. Cờ công khai; điều khiển và xử lý save/thành tích theo [gameplay](gameplay.md#fly-mode).

## MySQL

1. Cài/chạy MySQL Server, kết nối bằng Workbench hoặc mysql CLI.
2. Review rồi chạy `scripts/schema.sql` trên database mới/rỗng; file tạo `jumpking` và ba bảng trong [data](data.md).
3. Tạo user ứng dụng; thay password mẫu trong câu SQL trước khi chạy:

```sql
CREATE USER 'jumpking_app'@'127.0.0.1' IDENTIFIED BY 'CHANGE_ME';
GRANT SELECT, INSERT, UPDATE, DELETE ON jumpking.* TO 'jumpking_app'@'127.0.0.1';
```

4. Điền datasource; backend chỉ kiểm tra schema, không tự reset/update và không chạy bằng root.

mysql CLI: từ root chạy `SOURCE scripts/schema.sql;`. Workbench: File → Open SQL Script → schema.sql → Execute. Database có dữ liệu dùng SQL nâng cấp riêng, không chạy lại DDL tạo mới.

Backup bằng mysqldump/Workbench vào `.local/backups/`, dùng prompt nhập password hoặc cấu hình riêng. Thử restore vào instance cô lập, kiểm tra dữ liệu trước thay database thật; không commit dump. [MySQL CREATE USER](https://dev.mysql.com/doc/refman/8.4/en/create-user.html)

## Chạy development và build

Lệnh PowerShell dưới đây; Linux/macOS thay `.\mvnw.cmd` bằng `./mvnw` và dùng cú pháp copy của shell.

Từ root, copy các file mẫu theo bảng cấu hình và điền datasource. Terminal backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Terminal frontend:

```powershell
cd frontend
npm ci
npm run dev
```

Mở http://127.0.0.1:5173. Hai người cùng máy dùng hai browser profiles hoặc normal/incognito với một backend.

Build frontend bằng `npm run build`. Từ backend chạy `.\mvnw.cmd -Pweb clean package`; profile web lấy frontend/dist làm static resources trong target, không chép generated files vào source. Chạy:

```powershell
java -jar target/jumpking-server.jar --spring.profiles.active=local
```

Mở http://127.0.0.1:8080. Backend độc lập bỏ profile web; frontend độc lập dùng static host để chơi Solo, không mở dist/index.html bằng file://.

## LAN và ZeroTier

1. Host bind backend vào IP cần dùng hoặc 0.0.0.0; thêm `http://<host-ip>:8080` vào allowed origins.
2. Cho phép inbound TCP 8080 từ LAN/overlay cần dùng, không tắt firewall hoặc mở MySQL cho players.
3. Chạy JAR; người chơi LAN mở `http://<host-lan-ip>:8080`.
4. Với ZeroTier, các máy join cùng private network, được chủ mạng authorize; dùng `http://<host-zerotier-ip>:8080`.

Frontend/backend/MySQL chạy trên host; người chơi chỉ cần trình duyệt. Dùng cùng hostname/IP nhất quán để cookie đúng host.

| Lỗi | Kiểm tra |
| --- | --- |
| Host vào được, máy khác không | IP, authorization/route, bind và firewall |
| HTTP được, WS lỗi | Origin allowlist, cookie và /ws/game |
| Backend không start | MySQL, schema, grants và datasource |
| Community báo chưa lưu | Kết nối DB và save status; không khôi phục thủ công save cũ |
| Solo không lưu | Quyền/quota browser storage |
| Solo chưa đồng bộ Stats | Backend, mạng/phiên Account đúng owner và hàng chờ local; save vẫn ở thiết bị |

## Vận hành và deploy Internet

Dừng/restart có kiểm soát: ngừng nhận phiên mới, flush checkpoint/kết quả rồi dừng process. Crash vẫn có giới hạn trong [architecture](architecture.md#lỗi-và-khả-năng-mở-rộng). Cập nhật backend/frontend giữ contract tương thích; backup trước thay schema, rollback không ghi đè dữ liệu mới bằng dump cũ.

Khi triển khai phase Internet: frontend Cloudflare Pages, Spring Boot trên VPS qua reverse proxy HTTPS/WSS; MySQL chỉ backend truy cập. Dùng `game.example.com` và `api.example.com` cùng site để phù hợp cookie SameSite=Lax; *.pages.dev và API khác site cần proxy cùng origin hoặc thiết kế lại authentication, CORS không thay cookie policy.

Build Pages từ frontend, command `npm run build`, output `dist`, đặt VITE_BACKEND_ORIGIN trước build. VPS chạy JAR như service, proxy WebSocket upgrade, Secure cookies và origins cụ thể; chỉ tin forwarded headers từ proxy. Nghiệm thu từ mạng ngoài theo testing; Solo vẫn dùng được khi backend không sẵn sàng.
