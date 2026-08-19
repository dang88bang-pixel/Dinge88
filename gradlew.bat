@rem
@rem SecureGuard Enterprise - self-bootstrapping Gradle wrapper (Windows).
@rem SPDX-License-Identifier: Apache-2.0
@rem
@echo off
setlocal

set APP_HOME=%~dp0

rem Parse distributionUrl from gradle-wrapper.properties.
set WRAPPER_PROPS=%APP_HOME%gradle\wrapper\gradle-wrapper.properties
set DIST_URL=
for /f "usebackq tokens=1,* delims==" %%a in ("%WRAPPER_PROPS%") do (
  if "%%a"=="distributionUrl" set DIST_URL=%%b
)

if "%DIST_URL%"=="" (
  echo ERROR: distributionUrl not found in %WRAPPER_PROPS% 1>&2
  exit /b 1
)

rem Extract version like 8.5 from .../gradle-8.5-bin.zip
for /f "tokens=2 delims=-" %%v in ("%DIST_URL%") do set GRADLE_VERSION=%%v

if "%GRADLE_VERSION%"=="" (
  echo ERROR: Could not parse Gradle version from %DIST_URL% 1>&2
  exit /b 1
)

set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set DIST_DIR=%GRADLE_USER_HOME%\wrapper\dists\gradle-%GRADLE_VERSION%-bin
set ZIP=%DIST_DIR%\gradle-%GRADLE_VERSION%-bin.zip
set GRADLE_HOME=%DIST_DIR%\gradle-%GRADLE_VERSION%

if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  mkdir "%DIST_DIR%" 2>nul
  if not exist "%ZIP%" (
    echo Downloading Gradle %GRADLE_VERSION% from %DIST_URL% ...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%ZIP%'" || exit /b 1
  )
  echo Extracting Gradle %GRADLE_VERSION% ...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP%' -DestinationPath '%DIST_DIR%' -Force" || exit /b 1
)

call "%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
