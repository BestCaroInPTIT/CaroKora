# Hướng Dẫn Ghép Code & Triển Khai Phần Của Nguyễn Mạnh Đức (`B23DCKH028`)

Chào Đức! Toàn bộ khung dự án **KORA Caro** (Database trên **Neon PostgreSQL**, Backend Docker trên **Render**, và Android App giao diện **KORA** + 3 chức năng của Hà) đã được dựng xong chuẩn 1-1 theo Biểu đồ lớp báo cáo và để sẵn các **điểm chờ ghép nối (hooks)** cho 3 chức năng của cậu.

---

## 1. Thông tin hệ thống chung của nhóm

- **GitHub Repository**: https://github.com/BestCaroInPTIT/CaroKora
- **Server Cloud (Render — tự động deploy khi push lên nhánh `main`)**: https://carokora.onrender.com
  - Link kiểm tra Server & DB sống: https://carokora.onrender.com/health
- **Database Cloud**: **Neon PostgreSQL** (khu vực Singapore `ap-southeast-1`)
  - Nhận file `.env` từ Hà (gửi riêng qua tin nhắn, **tuyệt đối không commit `.env` lên GitHub**) và đặt ngay ở thư mục gốc `CaroKora/.env` để khi chạy Server ở máy cậu, Server tự kết nối thẳng vào DB Neon của nhóm.
- **Tài khoản mẫu có sẵn trên DB để đăng nhập test ngay**:
  | `gameID` | `password` | Họ tên |
  | :--- | :--- | :--- |
  | `ha88` | `123456` | Pham Thi Thien Ha |
  | `Bn` | `123456` | Phan B |
  | `A` | `123456` | Nguyen Van A |
  | `ds3` | `123456` | Tran Minh Duc |

---

## 2. Cấu trúc thư mục dự án (chuẩn Biểu đồ lớp)

```text
CaroKora/
├── .env                        <-- File Hà gửi riêng (đặt ở đây để chạy Server local, Git đã tự ẩn)
├── render.yaml                 <-- Cấu hình tự động deploy Docker lên Render
├── server/                     <-- Backend Kotlin + Ktor + JDBC/HikariCP (Cổng 8080)
│   └── src/main/
│       ├── resources/db/schema.sql
│       └── kotlin/com/caro/server/
│           ├── Application.kt  <-- Đăng ký thêm các endpoint /api/... của Đức vào trong route("/api")
│           ├── entity/         <-- 6 lớp thực thể dùng chung (ĐÃ CÓ SẴN)
│           ├── dao/            <-- DAO, PlayerDAO, PlayerStatisticDAO, MatchDAO, MoveDAO
│           └── controller/     <-- Thêm 3 ServerController của Đức vào đây
└── android-client/             <-- Project Android Native (Mở thư mục này bằng Android Studio)
    └── app/src/main/
        ├── AndroidManifest.xml <-- Khai báo thêm các Activity mới của Đức
        ├── java/com/caro/client/
        │   ├── entity/         <-- 6 lớp thực thể dùng chung (ĐÃ CÓ SẴN)
        │   ├── controller/     <-- ApiClient đã có sẵn; thêm 3 ClientController của Đức vào đây
        │   └── ui/             <-- Thêm các Activity của Đức vào đây (gói gọn hàm trong onCreate, onClick, onResume...)
        └── res/                <-- Đã có sẵn bộ màu colors.xml, khung viền KORA drawable/
```

---

## 3. Những gì ĐÃ CÓ SẴN — Đức chỉ việc gọi lại, KHÔNG cần tạo lại

1. **4 bảng CSDL chuẩn 3NF trên Neon** (`server/src/main/resources/db/schema.sql`):
   - `Player(id, gameID, password, fullName, email, avatar, bio, onlineStatus)`
   - `Match(id, mode, status, startedAt, endedAt, currentTurn, turnTimeLimit, boardSize)` (`mode`: `'CHALLENGE'`, `'HUMAN'`, `'AI'`)
   - `MatchParticipant(id, participantType, side, aiDifficulty, outcome, score, Playerid, Matchid)` (`Playerid` cho phép `NULL` khi `participantType = 'AI'`)
   - `Move(id, "row", "column", moveOrder, createdAt, MatchParticipantid, Matchid)`
2. **6 lớp Entity ở cả Server lẫn Android**:
   - `Player`, `PlayerStatistic`, `Match`, `MatchParticipant`, `Move`, `GameBoard` (17×17, hàng `"A".."Q"`, cột `1..17`).
3. **Hàm kiểm tra thắng/thua/hòa Caro 17×17 ở Server**:
   - Xem hàm `checkOutcome(board, lastMove)` trong `HumanMatchServerController.kt` (hỗ trợ đủ 5 quân liên tiếp, 4 quân không bị chặn đầu `.XXXX.`, và hòa khi đầy bàn cờ).
4. **Lớp `ApiClient` bên Android** (`com.caro.client.controller.ApiClient`):
   - Mặc định trỏ thẳng tới `https://carokora.onrender.com`. Các lớp `MatchHistoryClientController`, `RankingClientController`, `AIMatchClientController` chỉ cần kế thừa `: ApiClient()` rồi gọi `getJson(path, type)` hoặc `postJson(path, body, type)`.

---

## 4. Ba điểm ghép nối cụ thể cho 3 chức năng của Đức

1. **Xem lịch sử trận đấu (`MatchHistory`)**:
   - Trong `PlayerProfileActivity.kt` -> nhánh `R.id.btnViewMatchHistory` trong `onClick()`: mở `Intent` sang `MatchHistoryActivity`.
2. **Xem bảng xếp hạng (`Ranking`)**:
   - Trong `PlayerHomeActivity.kt` -> nhánh `R.id.btnViewRanking` trong `onClick()`: mở `Intent` sang `RankingActivity`.
3. **Chơi với máy (`AIMatch`)**:
   - Trong `GameModeActivity.kt` -> nhánh `R.id.btnPlayAgainstAI` trong `onClick()`: mở `Intent` sang `SelectDifficultyActivity`, sau khi tạo `Match(mode = "AI")` có thể mở luôn `MatchActivity` (`putExtra("extra_player", player)` và `putExtra("extra_match", createdMatch)`).

> **Lưu ý quan trọng theo yêu cầu của thầy**: Không tạo file `.kt` phụ ngoài Biểu đồ lớp, và trong các lớp `Activity` / `Dialog` chỉ để đúng các hàm có trên Biểu đồ lớp (`onCreate`, `onClick`, `onResume`, `onItemClick`...).

---

## 5. Cách chạy & đẩy code lên GitHub / Render

1. Clone & tạo nhánh riêng:
   ```powershell
   git clone https://github.com/BestCaroInPTIT/CaroKora.git
   cd CaroKora
   git checkout -b duc-modules
   ```
   (Đặt file `.env` Hà gửi vào `CaroKora/.env`).
2. Chạy Server local để test API mới:
   ```powershell
   .\server\gradlew.bat -p server run
   ```
3. Mở thư mục con **`CaroKora/android-client`** bằng **Android Studio** -> đợi Gradle Sync -> bấm **Run**.
4. Khi hoàn tất, push nhánh `duc-modules` và merge vào `main` -> **Render tự động build Docker và cập nhật lên `https://carokora.onrender.com`**.
