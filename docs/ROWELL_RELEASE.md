# Publish the Android download

Version 1.0.17 adds ZIP / 7Z game import and gives the camera joystick the walking pad's faint fill and outline. The engine, FPS setup, controls, saves and package identity are unchanged. `.gitignore` excludes game data, builds, signing keys and private caches. Keep `LICENSE`, `CREDITS.md`, dependency notices and source files. The owner authorized publishing this experimental pre-release; the new Android import flow and camera appearance still need physical-phone validation.

## Publish the verified APK

1. Commit and push the source when ready. On GitHub, open **Releases â†’ Draft a new release**.
2. Create the tag **`rowell-1.0.17`** on that source commit. Use **Monster Hunter 4 1.0.17** as the release title.
3. Add these local files as release assets:
   - `dist/Monster-Hunter-4-1.0.17.apk`
   - `dist/Monster-Hunter-4-Android-1.0.17.zip`
   - `dist/Monster-Hunter-4-Android-1.0.17.zip.sha256`
   - `dist/1.0.17/SHA256SUMS.txt`
4. Publish the release, optionally marking it as a pre-release. Add public download links only when the owner enables publication.

The current APK size and SHA256 are recorded in `dist/1.0.17/VERSION.json` and `dist/1.0.17/SHA256SUMS.txt` after verification. All 82 JVM checks and Android lint passed. A local 7Z archive extracted to a 4 GiB image matching its reference byte for byte. The Android picker, cancellation and new camera appearance still need phone validation. The short 1.0.14 Village FPS check is documented separately in `UPSTREAM_UPDATES.md`; it is not full quest or multiplayer validation.

The Android ZIP contains this same APK, instructions, version/check metadata, licence and credits. GitHub's automatically generated Source code archives are separate from the installable assets. Upload the APK and ZIP through Releases; keep build output out of the source history.

GitHub reference: [creating releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository), [release download links](https://docs.github.com/en/repositories/releasing-projects-on-github/linking-to-releases).

## Future builds

The source build uses `scripts/build-android.ps1`. An existing Windows JDK/SDK can also package UI-only changes with verified Rowell libraries as described in `ROWELL_ANDROID.md`; it does not start Docker. Preserve the private `.local/signing/rowell.jks` and password file so future builds can update the installed app.

The optional **Android (Rowell)** Actions workflow builds the pinned core and runs the JVM tests and Android lint. For that workflow, configure these repository Actions secrets from the same signing key:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded private Rowell keystore.
- `ANDROID_KEYSTORE_PASSWORD`: its password.
- `ANDROID_KEY_ALIAS`: `rowell`.
- `ANDROID_KEY_PASSWORD`: the key password.

The workflow stops if signing secrets are missing. It does not substitute another signing key. It has been updated locally but has not been run on your future repository. Its artifact is a newly built APK; for the current release, use the verified local files above.
