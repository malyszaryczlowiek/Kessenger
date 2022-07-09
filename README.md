# Kessenger

## What the project is

Project is simple CLI chat application build with Scala, Kafka, Spark, PostgreSQL, pgAdmin and Docker. 

## Project Architecture

TODO

## Before running project 

To build and run this project you need installed:

- [Java JDK 11](https://adoptopenjdk.net/) at leased. 
- [SBT](https://www.scala-sbt.org/).
- [Docker](https://www.docker.com/).
 

## How to run project

At first download the project, unpack, start Docker desktop and go to the Kessenger folder: <br>

```bash
 cd Kessenger
```

make scripts executable<br>

```
 chmod +x makeExecutable
```

and finally start project running `startProject` script:<br>

```bash
 ./startProject
```

This script builds all required docker containers, compile all source files and build executable JAR file of CLI client app. 

## How to use project

To play with project start ClientApp by opening new terminal/console window, go to Kessenger folder and run ClientApp with script `runClient`.

```bash
 ./runClient
```

Sign in with one of two predefined users (User1, User2) or create another one following next steps in program. 

