# Monster Hunter 4 - Rowell

A custom Android app for Monster Hunter 4 Ultimate, with a simpler launcher, organized settings and a movable touch-control editor. Built on the MH4U Android project and Azahar core; see [credits](CREDITS.md).

## Source code only

This repository currently shares the source code. APK and Android ZIP downloads are not published here yet. The 1.0.6 changes are prepared locally; phone testing is pending.

Android 10+, ARM64 and Vulkan are required. The game is supplied separately and is not included in this repository.

## Install and play

1. Build the APK from source using `scripts/build-android.ps1`, then install your local build. For updates, use the same signing key and install over the existing Rowell app to keep its data.
2. Open **Monster Hunter 4 - Rowell**, tap **Add game**, and select your decrypted `.3ds`, `.cci` or `.cxi` file. Wait for copying to finish.
3. Tap **Play game**. The first launch creates this app's own save data.

Normal emulation needs only your game. The optional recompiled library and USA v1.3.0 update are under **Settings → Advanced**. No game files are included.

MH4U uses two 3DS screens. The small screen in the upper right contains touch controls and menu choices. For larger text, choose **Menu → Display & performance → Screen layout → Side by side**. **Main screen only** hides these choices.

Screen layouts save automatically. During play, choose a layout and tap **Resume** above the settings; no game restart is needed. Existing player choices take priority over the default.

| Layout | Appearance |
| --- | --- |
| Game + small touch screen | The existing default with a small corner screen. |
| Centered · small screen top | Centered game, equal side margins, small screen at the top right. |
| Centered · small screen bottom | Same centered game, small screen at the bottom right. |
| Top & bottom screens | Both screens stacked like a 3DS. |

Menu stays inside the usable screen, clear of Android bars and the camera cutout, even when touch buttons are hidden. Android Back also opens Game menu.

The game always uses immersive fullscreen and the camera cutout area where Android permits it. Android's bars appear temporarily with an edge swipe. There are no fullscreen or camera-area switches to change accidentally. Menu stays clear of the physical camera; the camera may cover a small part of the picture. Black margins that preserve the game's proportions remain.

## Solo speed button

Tap the **1×** touch button to cycle **1× → 1.25× → 1.5× → 1×**. Move or hide it in the button editor, or use **Menu → Display & performance → Solo game speed**. It is available throughout offline play, including quests. Targets need spare device performance; return to 1× if play or sound becomes uneven.

Speed starts at 1× each game session, resets to 1× when leaving the app, and is forced to 1× while joining, hosting or playing multiplayer. It stays at 1× after leaving multiplayer until you choose a faster rate again.

The application ID is `com.rowell.mh4u`, separate from the original app and MH Tri - Rowell. Updating with the same signing key preserves this app's data; uninstalling removes its private files.

## Saves and control layouts

Save inside MH4U, exit the game, then open **Settings → Saves**. These tools copy the game's saved progress and extra data; they do not capture a live game session. They support this app's MH4U USA save files.

- **Create backup:** keeps the newest three verified backups, with local date, time, seconds and UTC offset. Unchanged saves reuse the latest copy.
- **Restore this backup:** choose a dated backup and confirm. Current saved progress is backed up first. Tap Play game and load your character afterward.
- **Export save:** choose where to save a portable ZIP. Keep a copy outside the app before uninstalling.
- **Import save ZIP:** select a Rowell save ZIP, review its date and confirm before restoring. Game files, updates, graphics settings and controls stay separate.

Under **Touch controls → Saved control layouts**, save named copies such as “My default” or “Great Sword.” Each copy includes button positions, combos, colours and vibration. Loading, deleting and restoring defaults ask first. The layout editor's **Undo** reverses up to 20 recent changes, including a whole drag gesture or combo deletion. **Save & play** commits the result; **Cancel** returns to the layout you started editing.

## Second screen and error reports

Tap **2nd** to temporarily enlarge the lower screen for dialogue and choices. Tap **Back** on that button or Android Back to return to your saved screen layout. Opening Menu also ends the enlarged view. Game buttons temporarily hide so you can tap the lower screen; Menu and the return button remain available. Move or hide **2nd** in the button editor, or use **Menu → Enlarge 2nd screen**.

For a problem, open **Settings → Help & reports → Share error report**. Confirm to create a ZIP with the app version, device model, graphics settings and recent app logs. Common private details are filtered; review the report before sharing. Game and save files are excluded. Android lets you choose where to share it.

## What changed

- Landscape-only launcher and game; immersive fullscreen is enabled while playing.
- MH4U logo, a jade interface, Home / Settings / About navigation, and a prominent Play button.
- Settings grouped into display, touch controls, controller bindings, audio, multiplayer, storage and advanced options.
- White menu titles and settings labels on dark backgrounds. Controller and keyboard bindings support compatible Bluetooth and USB devices on Android.
- Drag or collapse the layout toolbar. Save and Back keep the layout; leaving the app also saves it. Cancel discards the current edit. Reset asks first.
- Saved button positions survive appearance changes and reopening. Jade, blue and gold touch-button colours are available.
- Buttons brighten and compress slightly when pressed. Combos also highlight their individual buttons. A short release fade makes quick taps visible; it does not hold game input. **Keep pressed** stays active until tapped again, while the normal setting releases with your finger.
- Native layout refresh uses the saved choice at startup and updates the existing window when layouts change. Default rendering remains 2×; choose 1× in Display for lighter rendering.

## Development

Source: `port/android/`. Build with `scripts/build-android.ps1` using the existing Docker toolchain. See [Android build notes](docs/ROWELL_ANDROID.md) and [source provenance](source-basis.json).

Local APKs and Android ZIPs stay in the ignored `dist/` directory. This source-only repository does not include those packages or advertise APK download links.

Private keys, caches, imported engine binaries, build outputs and game data are ignored. The GPL licence and upstream notices remain in the project. The logo and game belong to Capcom.
