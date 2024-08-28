#!/usr/bin/env bash

set -eu

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
SDK_VERSION="2.1.0"

rm -rf "${SCRIPT_DIR}/../libs"
mkdir -p "${SCRIPT_DIR}/../libs"

pushd "${SCRIPT_DIR}/../libs"
wget "https://switchboard-sdk-public.s3.amazonaws.com/builds/release/${SDK_VERSION}/android/SwitchboardSDK.aar"
wget "https://switchboard-sdk-public.s3.amazonaws.com/builds/release/${SDK_VERSION}/android/SwitchboardSuperpowered.aar"
popd
