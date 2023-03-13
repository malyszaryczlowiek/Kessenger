# Kessenger

## What the project is

Project is simple, scalable chat application built with [Kafka](https://kafka.apache.org/), [Play](https://www.playframework.com/),
[Angular](https://angular.io/), [Spark](https://spark.apache.org/), [PostgreSQL](https://www.postgresql.org/) 
and [pgAdmin](https://www.pgadmin.org/). Whole system is containerized with [Docker](https://www.docker.com/).


## Project Architecture

![System Architecture](architecture.jpeg)

## Before running project 

To build and run this project you need installed:

- [Java JDK 11](https://adoptopenjdk.net/) at leased. 
- [SBT](https://www.scala-sbt.org/).
- [Docker](https://www.docker.com/).

 
## Running project

Simply open terminal/console and go to project folder. Then run starting script:

```bash
./runProd
```

Wait to building will stop, and then open new browser window or tab and go to `localhost:4200`. 

> **Note!** <br>
> First call after building containers take some time. 

Then open another browser and go to `localhost:4200` too. In both browsers create two different users and then create chat between them (group chats are possible between more than two users).