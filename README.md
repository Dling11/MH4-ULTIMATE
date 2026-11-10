# Monster Hunter 4

A custom Android app for Monster Hunter 4 Ultimate, with a simpler launcher, organized settings and a movable touch-control editor. Built on the MH4U Android project and Azahar core; see [credits](CREDITS.md).

Version 1.0.16 gives the walking joystick a faint theme-coloured tint while held and a softly filled, outlined moving thumb. The background stays see-through and follows the existing opacity setting. Other controls keep their existing appearance.

## Android download

[Download APK 1.0.16](https://github.com/Dling11/MH4-ULTIMATE/releases/download/rowell-1.0.16/Monster-Hunter-4-1.0.16.apk) · [Android ZIP and release notes](https://github.com/Dling11/MH4-ULTIMATE/releases/tag/rowell-1.0.16)

This is an experimental Android pre-release. The APK and ZIP contain the same signed app. Version 1.0.14 introduced **30 / 60 / 90 / 120 FPS** targets, with **60** as the default, and records the runtime/source contract for future upstream updates. Live FPS changes require the matching game update and a separately built private code library. They do not recompile the game or restart it for each selection. A short POCO F5 Village test reached **60 FPS at 100% game speed**; 120 FPS caused slow motion in that setup. Full quests, multiplayer and clean installation have not been validated for this FPS setup. A higher cap is not a guarantee of higher FPS.

Android 10+, ARM64 and Vulkan are required. The game is supplied separately and is not included in this repository.

## Install and play

1. Download and install the APK above, or extract the Android ZIP to find the same installer. For updates, save inside MH4U and install over the existing customized app. Do not uninstall or clear data; this preserves your progress and controls.
2. Extract your game archive if it is a ZIP, 7Z or multipart download. Open **Monster Hunter 4**, tap **Add game**, and select your decrypted `.3ds`, `.cci` or `.cxi` file. Wait for copying to finish; allow room for the imported copy.
3. Tap **Play game**. The first launch creates this app's own save data.

Normal emulation needs only your game. The optional recompiled library and USA v1.3.0 update are under **Settings → Advanced**. No game files are included.

MH4U uses two 3DS screens. The small screen in the upper right contains touch controls and menu choices. For larger text, choose **Menu → Display & performance → Screen layout → Side by side**. **Main screen only** hides these choices.

Screen layouts save automatically. During play, choose a layout and tap **Resume** above the settings; no game restart is needed. Existing player choices take priority over the default.

Home uses a compact side navigation menu, with a small logo inside the Play card and no large header or bottom navigation bar. Settings open in a grid with less padding. Gameplay continues behind both settings and the touch-control editor. Editing moves controls without sending game inputs. Leaving the app still pauses solo play. Multiplayer retains its existing no-pause behaviour. Save inside the game before importing progress or closing a session.

The movable **Hide / Show** button hides the small touch screen and restores your previous arrangement. Hidden touch-screen areas do not accept taps. **2nd** still enlarges the touch screen when needed; **Back** returns to the selected visibility. These display controls do not change your game progress. Their positions can be changed in the layout editor.

| Layout | Appearance |
| --- | --- |
| Game + small touch screen | The existing default with a small corner screen. |
| Centered · small screen top | Centered game, equal side margins, small screen at the top right. |
| Centered · small screen bottom | Same centered game, small screen at the bottom right. |
| Top & bottom screens | Both screens stacked like a 3DS. |

Menu stays inside the usable screen, clear of Android bars and the camera cutout, even when touch buttons are hidden. Android Back also opens Game menu.

The game always uses immersive fullscreen and the camera cutout area where Android permits it. Android's bars appear temporarily with an edge swipe. There are no fullscreen or camera-area switches to change accidentally. Menu stays clear of the physical camera; the camera may cover a small part of the picture. Black margins that preserve the game's proportions remain.

## Solo speed button

The frame-rate cap is separate from the FPS counter and the solo speed button.
Find the same 30 / 60 / 90 / 120 presets under **Display & performance** or **Game settings**.
The cap requires a matching recompiled library and game update; unavailable controls
stay visible but disabled. Normal emulation keeps the game's own frame rate. A cap
of 120 is a target, not evidence of 120 FPS gameplay. Existing saved cap values are
preserved until the user changes them. CPU clock defaults to 100% and works with
normal emulation too.

See [upstream updates and one-time FPS setup](docs/UPSTREAM_UPDATES.md) for the
source compatibility checks and private library build. Updates keep the app ID,
signing key and save folder. Game code and the matching update are never bundled
in this repository's APK or source archives.

Tap the **1×** touch button to cycle **1× → 1.25× → 1.5× → 1×**. Move or hide it in the button editor, or use **Menu → Display & performance → Solo game speed**. It is available throughout offline play, including quests. Targets need spare device performance; return to 1× if play or sound becomes uneven.

Speed starts at 1× each game session, resets to 1× when leaving the app, and is forced to 1× while joining, hosting or playing multiplayer. It stays at 1× after leaving multiplayer until you choose a faster rate again.

The application ID is `com.rowell.mh4u`, separate from the original app and MH Tri - Rowell. Updating with the same signing key preserves this app's data; uninstalling removes its private files.

## Saves and control layouts

Save inside MH4U first, then open **Menu → Saves** during play or **Home → Settings → Saves**. These tools copy the game's saved progress and extra data; they do not capture a live game session. MH4U USA saves are supported. Each action explains what it will do and asks before proceeding.

- **Create backup:** keeps the newest three verified backups, with local date, time, seconds and UTC offset. Unchanged saves reuse the latest copy.
- **Restore this backup:** choose a dated backup and confirm. Current saved progress is backed up first. Tap Play game and load your character afterward.
- **Export save → Use in another MH4U app:** creates a dated save folder for compatible apps such as Native or Azahar. Choose **MH4U_Saves** directly in Internal storage and keep the complete exported folder together.
- **Export save → Keep a backup file:** creates one ZIP that this app can import later. Keep a copy outside the app before uninstalling.
- **Import save → From another MH4U app:** choose an exported save folder containing **sdmc**, then review the character slots and extra data.
- **Import save → From a backup file:** choose a ZIP previously exported by this app. Both import choices ask Yes/No, back up current saved progress, then restart the app after a successful import. Choose **Continue** and load your character.

To move progress from Native, save and exit there, use its **Gear → Menu Options → Export save**, then choose **From another MH4U app** here and select that dated export. To move progress back, choose **Use in another MH4U app** here, then use Native's **Import save** and select the exported save it finds. Both apps must use MH4U USA saves. Import replaces saved progress; it does not merge characters or synchronize the apps. Unsaved gameplay is lost when restarting. Your game file, controls and graphics settings are preserved.

Resuming from unchanged app settings skips the core settings reload that recreated audio output and requested shader updates. Changed display, audio and controller settings still apply when you tap **Resume**. Gameplay continues behind the settings panel and control editor; Hide / Show and 2nd / Back were checked on the owner's POCO F5.

Leaving the app pauses solo play. The FPS badge starts a fresh one-second measurement after Resume or returning from the background, so paused time does not appear as a gameplay FPS drop.

The **30–120 FPS cap** under **Advanced → Recompiled game options** requires a compatible code library for this older port. Normal Azahar emulation does not gain that option from installing the separate Native app. The solo speed button changes game speed and is a separate feature; neither setting guarantees a particular measured FPS.

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

Local APKs and Android ZIPs stay in the ignored `dist/` directory. Installable packages are attached to GitHub Releases; they are not committed to the source tree.

Private keys, caches, imported engine binaries, build outputs and game data are ignored. The GPL licence and upstream notices remain in the project. The logo and game belong to Capcom.
