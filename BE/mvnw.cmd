@echo off
setlocal

set "MAVEN_PROJECTBASEDIR=%~dp0"
set "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"

set "PROPERTIES_FILE=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties"

for /f "usebackq tokens=1,* delims==" %%A in ("%PROPERTIES_FILE%") do (
    if "%%A"=="distributionUrl" set "DOWNLOAD_URL=%%B"
)

set "MAVEN_USER_HOME=%USERPROFILE%\.m2\wrapper\dists"
for %%F in ("%DOWNLOAD_URL%") do set "DIST_NAME=%%~nF"

for /f "delims=-" %%V in ('echo %DIST_NAME%') do set "dummy=%%V"
set "DIST_VERSION_NAME="
for %%P in ("%DOWNLOAD_URL%") do set "ZIP_FILENAME=%%~nxP"

set "EXTRACT_DIR=%MAVEN_USER_HOME%\%DIST_NAME%"

set "MVN_CMD="
if exist "%EXTRACT_DIR%" (
    for /f "delims=" %%D in ('dir /b /ad "%EXTRACT_DIR%\apache-maven-*" 2^>NUL') do (
        if exist "%EXTRACT_DIR%\%%D\bin\mvn.cmd" set "MVN_CMD=%EXTRACT_DIR%\%%D\bin\mvn.cmd"
    )
)
if not "%MVN_CMD%"=="" goto runMaven

set "MAVEN_DIR=%MAVEN_USER_HOME%\%DIST_NAME:-bin=%"
if exist "%MAVEN_DIR%\bin\mvn.cmd" (
    set "MVN_CMD=%MAVEN_DIR%\bin\mvn.cmd"
    goto runMaven
)

echo Downloading Maven from %DOWNLOAD_URL% ...
set "ZIP_FILE=%MAVEN_USER_HOME%\%DIST_NAME%.zip"
if not exist "%MAVEN_USER_HOME%" mkdir "%MAVEN_USER_HOME%"
powershell -noprofile -command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile '%ZIP_FILE%' -UseBasicParsing"
if ERRORLEVEL 1 (
    echo Failed to download Maven distribution.
    exit /B 1
)

echo Extracting Maven...
powershell -noprofile -command "Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%EXTRACT_DIR%' -Force"
if ERRORLEVEL 1 (
    echo Failed to extract Maven distribution.
    exit /B 1
)
del "%ZIP_FILE%" 2>NUL

for /f "delims=" %%D in ('dir /b /ad "%EXTRACT_DIR%\apache-maven-*" 2^>NUL') do (
    if exist "%EXTRACT_DIR%\%%D\bin\mvn.cmd" set "MVN_CMD=%EXTRACT_DIR%\%%D\bin\mvn.cmd"
)

if "%MVN_CMD%"=="" (
    echo ERROR: Maven binary not found after extraction in %EXTRACT_DIR%
    exit /B 1
)

:runMaven
"%MVN_CMD%" %*
exit /B %ERRORLEVEL%
