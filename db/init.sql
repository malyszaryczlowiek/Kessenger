-- CREATE DATABASE with user information
CREATE TABLE users (
  -- user_id uuid DEFAULT gen_random_uuid () PRIMARY KEY,
  user_id uuid PRIMARY KEY,
  login varchar(255) NOT NULL,
  pass  varchar(255) NOT NULL
  -- second_name varchar(255) NOT NULL,
  -- first_name varchar(255) NOT NULL
);

CREATE TABLE chats (
  chat_id varchar(255) PRIMARY KEY,
  chat_name varchar(255) NOT NULL
);


-- connecting users and chats, no duplicate possible
CREATE TABLE users_chats (
  chat_id varchar(255) REFERENCES chats(chat_id),
  user_id uuid REFERENCES users(user_id),
  CONSTRAINT user_chat PRIMARY KEY(chat_id, user_id)
);

-- add to db to clients
INSERT INTO users (user_id, login, pass) VALUES ( '9039d5ad-99d5-47af-a9ae-a8afee0bf2e8', 'Kowalski', 'aaa');
INSERT INTO users (user_id, login, pass) VALUES ( '197cc871-2da6-4f76-9ead-6c45f0020768', 'Nowakowski', 'bbb');




-- working with postgres using docker and terminal

-- to check execution open bash in container
-- docker exec -it KessengerDB /bin/bash

-- next login to postgres
-- psql -v ON_ERROR_STOP=1 --username admin --dbname kessenger_schema

-- check if table exists
-- \dt

-- check content of clients table
-- SELECT * FROM clients;


-- to exit from psql type exit
-- to close container type exit