@echo off
REM 简化版本：只保存错误信息到文件
setlocal enabledelayedexpansion

set "JAVA_HOME=C:\Program Files\Java\jdk-25"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "MAVEN_OPTS=-Xmx1024m"

cd /d %~dp0

REM 创建日志文件（清空旧日志）
set "LOG_FILE=error-log.txt"
echo 正在运行并保存错误信息到 %LOG_FILE% ...
echo 开始时间: %date% %time% > "%LOG_FILE%"
echo ======================================== >> "%LOG_FILE%"
echo.

REM 先清理并编译（可选，确保代码最新）
echo 正在清理并编译项目...
call mvnw.cmd clean compile -e >> "%LOG_FILE%" 2>&1
if errorlevel 1 (
    echo 编译失败！请查看 %LOG_FILE% 文件
    goto :show_log
)

REM 运行应用并保存所有输出（包括错误）
echo 编译成功，正在启动应用...
echo ======================================== >> "%LOG_FILE%"
echo 开始运行应用... >> "%LOG_FILE%"
echo ======================================== >> "%LOG_FILE%"
REM 使用 -e 显示错误堆栈，如果需要更详细的调试信息可以使用 -X（但会产生大量输出）
call mvnw.cmd spring-boot:run -e >> "%LOG_FILE%" 2>&1

:show_log
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

