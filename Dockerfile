FROM gradle:9.3.0-jdk25-alpine AS build

ARG GRADLE_OPTS

RUN apk add --no-cache protoc unzip

COPY --chown=gradle:gradle . /usr/src/cirrina
WORKDIR /usr/src/cirrina

RUN gradle distZip

# Unpack distribution and normalize path
RUN unzip build/distributions/cirrina.zip -d /tmp \
    && chmod +x /tmp/cirrina/bin/cirrina

FROM gcr.io/distroless/java21-debian12 AS runtime

COPY --from=build /tmp/cirrina /opt/cirrina

ENTRYPOINT ["java", "-cp", "/opt/cirrina/lib/*", "at.ac.uibk.dps.cirrina.cirrina.CirrinaKt"]