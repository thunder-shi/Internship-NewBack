# Spring Boot 项目运行指南

这是一个 Spring Boot 3.3.5 项目，使用 Java 21 和 Maven 构建。

## 运行方式

### 方式一：使用 IDE 运行（推荐，最简单）

1. **IntelliJ IDEA**:
   - 打开项目
   - 找到 `src/main/java/com/example/newcms/NewcmsApplication.java`
   - 右键点击文件，选择 `Run 'NewcmsApplication.main()'`
   - 或直接点击类名旁边的绿色运行按钮

2. **Eclipse/STS**:
   - 导入项目（File -> Import -> Existing Maven Projects）
   - 找到 `NewcmsApplication.java`
   - 右键 -> Run As -> Java Application

3. **VS Code**:
   - 安装 Java Extension Pack
   - 打开 `NewcmsApplication.java`
   - 点击 `Run` 按钮

### 方式二：使用 Maven 命令运行

**前提条件**：需要安装 Maven 并配置到系统 PATH

1. 安装 Maven：
   - 下载：https://maven.apache.org/download.cgi
   - 解压并配置环境变量 `MAVEN_HOME` 和 `PATH`

2. 运行项目：
   ```bash
   mvn spring-boot:run
   ```

3. 或者先编译再运行：
   ```bash
   mvn clean package
   java -jar target/newcms-0.0.1-SNAPSHOT.jar
   ```

### 方式三：使用 Maven Wrapper（无需安装 Maven）

如果项目包含 Maven Wrapper（mvnw），可以直接使用：

**Windows:**
```cmd
mvnw.cmd spring-boot:run
```

**Linux/Mac:**
```bash
./mvnw spring-boot:run
```

## 验证运行

项目启动后，默认运行在：**http://localhost:8080**

你可以在浏览器中访问该地址，或者使用以下命令测试：
```bash
curl http://localhost:8080
```

## 项目配置

- **端口**: 8080（可在 `application.properties` 中修改）
- **Java版本**: 21
- **Spring Boot版本**: 3.3.5

## 常见问题

1. **端口被占用**：修改 `src/main/resources/application.properties` 中的 `server.port` 值

2. **Java版本不匹配**（重要）：
   - 项目需要 **Java 21** 或更高版本
   - 当前系统检测到 Java 1.8，需要升级
   - 下载地址：https://www.oracle.com/java/technologies/downloads/#java21
   - 或使用 OpenJDK：https://adoptium.net/
   - 安装后配置 `JAVA_HOME` 环境变量

3. **Maven依赖下载失败**：检查网络连接，或配置 Maven 镜像源

4. **Maven未安装**：
   - 如果使用IDE运行，通常不需要单独安装Maven
   - 如果使用命令行，需要安装Maven：https://maven.apache.org/download.cgi

