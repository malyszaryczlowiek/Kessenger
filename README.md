# Kessenger

## What the project is

Project is simple CLI chat application build in Scala with Kafka, Kafka Streams, Spark, PostgreSQL, pgAdmin and contenerized with Docker. 

## Project Architecture

TODO

## Before running project 

To build and run this project you need installed:

- [Java JDK 11](https://adoptopenjdk.net/) at leased. 
- [SBT](https://www.scala-sbt.org/).
- [Docker](https://www.docker.com/).
 

## How to run project

At first download the project, unpack, start Docker desktop, open new terminal/console window and go to the Kessenger folder: <br>

```bash
 cd Kessenger
```

make scripts executable and run it.<br>

```bash
 chmod +x makeExecutable
 ./makeExecutable
```

And finally start project running `startProject` script:<br>

```bash
 ./startProject
```

This script builds all required docker images, starts docker containers, compile all source files and build executable JAR files (some of them are required by docker images). 

## How to use project

To play with project start ClientApp by opening new terminal/console window, go to Kessenger folder and run *ClientApp* with script `runClient`: 

```bash
 ./runClient
```

which runs JAR file with *ClientApp* application.

Sign in with one of two predefined users (Walo <password 'aaa'>, Spejson <password 'bbb'>) or create another one following proper steps in program. 

## How to close project

If you want to stop playing with project close all running *ClientApp*s and then close and remove all docker containers in project by running `stopProject` script with `--all` option:

```bash
 ./stopProject --all
```
