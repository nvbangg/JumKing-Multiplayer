# JumpKing Multiplayer

Website game Jump King với một map Main Babe đầy đủ, hỗ trợ chơi đơn và nhiều người qua mạng.

| Kiểu chơi | Mục đích |
| --- | --- |
| Solo | Luyện tập offline, autosave/Continue; Account đồng bộ thống kê khi có mạng, không lên leaderboard |
| Community Tower | Account chơi cùng sảnh, lưu tiến độ trên server và tính leaderboard |
| Room | Tạo/join bằng ID, tối thiểu 2 người và Host Start; Race, Hardcore, Time Limit hoặc No Walk; Results và History |

Frontend dùng React + TypeScript/Phaser; backend Spring Boot/Java quyết định trạng thái và kết quả online. MySQL lưu identity, lượt chơi và trận Room. Thống kê tổng và History gồm Solo, Community và Room; Guest có thể Merge lịch sử Room vào Account. Luật chơi trong [gameplay](docs/gameplay.md); tiến độ triển khai trong [PLAN](PLAN.md).

## Cài đặt và chạy

Cần **JDK 25, Node.js 24, MySQL Server 8.4**; Workbench để quản trị database. Tiến độ triển khai và các bước đã kiểm chứng theo [PLAN](PLAN.md).

1. Clone project; chạy `scripts/schema.sql` bằng Workbench hoặc mysql CLI để tạo database `jumpking`.
2. Tạo user MySQL dành cho ứng dụng theo [hướng dẫn MySQL](docs/deployment.md#mysql).
3. Copy cấu hình mẫu từ root project:

```powershell
Copy-Item backend/config/application-local.properties.example backend/config/application-local.properties
Copy-Item frontend/.env.example frontend/.env.local
```

Điền datasource URL/user/password trong file backend riêng. Frontend dùng proxy mặc định; để VITE_BACKEND_ORIGIN trống khi cùng origin. Bảng giá trị và cách đổi IP/port trong [deployment](docs/deployment.md#cấu-hình); không đưa secrets vào file mẫu hoặc frontend.

Mở hai terminal. Backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Frontend:

```powershell
cd frontend
npm ci
npm run dev
```

Mở **http://127.0.0.1:5173**. Backend dùng **8080**, MySQL **3306**. Linux/macOS thay mvnw.cmd bằng ./mvnw. Hai người trên cùng máy dùng hai browser profiles hoặc normal/incognito, chỉ chạy một backend.

Build: frontend `npm run build`, backend `.\mvnw.cmd -Pweb clean package`; từ backend chạy `java -jar target/jumpking-server.jar --spring.profiles.active=local`, mở **http://127.0.0.1:8080**. Chi tiết [build/local](docs/deployment.md#chạy-development-và-build).

## Demo nhanh

- **Solo:** bắt đầu lượt mới hoặc tiếp tục save trên thiết bị; không cần đăng nhập/backend sau khi frontend đã tải.
- **Community Tower:** đăng ký/đăng nhập, chọn Community Tower; tiến độ tự lưu và được tính leaderboard.
- **Room:** mở hai browser profiles, một người Create Room, người còn lại Join by ID; Host chọn mode và Start khi đủ hai người.

## Chơi qua LAN/ZeroTier

Host bind backend vào IP cần dùng hoặc 0.0.0.0, thêm origin IP:8080 vào allowlist và cho phép firewall TCP 8080 từ mạng cần dùng. Người chơi mở `http://<host-ip>:8080`. ZeroTier cần cùng network và được authorize; dùng IP ZeroTier của host. Không mở MySQL cho người chơi. [Các bước và xử lý lỗi](docs/deployment.md#lan-và-zerotier)

## Tài liệu

| Nội dung | Nguồn chính |
| --- | --- |
| Công nghệ, thành phần và cấu trúc code | [architecture](docs/architecture.md) |
| Gameplay, map, kiểu chơi và nội dung game | [gameplay](docs/gameplay.md) |
| HTTP/WebSocket và bảo mật | [protocol](docs/protocol.md) |
| Database, checkpoint và thành tích | [data](docs/data.md) |
| Cấu hình, MySQL, Local/LAN/ZeroTier/Internet | [deployment](docs/deployment.md) |
| Lệnh kiểm thử và tiêu chí nghiệm thu | [testing](docs/testing.md) |
| Kế hoạch, kết quả thực nghiệm và giới hạn | [PLAN](PLAN.md) |
| Quy trình agent | [AGENTS](AGENTS.md) |
| Yêu cầu bài tập lớn | [Instruction](docs/course-materials/final-project/final-project/Instruction.md) |
