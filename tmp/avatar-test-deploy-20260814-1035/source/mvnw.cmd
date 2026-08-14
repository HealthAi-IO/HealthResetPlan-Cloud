@echo off
setlocal

set "PROJECT_DIR=%~dp0"
set "MAVEN_CMD=%PROJECT_DIR%..\tools\maven\apache-maven-3.9.9\bin\mvn.cmd"
set "MAVEN_REPO=%PROJECT_DIR%..\.m2repo"

if not exist "%MAVEN_CMD%" (
  echo Maven 3.9.9 was not found at "%MAVEN_CMD%".
  echo Please check the tools\maven directory.
  exit /b 1
)

call "%MAVEN_CMD%" -Dmaven.repo.local="%MAVEN_REPO%" %*
exit /b %ERRORLEVEL%
