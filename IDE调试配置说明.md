# IDE 远程调试配置说明

## 前提条件

1. 使用 `run-debug.bat` 启动应用（已配置调试端口 5005）
2. 应用启动后，控制台会显示类似信息：
   ```
   Listening for transport dt_socket at address: 5005
   ```

## IntelliJ IDEA 配置步骤

### 方法一：使用图形界面配置（推荐）

1. **打开运行配置**
   - 点击右上角的运行配置下拉菜单
   - 选择 `Edit Configurations...`
   - 或使用快捷键：`Alt + Shift + F10` → `0` (Edit Configurations)

2. **创建远程调试配置**
   - 点击左上角的 `+` 号
   - 选择 `Remote JVM Debug`
   - 或选择 `Remote` → `Remote JVM Debug`

3. **配置参数**
   - **Name**: `Remote Debug - newcms` (可自定义)
   - **Host**: `localhost`
   - **Port**: `5005`
   - **Use module classpath**: 选择 `newcms`
   - **JDWP**: 保持默认（Java 9+ 会自动使用新格式）

4. **保存并启动**
   - 点击 `OK` 保存配置
   - 在运行配置下拉菜单中选择 `Remote Debug - newcms`
   - 点击调试按钮（绿色虫子图标）或按 `Shift + F9`

5. **验证连接**
   - 如果连接成功，IDEA 底部会显示 "Connected to the target VM"
   - 现在可以在代码中设置断点，断点会生效

### 方法二：直接运行主类（最简单，推荐）

如果不需要通过 Maven 启动，可以直接在 IDE 中运行：

1. 打开 `src/main/java/newcms/NewcmsApplication.java`
2. 右键点击类名或 main 方法
3. 选择 `Debug 'NewcmsApplication.main()'`
4. 这样断点会直接生效，无需配置远程调试

## Eclipse/STS 配置步骤

1. **打开调试配置**
   - 菜单：`Run` → `Debug Configurations...`
   - 或右键项目 → `Debug As` → `Debug Configurations...`

2. **创建远程调试配置**
   - 左侧展开 `Remote Java Application`
   - 右键 → `New Configuration`

3. **配置参数**
   - **Name**: `Remote Debug - newcms` (可自定义)
   - **Project**: 选择 `newcms` 项目
   - **Connection Type**: `Standard (Socket Attach)`
   - **Host**: `localhost`
   - **Port**: `5005`

4. **保存并启动**
   - 点击 `Apply` 保存
   - 点击 `Debug` 启动调试
   - 如果连接成功，Eclipse 会切换到 Debug 透视图

## Cursor / VS Code 配置步骤

### ✅ 已自动配置

我已经为你创建了 `.vscode/launch.json` 配置文件，包含以下调试配置：

1. **Attach to Remote Program (端口 5005)** - 连接到 `run-debug.bat` 启动的应用
2. **Run NewcmsApplication (直接运行)** - 直接在 Cursor 中运行应用
3. **Debug Current File** - 调试当前打开的 Java 文件

### 使用步骤

#### 方法一：远程调试（使用 run-debug.bat）

1. **启动应用**
   - 运行 `run-debug.bat` 启动应用
   - 等待看到：`Listening for transport dt_socket at address: 5005`

2. **在 Cursor 中连接调试器**
   - 按 `F5` 或点击左侧调试图标（虫子图标）
   - 在顶部下拉菜单中选择：`Attach to Remote Program (端口 5005)`
   - 点击绿色播放按钮或按 `F5` 启动调试

3. **验证连接**
   - 如果连接成功，Cursor 底部状态栏会显示调试信息
   - 现在可以在代码中设置断点，断点会生效

#### 方法二：直接运行（推荐，最简单）

1. **确保项目已编译**
   ```bash
   mvn clean compile
   ```

2. **在 Cursor 中运行**
   - 按 `F5` 或点击左侧调试图标
   - 选择：`Run NewcmsApplication (直接运行)`
   - 点击绿色播放按钮或按 `F5`
   - 这样断点会直接生效，无需配置远程调试

### 设置断点

1. 在代码行号左侧点击，会出现红色圆点（断点）
2. 触发断点所在的代码路径
3. 程序会在断点处暂停，可以查看变量值、调用栈等

### 调试快捷键

- `F5`: 继续执行 / 启动调试
- `F9`: 切换断点
- `F10`: Step Over（单步跳过）
- `F11`: Step Into（单步进入）
- `Shift + F11`: Step Out（单步跳出）
- `Shift + F5`: 停止调试

### 如果遇到问题

1. **确保安装了 Java 扩展**
   - 在 Cursor 中按 `Ctrl + Shift + X` 打开扩展面板
   - 搜索并安装 "Extension Pack for Java"（Microsoft 官方）

2. **检查 Java 环境**
   - 按 `Ctrl + Shift + P` 打开命令面板
   - 输入 `Java: Configure Java Runtime`
   - 确保指向正确的 Java 25 路径

3. **重新编译项目**
   ```bash
   mvn clean compile
   ```

## 验证调试是否生效

1. 在代码中设置断点（点击行号左侧）
2. 触发断点所在的代码路径
3. 如果调试器连接成功，程序会在断点处暂停
4. 可以查看变量值、调用栈等信息

## 常见问题

### 1. 连接失败：无法连接到 localhost:5005

**原因**：
- 应用未启动或未启用调试模式
- 端口被占用
- 防火墙阻止连接

**解决方法**：
- 确认使用 `run-debug.bat` 启动
- 检查控制台是否显示 "Listening for transport dt_socket at address: 5005"
- 检查端口是否被占用：`netstat -ano | findstr 5005`
- 临时关闭防火墙测试

### 2. 断点不生效

**原因**：
- 代码版本不一致（编译的代码与源码不同）
- 断点设置在错误的位置
- 调试器未正确连接

**解决方法**：
- 重新编译项目：`mvn clean compile`
- 确认断点设置在可执行代码行（不是空行或注释）
- 检查调试器连接状态

### 3. 调试器连接后立即断开

**原因**：
- 应用启动时出错
- JVM 参数配置错误

**解决方法**：
- 检查应用启动日志
- 确认 Java 版本正确（Java 25）
- 检查 pom.xml 中的调试配置

## 调试技巧

1. **条件断点**：右键断点 → 设置条件
2. **日志断点**：右键断点 → 选择 "Log evaluated expression"
3. **异常断点**：在断点视图中添加异常断点
4. **步进调试**：
   - `F8`: Step Over（单步跳过）
   - `F7`: Step Into（单步进入）
   - `Shift + F8`: Step Out（单步跳出）
   - `F9`: Resume（继续执行）

