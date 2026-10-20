param(
    [string]$SigningDirectory = "$env:USERPROFILE\Documents\Kilometrina-signing",
    [string]$OutputDirectory = "$env:USERPROFILE\Downloads\Kilometrina-releases"
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$config = Get-Content -LiteralPath (Join-Path $SigningDirectory 'signing.json') -Raw | ConvertFrom-Json
$secret = (Get-Content -LiteralPath (Join-Path $SigningDirectory 'password.dpapi') -Raw).Trim() | ConvertTo-SecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
$names = @('ANDROID_KEYSTORE_PATH','ANDROID_KEYSTORE_PASSWORD','ANDROID_KEY_ALIAS','ANDROID_KEY_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $env:ANDROID_KEYSTORE_PATH = Join-Path $SigningDirectory $config.keystore
    $env:ANDROID_KEY_ALIAS = $config.alias
    $env:ANDROID_KEYSTORE_PASSWORD = $password
    $env:ANDROID_KEY_PASSWORD = $password
    $javaBin = Join-Path $env:JAVA_HOME 'bin'
    $pin = (Get-Content (Join-Path $repo 'docs/release-certificate-sha256.txt') -Raw).Trim().ToUpperInvariant()
    $cert = Join-Path $SigningDirectory 'release-certificate.der'
    & "$javaBin/keytool.exe" -exportcert -keystore $env:ANDROID_KEYSTORE_PATH -storepass:env ANDROID_KEYSTORE_PASSWORD -alias $env:ANDROID_KEY_ALIAS -file $cert
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read signing certificate' }
    if ((Get-FileHash $cert -Algorithm SHA256).Hash -ne $pin) { throw 'Keystore does not match the approved release identity' }
    Push-Location $repo
    try {
        & .\gradlew.bat --no-daemon --no-parallel :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed' }
    } finally { Pop-Location }
    $sdk = $env:ANDROID_HOME
    if (!$sdk) { $sdk = $env:ANDROID_SDK_ROOT }
    $signer = Get-ChildItem "$sdk/build-tools/*/apksigner.bat" | Sort-Object { [version]$_.Directory.Name } | Select-Object -Last 1
    if (!$signer) { throw 'Android apksigner not found' }
    $apk = Join-Path $repo 'app/build/outputs/apk/release/app-release.apk'
    $aab = Join-Path $repo 'app/build/outputs/bundle/release/app-release.aab'
    $verification = & $signer.FullName verify --verbose --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
    $digests = @($verification | Select-String 'certificate SHA-256 digest:\s*([a-fA-F0-9:]+)' | ForEach-Object { $_.Matches[0].Groups[1].Value.Replace(':','').ToUpperInvariant() } | Select-Object -Unique)
    if ($digests.Count -ne 1 -or $digests[0] -ne $pin) { throw 'APK signing certificate mismatch' }
    & "$javaBin/jarsigner.exe" -verify -strict -keystore $env:ANDROID_KEYSTORE_PATH -storepass:env ANDROID_KEYSTORE_PASSWORD $aab $env:ANDROID_KEY_ALIAS
    if ($LASTEXITCODE -ne 0) { throw 'AAB signature verification failed' }
    $aabCert = & "$javaBin/keytool.exe" -printcert -jarfile $aab
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect AAB certificate' }
    $aabDigest = ($aabCert | Select-String 'SHA256:\s*([a-fA-F0-9:]+)').Matches.Groups[1].Value.Replace(':','').ToUpperInvariant()
    if ($aabDigest -ne $pin) { throw 'AAB signing certificate mismatch' }
    $version = [regex]::Match((Get-Content "$repo/app/build.gradle.kts" -Raw), 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
    if (!$version) { throw 'Version name missing' }
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    $destApk = Join-Path $OutputDirectory "Kilometrina-$version.apk"
    $destAab = Join-Path $OutputDirectory "Kilometrina-$version.aab"
    Copy-Item $apk $destApk
    Copy-Item $aab $destAab
    Get-FileHash $destApk,$destAab -Algorithm SHA256 | Format-Table -AutoSize
    Write-Output "Verified release certificate: $pin"
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $password = $null
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
