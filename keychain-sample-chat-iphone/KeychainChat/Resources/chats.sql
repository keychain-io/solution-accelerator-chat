DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS chats;
DROP TABLE IF EXISTS messages;

CREATE TABLE users (
    id TEXT PRIMARY KEY,
    firstName TEXT NOT NULL,
    lastName TEXT NOT NULL,
    status INTEGER NOT NULL DEFAULT 0,
    photo TEXT,
    uri TEXT UNIQUE
);

CREATE TABLE chats (
    id TEXT PRIMARY KEY,
    participantIds TEXT,
    lastMsg TEXT,
    timestamp TEXT NOT NULL
);

CREATE TABLE messages (
    id TEXT PRIMARY KEY,
    chatId TEXT,
    sendOrRcvd STRING NOT NULL,
    senderId TEXT,
    receiverId TEXT,
    imageUrl TEXT,
    msg TEXT,
    timestamp TEXT NOT NULL,
    FOREIGN KEY (senderId)
      REFERENCES users (id)
         ON DELETE CASCADE
         ON UPDATE NO ACTION,
    FOREIGN KEY (chatId)
      REFERENCES chats (id)
         ON DELETE CASCADE
         ON UPDATE NO ACTION
);

CREATE UNIQUE INDEX IF NOT EXISTS CHAT_PARTICIPANTS ON chats (
    participantIds
);
