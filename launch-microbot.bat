@echo off
setlocal

set "REPO_DIR=%~dp0"
set "JAVA_EXE=%USERPROFILE%\.jdks\temurin-17.0.19\bin\javaw.exe"
if not exist "%JAVA_EXE%" set "JAVA_EXE=javaw.exe"

for /f "tokens=2 delims==" %%V in ('findstr /b "microbot.version=" "%REPO_DIR%gradle.properties"') do set "MICROBOT_VERSION=%%V"
set "JAR_PATH=%REPO_DIR%runelite-client\build\libs\microbot-%MICROBOT_VERSION%.jar"

if not exist "%JAR_PATH%" (
    for /f "delims=" %%J in ('dir /b /o-d "%REPO_DIR%runelite-client\build\libs\microbot-*.jar" 2^>nul') do (
        set "JAR_PATH=%REPO_DIR%runelite-client\build\libs\%%J"
        goto :launch
    )
)

:launch
if not exist "%JAR_PATH%" (
    echo Microbot jar not found.
    echo Build it first with: .\gradlew.bat :client:microbotReleaseJar
    pause
    exit /b 1
)

start "Microbot" /D "%REPO_DIR%" "%JAVA_EXE%" -jar "%JAR_PATH%"
