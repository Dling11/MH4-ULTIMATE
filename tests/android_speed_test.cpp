#include "../port/android/jni/speed_control.h"

#include <cassert>
#include <iostream>

int main() {
    Mh4uAndroid::SpeedControl speed;
    assert(speed.Target() == 100);
    assert(speed.Request(125, false) == 125);
    assert(speed.Resolve(false) == 125);
    assert(speed.Request(150, false) == 150);
    assert(speed.Resolve(true) == 100);
    assert(speed.Resolve(false) == 100); // Leaving a room must not restore acceleration.
    assert(speed.Request(150, true) == 100);
    for (int request = -100; request <= 1000; ++request) {
        const int result = speed.Request(request, false);
        assert(result == 100 || result == 125 || result == 150);
        if (request != 125 && request != 150) assert(result == 100);
        assert(speed.Resolve(true) == 100);
    }
    speed.Request(150, false);
    speed.Reset();
    assert(speed.Target() == 100);
    std::cout << "Speed defaults, bounded requests, network reset and session reset passed.\n";
}
