#!/bin/bash

set -eu

PROJECT_DIR="$(git rev-parse --show-toplevel)"

pushd "${PROJECT_DIR}"
./gradlew build
popd