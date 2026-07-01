@echo off
set "JAVA_HOME=C:\Users\Billy\.jdks\temurin-17.0.19"
set "PATH=%JAVA_HOME%\bin;%PATH%"
java -jar "%~dp0runelite-client\build\libs\microbot-2.6.10.jar"
