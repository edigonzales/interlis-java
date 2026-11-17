@echo off
@rem SPDX-License-Identifier: Apache-2.0
setlocal EnableExtensions DisableDelayedExpansion
set "JAVA_EXE=java.exe"
if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME:"=%\bin\java.exe"
"%JAVA_EXE%" --module-path "%~dp0..\lib" --module guru.interlis.validator.cli/guru.interlis.validator.cli.Main %*
exit /b %ERRORLEVEL%
