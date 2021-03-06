-- CREATE DATABASE with user information
CREATE TABLE IF NOT EXISTS users (
  user_id uuid DEFAULT gen_random_uuid () UNIQUE,
  login varchar(255) UNIQUE,
  salt varchar(255) NOT NULL,
  pass varchar(255) NOT NULL,
  joining_offset BIGINT DEFAULT -1 NOT NULL, -- if user has set to -1 this means that he has not running joining topic in kafka broker
  PRIMARY KEY (user_id, login)
);

-- CREATE TABLE IF NOT EXISTS

CREATE TABLE IF NOT EXISTS chats (
  chat_id varchar(255) REFERENCES chats(chat_id) PRIMARY KEY,
  chat_name varchar(255) NOT NULL,
  group_chat BOOLEAN NOT NULL
  -- accepted BOOLEAN NOT NULL
);


-- connecting users and chats, no duplicate pairs possible
CREATE TABLE users_chats (
  chat_id varchar(255) REFERENCES chats(chat_id) ON DELETE CASCADE,
  user_id uuid REFERENCES users(user_id) ON DELETE CASCADE,
  users_offset BIGINT DEFAULT 0 NOT NULL, --ON DELETE CASCADE,
  message_time BIGINT DEFAULT 0 NOT NULL,
  PRIMARY KEY (chat_id, user_id)
);

-- add two users to db only for some tests
INSERT INTO users (login, salt, pass) VALUES ( 'Walo'   , '$2a$10$8K1p/a0dL1LXMIgoEDFrwO', '$2a$10$8K1p/a0dL1LXMIgoEDFrwO2L7cYK91Q7Ui9I4HeoAHUf46pq8IdFK'); -- aaa
INSERT INTO users (login, salt, pass) VALUES ( 'Spejson', '$2a$10$8K1p/a0dL1LXMIgoEDFrwO', '$2a$10$8K1p/a0dL1LXMIgoEDFrwOra5VEq4VeXudMZmp9DH9OnhYQ6iDV1e'); -- bbb




-- working with postgres using docker and terminal

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