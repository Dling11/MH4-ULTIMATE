# Updating the customized Android build

The app uses the Android source from klark231/MH4U-Android and a pinned
klark231/AzaharPlus core. CompaLarios18/MH4UltimateDC is the upstream port and
recompiler reference. The separate khorne-hub Native app is a different build;
its libraries and patches must not be copied into this app without verification.

## Check before updating

Run `python tools/check_android_compatibility.py`. This reads
`config/android-compatibility.json` and checks the core pin, runtime contract,
recompiler, hook configuration and Android patches. It changes no files.
`--latest` also reports the public upstream branch tips without merging,
installing or changing a Git branch. The Android bootstrap runs the local check.

A changed source hash is a review gate. Do not refresh the lock just to silence
it. Review the upstream diff, retain the app's settings/editor/save changes, and
build and test the affected engine/library pair first. Adding a game hook or
changing the runtime contract requires rebuilding the private game-code library.
UI-only changes can reuse the verified native libraries.

## One-time live-FPS setup

The game-code library is built from the matching USA v1.3.0 update. It is private
game-derived output, not part of the APK or source repository. The known target
hashes are in `config/versions.json`; unknown versions must not be forced through.
The app must install that same game update and load the matching code library.
For an unmodified game, the existing upstream builder can be used with an Android
NDK compiler, for example inside the prepared Android build environment:

```sh
python3 tools/check_android_compatibility.py
python3 tools/build_game.py --dump /path/to/your/MH4U-update.cia \
  --out .local/mh4u-game-code --no-mods --lib-ext so --opt O1 --jobs 2 \
  --cc "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang"
```

For code mods, build against the exact same mods the app will load instead of
using `--no-mods`. Preserve the build record and hashes beside the library.
Import the update through Home settings → Advanced → Install game update and the
library through Import code library. Save before this initial setup and back up
the app's saves/settings first. The code library is named
`0004000000126300.so` under this app's own `recomp` folder.

Once the matching backend is running, the existing FPS hook updates the game cap
and emulated refresh rate live. Changing 30 / 60 / 90 / 120 FPS does not rebuild
the library or restart the game. 60 is the default; higher values are experimental
targets, not a guarantee of device performance. Confirm normal gameplay speed,
audio, controls and progress on a real phone before publication.

The initial POCO F5 Village check on 1.0.14 confirmed the recompiled backend and
live switching between 60 and 120 FPS. At 60, the display showed 60 FPS and 100%
game speed and the player confirmed normal movement/audio. At 120, it showed
about 70 FPS and 62% speed with the existing 2x resolution. Keep 60 as the default;
use a lower cap if game speed drops. These are short local observations, not
sustained quest, multiplayer or clean-install validation. New-user library
generation/setup is not automatic in the APK.

## Preserve compatibility and progress

Keep `com.rowell.mh4u`, the existing signing key and the `MH4U_Rowell` data folder.
Keep game-derived files, signing material and AI handoffs ignored. Verify the
APK signature, dependencies and source changes before updating a phone. Preserve
the original and Native apps. Use an explicit saved-progress confirmation and a
fresh verified save/control backup before closing an active game.

Check both initial startup and live FPS changes, along with menus, touch editing,
audio, import/export and a quest. A successful build or higher FPS counter alone
does not establish full gameplay or multiplayer compatibility. Publishing a new
APK or integrating an upstream change remains an owner-controlled action.
