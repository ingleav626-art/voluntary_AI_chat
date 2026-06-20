@echo off
cd /d D:\voluntary_AI_chat
echo === Installing dependencies ===
call mvnw.cmd install -pl common,server -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true -q
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Dependency installation failed!
    pause
    exit /b 1
)
echo === Starting Unified Application (Backend + Frontend) ===
call mvnw.cmd javafx:run -pl client -Dcheckstyle.skip=true -Dspotbugs.skip=true
pause
