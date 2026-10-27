# Publish the Android download

The source includes the 1.0.6 save manager, named control layouts, editor Undo, temporary second-screen enlargement and error-report sharing, plus the earlier fullscreen, speed and display fixes. Upload this project's source to your new repository; `.gitignore` excludes game data, builds, signing keys and private caches. Keep `LICENSE`, `CREDITS.md`, dependency notices and the source files.

## Publish the verified APK

1. Commit and push the source when ready. On GitHub, open **Releases → Draft a new release**.
2. Create the tag **`rowell-1.0.6`** on that source commit. Use **Monster Hunter 4 - Rowell 1.0.6** as the release title.
3. Add these local files as release assets:
   - `dist/Monster-Hunter-4-Rowell-1.0.6.apk`
   - `dist/Monster-Hunter-4-Rowell-Android-1.0.6.zip`
   - `dist/Monster-Hunter-4-Rowell-Android-1.0.6.zip.sha256`
   - `dist/1.0.6/SHA256SUMS.txt`
4. Publish the release, optionally marking it as a pre-release. The README's version-specific download links then work with your repository name.

The current APK size and SHA256 are recorded in `dist/1.0.6/VERSION.json` and `dist/1.0.6/SHA256SUMS.txt` after verification. That metadata also records which phone checks have been completed. Version 1.0.6 was prepared without connecting to a phone; complete the device checks in `ROWELL_ANDROID.md` before describing it as phone-tested.

The Android ZIP contains this same APK, instructions, version/check metadata, licence and credits. GitHub's automatically generated Source code archives are separate from the installable assets. Upload the APK and ZIP through Releases; keep build output out of the source history.

GitHub reference: [creating releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository), [release download links](https://docs.github.com/en/repositories/releasing-projects-on-github/linking-to-releases).

## Future builds

The local Docker build uses `scripts/build-android.ps1`. Preserve the private `.local/signing/rowell.jks` and password file so future builds can update the installed app.

The optional **Android (Rowell)** Actions workflow builds the pinned core and runs the JVM tests and Android lint. For that workflow, configure these repository Actions secrets from the same signing key:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded private Rowell keystore.
- `ANDROID_KEYSTORE_PASSWORD`: its password.
- `ANDROID_KEY_ALIAS`: `rowell`.
- `ANDROID_KEY_PASSWORD`: the key password.

The workflow stops if signing secrets are missing. It does not substitute another signing key. It has been updated locally but has not been run on your future repository. Its artifact is a newly built APK; for the current release, use the verified local files above.



