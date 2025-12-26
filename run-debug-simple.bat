@echo off
REM 简化版本：只保存错误信息到文件
setlocal enabledelayedexpansion

set "JAVA_HOME=C:\Program Files\Java\jdk-25"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "MAVEN_OPTS=-Xmx1024m"

cd /d %~dp0

REM 创建日志文件
set "LOG_FILE=error-log.txt"
echo 正在运行并保存错误信息到 %LOG_FILE% ...
echo.

REM 运行并保存所有输出（包括错误）
call mvnw.cmd clean compile spring-boot:run -e -X > "%LOG_FILE%" 2>&1

REM 显示最后50行错误信息
echo.
echo ========================================
echo 最后50行输出（完整信息在 %LOG_FILE% 文件中）:
echo ========================================
powershell -Command "Get-Content '%LOG_FILE%' -Tail 50"

echo.
echo ========================================
echo 完成！所有输出已保存到: %LOG_FILE%
echo ========================================
echo.
echo 请将 %LOG_FILE% 文件的内容发送给我
echo.
pause

