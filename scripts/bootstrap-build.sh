#!/bin/sh
set -eu
cd /workspace
export GRADLE_USER_HOME=/workspace/.local/gradle
mkdir -p .local/tools .local/signing
if [ ! -x .local/tools/gradle-8.9/bin/gradle ]; then
  curl --fail --location --retry 3 https://services.gradle.org/distributions/gradle-8.9-bin.zip -o .local/tools/gradle.zip
  curl --fail --location --retry 3 https://services.gradle.org/distributions/gradle-8.9-bin.zip.sha256 -o .local/tools/gradle.sha256
  python3 - <<'PY'
import hashlib, pathlib, zipfile
p = pathlib.Path('.local/tools/gradle.zip')
expected = pathlib.Path('.local/tools/gradle.sha256').read_text().strip()
assert hashlib.sha256(p.read_bytes()).hexdigest() == expected, 'Gradle checksum mismatch'
with zipfile.ZipFile(p) as z:
    assert all(not n.startswith('/') and '..' not in pathlib.PurePosixPath(n).parts for n in z.namelist())
    z.extractall('.local/tools')
pathlib.Path('.local/tools/gradle-8.9/bin/gradle').chmod(0o755)
PY
fi
if [ ! -f .local/signing/rowell.jks ]; then
  python3 - <<'PY'
import pathlib, secrets
p = pathlib.Path('.local/signing/password.txt')
if not p.exists():
    p.write_text(secrets.token_hex(32))
    p.chmod(0o600)
PY
  keytool -genkeypair -noprompt -keystore .local/signing/rowell.jks -alias rowell \
    -storepass:file .local/signing/password.txt -keypass:file .local/signing/password.txt \
    -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=MH4U Rowell'
fi
sh /workspace/scripts/prepare-native.sh
if [ "${1:-}" = '--prepare-only' ]; then exit 0; fi
sh /workspace/scripts/test-android-layout.sh
export CMAKE_BUILD_PARALLEL_LEVEL=2
cd port/android
../../.local/tools/gradle-8.9/bin/gradle --no-daemon --max-workers=3 --console=plain testDebugUnitTest assembleRelease lintRelease
