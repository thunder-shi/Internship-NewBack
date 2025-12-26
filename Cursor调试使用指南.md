# Cursor 调试使用指南

## ✅ 推荐方式：远程调试（最可靠）

### 步骤 1：启动应用（调试模式）

双击运行 `run-debug.bat`，或者命令行执行：
```bash
run-debug.bat
```

等待看到以下信息，表示应用已启动并等待调试器连接：
```
Listening for transport dt_socket at address: 5005
```

### 步骤 2：在 Cursor 中连接调试器

1. **按 `F5`** 或点击左侧调试图标（虫子图标 🐛）

2. **选择配置**：
   - 在顶部下拉菜单中选择：`🔗 远程调试 (端口 5005) - 推荐`

3. **启动调试**：
   - 点击绿色播放按钮 ▶️ 或按 `F5`

4. **验证连接**：
   - 如果连接成功，Cursor 底部状态栏会显示调试信息
   - 状态栏会显示 "调试" 或 "Debug" 字样

### 步骤 3：设置断点并测试

1. **设置断点**：
   - 在代码行号左侧点击，会出现红色圆点 🔴
   - 例如：在 `RoleInfoController.java` 第 20 行设置断点

2. **触发断点**：
   - 调用相应的 API 接口
   - 程序会在断点处暂停 ⏸️

3. **查看变量**：
   - 鼠标悬停在变量上查看值
   - 左侧调试面板可以查看所有变量、调用栈等

---

## ⚠️ 如果远程调试不行，使用直接运行方式

### 前提条件

1. **确保安装了 Java 扩展**：
   - 按 `Ctrl + Shift + X` 打开扩展面板
   - 搜索并安装 **"Extension Pack for Java"**（Microsoft 官方）

2. **让 Java 扩展加载项目**：
   - 按 `Ctrl + Shift + P` 打开命令面板
   - 输入：`Java: Clean Java Language Server Workspace`
   - 执行后重启 Cursor
   - 等待右下角 "Java Projects" 加载完成（可能需要几分钟）

### 使用步骤

1. **编译项目**（首次使用）：
   - 按 `Ctrl + Shift + P`
   - 输入：`Tasks: Run Task`
   - 选择：`Maven: Compile`

2. **运行调试**：
   - 按 `F5`
   - 选择：`▶️ 直接运行 (需要先编译)`
   - 点击播放按钮

---

## 🎯 调试快捷键

- **`F5`**: 继续执行 / 启动调试
- **`F9`**: 切换断点（在当前行添加/删除断点）
- **`F10`**: Step Over（单步跳过，不进入函数内部）
- **`F11`**: Step Into（单步进入，进入函数内部）
- **`Shift + F11`**: Step Out（单步跳出，跳出当前函数）
- **`Shift + F5`**: 停止调试

---

## 🔧 常见问题解决

### 问题 1：找不到主类 `newcms.NewcmsApplication`

**原因**：项目未编译或 Java 扩展未正确识别项目

**解决方法**：
1. 使用远程调试方式（推荐，不需要编译）
2. 或者先编译：`Ctrl + Shift + P` → `Tasks: Run Task` → `Maven: Compile`

### 问题 2：连接失败 "无法连接到 localhost:5005"

**原因**：
- 应用未启动
- 端口被占用
- 防火墙阻止

**解决方法**：
1. 确认 `run-debug.bat` 正在运行
2. 检查控制台是否显示 "Listening for transport dt_socket at address: 5005"
3. 检查端口：`netstat -ano | findstr 5005`

### 问题 3：断点不生效

**原因**：
- 代码版本不一致
- 断点设置在错误位置
- 调试器未连接

**解决方法**：
1. 确认调试器已连接（底部状态栏显示调试信息）
2. 确认断点设置在可执行代码行（不是空行或注释）
3. 重新编译项目：`mvn clean compile`

### 问题 4：Java 扩展无法识别项目

**解决方法**：
1. 确保安装了 "Extension Pack for Java"
2. 按 `Ctrl + Shift + P` → `Java: Clean Java Language Server Workspace`
3. 重启 Cursor
4. 等待项目加载完成（查看右下角状态）

---

## 💡 调试技巧

1. **条件断点**：
   - 右键断点 → 选择 "Edit Breakpoint"
   - 设置条件，例如：`roleId != null && roleId.equals("1")`

2. **日志断点**：
   - 右键断点 → 选择 "Logpoint"
   - 输入要打印的表达式，例如：`roleId: ${roleId}`

3. **异常断点**：
   - 在断点面板中点击 "+" → 选择 "Exception Breakpoint"
   - 可以捕获所有异常或特定异常

4. **查看调用栈**：
   - 左侧调试面板的 "Call Stack" 可以查看完整的调用链

---

## 📝 总结

**最简单可靠的方式**：
1. 运行 `run-debug.bat` 启动应用
2. 在 Cursor 中按 `F5`，选择 `🔗 远程调试 (端口 5005) - 推荐`
3. 设置断点，开始调试

这样就不需要配置 Java 扩展，也不需要编译项目，直接就能调试！

