param([Parameter(Mandatory=$true)][string]$Apk)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$expected = 'be84e23f127fd91a7227d0051b24e8a9d02c01d246cc297727b3ed53c829948e'
if ((Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { throw 'This APK is not the verified upstream build. Do not import it automatically.' }
$engineDir = Join-Path $projectRoot '.local\engine\arm64-v8a'
New-Item -ItemType Directory -Force -Path $engineDir | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Apk).Path)
try {
    $entries = @($archive.Entries | Where-Object { $_.FullName -match '^lib/arm64-v8a/[A-Za-z0-9_.-]+\.so$' })
    if ($entries.Count -ne 8) { throw 'Unexpected engine library count.' }
    foreach ($entry in $entries) { [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $engineDir $entry.Name), $true) }
} finally { $archive.Dispose() }
Write-Output 'Imported the eight verified emulator libraries. No game code library or game data was imported.'
