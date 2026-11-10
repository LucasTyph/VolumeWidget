@echo off
rem Builds the debug APK -> build\outputs\apk\debug\VolumeWidget-debug.apk
set "JAVA_HOME=%~dp0tools\jdk"
set "ANDROID_HOME=%~dp0tools\sdk"
call "%~dp0tools\gradle\bin\gradle.bat" -p "%~dp0." assembleDebug %*
