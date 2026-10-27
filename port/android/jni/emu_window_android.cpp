#include "emu_window_android.h"
#include "centered_layout.h"

#include <algorithm>

#include <android/native_window.h>

#include "common/logging/log.h"

namespace Mh4uAndroid {

namespace {
// Azahar's Vulkan renderer asks its window for a context to find the driver in; none is needed
// when the system's libvulkan is the driver.
class NoContext final : public Frontend::GraphicsContext {};
} // namespace

EmuWindow::EmuWindow(ANativeWindow* surface, const std::string& style)
    : Frontend::EmuWindow(false), screen_style(style) {
    window_info.type = Frontend::WindowSystemType::Android;
    window_info.render_surface = surface;
    window_info.render_surface_scale = 1.0f;
    if (surface) {
        width = ANativeWindow_getWidth(surface);
        height = ANativeWindow_getHeight(surface);
        RefreshLayout();
    }
}

bool EmuWindow::OnSurfaceChanged(ANativeWindow* surface) {
    const int w = surface ? ANativeWindow_getWidth(surface) : 0;
    const int h = surface ? ANativeWindow_getHeight(surface) : 0;
    if (window_info.render_surface == surface && w == width && h == height) {
        return false;
    }
    width = w;
    height = h;
    window_info.type = Frontend::WindowSystemType::Android;
    window_info.render_surface = surface;
    RefreshLayout();
    return true;
}

void EmuWindow::RefreshLayout() {
    if (width > 0 && height > 0) {
        UpdateCurrentFramebufferLayout(static_cast<unsigned>(width), static_cast<unsigned>(height),
                                       height > width);
    }
}

void EmuWindow::UpdateCurrentFramebufferLayout(unsigned width, unsigned height, bool portrait) {
    if (width > 0 && height > 0) {
        if (second_screen_expanded) {
            // A temporary lower-screen view. Saved screen style and swap settings stay intact.
            NotifyFramebufferLayoutChanged(Layout::SingleFrameLayout(width, height, true, false));
            return;
        }
        if (!portrait && (screen_style == "centered-top" || screen_style == "centered-bottom")) {
            const bool swapped = Settings::values.swap_screen.GetValue();
            auto layout = Layout::SingleFrameLayout(width, height, swapped, false);
            const auto& main = swapped ? layout.bottom_screen : layout.top_screen;
            const auto& small = swapped ? layout.top_screen : layout.bottom_screen;
            const auto rects = CenteredLayout(width, height, main.GetWidth(), main.GetHeight(),
                                              small.GetWidth(), small.GetHeight(),
                                              screen_style == "centered-top");
            const Common::Rectangle<u32> main_rect{rects.main.left, rects.main.top,
                                                    rects.main.right, rects.main.bottom};
            const Common::Rectangle<u32> small_rect{rects.small.left, rects.small.top,
                                                     rects.small.right, rects.small.bottom};
            layout.top_screen = swapped ? small_rect : main_rect;
            layout.bottom_screen = swapped ? main_rect : small_rect;
            layout.top_screen_enabled = layout.bottom_screen_enabled = true;
            NotifyFramebufferLayoutChanged(layout);
            return;
        }
        Frontend::EmuWindow::UpdateCurrentFramebufferLayout(width, height, portrait);
    }
}

void EmuWindow::OnTouch(int x, int y, bool down) {
    if (down) {
        TouchPressed(static_cast<unsigned>(std::max(x, 0)), static_cast<unsigned>(std::max(y, 0)));
    } else {
        TouchReleased();
    }
}

void EmuWindow::OnTouchMoved(int x, int y) {
    TouchMoved(static_cast<unsigned>(std::max(x, 0)), static_cast<unsigned>(std::max(y, 0)));
}

std::unique_ptr<Frontend::GraphicsContext> EmuWindow::CreateSharedContext() const {
    return std::make_unique<NoContext>();
}

} // namespace Mh4uAndroid
