#!/bin/bash

./mvnw versions:set -DnewVersion=1.0.3-dev
./mvnw clean
./mvnw install
./mvnw versions:revert