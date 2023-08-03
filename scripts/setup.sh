#!/usr/bin/env bash

set -eu

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

rm -rf "${SCRIPT_DIR}/../libs"
mkdir -p "${SCRIPT_DIR}/../libs"

pushd "${SCRIPT_DIR}/../libs"
wget "https://switchboard-sdk-android.s3.amazonaws.com/develop/SwitchboardSDK.aar"
wget "https://switchboard-sdk-android.s3.amazonaws.com/develop/SwitchboardSuperpowered.aar"
popd