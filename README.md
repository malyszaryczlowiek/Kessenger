# Kessenger

## What the project is

Project is simple, horizontally-scalable chat application built with [Kafka](https://kafka.apache.org/), [Play](https://www.playframework.com/), [Angular](https://angular.io/),
[Spark](https://spark.apache.org/) ([Streaming](https://spark.apache.org/streaming/), [GraphX](https://spark.apache.org/graphx/)), [PostgreSQL](https://www.postgresql.org/) 
and [pgAdmin](https://www.pgadmin.org/). Whole system is containerized with [Docker](https://www.docker.com/).


## Project Architecture

![System Architecture](architecture.jpeg)

## Before Project Running 

To build and run this project you need installed:

- [Java JDK 11](https://adoptopenjdk.net/) at leased. 
- [SBT](https://www.scala-sbt.org/).
- [Docker](https://www.docker.com/).

 
## Running Project

Simply open terminal/console, go to project folder and then make scripts executable (Mac):

```zsh
chmod +x kessengerZsh
```
or
```bash
chmod +x kessengerBash
```
in Linux systems with bash shell.

Be sure that docker is running and run building script:

```zsh
./kessengerZsh
```


Wait building process will end and then open new browser window or tab and go to `localhost:4200`. 

> **Note!** <br>
> First build will take some time (several minutes).

Then open *another browser* and go to `localhost:4200` too. In both browsers create two different users and then create 
chat between them.

## Spark Analysers
**Spark-streaming-analyser** and **Spark-graphx-analyser** are [driver apps](https://spark.apache.org/docs/latest/cluster-overview.html) 
running within spark cluster. **Spark-streaming-analyser** is simple stream analyser which poll data from kafka brokers, 
analyse and save calculated results back (in real time) to specific topic in kafka brokers. **Spark-graphx-analyser** 
is app taking users data from database and calculating page rank of each user based on number of users chat number. 
Calculated result is saved back to database. 

### Running Analysers
**Spark-streaming-analyser** and **Spark-graphx-analyser** do not start with system right away. They need data to 
operate, so it is required to run them when some data are generated. Both applications are runnable with scripts:

```zsh
./runSparkStreamingAnalyserZsh
./runGraphAnalyserZsh
```

These scripts build docker images and create containers which are thereafter connected to existing inner docker network. 


## System State Monitoring
System allows monitoring database and spark cluster states.


### Database state
Open new browser tab and go to `localhost:5050`. Sign in with defined credentials and configure database connection. 
Then you can for example check tables content, currently running queries and manually modify database. 



### Spark Cluster state
Open new browser tab and go to `localhost:8082`. Here you can find information of all submitted, running and
finished spark application as well Spark Workers condition.


## Shut Down and Cleaning System
If you run system with `./kessengerZsh` script, you can shut down whole system using command below

```zsh
./stopkessengerZsh
```

This script stops and removes all containers, and then removes built docker images, so no disk space is wasted for 
keeping unused docker stuff.   