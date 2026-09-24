$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Version = '9.7.1'
$Url = "https://services.gradle.org/distributions/gradle-$Version-wrapper.jar"
$Expected = '7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d'
$Out = Join-Path $Root 'gradle\wrapper\gradle-wrapper.jar'
$Tmp = "$Out.download"
New-Item -ItemType Directory -Force (Split-Path $Out) | Out-Null
Invoke-WebRequest $Url -OutFile $Tmp
$Actual = (Get-FileHash $Tmp -Algorithm SHA256).Hash.ToLowerInvariant()
if ($Actual -ne $Expected) { Remove-Item $Tmp -Force; throw "Wrapper JAR SHA-256 mismatch. Expected $Expected, actual $Actual" }
Move-Item $Tmp $Out -Force
Write-Host "Installed official Gradle $Version Wrapper JAR. SHA-256=$Actual"
