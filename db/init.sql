-- CREATE DATABASE with user information
CREATE TABLE IF NOT EXISTS users (
  user_id uuid DEFAULT gen_random_uuid () UNIQUE,
  login varchar(255) UNIQUE,
  pass  varchar(255) NOT NULL,
  PRIMARY KEY (user_id, login)
);



-- CREATE TABLE IF NOT EXISTS
CREATE TABLE IF NOT EXISTS chats (
  chat_id varchar(255) PRIMARY KEY,
  group_chat BOOLEAN NOT NULL
--  chat_topic_exists    boolean DEFAULT false NOT NULL,
--  writing_topic_exists boolean DEFAULT false NOT NULL
);



-- connecting users and chats, no duplicate pairs possible
CREATE TABLE IF NOT EXISTS users_chats (
  user_id        uuid         REFERENCES users(user_id) ON DELETE CASCADE,
  chat_id        varchar(255) REFERENCES chats(chat_id) ON DELETE CASCADE,
  chat_name      varchar(255) NOT NULL,
  message_time   BIGINT  DEFAULT 0 NOT NULL,
  silent         boolean DEFAULT false NOT NULL,
  users_offset_0 BIGINT  DEFAULT 0 NOT NULL,
  users_offset_1 BIGINT  DEFAULT 0 NOT NULL,
  users_offset_2 BIGINT  DEFAULT 0 NOT NULL,
  PRIMARY KEY (chat_id, user_id)
);



CREATE TABLE IF NOT EXISTS sessions (
  session_id uuid UNIQUE,
  user_id uuid REFERENCES users(user_id) ON DELETE CASCADE,
  validity_time BIGINT DEFAULT 0 NOT NULL,
  PRIMARY KEY (session_id)
);



CREATE TABLE IF NOT EXISTS settings (
  user_id uuid REFERENCES users(user_id) ON DELETE CASCADE,  --UNIQUE,
  joining_offset   BIGINT       DEFAULT 0      NOT NULL,
  session_duration BIGINT       DEFAULT 900000 NOT NULL,
  zone_id          varchar(255) DEFAULT 'UTC'  NOT NULL,
  PRIMARY KEY (user_id)
);



CREATE TABLE IF NOT EXISTS logging_attempts (
  user_id uuid REFERENCES users(user_id) ON DELETE CASCADE,
  from_ip varchar(255) NOT NULL, -- https://www.postgresql.org/docs/current/datatype-net-types.html
  num_unsuccessful_attempts int,
  last_attempt BIGINT
);



-- add two users to db only for some tests
-- in db we store truncated hashed password
INSERT INTO users (user_id, login, pass) VALUES ( 'c8b8c9e6-8cb5-4e5c-86b3-84f55f012172', 'Walo',    '5gK1Ve3u3CosziY2B6ZUi8bffjEigTe'); -- password Password1!  salt: $2a$10$8K1p/a0dL1LXMIgoEDFrwO
INSERT INTO users (user_id, login, pass) VALUES ( '7246bdb7-d4af-4195-a011-d82b13845580', 'Spejson', 'R0fLJZOJj.79gepc.MnAVSlFpq6cY16'); -- password Password2!
INSERT INTO settings (user_id, zone_id) VALUES  ( 'c8b8c9e6-8cb5-4e5c-86b3-84f55f012172', 'Europe/Warsaw');
INSERT INTO settings (user_id, zone_id) VALUES  ( '7246bdb7-d4af-4195-a011-d82b13845580', 'Europe/Warsaw');

-- salt $2a$10$8K1p/a0dL1LXMIgoEDFrwO


-- to check execution open bash in container
-- docker exec -it KessengerDB /bin/bash

-- next login to postgres
-- psql -v ON_ERROR_STOP=1 --username admin --dbname kessenger_schema

-- check if table exists
-- \dt

-- check content of clients table
-- SELECT * FROM users;


-- to exit from psql type exit
-- to close container type exit