#!/bin/bash

set -e

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

k6 run -e BASE_URL=http://localhost:8090 \
    -e USER_WEB_LOGIN=LOGIN \
    -e USER_WEB_PWD=PASS \
    -e BLOG_NUMBER=1000 \
    -e SLEEP_SECONDS=2 \
    $SCRIPT_DIR/../src/test/js/stresstest/scenarios/crud/index.js
