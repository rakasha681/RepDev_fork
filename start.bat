@echo off
setlocal

set JPATH=
"%JPATH%java.exe" -version

echo.

set APPDIR=%~dp0
if exist "%APPDIR%lib\jsvg-2.0.0.jar" goto packaged
if exist "%APPDIR%target\app\lib\jsvg-2.0.0.jar" set APPDIR=%APPDIR%target\app\

:packaged
if exist "%APPDIR%lib\jsvg-2.0.0.jar" (
  "%JPATH%java.exe" -cp "%APPDIR%repdev.jar;%APPDIR%lib\*" -Djava.library.path="%APPDIR%lib" -Dfile.encoding=UTF-8 com.repdev.RepDevMain
) else (
  echo RepDev is not packaged yet. Run: mvn -q -DskipTests package
  exit /b 1
)

if NOT %ERRORLEVEL% == 0 pause
