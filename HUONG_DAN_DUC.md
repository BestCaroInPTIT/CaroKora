# Hướng Dẫn Ghép Code & Triển Khai Phần Của Nguyễn Mạnh Đức (`B23DCKH028`)

Chào Đức! Toàn bộ khung dự án **KORA Caro** (Database trên **Neon PostgreSQL**, Backend Docker trên **Render**, và Android App giao diện **KORA** + 3 chức năng của Hà) đã được dựng xong, chạy thực tế và để sẵn các **điểm chờ ghép nối (hooks)** đúng theo báo cáo thiết kế của cậu.

Cậu chỉ cần làm theo đúng tài liệu này là ghép 3 chức năng của cậu vào chạy chung **1 App + 1 Server + 1 Database** mà không bị xung đột code.

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

## 2. Cấu trúc thư mục dự án

```text
CaroKora/
├── .env                        <-- File Hà gửi riêng (đặt ở đây để chạy Server local, Git đã tự ẩn)
├── render.yaml                 <-- Cấu hình tự động deploy Docker lên Render
├── server/                     <-- Backend Kotlin + Ktor + JDBC/HikariCP (Cổng 8080)
│   └── src/main/
│       ├── resources/db/schema.sql
│       └── kotlin/com/caro/server/
│           ├── Application.kt  <-- Mở comment configureDucRoutes() tại dòng 111
│           ├── entity/         <-- 6 lớp thực thể dùng chung (ĐÃ CÓ SẴN)
│           ├── dao/            <-- DAO, PlayerDAO, PlayerStatisticDAO, MatchDAO, MoveDAO
│           ├── controller/     <-- Thêm 3 ServerController của Đức vào đây
│           └── routes/         <-- Tạo thêm file DucRoutes.kt vào đây
└── android-client/             <-- Project Android Native (Mở thư mục này bằng Android Studio)
    └── app/src/main/
        ├── AndroidManifest.xml <-- Khai báo thêm các Activity mới của Đức
        ├── java/com/caro/client/
        │   ├── entity/         <-- 6 lớp thực thể dùng chung (ĐÃ CÓ SẴN)
        │   ├── controller/     <-- ApiClient đã có sẵn; thêm 3 ClientController của Đức vào đây
        │   └── ui/             <-- Thêm các Activity của Đức vào đây
        └── res/                <-- Đã có sẵn bộ màu colors.xml, khung viền đôi KORA drawable/
```

---

## 3. Những gì ĐÃ CÓ SẴN — Đức chỉ việc gọi lại, KHÔNG cần tạo lại

1. **4 bảng CSDL chuẩn 3NF trên Neon** (`server/src/main/resources/db/schema.sql`):
   - `Player(id, gameID, password, fullName, email, avatar, bio, onlineStatus)`
   - `Match(id, mode, status, startedAt, endedAt, currentTurn, turnTimeLimit, boardSize)`
     - Cột `mode` đã hỗ trợ `'CHALLENGE'`, `'HUMAN'`, `'AI'`.
   - `MatchParticipant(id, participantType, side, aiDifficulty, outcome, score, Playerid, Matchid)`
     - Khi tạo participant cho Máy (AI): để `participantType = "AI"`, `side = "O"`, `aiDifficulty = "BEGINNER" | "INTERMEDIATE" | "EXPERT"`, và `Playerid = NULL` (cột `Playerid` đã cho phép `NULL` cho AI!).
   - `Move(id, "row", "column", moveOrder, createdAt, MatchParticipantid, Matchid)`
     - Lưu ý: `"row"` và `"column"` là từ khóa của PostgreSQL nên trong câu SQL nhớ bọc dấu ngoặc kép `"row"`, `"column"` (xem mẫu trong `MoveDAO.kt`).
2. **6 lớp Entity ở cả Server lẫn Android**:
   - `Player`, `PlayerStatistic`, `Match`, `MatchParticipant`, `Move`, `GameBoard` (bàn cờ 17×17, hàng `"a".."q"`, cột `1..17`, có sẵn `GameBoard.rebuildFromMoves(moves)`).
3. **Hàm kiểm tra thắng/thua/hòa Caro 17×17 ở Server**:
   - Gọi trực tiếp `CaroOutcomeChecker.checkOutcome(board, move)` trả về `"WIN"`, `"DRAW"`, hoặc `"CONTINUE"`.
4. **Lớp `ApiClient` bên Android** (`com.caro.client.controller.ApiClient`):
   - Đã cấu hình mặc định trỏ tới `https://carokora.onrender.com` (timeout 45s chịu được cold-start của Render Free).
   - Các lớp `MatchHistoryClientController`, `RankingClientController`, `AIMatchClientController` chỉ cần kế thừa `: ApiClient()` rồi gọi `getJson(path, type)` hoặc `postJson(path, body, type)` (xem mẫu cực ngắn trong `PlayerClientController.kt`).
5. **Style giao diện KORA đồng bộ toàn App**:
   - Dùng lại các màu trong `res/values/colors.xml` (`@color/kora_ink`, `@color/kora_mint`, `@color/kora_coral`, `@color/kora_card_bg`, `@color/kora_cream`...) và background viền đôi trong `res/drawable/` (`@drawable/bg_box_outline`, `@drawable/bg_box_cream`, `@drawable/bg_box_zebra`, `@drawable/bg_btn_mint`, `@drawable/bg_btn_neutral`, `@drawable/bg_btn_cream`).

---

## 4. Ba điểm ghép nối cụ thể cho 3 chức năng của Đức

### Chức năng 1: Xem lịch sử trận đấu (`MatchHistory`)
- **Điểm nối trên Giao diện Android**:
  - Mở `android-client/.../ui/PlayerProfileActivity.kt`, tìm nhánh `R.id.btnViewMatchHistory` (khoảng dòng 176).
  - Thay hộp thoại `KoraNoticeDialog.show(...)` bằng `Intent` mở `MatchHistoryActivity` (truyền `player` hoặc `player.id`).
- **Các lớp Đức cần tạo thêm**:
  - **Android UI**: `MatchHistoryActivity` (`matchhistory.xml`), `MatchDetailActivity` (`matchdetail.xml`) — nhớ đăng ký trong `AndroidManifest.xml`.
  - **Android Controller**: `MatchHistoryClientController : ApiClient()` (`getMatchHistory(playerId, key)`, `getMatchDetail(matchId)`).
  - **Server Controller & DAO**:
    - Tạo `MatchHistoryServerController` (`handleGetMatchHistory`, `handleGetMatchDetail`).
    - Trong `MatchDAO.kt`: viết thêm hàm `getMatchHistory(playerId: Int, key: String): ArrayList<Match>` (lọc theo mã trận, tên đối thủ hoặc ngày đấu; chi tiết 1 trận có thể dùng luôn `matchDAO.getMatchById(matchId)` + `moveDAO.getMoveHistory(matchId)` đã có sẵn!).

---

### Chức năng 2: Xem bảng xếp hạng (`Ranking`)
- **Điểm nối trên Giao diện Android**:
  - Mở `android-client/.../ui/PlayerHomeActivity.kt`, tìm nhánh `R.id.btnViewRanking` (dòng 110).
  - Thay `KoraNoticeDialog.show(...)` bằng `Intent` mở `RankingActivity` (truyền `player`).
- **Mẹo tiết kiệm thời gian cho màn Hồ sơ công khai (`PublicProfileActivity`)**:
  - Trong `PlayerStatisticDAO.kt` ở Server **đã viết sẵn** câu truy vấn SQL CTE tính đầy đủ cả 8 chỉ số thống kê + `DENSE_RANK()` xếp hạng trong hàm `getPlayerStatistics(playerId)`! Cậu chỉ cần viết thêm hàm `getRanking(): ArrayList<PlayerStatistic>` dùng cùng CTE đó và `ORDER BY rank ASC` là xong!
  - Khi click vào 1 người chơi trên bảng xếp hạng:
    - Nếu muốn giữ đúng tên lớp `PublicProfileActivity` như báo cáo của cậu: tạo `PublicProfileActivity` dùng lại luôn layout `R.layout.playerprofile` (ẩn dòng đổi mật khẩu & nút sửa bio như cách `PlayerProfileActivity` đang làm ở dòng 89–95).
    - Hoặc gọi thẳng `PlayerProfileActivity` với `EXTRA_IS_PUBLIC = true` và `EXTRA_TARGET_PLAYER_ID = selectedPlayerId`.
- **Các lớp Đức cần tạo thêm**:
  - **Android UI**: `RankingActivity` (`ranking.xml`), `PublicProfileActivity` — nhớ đăng ký trong `AndroidManifest.xml`.
  - **Android Controller**: `RankingClientController : ApiClient()` (`getPlayerStatistics(playerId)`, `getRanking()`).
  - **Server Controller & DAO**: `RankingServerController` + bổ sung `PlayerStatisticDAO.getRanking()`.

---

### Chức năng 3: Chơi với máy (`AIMatch` / Bot)
- **Điểm nối trên Giao diện Android**:
  1. Mở `android-client/.../ui/GameModeActivity.kt`, tìm nhánh `R.id.btnPlayAgainstAI` (dòng 49) -> thay `KoraNoticeDialog.show(...)` bằng `Intent` mở `SelectDifficultyActivity` (`BEGINNER`, `INTERMEDIATE`, `EXPERT`).
  2. Khi người chơi chọn độ khó trong `SelectDifficultyActivity` -> gọi `AIMatchClientController.createAIMatch(player.id, difficulty)` nhận về đối tượng `Match` (`mode = "AI"`) -> mở luôn **`MatchActivity` có sẵn** bằng:
     ```kotlin
     val intent = Intent(this, MatchActivity::class.java)
     intent.putExtra(MatchActivity.EXTRA_PLAYER, player)
     intent.putExtra(MatchActivity.EXTRA_MATCH, createdMatch)
     startActivity(intent)
     ```
  3. **Tái sử dụng 100% bàn cờ 17×17 của `MatchActivity` và `MatchResultDialog`**:
     - Trong `MatchActivity.kt`:
       - Tại `handleBoardCellClick()` (khoảng dòng 291): thêm nhánh `else if (match.mode.equals("AI", ignoreCase = true))` gọi `aiMatchClientController.makeMoveAndGetAIMove(newMove)`.
       - Tại `pollMatchStateFromServer()` (dòng 323): với `mode == "AI"` không cần poll liên tục từ đối thủ (hoặc gọi `getMatchState`), và tại `confirmStopPlaying()` thêm nhánh dừng trận AI.
       - Ở phần hiển thị tên đối thủ khi `opponentParticipant?.participantType == "AI"`: hiển thị tên theo `aiDifficulty` (ví dụ `"Beginner AI"`, `"Intermediate AI"`, `"Expert AI"`) và Rank là `"-"`.
     - Trong `MatchResultDialog.kt`: khi bấm `YES` tái đấu nhanh (`btnYesRematch`), nếu `match.mode.equals("AI", ignoreCase = true)` thì gọi `aiMatchClientController.quickRematch(match.id)` để tạo ván mới cùng độ khó AI!
- **Các lớp Đức cần tạo thêm**:
  - **Android UI**: `SelectDifficultyActivity` (`selectdifficulty.xml`) — đăng ký trong `AndroidManifest.xml`.
  - **Android Controller**: `AIMatchClientController : ApiClient()`.
  - **Server Controller & DAO**:
    - `AIMatchServerController` (`handleCreateAIMatch`, `handleMakeMoveAndAIMove`, `chooseAIMove`, `handleStopMatch`, `handleQuickRematch`).
    - Bổ sung `MatchDAO.createAIMatch(playerId, difficulty)` và cập nhật/thêm `quickRematch` cho chế độ `'AI'`.

---

### Đăng ký API Route của Đức trên Server (Không đụng vào file route của Hà)
1. Tạo file mới: `server/src/main/kotlin/com/caro/server/routes/DucRoutes.kt`:
   ```kotlin
   package com.caro.server.routes

   import io.ktor.server.routing.*

   fun Route.configureDucRoutes() {
       // Khai bao cac endpoint cho MatchHistory, Ranking, AIMatch tai day
       // Vi du:
       // get("/api/matches/history/{playerId}") { ... }
       // get("/api/ranking") { ... }
       // post("/api/ai-match/create") { ... }
   }
   ```
2. Mở `server/src/main/kotlin/com/caro/server/Application.kt`, bỏ dấu `//` ở dòng 111:
   ```kotlin
   configureHaRoutes()
   configureDucRoutes()
   ```

---

## 5. Cách chạy & test trên máy của Đức

### Bước 1: Clone code & đặt file `.env`
```powershell
git clone https://github.com/BestCaroInPTIT/CaroKora.git
cd CaroKora
```
Copy file `.env` Hà gửi vào đúng thư mục `CaroKora/.env`.

### Bước 2: Tạo nhánh riêng để code an toàn
```powershell
git checkout -b duc-modules
```

### Bước 3: Chạy thử Server ngay trên máy của Đức (khi đang code API mới)
```powershell
.\server\gradlew.bat -p server run
```
- Server sẽ chạy tại `http://localhost:8080` (kết nối thẳng tới DB Neon chung của nhóm).
- Khi test với **Android Emulator** và muốn gọi vào Server đang chạy trên máy cậu (trước khi push lên Render): ở góc trên bên phải màn hình **LOGIN** của App có nút **`SERVER`** -> bấm vào đổi thành:
  - Test máy local qua Emulator: `http://10.0.2.2:8080`
  - Chạy Server thật trên Cloud của nhóm: `https://carokora.onrender.com`

### Bước 4: Mở App Android
- Mở **Android Studio** -> **Open** -> chọn thư mục **`CaroKora/android-client`** (chú ý chọn đúng thư mục con `android-client`, không mở thư mục cha).
- Đợi Gradle Sync xong -> bấm **Run (`Shift + F10`)**.

### Bước 5: Đẩy code lên GitHub & tự động cập nhật Server Render
Khi đã test ngon lành trên nhánh `duc-modules`:
```powershell
git add .
git commit -m "Implement MatchHistory, Ranking, and AIMatch modules (Duc)"
git push -u origin duc-modules
```
Sau đó tạo **Pull Request** trên GitHub để merge `duc-modules` vào `main` (hoặc `git checkout main` -> `git pull` -> `git merge duc-modules` -> `git push`). Ngay khi code lên nhánh `main`, **Render sẽ tự động build Docker và cập nhật API mới lên `https://carokora.onrender.com`** sau khoảng 2–3 phút!
