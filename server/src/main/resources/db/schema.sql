-- =====================================================================
-- CSDL HE THONG GAME CO CARO (CHUONG 3 - THIET KE CO SO DU LIEU)
-- Nen tang: Neon Lakebase PostgreSQL
-- Luu y: Bang Match KHONG luu cot boardState (thuoc tinh dan xuat trong GameBoard)
-- =====================================================================

CREATE TABLE IF NOT EXISTS Player (
    id SERIAL PRIMARY KEY,
    gameID VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    fullName VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    avatar VARCHAR(255),
    bio VARCHAR(255),
    onlineStatus VARCHAR(255) NOT NULL DEFAULT 'OFFLINE'
);

CREATE TABLE IF NOT EXISTS Match (
    id SERIAL PRIMARY KEY,
    mode VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    startedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    endedAt TIMESTAMP,
    currentTurn VARCHAR(255) NOT NULL DEFAULT 'X',
    turnTimeLimit INTEGER NOT NULL DEFAULT 15,
    boardSize VARCHAR(255) NOT NULL DEFAULT '17x17'
);

CREATE TABLE IF NOT EXISTS MatchParticipant (
    id SERIAL PRIMARY KEY,
    participantType VARCHAR(255) NOT NULL,
    side VARCHAR(255) NOT NULL,
    aiDifficulty VARCHAR(255),
    outcome VARCHAR(255),
    score DOUBLE PRECISION,
    Playerid INTEGER REFERENCES Player(id) ON DELETE SET NULL,
    Matchid INTEGER NOT NULL REFERENCES Match(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Move (
    id SERIAL PRIMARY KEY,
    "row" VARCHAR(255) NOT NULL,
    "column" INTEGER NOT NULL,
    moveOrder INTEGER NOT NULL,
    createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MatchParticipantid INTEGER NOT NULL REFERENCES MatchParticipant(id) ON DELETE CASCADE,
    Matchid INTEGER NOT NULL REFERENCES Match(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_matchparticipant_playerid ON MatchParticipant(Playerid);
CREATE INDEX IF NOT EXISTS idx_matchparticipant_matchid ON MatchParticipant(Matchid);
CREATE INDEX IF NOT EXISTS idx_move_matchid ON Move(Matchid, moveOrder);

-- Du lieu nguoi choi mau phuc vu kiem thu va demo
INSERT INTO Player (gameID, password, fullName, email, avatar, bio, onlineStatus)
VALUES
    ('ha88', '123456', 'Pham Thi Thien Ha', 'ha88@ptit.edu.vn', 'avatar_1', 'Top Caro PTIT', 'OFFLINE'),
    ('Bn', '123456', 'Phan B', 'phanb@gmail.com', 'avatar_2', 'Top 11', 'OFFLINE'),
    ('A', '123456', 'Nguyen Van A', 'nguyenvana@gmail.com', 'avatar_3', 'Top 11', 'OFFLINE'),
    ('ds3', '123456', 'Tran Minh Duc', 'ds3@ptit.edu.vn', 'avatar_4', 'Ready to challenge', 'OFFLINE')
ON CONFLICT (gameID) DO NOTHING;
