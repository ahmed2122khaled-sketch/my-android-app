$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Tools = Join-Path $Root '.tools'
$Sdk = Join-Path $Tools 'android-sdk'
$GradleVersion = '9.7.1'
$GradleDir = Join-Path $Tools "gradle-$GradleVersion"
New-Item -ItemType Directory -Force $Tools | Out-Null
if (!(Test-Path (Join-Path $Root 'gradle\wrapper\gradle-wrapper.jar'))) {
  try { & (Join-Path $Root 'tools\fetch-gradle-wrapper.ps1') } catch { Write-Warning 'Wrapper JAR download unavailable; continuing with bundled Gradle bootstrap.' }
}
$java = & java -version 2>&1 | Out-String
if ($java -notmatch 'version "(17|18|19|20|21|22|23|24|25)') { throw 'JDK 17+ is required.' }
if (!(Test-Path (Join-Path $GradleDir 'bin\gradle.bat'))) {
  $zip = Join-Path $Tools "gradle-$GradleVersion-bin.zip"
  Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" -OutFile $zip
  Expand-Archive $zip -DestinationPath $Tools -Force
}
$cmd = Join-Path $Sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
if (!(Test-Path $cmd)) {
  $zip = Join-Path $Tools 'commandlinetools-win.zip'
  Invoke-WebRequest 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' -OutFile $zip
  $tmp = Join-Path $Tools 'cmdline-tools-tmp'
  Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
  Expand-Archive $zip -DestinationPath $tmp -Force
  New-Item -ItemType Directory -Force (Join-Path $Sdk 'cmdline-tools\latest') | Out-Null
  Copy-Item (Join-Path $tmp 'cmdline-tools\*') (Join-Path $Sdk 'cmdline-tools\latest') -Recurse -Force
  Remove-Item $tmp -Recurse -Force
}
$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk
$env:Path = "$(Join-Path $GradleDir 'bin');$(Join-Path $Sdk 'platform-tools');$(Join-Path $Sdk 'cmdline-tools\latest\bin');$env:Path"
& $cmd --licenses | Out-Null
& $cmd --install 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'
& (Join-Path $GradleDir 'bin\gradle.bat') --no-daemon ':app:assembleDebug'
$apk = Join-Path $Root 'app\build\outputs\apk\debug\app-debug.apk'
if (!(Test-Path $apk)) { throw 'APK was not produced.' }
Get-FileHash $apk -Algorithm SHA256
Write-Host "APK=$apk"
