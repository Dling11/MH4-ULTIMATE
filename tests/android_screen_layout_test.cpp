#include "../port/android/jni/centered_layout.h"

#include <cassert>
#include <cmath>
#include <iostream>

int main() {
    unsigned cases = 0;
    for (const unsigned width : {640U, 800U, 960U, 1280U, 1920U, 2400U, 2560U}) {
        for (const unsigned height : {360U, 540U, 720U, 1080U, 1440U}) {
            if (width < height) continue;
            for (const unsigned top_width : {320U, 400U, 427U}) {
                for (const bool at_top : {true, false}) {
                    const auto rects = Mh4uAndroid::CenteredLayout(width, height, top_width, 240, 320, 240, at_top);
                    const auto& main = rects.main;
                    const auto& small = rects.small;
                    assert(main.Width() > 0 && main.Height() > 0);
                    assert(small.Width() > 0 && small.Height() > 0);
                    assert(main.right < width && main.bottom < height);
                    assert(small.right <= width && small.bottom <= height);
                    assert(std::abs(static_cast<int>(main.left) - static_cast<int>(width - main.right)) <= 1);
                    assert(std::abs(static_cast<int>(main.top) - static_cast<int>(height - main.bottom)) <= 1);
                    assert(small.left > main.right);
                    assert(std::abs(main.Width() * 240.0 / top_width - main.Height()) <= 1.0);
                    assert(std::abs(small.Width() * .75 - small.Height()) <= 1.0);
                    assert(at_top ? small.top < height / 2 : small.top > height / 2);
                    ++cases;
                }
            }
        }
    }
    assert(Mh4uAndroid::CenteredLayout(0, 1080, 400, 240, 320, 240, true).main.Width() == 0);
    std::cout << cases << " centered-screen geometry cases passed.\n";
}
