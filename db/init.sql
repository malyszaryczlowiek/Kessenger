-- CREATE DATABASE with user information
CREATE TABLE clients (
  user_id uuid DEFAULT gen_random_uuid () PRIMARY KEY,
  second_name varchar(255) NOT NULL,
  first_name varchar(255) NOT NULL
  -- Login varchar(255) NOT NULL,  -- to add in future
  -- Passwd varchar(255) NOT NULL  -- to add in future
);

-- add to db to clients
INSERT INTO clients (second_name, first_name) VALUES ( 'Kowalski', 'Jan');
INSERT INTO clients (second_name, first_name) VALUES ( 'Nowakowski', 'Lucjan');


-- to check execution open bash in container
-- docker exec -it ClientDB /bin/bash

-- next login to postgres
-- psql -v ON_ERROR_STOP=1 --username admin --dbname clients_data

-- check if table exists
-- \dt

-- check content of clients table
-- SELECT * FROM clients;
