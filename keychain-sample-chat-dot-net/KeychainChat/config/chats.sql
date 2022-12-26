CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    firstName TEXT NOT NULL,
    lastName TEXT NOT NULL,
    status INTEGER NOT NULL DEFAULT 0,
    photo TEXT,
    uri TEXT UNIQUE
);

CREATE TABLE IF NOT EXISTS chats (
    id TEXT PRIMARY KEY,
    participantIds TEXT,
    lastMsg TEXT,
    timestamp STRING NOT NULL
);

CREATE TABLE IF NOT EXISTS messages (
    id TEXT PRIMARY KEY,
    chatId TEXT,
    sendOrRcvd STRING NOT NULL,
    senderId TEXT,
    receiverId TEXT,
    imageUrl TEXT,
    msg TEXT,
    timestamp STRING NOT NULL,
    FOREIGN KEY (senderId)
      REFERENCES users (id)
         ON DELETE CASCADE
         ON UPDATE NO ACTION,
    FOREIGN KEY (chatId)
      REFERENCES chats (id)
         ON DELETE CASCADE
         ON UPDATE NO ACTION
);
