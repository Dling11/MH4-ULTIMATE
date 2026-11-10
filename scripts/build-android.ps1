param([switch]$PrepareOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$buildArgs = @('run', '--rm', '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges', '--pids-limit', '512', '--memory', '7g', '--cpus', '4', '--mount', "type=bind,source=$projectRoot,target=/workspace", '--workdir', '/workspace', '--entrypoint', '/bin/sh', 'monster-hunter-tri-android:rowell', '/workspace/scripts/bootstrap-build.sh')
if ($PrepareOnly) { $buildArgs += '--prepare-only' }
& docker @buildArgs
if ($LASTEXITCODE -ne 0) { throw "Android build failed (exit $LASTEXITCODE)." }
if (!$PrepareOnly) {
    $outputFolder = Join-Path $projectRoot 'port\android\app\build\outputs\apk\release'
    $outputMetadata = Get-Content -Raw -LiteralPath (Join-Path $outputFolder 'output-metadata.json') | ConvertFrom-Json
    if ($outputMetadata.applicationId -ne 'com.rowell.mh4u') { throw 'Unexpected app identity; packaging stopped.' }
    $distFolder = Join-Path $projectRoot 'dist'
    New-Item -ItemType Directory -Force -Path $distFolder | Out-Null
    Copy-Item -LiteralPath (Join-Path $outputFolder 'app-release.apk') -Destination (Join-Path $distFolder "Monster-Hunter-4-$($outputMetadata.elements[0].versionName).apk")
}
