@echo off
setlocal
set "APP_HOME=%~dp0"
if exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
  where java >nul 2>&1
  if errorlevel 1 (
    echo Java is required to run Gradle Wrapper.
    exit /b 1
  )
  java -jar "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" %*
  exit /b %errorlevel%
)
if exist "%APP_HOME%.tools\gradle-9.7.1\bin\gradle.bat" (
  call "%APP_HOME%.tools\gradle-9.7.1\bin\gradle.bat" --project-dir "%APP_HOME%" %*
  exit /b %errorlevel%
)
where gradle >nul 2>&1
if not errorlevel 1 (
  gradle --project-dir "%APP_HOME%" %*
  exit /b %errorlevel%
)
echo Gradle Wrapper JAR is missing. Run tools\fetch-gradle-wrapper.ps1 or tools\bootstrap-windows.ps1 first.
exit /b 127
