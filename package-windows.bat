@echo off
setlocal

set ROOT=%~dp0
pushd "%ROOT%" >nul

echo Building RepDev...
call "C:\Users\soul\AppData\Local\apache-maven-3.9.15\bin\mvn.cmd" -q -DskipTests clean package
if errorlevel 1 goto fail

if not exist "target\app\repdev.jar" (
  echo Missing target\app\repdev.jar
  goto fail
)

rem Locate jpackage. Prefer JAVA_HOME (same JDK that ran Maven above); fall
rem back to PATH lookup. jpackage ships with the JDK starting in 14.
set JPACKAGE=
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" set JPACKAGE=%JAVA_HOME%\bin\jpackage.exe
if not defined JPACKAGE for /f "delims=" %%I in ('where jpackage 2^>nul') do set JPACKAGE=%%I
if not defined JPACKAGE (
  echo jpackage not found. Install JDK 14+ and either point JAVA_HOME at it
  echo or add its bin directory to PATH.
  goto fail
)

echo Running jpackage...
"%JPACKAGE%" ^
  --type app-image ^
  --input target\app ^
  --main-jar repdev.jar ^
  --main-class com.repdev.RepDevMain ^
  --name RepDev ^
  --dest target\windows ^
  --icon repdev-icons\monkeyIcon32.ico ^
  --app-version 2.0.0 ^
  --vendor "RepDev LLC" ^
  --description "RepDev" ^
  --copyright "(c) 2008-2026 RepDev LLC" ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --java-options "-Xms128m" ^
  --java-options "-Xmx384m"
if errorlevel 1 goto fail

echo App image ready at target\windows\RepDev\RepDev.exe

popd >nul
exit /b 0

:fail
popd >nul
exit /b 1
