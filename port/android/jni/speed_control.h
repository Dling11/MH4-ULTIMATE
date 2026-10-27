#pragma once

#include <atomic>

namespace Mh4uAndroid {

// Session-only limits. Never accept zero (unlimited) or restore fast speed after a room.
class SpeedControl {
public:
    int Request(int percent, bool network_blocked) {
        const int accepted = !network_blocked && (percent == 125 || percent == 150) ? percent : 100;
        target.store(accepted);
        return accepted;
    }
    int Resolve(bool network_blocked) {
        if (network_blocked) Reset();
        return target.load();
    }
    int Target() const { return target.load(); }
    void Reset() { target.store(100); }

private:
    std::atomic<int> target{100};
};

} // namespace Mh4uAndroid
