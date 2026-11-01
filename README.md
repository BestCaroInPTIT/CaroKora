# Hệ Thống Game Cờ Caro Trực Tuyến (KORA Caro)

Dự án môn **Phát triển phần mềm hướng đối tượng & Lập trình thiết bị di động** — **Nhóm 07 (PTIT)**.

- **Cơ sở dữ liệu đám mây**: **Neon Lakebase PostgreSQL** (`ap-southeast-1`, Singapore)
- **Backend Server**: **Kotlin + Ktor (Netty) + JDBC / HikariCP** (triển khai Docker Web Service trên **Render**)
- **Mobile Client**: **Android Native (Kotlin + XML Layouts + OkHttp + Gson)**, giao diện **KORA** chuẩn thiết kế Figma/PPT

---

## 1. Phân công và phạm vi mã nguồn hiện tại

Mã nguồn hiện tại triển khai **đúng và đủ 3 cụm chức năng của Phạm Thị Thiên Hà (`B23DCCN266`)** theo sát tài liệu thiết kế lớp, biểu đồ tuần tự và CSDL mức 3NF, đồng thời để sẵn các điểm ghép nối sạch cho thành viên cùng nhóm:

### Phần của Phạm Thị Thiên Hà (`B23DCCN266`) — Đã hoàn thiện 100%
1. **Chức năng 1 — Quản lý thông tin & Đổi mật khẩu**:
   - Client: `LoginActivity` (`login.xml`), `PlayerHomeActivity` (`playerhome.xml`), `PlayerProfileActivity` (`playerprofile.xml`), `ChangePasswordDialog` (`changepassword.xml`), `ApiClient`, `PlayerClientController`.
   - Server: `PlayerServerController`, `DAO`, `PlayerDAO`, `PlayerStatisticDAO`.
2. **Chức năng 2 — Thách đấu người chơi trực tuyến & Xem lịch sử nước đi**:
   - Client: `PlayerHomeActivity`, `OnlinePlayersActivity` (`onlineplayers.xml`), `ChallengeDialog` (`challenge.xml`), `MatchActivity` (`match.xml`), `ChallengeClientController`.
   - Server: `ChallengeServerController`, `PlayerStatisticDAO`, `MatchDAO`, `MoveDAO`.
3. **Chức năng 3 — Chơi với người (Ghép trận, Bàn cờ 17×17, Dừng chơi, Tái đấu nhanh)**:
   - Client: `PlayerHomeActivity`, `GameModeActivity` (`gamemode.xml`), `FindOpponentDialog` (`findopponent.xml`), `MatchActivity` (`match.xml`), `MatchResultDialog` (`matchresult.xml`), `HumanMatchClientController`.
   - Server: `HumanMatchServerController`, `MatchDAO`, `MoveDAO`, `PlayerStatisticDAO`.

### Điểm ghép nối cho Nguyễn Mạnh Đức (`B23DCKH028`)
Để ghép 3 chức năng của Đức (**Xem lịch sử đấu cá nhân**, **Xem bảng xếp hạng**, **Chơi với máy / AI**) vào chung 1 hệ thống mà **không xung đột Git**:
1. **CSDL & Thực thể dùng chung (`entity/`)**: Cả 4 bảng (`Player`, `Match`, `MatchParticipant`, `Move`) trên Neon và 6 lớp thực thể (`Player`, `PlayerStatistic`, `Match`, `MatchParticipant`, `Move`, `GameBoard`) đã hỗ trợ đầy đủ cột `aiDifficulty`, `participantType = 'AI'`, `mode = 'AI'`, `rank`...
2. **Phía Server (`server/`)**:
   - Tạo thêm các controller/DAO riêng của Đức và file `server/src/main/kotlin/com/caro/server/routes/DucRoutes.kt` (`fun Route.configureDucRoutes()`).
   - Mở comment `configureDucRoutes()` tại dòng 111 của `server/src/main/kotlin/com/caro/server/Application.kt`.
3. **Phía Android Client (`android-client/`)**:
   - Nút `btnViewRanking` trong `PlayerHomeActivity.kt` -> chuyển sang `RankingActivity`.
   - Nút `btnViewMatchHistory` trong `PlayerProfileActivity.kt` -> chuyển sang `MatchHistoryActivity`.
   - Nút `btnPlayAgainstAI` trong `GameModeActivity.kt` -> chuyển sang màn hình chọn độ khó / `BotMatch`.
   - Khai báo thêm các Activity mới trong `AndroidManifest.xml`.

---

## 2. Thiết kế CSDL trên Neon PostgreSQL (Chuẩn 3NF)

File DDL tự động khởi tạo khi Server chạy lần đầu: `server/src/main/resources/db/schema.sql`.
- Bảng `Player(id, gameID, password, fullName, email, avatar, bio, onlineStatus)`
- Bảng `Match(id, mode, status, startedAt, endedAt, currentTurn, turnTimeLimit, boardSize)` *(không lưu `boardState` dư thừa; trạng thái bàn cờ 17×17 được tái tạo trong RAM bởi `GameBoard.rebuildFromMoves()`)*
- Bảng `MatchParticipant(id, participantType, side, aiDifficulty, outcome, score, Playerid, Matchid)`
- Bảng `Move(id, "row", "column", moveOrder, createdAt, MatchParticipantid, Matchid)`

Tài khoản mẫu có sẵn để test đăng nhập trên 2 máy / 2 máy ảo:
| `gameID` | `password` | Họ tên |
|---|---|---|
| `ha88` | `123456` | Pham Thi Thien Ha |
| `Bn` | `123456` | Phan B |
| `A` | `123456` | Nguyen Van A |
| `ds3` | `123456` | Tran Minh Duc |

---

## 3. Hướng dẫn Deploy Server lên Render kết nối Neon

1. **Đẩy code lên GitHub** (file `.gitignore` đã tự động chặn `.env` chứa mật khẩu thật):
   ```powershell
   git init
   git add .
   git commit -m "Implement Caro Game Server & Android Client (Ha modules)"
   git branch -M main
   git remote add origin https://github.com/BestCaroInPTIT/CaroKora.git
   git push -u origin main
   ```
2. **Tạo Web Service trên Render**:
   - Đăng nhập https://dashboard.render.com -> **New +** -> **Web Service** -> chọn GitHub repo vừa push.
   - **Name**: `caro-game-server`
   - **Region**: `Singapore (Southeast Asia)` (cùng khu vực với Neon `ap-southeast-1` để độ trễ thấp nhất).
   - **Root Directory**: `server`
   - **Runtime**: `Docker`
   - **Instance Type**: `Free`
   - Trong mục **Environment Variables**, thêm biến:
     - `DATABASE_URL` = *(dán chuỗi `DATABASE_URL_POOLED` có hậu tố `-pooler` từ Neon Dashboard / file `.env`)*
   - Nhấn **Deploy Web Service**. Khi lên trạng thái **Live**, mở `https://carokora.onrender.com/health` thấy `{"status":"OK","database":"Neon PostgreSQL"}` là thành công!

---

## 4. Hướng dẫn chạy Server Local & Android App

### Chạy Server tại máy cá nhân (kết nối thẳng tới Neon qua `.env`)
```powershell
.\server\gradlew.bat -p server run
```
Server lắng nghe tại `http://localhost:8080` (kiểm tra tại `http://localhost:8080/health`).

### Chạy ứng dụng Android (`android-client`)
1. Mở **Android Studio** -> **Open** -> chọn thư mục **`android-client`**.
2. Đợi Gradle Sync hoàn tất -> nhấn **Run (`Shift + F10`)** lên máy ảo Android Emulator hoặc điện thoại thật.
3. Ngay ở góc trên bên phải màn hình **LOGIN**, bấm nút **`SERVER`**:
   - Nếu chạy với **Render Cloud**: nhập `https://carokora.onrender.com` -> **SAVE**.
   - Nếu chạy với **Server Local trên máy tính** (qua Android Emulator): nhập `http://10.0.2.2:8080` -> **SAVE**.
4. Đăng nhập trên 2 máy ảo (ví dụ Máy 1: `ha88` / `123456`, Máy 2: `Bn` / `123456`) để demo trực tiếp Đổi mật khẩu, Thách đấu & Xem lịch sử nước đi, và Ghép trận Chơi với người!
