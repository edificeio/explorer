#!/bin/bash

set -e

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

k6 run -e BASE_URL=https://localhost:8090 \
    -e USER_WEB_LOGIN=LOGIN \
    -e USER_WEB_PWD=PASSWORD \
    -e BLOG_NUMBER=10000 \
    -e VUS_COUNT=1 \
    $SCRIPT_DIR/../src/test/js/stresstest/scenarios/read/index.js
