@echo off
setlocal

set "REPO_DIR=C:\Users\Billy\IdeaProjects\Microbot-shortestpath-sync\"
set "JAVA_EXE=%USERPROFILE%\.jdks\temurin-17.0.19\bin\java.exe"
if not exist "%JAVA_EXE%" set "JAVA_EXE=java.exe"

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
    echo [SHORTEST PATH SPIKE] Microbot jar not found.
    echo Build it first from this worktree with: .\gradlew.bat :client:microbotReleaseJar
    pause
    exit /b 1
)

pushd "%REPO_DIR%"
for /f "delims=" %%B in ('git branch --show-current 2^>nul') do set "GIT_BRANCH=%%B"
title Microbot SHORTEST PATH SPIKE - %GIT_BRANCH%
echo ============================================================
echo  MICROBOT SHORTEST PATH SPIKE CLIENT
echo ============================================================
echo Worktree: %REPO_DIR%
if defined GIT_BRANCH echo Branch:   %GIT_BRANCH%
echo Jar:      %JAR_PATH%
echo.
echo This is the experimental upstream shortest-path integration.
echo Do not use this window as the normal Microbot launcher.
echo ============================================================
echo.
"%JAVA_EXE%" -jar "%JAR_PATH%"
echo.
echo [SHORTEST PATH SPIKE] Microbot exited with code %ERRORLEVEL%.
pause
