# NexSky Android derlemesi:
#   powershell -ExecutionPolicy Bypass -File build-apk.ps1
# Çıktı: dist\NexSky.apk
# Gerekenler: Android Studio (SDK + JBR) ya da Android SDK + Java 17.
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $Root
try {
    $Ver = (Get-Content 'version.txt' -Raw).Trim()
    Write-Host "== NexSky Android $Ver"

    # --- Android SDK: the first folder that has platforms installed.
    $sdks = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'), 'C:\Android\Sdk') |
        Where-Object { $_ -and (Test-Path (Join-Path $_ 'platforms')) } | Select-Object -Unique
    $Sdk = $sdks | Select-Object -First 1
    if (-not $Sdk) { throw 'Android SDK bulunamadı. Android Studio > SDK Manager ile kurun.' }
    # Gradle rejects ANDROID_HOME and ANDROID_SDK_ROOT pointing at different folders.
    $env:ANDROID_HOME = $Sdk
    $env:ANDROID_SDK_ROOT = $Sdk

    # --- Java 17+: JAVA_HOME, Android Studio's JBR, Adoptium/Microsoft JDK or java on PATH
    if (-not ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe')))) {
        $env:JAVA_HOME = $null
        $cands = @(
            "$env:ProgramFiles\Android\Android Studio*\jbr",
            "$env:LOCALAPPDATA\Programs\Android Studio*\jbr",
            "$env:LOCALAPPDATA\JetBrains\Toolbox\apps\AndroidStudio\*\*\jbr",
            "$env:ProgramFiles\Eclipse Adoptium\jdk-*",
            "$env:ProgramFiles\Microsoft\jdk-*",
            "$env:ProgramFiles\Java\jdk-*"
        )
        $j = $cands | ForEach-Object { Get-Item $_ -ErrorAction SilentlyContinue } |
            Where-Object { Test-Path (Join-Path $_ 'bin\java.exe') } | Select-Object -First 1
        if (-not $j) {
            $c = Get-Command java -ErrorAction SilentlyContinue
            if ($c) { $j = Split-Path (Split-Path $c.Source) }
        }
        if ($j) { $env:JAVA_HOME = "$j" }
    }
    if (-not $env:JAVA_HOME) { throw 'Java 17 bulunamadı. JAVA_HOME ayarlayın.' }
    $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
    Write-Host "SDK: $Sdk"; Write-Host "Java: $env:JAVA_HOME"

    # --- release keystore (same key for every release, or updates fail)
    $nd = Join-Path $env:USERPROFILE '.nexsky'
    New-Item -ItemType Directory -Force -Path $nd | Out-Null
    $ks = Join-Path $nd 'android-release.jks'
    $kp = Join-Path $nd 'keystore.properties'
    if (-not (Test-Path $ks)) {
        Write-Host '== İmza anahtar deposu oluşturuluyor (bir kez)'
        $pw = ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')).Substring(0, 32)
        & keytool -genkeypair -v -keystore $ks -storetype PKCS12 -alias nexsky -keyalg RSA -keysize 4096 -validity 36500 `
            -storepass $pw -keypass $pw -dname 'CN=NexSky, O=Eirahoutmoss, C=TR'
        if ($LASTEXITCODE -ne 0) { throw 'keytool başarısız.' }
        @("storeFile=$($ks -replace '\\','/')", "storePassword=$pw", 'keyAlias=nexsky', "keyPassword=$pw") | Set-Content -Encoding ASCII $kp
        Write-Warning "YEDEKLEYİN: $ks ve $kp — kaybolursa kurulu uygulamalar güncellenemez."
    }

    # --- APK
    "sdk.dir=$($Sdk -replace '\\','/')" | Set-Content -Encoding ASCII 'local.properties'
    Write-Host '== APK derleniyor (Gradle)'
    & .\gradlew.bat --no-daemon assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Gradle derlemesi başarısız.' }
    New-Item -ItemType Directory -Force -Path 'dist' | Out-Null
    Copy-Item 'app\build\outputs\apk\release\app-release.apk' 'dist\NexSky.apk' -Force
    Write-Host ''
    Write-Host "APK HAZIR: dist\NexSky.apk (v$Ver)"
}
finally { Pop-Location }
