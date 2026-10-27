// The game's window on Android: a Surface the Kotlin side hands over, drawn on by Azahar's
// Vulkan renderer. Touches on the touch screen come in as window pixels. (Modelled on Azahar's
// own Android window; one window, Vulkan only, the system's Vulkan driver.)
#pragma once

#include <memory>
#include <atomic>
#include <string>

#include "core/frontend/emu_window.h"

struct ANativeWindow;

namespace Mh4uAndroid {

class EmuWindow final : public Frontend::EmuWindow {
public:
    explicit EmuWindow(ANativeWindow* surface, const std::string& screen_style);
    ~EmuWindow() override = default;

    /// The surface changed (rotation, the app came back). False if nothing differs.
    bool OnSurfaceChanged(ANativeWindow* surface);

    /// Layout settings can change while the surface and its size remain identical.
    void RefreshLayout();
    void SetScreenStyle(const std::string& style) { screen_style = style; }
    void SetSecondScreenExpanded(bool expanded) { second_screen_expanded = expanded; }
    void UpdateCurrentFramebufferLayout(unsigned width, unsigned height,
                                        bool is_portrait_mode = false) override;

    void OnTouch(int x, int y, bool down);
    void OnTouchMoved(int x, int y);

    void PollEvents() override {}
    void MakeCurrent() override {}
    void DoneCurrent() override {}
    std::unique_ptr<GraphicsContext> CreateSharedContext() const override;

    int Width() const { return width; }
    int Height() const { return height; }

private:
    int width = 0;
    int height = 0;
    std::string screen_style;
    std::atomic<bool> second_screen_expanded{false};
};

} // namespace Mh4uAndroid
