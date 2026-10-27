#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>

namespace Mh4uAndroid {

struct ScreenRect {
    std::uint32_t left{}, top{}, right{}, bottom{};
    std::uint32_t Width() const { return right - left; }
    std::uint32_t Height() const { return bottom - top; }
};

struct CenteredScreens {
    ScreenRect main;
    ScreenRect small;
};

// Equal side margins for controls. The second screen fits in the right margin,
// outside the main picture; both screens keep their own aspect ratios.
inline CenteredScreens CenteredLayout(std::uint32_t width, std::uint32_t height,
                                     std::uint32_t main_width, std::uint32_t main_height,
                                     std::uint32_t small_width, std::uint32_t small_height,
                                     bool small_at_top) {
    if (!width || !height || !main_width || !main_height || !small_width || !small_height)
        return {};
    const double scale = std::min(width * .8 / main_width, height * .8 / main_height);
    const auto w = std::max(1L, std::lround(main_width * scale));
    const auto h = std::max(1L, std::lround(main_height * scale));
    const auto x = (width - w) / 2;
    const auto y = (height - h) / 2;
    const auto padding = std::max(1U, std::min(width, height) / 100);
    const double available = std::max(1.0, static_cast<double>(width - x - w) - 2 * padding);
    const double small_scale = std::min(height * .25 / small_height, available / small_width);
    const auto sw = std::max(1L, std::lround(small_width * small_scale));
    const auto sh = std::max(1L, std::lround(small_height * small_scale));
    const auto sx = width - padding - sw;
    const auto sy = small_at_top ? padding : height - padding - sh;
    return {{static_cast<std::uint32_t>(x), static_cast<std::uint32_t>(y),
             static_cast<std::uint32_t>(x + w), static_cast<std::uint32_t>(y + h)},
            {static_cast<std::uint32_t>(sx), static_cast<std::uint32_t>(sy),
             static_cast<std::uint32_t>(sx + sw), static_cast<std::uint32_t>(sy + sh)}};
}

} // namespace Mh4uAndroid
