# Build with: docker build --build-arg version=tags/v0.0.1 -t jnidzwetzki/bboxdb:0.0.1 - < Dockerfile
# To build the most recent version, use:
# docker build --build-arg version=master -t jnidzwetzki/bboxdb:latest --no-cache - < Dockerfile

FROM alpine/git as clone
ARG version
RUN git clone https://github.com/jnidzwetzki/bboxdb.git /bboxdb
WORKDIR /bboxdb
RUN git checkout ${version}

FROM maven:3.5-jdk-8-alpine as build
WORKDIR /bboxdb
COPY --from=clone /bboxdb /bboxdb
RUN mvn install -DskipTests

FROM debian:stretch-slim
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections
WORKDIR /bboxdb
COPY --from=build /bboxdb /bboxdb
ENTRYPOINT ["sh", "-c"]
ENV BBOXDB_HOME=/bboxdb
ENV BBOXDB_FOREGROUND=true

# BBoxDB database port
EXPOSE 50505/tcp

# Performance counter (prometheus)
EXPOSE 10085/tcp

CMD ["/bboxdb/bin"]
