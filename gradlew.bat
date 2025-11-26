@ECHO OFF
SETLOCAL

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.

set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

SET CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

SET JAVA_EXE=java.exe

"%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
