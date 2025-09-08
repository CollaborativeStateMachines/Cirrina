# Stage 1: Build Stage
FROM gradle:8.7.0-jdk21-alpine AS build

ARG GRADLE_OPTS

# Install protoc
RUN apk add protoc

# Copy application source code into the container
COPY --chown=gradle:gradle . /usr/src/cirrina

# Set working directory
WORKDIR /usr/src/cirrina

# Build the application distribution ZIP
RUN gradle distZip

# Stage 2: Runtime Stage
FROM openjdk:21-bookworm

# Copy the application distribution ZIP from the build stage
COPY --from=build /usr/src/cirrina/build/distributions/cirrina.zip /tmp/cirrina.zip

# Unzip the application distribution to /usr/bin
RUN unzip /tmp/cirrina.zip -d /usr/bin \
    && chmod +x /usr/bin/cirrina/bin/cirrina

# Set the working directory for the application
WORKDIR /usr/bin/cirrina

# Use shell form ENTRYPOINT to execute the application
ENTRYPOINT ["/usr/bin/cirrina/bin/cirrina"]