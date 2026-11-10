# Monster Hunter 4 - Rowell

This is a separately compiled Android frontend. The original APK is not edited or overwritten. The initial build links the exact eight ARM64 emulator libraries from the verified upstream APK, preserving the engine used in the user's smooth normal-emulation test.

Application ID: `com.rowell.mh4u`. The Kotlin/JNI namespace remains `org.mh4u.port` because the native core calls those classes by name. Both launcher and game are locked to landscape. Each installation has a separate `files/MH4U_Rowell` user folder.

## Version 1.0.16: faint walking-joystick feedback

The walking circle pad keeps the theme colour with a faint pressed fill. Its base uses 8% of the selected opacity at rest and 14% while held; the outlined moving thumb uses 22–30%. The camera stick, action/combo buttons and input handling are unchanged. This is a UI-only update that keeps the seven native libraries from 1.0.15 and the existing game-code library, saves and layouts.

## Build in Docker

The existing `monster-hunter-tri-android:rowell` image supplies Java, Android SDK 35, NDK 29.0.14206865 and CMake 3.28.3. No Windows SDK or Android Studio installation is needed. Gradle 8.9 is downloaded and checked against its official SHA256. Dependencies, native source, signing key and logs stay in ignored `.local/`.

```powershell
.\scripts\build-android.ps1
```

Keep `.local/signing/rowell.jks` and its password file privately backed up: future app updates need this signing identity. Do not publish them. The Android sources compile from this project; no game ROM or generated game-code library is bundled.

Since 1.0.2 the default build compiles the native backend from source so the Android window fix is included. `prepare-native.sh` pins AzaharPlus to `039adda20c31fdf644fd3c9cf6962812ff109818`, initializes its pinned submodules and applies the existing Android port patch. Since 1.0.3 it also applies `rowell-window-layout.patch`, making framebuffer layout refresh virtual so renderer-initiated refreshes retain the Android preset. The layout implementation lives in this project's JNI sources. Native compilation uses two jobs and one link job within the existing Docker limits. The earlier verified upstream libraries remain a comparison baseline; importing them alone does not include these layout fixes.

## UI builds with an existing Windows toolchain

For UI-only edits, use the seven verified ARM64 libraries from a source build with matching JNI functions. Version 1.0.8 adds `Native.withSavedFiles`; engines from 1.0.6/1.0.7 cannot run the new save screens. Build the native engine first with the source workflow above. For later UI-only edits, set `JAVA_HOME` to JDK 17 and `ANDROID_HOME` to an Android SDK containing platform/build-tools 35, then use Gradle 8.9:

```powershell
gradle --project-dir port/android -Pengine.prebuilt=true -Pengine.prebuilt.dir=C:\private\rowell-engine testDebugUnitTest assembleRelease lintRelease
```

The selected engine directory must contain `arm64-v8a/*.so`. Preserve the existing Rowell signing files. This mode does not require Docker or an NDK because it keeps the verified native libraries unchanged. It is suitable only when no JNI/native source changes need compiling. The original imported upstream engine lacks the newer Rowell JNI functions; it and the separate khorne-hub Native app's libraries must not be substituted. The normal source build above remains available for native changes.

## Version 1.0.8: clearer save dialogs and in-game operations

Save choices describe the user's task: **Use in another MH4U app**, **Keep a backup file**, **From another MH4U app**, and **From a backup file**. Personal names are removed from save-format labels, save ZIP filenames and format-error messages. The existing archive manifest identifier remains unchanged so older exported backups stay compatible.

The save dialogs use a scrollable body, fixed confirmation footer, clear option cards and completion/error dialogs. Folder selection instructions appear before Android's picker opens. Backup/export remain copies of the last in-game save. Import's Yes/No confirmation explains replacement, the safety backup, unchanged settings and the required restart.

Backup and Export are available in the running game's menu. `Native.withSavedFiles` schedules their synchronous file-copy work between emulated CPU loops, flushes buffered native file streams, and also services the read while the offline menu is paused. A closed core performs the same read under the run mutex. The dedicated read queue cancels pending requests at shutdown. The UI's file-work gate prevents overlapping operations. These callbacks are for reading saved progress and writing backup/export files only; they never restore into the active save directory.

Import validates its archive before stopping the game. After Yes, the activity holds its normal finish callback, requests shutdown and waits until the run mutex confirms filesystem closure. The existing transactional restore then makes its safety backup and replaces progress. On success, a private, unexported restart activity in a small helper process restarts only this app and opens the game when its configured file is available. The helper does not initialize the emulator or open saves. Restarting the process also discards the old in-memory game and multiplayer session. Controls, settings, game files and the separate official Native app are outside the import scope. Failure leaves the user with a message/recovery route and does not launch a new game over an unfinished restore.

Validation includes real-file restore sequencing tests (including the final shutdown flush), rejection before closing for invalid input, and native scheduling tests for a paused game, shutdown cancellation, exceptions and stale requests. Compile/lint tests do not verify Android restart/picker behavior or visual layout; those checks remain pending on the phone. The earlier FPS/menu-pause investigation remains deferred at the owner's request.

## Layout persistence

An editor session owns a deep copy. Save, Android Back and leaving the app commit the layout using an atomic file replacement. Cancel discards the draft. Reset asks for confirmation and affects the draft. Appearance changes reload the latest saved positions before writing, so changing opacity cannot revert an earlier layout edit.

The floating editor can be dragged by its header and collapsed. The renderer's existing touch-screen overlay is configured independently of the game's CPU backend. Game-specific frame-rate and widescreen hooks remain optional advanced features requiring compatible recompiled code.

The screen layout and touch-button arrangement are separate settings. The default corner view is written only when no screen choice exists. The window now reads the saved choice before caching its initial geometry, and live settings explicitly rebuild the geometry even when Android supplies the same surface and dimensions. Changes queued while the offline game is paused apply on Resume; a game or phone restart is not required.

## Validation boundary

The original APK's normal-emulation gameplay was reported smooth and working by the user on a POCO F5. That is the baseline, not a benchmark of this new APK. Compile checks, layout persistence tests, Android lint, signature and native-library hash checks are followed by a phone test before claiming the new interface is equally smooth.

The user subsequently reported invisible dialogue, quest choices and cooking choices in the original APK after reaching the Village. Its captured configuration uses `screens = single`, hiding the lower screen. The separate Rowell app was then installed successfully, with a copy of the user's saved character. A Village quest-counter screenshot shows the choices rendered on the small lower-screen overlay, and the user confirmed seeing that second screen. All 32 original save/system files retained their checksums. Dialogue and cooking have not yet been separately captured, and touch editing and sustained gameplay still need owner testing.

Version 1.0.1 adds white menu/settings text, explicitly supplies the foreground colour for dark panels, clarifies Android Bluetooth/USB bindings, and corrects launcher text spacing. It was installed after the user saved. All 29 NAND/SDMC files retained their checksums across the update, and the launcher opened in landscape with unclipped text. A phone screenshot also verifies white Game menu and Touch controls headings. The APK SHA256 is `da1597c1f90ed2fc2ace8b2989ca190887250e81a818a4f27751354009e0b091`; the versioned APK and ZIP in `dist/` match it. Build, three layout tests, lint and signature checks passed, and the eight native engine libraries remain unchanged.

Version 1.0.2 is installed and its launcher opened on the POCO F5. All 29 NAND/SDMC files and all three settings/layout files retained their checksums, preserving the player's 16 controls. The installed APK was pulled back and matches the distribution SHA256 `a801542f705741d29a73295becf4e322c234993c5a0d23edf1fa494e39e5604d`. It fixes initial and live screen-layout refresh and adds white editor text, pressed button/combo feedback and a clearer Keep pressed explanation. Six JVM tests passed; lint reports zero errors and eleven warnings. The APK signature matches the existing Rowell key. ELF inspection verifies the original core revision above, the layout refresh change and all packaged native dependencies. The ZIP contains the exact verified APK. Phone gameplay, the live layout switch and combo feedback still need the player's check on this version.

The README has version-specific APK and Android ZIP download links for a future GitHub release. Publishing steps are in `ROWELL_RELEASE.md`. The inherited Android Actions workflow now uses the pinned core and matching NDK/CMake, runs geometry checks, JVM tests and lint, names its APK for download and requires the existing signing identity. YAML and shell syntax are checked locally; the workflow has not been run on GitHub.

Version 1.0.3 adds centered presets with equal side margins and a second screen in the upper or lower right margin. Geometry checks cover 162 combinations of window size, screen proportion and placement, including bounds, symmetry and non-overlap. The game surface and controls share a usable viewport inside Android's current bars and cutout, so their touch coordinates stay aligned. Menu also has a minimum 48 dp touch target. Its saved coordinates are preserved; drawing and hit testing use the same safe position. Menu stays available when touch controls are hidden. The settings now explicitly say to tap Resume to apply display choices. This version is installed and its launcher opened on the POCO F5. All 29 NAND/SDMC files and three settings/layout files retained their checksums, preserving the player's 16 controls. The installed APK was pulled back and exactly matches the downloadable APK (SHA256 `325fea00d95c80e29baf58aa2a2044a0baab25c30061b4c70d92795887d1681f`). Six JVM tests, 162 geometry checks, lint (zero errors), signature and native dependency checks passed. The player subsequently confirmed that Menu works with Android bars shown and hidden, and that all display presets work, including the centered layouts.
Version 1.0.4 adds session-only speed targets of 100%, 125% and 150% through the existing emulation frame limiter. Requests are bounded; joining, hosting or connecting a network room clears acceleration, and leaving that room does not restore it. Android backgrounding and a new game session also restore 1x. The renderer patch refreshes presentation mode on the existing Vulkan surface when entering or leaving accelerated speed; it does not use the surface-replacement path, which waits for a new surface. Normal 1x presentation remains the baseline.

The speed button is added in memory when loading older layouts; existing files and controls remain intact until a layout is explicitly saved. A JVM test checks preservation and prevents duplicate speed controls. The camera-area setting optionally removes the reserved cutout padding where Android permits it; the default retains camera protection, and picture proportions remain unchanged. Version 1.0.4 is installed and its launcher opened on the POCO F5. A fresh local backup was verified before installation. All 29 NAND/SDMC files and three settings/layout files retained their checksums, preserving the player's 16 saved controls. The installed APK was pulled back and matches the distribution APK exactly (SHA256 `06524e2b9f8fe70c7094ae6bf6d01c5c9686993bc927851a18101d6d12c86a62`). No fatal startup error was found in the app's captured logs. A subsequent screenshot shows Village gameplay with the new speed button, and native logs confirm applied targets of 100%, 125% and 150%. This confirms that requests reach the core, not that every scene reaches the requested speed. Seven JVM tests, speed-policy checks, 162 geometry checks, lint and signing checks passed. Sustained accelerated gameplay, sound, offline quest behavior and camera-area appearance still need the player's check; multiplayer speed guards have automated policy coverage but have not been exercised in a live network room.

The player subsequently confirmed that the 1.0.4 speed button works. Version 1.0.5 makes immersive fullscreen and use of the camera cutout area automatic, removing both switches. Android's bars remain available through a transient edge swipe. Older `fullscreen` and `use_cutout_area` preferences are ignored without rewriting saved settings. The shared game viewport no longer reserves the camera strip, while Menu and Compose menus still receive cutout insets. Android 10 uses the short-edge cutout mode; Android 11+ requests the always mode. Aspect-preserving black margins remain. The APK and Android ZIP are built and verified. Seven JVM tests, 162 geometry cases, speed-policy checks and lint (zero errors) passed. The seven native libraries match 1.0.4 byte for byte, and the signing identity is unchanged. Packaged code checks confirm both removed switch labels and the retired camera preference are absent. This version is installed and its launcher opened on the POCO F5. All 29 NAND/SDMC files and three settings/layout files retained their checksums immediately after installation, preserving the player's 16 saved controls. The installed APK matches the download exactly (SHA256 `99efb735a6bd091e74d9e936a9908828652b76d02ec50631262ae5ab938cee05`). A subsequent Village screenshot shows the camera strip removed, and logs show the full 2400x1080 viewport with live layout switches and speed targets of 100%, 125% and 150%. The player confirmed the automatic camera-area fill, Menu access and removed switches work. A manual edge-swipe test, sustained accelerated quest play and live multiplayer tests have not been performed on this version.

## Version 1.0.6: local preparation

Prepared without connecting to the phone, which remains on 1.0.5. The new tools are:

- A three-copy save manager with local timestamps, duplicate suppression, file-picker export/import and explicit restore confirmation. Only MH4U USA's save-data directory and matching extra data are included. Game files, updates, NAND configuration, controls and graphics settings are outside the restore scope. Archives have a title/format manifest, bounded file sizes/counts and SHA256 verification. Restore stages and checks the input before backing up current progress, then uses a recovery journal and directory moves. Startup rolls back an interrupted restore; Play is blocked if recovery needs attention. Native's run mutex check keeps these tools unavailable until the core has finished flushing files.
- Named copies of landscape controls, including combos and appearance. Profiles use generated filenames, reject duplicate names and ask before loading, deleting or using defaults.
- Up to 20 editor Undo steps. A drag or pinch gesture is one change; combo creation/deletion, size, visibility and reset are undoable. Cancel still returns the original draft.
- A temporary lower-screen view through the movable `2nd` button or Game menu. The Android window shows the bottom screen alone without changing saved screen style or swap preferences. Return, Android Back, Menu or backgrounding ends the view. Menu and return controls stay available while game controls temporarily hide.
- Opt-in error-report sharing through a cache-only FileProvider. Reports include allowlisted app/device/display details and bounded logs from this app's PID and native log. Common private details are filtered. No game/save files or signing data are included. The chooser sends nothing until the player chooses a destination.

The pinned core revision and three Azahar patches remain unchanged. The native Android window and JNI glue change for screen focus and save-data-idle checking. The final build passed 27 JVM tests, 162 centered-layout geometry cases, speed-policy checks and Android lint (zero errors, the same eleven warnings as 1.0.5). APK identity, persistent signing certificate, new JNI exports, core revision and packaged native dependencies are verified. The six unrelated native libraries match 1.0.5 exactly. Report sharing adds no storage or log-reading permission; its provider only exposes the reports cache directory.

An additional JVM integration check exported and restored an isolated copy of the existing PC backup. All 15 MH4U save/extra-data files matched after the round trip, and the original PC backup retained its hashes. No phone connection was used. Phone installation, file pickers, sharing, native lower-screen touch behavior and sustained gameplay remain unverified for this version. `dist/1.0.6/VERSION.json` records these boundaries with the APK checksum.

### Later phone checks

1. Save inside MH4U and exit. Create a backup, repeat with unchanged progress and confirm only one copy exists. Export it outside the app. Confirm No on a restore leaves progress intact; test restore/import of that same latest save before trying older progress.
2. Save a named control layout, load another/default after confirmation, and reload the named layout. In the editor, move or delete a combo, Undo, then exercise Save and Cancel.
3. Open dialogue/cooking/quest choices, tap `2nd`, use lower-screen touch input and return. Verify Menu remains reachable and the prior layout returns for each display preset.
4. Create an error report, inspect its ZIP contents and share/cancel through Android. Verify the app returns to its menu normally.
5. Play a full quest, check normal and accelerated speed, sound, and Home/reopen after saving. Check a second device before claiming broad Android performance compatibility.

## Version 1.0.7: save transfers and Menu → Resume

This update targets `com.rowell.mh4u` only. The separate official Native app stays unchanged. It includes the prepared 1.0.6 tools above.

Export and Import now offer **Native / Azahar folder** or **Rowell backup ZIP**. The folder importer reads a user-selected `sdmc` export through Android's system picker, checks the USA title, character files, metadata and size limits, then converts it into the existing verified archive format. It stages everything before confirmation and uses the same safety backup, transactional restore and recovery journal. Only the game's save and extra-data scopes are replaced. Game images, NAND, controls and settings remain outside that scope. Incomplete, European or ambiguous exports produce an error before touching saved progress. Missing extra data is explicitly disclosed before replacement.

The folder exporter creates a new dated folder and verifies each written file by reading it back. Choose a destination directly in Internal storage, such as `MH4U_Saves`. The output contains both the standard Azahar `sdmc` structure and a compact title/extra-data structure that Native 0.1.8's six-level storage scan can discover. Keep the entire export folder together. No permission to another app's private data or background synchronization is added. The existing manifest ZIP format remains available for Rowell backups.

On Resume, the app formerly submitted all core settings even when nothing changed. The pinned core's `System::ApplySettings()` requests shader updates and invokes `DspInterface::SetSink()`, which recreates the audio sink. The new gate compares the relevant core settings, screen extras and touch-screen layout. Unchanged resumes skip that work; real changes, new game sessions and forced configuration refreshes still apply. Repeatedly closing an already-closed enlarged second-screen view is also skipped. The intentional offline pause while settings are open remains.

A recording of the first 1.0.7 phone test showed the title-screen counter dropping briefly after Resume and recovering to about 60 FPS. The native counter divides frame counts by elapsed real time, so the interval overlapping a pause reports a low number. The updated badge discards that paused interval, waits for a full live second and then shows the measured value. Menu/editor/background time is excluded; no target FPS, CPU clock or game timing is changed. This counter correction still needs an updated-device retest and does not establish that every possible gameplay hitch is resolved.

The build uses the same seven native libraries as 1.0.6. All 41 JVM tests and Android lint passed. An isolated round trip of the owner's previously backed-up save checked all 16 save/extra-data files and preserved controls/settings. These checks do not establish Android picker behavior or a measured gameplay improvement. With the owner's approval, 1.0.7 was installed over Rowell 1.0.5 on the POCO F5 and its launcher opened. All 36 backed-up Rowell files retained their hashes, as did the separate Native app's 25 checked files; its installed package stayed unchanged. Menu → Resume, file pickers and gameplay still need user testing. Check an unchanged Resume, an actual display change, import cancellation, a same-save import, and a full quest.

### Frame-rate limits

The existing 30–120 FPS cap is under **Advanced → Recompiled game options** and is shown only when this older port has its matching USA code library. Without it, Rowell uses normal Azahar CPU emulation. The separate official Native app's differently named library is not compatible with this older loader. This patch does not unlock 120 FPS or claim an FPS benchmark; the offline speed multiplier is a different setting.

# Version 1.0.9: compact launcher and live settings

Historical behaviour below is superseded by the 1.0.10 editor/visibility update.

The launcher removes the large logo/name header and bottom navigation bar. Home,
Settings and About use compact side navigation. Home keeps the selected game and
Play action together; settings categories use an adaptive grid. Detail pages have
smaller margins while retaining their existing controls and save confirmations.

In-game settings open in a side panel with the game visible behind it. Opening
that panel does not pause solo gameplay. Editing touch controls or leaving the
app still pauses solo play; returning from Android Home resumes play unless the
control editor is still open. Multiplayer retains its existing no-pause policy.
Game inputs are released and blocked while the menu is open. Native settings
changes still use the existing queued application path, and save export/import
retains the existing safe-copy and stopped-core restore paths.

Version 1.0.9 uses exactly the same seven engine libraries as the verified 1.0.8
APK. Package, signing certificate, save folders, game selection and stored control
layouts are unchanged. No Azahar/AzaharPlus core upgrade is included. All 51 JVM
tests passed and Android lint reports zero errors. Home appearance, real gameplay
continuing behind the menu and background/editor transitions still need physical
phone validation before publication.

# Version 1.0.10: live editor and Hide / Show

The owner accepted the compact Home/settings layout and reported that only the
touch-control editor still froze the scene. The editor now keeps solo gameplay
running while controls are moved or resized. Entering it releases all held game
inputs, and editor gestures and controller input remain blocked from the game.
Android Home/lock still pauses solo play; returning resumes it. Multiplayer keeps
its existing no-pause policy. Edit in a safe area because the game remains active.

A separate movable Hide / Show control hides the small screen using the existing
Main screen only layout. Show restores the previous layout and screen-swap choice.
Hidden touch-screen areas reject taps immediately, including while a layout change
is queued. The original 2nd control still opens the enlarged touch screen, even
when hidden; Back returns to the previously selected visibility. No screen or
touch-button positions are reset. Existing layout files gain only the new utility
button in memory; they are not rewritten until the owner saves a layout.

This is a Kotlin-only update using the seven verified 1.0.9 native libraries.
The package, signing key and data folders remain unchanged. No Native-app, game
assets, engine upgrade, Docker operation or public download change is included.
Check editor animation, input isolation, Hide/2nd/Back/Show, restored layouts and
Android Home/return on the phone before claiming those physical checks passed.

# Version 1.0.11: screen changes preserve audio

The owner confirmed live control editing in 1.0.10. Hide / Show worked but caused
a brief sound interruption, unlike 2nd. Changing the saved screen layout previously
used System::ApplySettings, which recreates the audio sink and reloads input devices.

The apply gate now compares core settings separately from screen geometry. A layout,
screen-swap, gap or touch-screen corner/size change uses a queued JNI window refresh
without applying the core's audio, shader or input configuration. An unchanged Resume
does nothing. Actual audio/resolution/input changes, a forced refresh or a new game
session still use the full settings path. Failed submissions can be retried.

Only libmh4u-android.so is rebuilt, using the same pinned Azahar core revision.
The other six native libraries, package, signing key, saves and control layout format
remain unchanged. No Native-app or website change is included. The separate 2nd/Back
behaviour and live editor are retained. The owner confirmed continuous music and
working Hide / Show and 2nd / Back on the POCO F5 in 1.0.11. This targeted check
does not establish sustained quest, multiplayer or benchmark performance.

# Version 1.0.12: Monster Hunter 4

The launcher label, About title and storage-settings heading now read Monster
Hunter 4. Rowell's custom-interface credit and all upstream credits remain in
About. The game logo and compact Home design stay unchanged. APK filenames use
Monster-Hunter-4-1.0.12.apk; the source build script uses this filename too.

This is a name-only update. All seven native libraries match the owner-tested
1.0.11 build. Keep applicationId com.rowell.mh4u, the existing signing key and
MH4U_Rowell data folder so installing over the existing app preserves its game,
progress, controls and settings. Do not migrate or rename the saved-data folder.

# Version 1.0.13: visible Game settings and frame-cap presets

Game settings is now a root category and remains accessible from Advanced without
requiring a code library just to open the page. CPU clock is independently usable
with normal emulation. Frame rate uses explicit 30 / 60 / 120 FPS presets, with 60
as the missing-setting default. Opening the page never overwrites an existing cap,
including older custom values. The same frame-cap component is available in Display
& performance. The performance counter and session speed controls remain separate.

Hook-specific frame cap, game effects, net sync and quest shortcuts stay visible
but disabled without a code library, or when the running backend reports a library
problem. Standard graphics settings and the renderer's touch-overlay layout stay
available. File presence alone does not prove runtime compatibility; no actual
120 FPS claim is made. The user's current default recomp folder has no library,
so this UI update does not unlock a higher frame rate for that normal-emulation
session. Adding functional recompiled-mode support still needs matching game code.

Widget rows support disabled pointer and accessibility actions. No engine, save,
input mapping, signing or data-folder changes are included. All seven native
libraries match 1.0.12, including the owner-confirmed live-editor and sound fixes.

# Version 1.0.14: one-time FPS setup and upstream compatibility checks

The presets now include 90 FPS. With a matching update and code library loaded,
the existing game hooks change the cap and GPU refresh rate live. No library
rebuild or game restart is required for each selection. 60 stays the default;
normal gameplay speed and actual FPS must be checked on the phone.

The first library was generated privately from the already-present USA v1.3.0
update after checking the known code and identifying-header hashes. Generated
code, the update and the library remain ignored and separate from the APK. The
separate Native app is not modified or used as this app's runtime.

`config/android-compatibility.json` records the pinned core and source contract.
`tools/check_android_compatibility.py` checks that contract, including the actual
prepared core checkout, and can report upstream tips without merging. The Android
bootstrap invokes the local check. See `UPSTREAM_UPDATES.md` for review/rebuild and
phone-validation requirements. Existing custom controls, save management and the
owner-confirmed audio/live-menu fixes remain unchanged.

# Version 1.0.17: archive import and matching camera joystick

Home's Add game accepts a decrypted image or a single ZIP / 7Z archive. Archives
are read through Android's read-only file picker and a seekable descriptor; only
the game image is extracted, directly into games/. No second compressed copy is
stored. Providers that cannot seek ask the user to save the archive locally.
RAR and multipart files ask for manual extraction; nothing is renamed to pretend
that one part is a complete game. Multiple images ask the user to choose manually.

The importer checks declared size, available storage, output size, archive CRC
where present, and a 3DS container header. It bounds image size, entry count and
7Z decoder memory. It never extracts entry paths, overwrites a previous image or
changes saves, controls or the selected game on failure. Cancellation removes
temporary output; the next launch also removes this importer's abandoned partials.
Original downloads remain untouched. Only successful import selects the new image.
The header check does not prove game region, decryption or complete compatibility.

Apache Commons Compress 1.28.0 and XZ for Java 1.12 provide ZIP/7Z reading. Their
licences and the transitive Commons dependencies' notices are available in About.
Zstandard ZIP is not bundled; the importer rejects it before reaching that optional
native decoder. All seven engine libraries remain identical to 1.0.16.

The camera C-stick now uses the walking pad's same faint background tint, softly
filled thumb and outline. Positions, opacity settings, palettes, sensitivity and
input calculations are unchanged. Physical Android picker/cancellation and camera
appearance still require device validation before declaring the update tested.

