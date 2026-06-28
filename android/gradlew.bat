@echo off
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

set DIR=%~dp0
set APP_HOME=%DIR%
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java.exe
)

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% ^
  -Dorg.gradle.appname=gradlew ^
  -classpath "%CLASSPATH%" ^
  org.gradle.wrapper.GradleWrapperMain %*
