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
| backend/config/application-prod.properties.example | backend/config/application-prod.properties |
| frontend/.env.example | frontend/.env.local |

Chạy backend từ `backend/`; profile local/prod đọc file tương ứng trong `config/`. Spring không tự nạp `.env`; backend dùng properties hoặc process ENV. Ví dụ local:

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

IP/port trong file mẫu là mặc định local, không phải địa chỉ bị khóa trong logic game. Với JAR cùng origin, frontend tự dùng URL đang mở; chuyển sang LAN/ZeroTier chỉ đổi cấu hình backend riêng, không sửa TypeScript/Java.

| ENV tương ứng | Property / mặc định |
| --- | --- |
| SPRING_PROFILES_ACTIVE | local cho HTTP Local/LAN; prod cho HTTPS qua proxy |
| SPRING_DATASOURCE_URL/USERNAME/PASSWORD | spring.datasource.url/username/password |
| SERVER_ADDRESS/PORT | server.address/port |
| APP_ALLOWED_ORIGINS | app.allowed-origins; origins cụ thể cách nhau dấu phẩy |
| SERVER_SERVLET_SESSION_COOKIE_SECURE | true khi HTTPS; áp dụng cả Guest cookie |
| SERVER_SERVLET_SESSION_TIMEOUT | server.servlet.session.timeout; idle theo [protocol](protocol.md#identity-và-security) |
| APP_SESSION_ABSOLUTE_TIMEOUT | app.session-absolute-timeout; hạn tối đa theo protocol |
| APP_AUTH_RATE_PER_MINUTE / APP_HTTP_BODY_LIMIT | app.auth-rate-per-minute=20; app.http-body-limit=16384 bytes |
| APP_SOLO_SYNC_RATE_PER_MINUTE | app.solo-sync-rate-per-minute=60; giới hạn theo Account |
| APP_CHECKPOINT_INTERVAL_MS | app.checkpoint-interval-ms=1000; chu kỳ checkpoint Community và metrics Room |
| APP_PERSISTENCE_RETRY_COUNT | app.persistence-retry-count=3; tối đa 3 lần thử lại sau lần ghi đầu |
| APP_PERSISTENCE_RETRY_DELAY_MS | app.persistence-retry-delay-ms=1000; khoảng chờ giữa các lần thử |
| APP_PERSISTENCE_THREADS / QUEUE / RUN_QUEUE | app.persistence-threads=2, app.persistence-queue=256, app.persistence-run-queue=16; workers chung và hàng chờ từng lượt |
| FLY_ENABLED | app.fly-enabled=${FLY_ENABLED:false}; mặc định false |

Ưu tiên config: command-line → process ENV → external properties → JAR. Backend validate datasource/allowlist. Gameplay/rules ở shared, network bounds theo protocol, presentation ở frontend config. [Spring external config](https://docs.spring.io/spring-boot/reference/features/external-config.html)

Root .gitignore chứa quy tắc chung; mỗi module ignore config riêng, dependencies, build/test output.

`backend/config/` chỉ đưa các mẫu `.properties.example` lên Git. File thực tế phải có quyền đọc giới hạn cho người chạy ứng dụng. Tài khoản MySQL chỉ có quyền DML trong database của game; không dùng root.

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
4. Với ZeroTier, các máy join cùng private network, được chủ mạng authorize; dùng `http://<host-zerotier-ip>:8080`. [ZeroTier quickstart](https://docs.zerotier.com/quickstart/)

Frontend/backend/MySQL chạy trên host; người chơi chỉ cần trình duyệt. Dùng cùng hostname/IP nhất quán để cookie đúng host.

Windows host: mở PowerShell Administrator, thay IP/subnet ví dụ bằng mạng ZeroTier thực tế rồi tạo rule giới hạn trên mạng đó:

```powershell
New-NetFirewallRule -DisplayName 'JumpKing ZeroTier 8080' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8080 -LocalAddress 10.147.17.5 -RemoteAddress 10.147.17.0/24 -Profile Any
```

Máy người chơi kiểm tra `Test-NetConnection 10.147.17.5 -Port 8080`; `TcpTestSucceeded=True` mới xác nhận cổng tới được. Sau đó dùng trình duyệt thử đăng nhập, hai người Create/Join cùng Room, Start/nhảy/chat/End/Leave; Community thử Save & Exit/Continue. Không chạy lại schema hoặc cài backend/MySQL ở máy người chơi.

| Lỗi | Kiểm tra |
| --- | --- |
| Host vào được, máy khác không | IP, authorization/route, bind và firewall |
| HTTP được, WS lỗi | Origin allowlist, cookie và /ws/game |
| Đăng nhập xong lại mất phiên | Dùng nhất quán một hostname/IP; HTTP Local/ZeroTier dùng profile local với cookie Secure=false, HTTPS dùng prod/Secure=true |
| Backend không start | MySQL, schema, grants và datasource |
| Community báo chưa lưu | Kết nối DB và save status; không khôi phục thủ công save cũ |
| Solo không lưu | Quyền/quota browser storage |
| Solo chưa đồng bộ Stats | Backend, mạng/phiên Account đúng owner và hàng chờ local; save vẫn ở thiết bị |

## VPS một domain

Phương án gọn: JAR web + MySQL + Caddy cùng VPS Linux; không cần Node.js trên VPS nếu build JAR ở máy khác. Cài JDK 25, MySQL và [Caddy](https://caddyserver.com/docs/install), trỏ DNS domain về VPS. Chỉ mở 80/443 cho web; giữ 8080/3306 nội bộ.

1. Tạo schema/user theo phần MySQL; chuẩn bị JAR web đã build với `VITE_BACKEND_ORIGIN` trống để dùng cùng origin.
2. Tạo user hệ thống và thư mục (thực hiện một lần):

```bash
sudo useradd --system --user-group --home-dir /opt/jumpking --shell /usr/sbin/nologin jumpking
sudo install -d -o jumpking -g jumpking /opt/jumpking/config
```

Chép JAR web thành `/opt/jumpking/jumpking-server.jar`. Tạo `/opt/jumpking/config/application-prod.properties` từ mẫu prod; điền datasource và `app.allowed-origins=https://game.example.com` theo domain thật. Sau khi chép JAR/config, cấp quyền:

```bash
sudo chown jumpking:jumpking /opt/jumpking/jumpking-server.jar /opt/jumpking/config/application-prod.properties
sudo chmod 600 /opt/jumpking/config/application-prod.properties
```

3. Tạo `/etc/systemd/system/jumpking.service` (đổi đường dẫn Java nếu khác):

```ini
[Unit]
Description=JumpKing
After=network.target mysql.service

[Service]
User=jumpking
WorkingDirectory=/opt/jumpking
ExecStart=/usr/bin/java -jar /opt/jumpking/jumpking-server.jar --spring.profiles.active=prod
Restart=on-failure
RestartSec=5
TimeoutStopSec=60
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

4. Trong `/etc/caddy/Caddyfile`, thay domain:

```caddyfile
game.example.com {
    encode zstd gzip
    reverse_proxy 127.0.0.1:8080
}
```

5. Chạy `sudo systemctl daemon-reload`, `sudo systemctl enable --now jumpking`, rồi `sudo systemctl reload caddy`. Đọc lỗi bằng `sudo journalctl -u jumpking -n 100`; health tại `https://game.example.com/api/health` phải trả `UP`. Nghiệm thu đăng nhập, Community save/Continue và Room hai người qua mạng ngoài.

Profile prod bật Secure cookies, chỉ tin forwarded headers từ proxy loopback và luôn tắt Fly. Không mở JAR trực tiếp ra Internet; Caddy ghi đè forwarded headers và tự xử lý [HTTPS](https://caddyserver.com/docs/automatic-https), [WebSocket](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy). Không đặt `internal-proxies` rỗng hoặc tin mọi IP. [Spring proxy configuration](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html#howto.webserver.use-behind-a-proxy-server)

## Vận hành và Cloudflare Pages

Dừng/restart có kiểm soát: ngừng nhận phiên mới, flush checkpoint/kết quả rồi dừng process. Crash vẫn có giới hạn trong [architecture](architecture.md#lỗi-và-khả-năng-mở-rộng). Cập nhật backend/frontend giữ contract tương thích; backup trước thay schema, rollback không ghi đè dữ liệu mới bằng dump cũ.

Nếu tách frontend lên Cloudflare Pages: dùng `game.example.com` và API VPS `api.example.com` cùng site để phù hợp cookie SameSite=Lax; *.pages.dev và API khác site cần proxy cùng origin hoặc thiết kế lại authentication, CORS không thay cookie policy.

Build Pages từ frontend, command `npm run build`, output `dist`, đặt VITE_BACKEND_ORIGIN trước build. VPS chạy JAR như service, proxy WebSocket upgrade, Secure cookies và origins cụ thể; chỉ tin forwarded headers từ proxy. Nghiệm thu từ mạng ngoài theo testing; Solo vẫn dùng được khi backend không sẵn sàng.
