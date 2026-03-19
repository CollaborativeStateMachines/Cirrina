FROM gradle:9.3.0-jdk25 AS build

RUN apt-get update && apt-get install -y protobuf-compiler unzip

COPY --chown=gradle:gradle . /usr/src/cirrina

WORKDIR /usr/src/cirrina

RUN gradle distZip --no-daemon

RUN unzip build/distributions/cirrina.zip -d /tmp

FROM gcr.io/distroless/java25-debian13 AS runtime

COPY --from=build /tmp/cirrina /opt/cirrina

ENTRYPOINT [ \
    "java", \
    "-XX:+UseZGC", \
    "-XX:+AlwaysPreTouch", \
    "-Xms4G", \
    "-Xmx4G", \
    "--enable-native-access=ALL-UNNAMED", \
    "--sun-misc-unsafe-memory-access=allow", \
    "-cp", "/opt/cirrina/lib/*", \
    "at.ac.uibk.dps.cirrina.CirrinaKt" \
]