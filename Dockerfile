# Base image
FROM openjdk:11-jdk

RUN apt-get update && apt-get upgrade -y
RUN apt-get install tzdata ssh openssh-server vim wget sudo  -y

RUN echo 'root:cintix' | chpasswd

ENV TZ=Europe/Copenhagen
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

ENV APP_HOME=/opt/lib/
RUN mkdir -p $APP_HOME

WORKDIR $APP_HOME

COPY lib/. $APP_HOME/
COPY dist/. $APP_HOME/


RUN useradd -rm -d /home/docker -s /bin/bash -g root -G sudo -u 1001 docker
USER docker
WORKDIR /home/docker

EXPOSE 8080
EXPOSE 80
EXPOSE 22
