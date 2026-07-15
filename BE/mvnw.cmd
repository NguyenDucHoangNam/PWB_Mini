@REM Maven Wrapper for Windows
@echo off
setlocal

set WRAPPER_JAR=%~dp0.mvn\wrapper\maven-wrapper.jar
set WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.4/maven-wrapper-3.3.4.jar

if not exist "%WRAPPER_JAR%" (
    echo Downloading Maven Wrapper JAR...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%' -UseBasicParsing"
)

set MAVEN_PROJECTBASEDIR=%~dp0
set MAVEN_CMD_LINE_ARGS=%*

java -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %MAVEN_CMD_LINE_ARGS%

if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
endlocal & set ERROR_CODE=%ERROR_CODE%
exit /B %ERROR_CODE%
