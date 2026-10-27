#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p .local/checks
c++ -std=c++17 -Wall -Wextra -Werror -pedantic tests/android_screen_layout_test.cpp -o .local/checks/android-screen-layout
.local/checks/android-screen-layout
c++ -std=c++17 -Wall -Wextra -Werror -pedantic tests/android_speed_test.cpp -o .local/checks/android-speed
.local/checks/android-speed
