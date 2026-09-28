@rem PixelForge one-step Gradle launcher for Windows (self-bootstrapping).
@rem On first run it fetches gradle-wrapper.jar, then delegates to Gradle.
@rem Requires JDK 17+ and network access.
@echo off
setlocal
set APP_HOME=%~dp0
set JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set WRAPPER_URL=https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar

if not exist "%JAR%" (
  echo [PixelForge] Fetching gradle-wrapper.jar (first run only)...
  if not exist "%APP_HOME%gradle\wrapper" mkdir "%APP_HOME%gradle\wrapper"
  powershell -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%JAR%'"
)

if defined JAVA_HOME (
  set JAVACMD=%JAVA_HOME%\bin\java.exe
) else (
  set JAVACMD=java
)

"%JAVACMD%" -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
