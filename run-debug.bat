@echo off
REM 设置 Java 版本 - 项目需要 Java 25
REM 如果您的 Java 25 安装在其他路径，请修改下面的路径
set "JAVA_HOME=C:\Program Files\Java\jdk-25"

REM 检查 Java 是否存在
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [错误] 找不到 Java 25，请检查 JAVA_HOME 路径是否正确
    echo 当前设置的路径: %JAVA_HOME%
    echo.
    echo 请确保：
    echo 1. 已安装 Java 25
    echo 2. 修改 run-debug.bat 中的 JAVA_HOME 路径为您的 Java 25 安装路径
    echo.
    pause
    exit /b 1
)

REM 验证 Java 版本
echo 使用 Java 版本: %JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version
echo.

REM 设置 PATH，确保使用正确的 Java
set "PATH=%JAVA_HOME%\bin;%PATH%"

cd /d %~dp0
echo 正在启动 Spring Boot 应用（调试模式）...
echo.
echo 当前 JAVA_HOME: %JAVA_HOME%
echo.
echo ========================================
echo 调试端口: 5005
echo 请在 IDE 中配置远程调试连接到 localhost:5005
echo ========================================
echo.

REM 设置 Maven 选项
set "MAVEN_OPTS=-Xmx1024m"

REM 使用 setlocal 确保环境变量传递
setlocal
set "JAVA_HOME=%JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
REM 使用 debug profile 启动，启用远程调试
call mvnw.cmd spring-boot:run -Pdebug
endlocal
pause

