---
name: api-test
description: 为指定 API 接口生成 curl shell 测试脚本，支持登录态管理和结果断言
user_invocable: true
---

# API 接口测试脚本生成

为本项目的 REST API 接口生成可直接运行的 curl shell 测试脚本。

## 使用方式

```
/api-test <接口路径或描述>
```

示例：
- `/api-test /sign/login` — 生成登录接口测试
- `/api-test 实习项目管理全部接口` — 生成 InternshipProcessController 所有接口测试
- `/api-test /internshipPost/StuSelPost` — 生成学生选岗接口测试

## 执行步骤

1. **识别目标接口**：根据用户输入在 `controller/` 目录下找到对应的 Controller 类，读取完整源码
2. **分析接口签名**：提取 HTTP 方法、路径、请求参数（@RequestBody / @PathVariable / @RequestParam）、是否需要加密
3. **判断认证需求**：检查接口是否在 ShiroConfig 的匿名路径中（`/sign/login`, `/sign/logout`, `/common/**`），不在则需要先登录
4. **判断加密需求**：检查 Controller 中是否调用了 `encryptUtil.getKeyWord()`，若调用则该参数需要加密传输（标注为需要加密，在脚本中添加说明）
5. **生成测试脚本**，输出到 `tests/` 目录，遵循以下规范：

## 脚本规范

### 文件结构
```
tests/
├── config.sh              # 全局配置（BASE_URL、账号、工具函数）
└── test_<模块名>.sh        # 各模块测试脚本
```

### config.sh 必须包含
```bash
BASE_URL="${BASE_URL:-http://localhost:8111}"
COOKIE_JAR="/tmp/internship_test_cookies.txt"
TEST_ACCOUNT="${TEST_ACCOUNT:-15505096851}"
TEST_PASSWORD="${TEST_PASSWORD:-Axt-1234}"
```

以及以下工具函数：
- `api_post <path> <json>` — 发送 POST 请求，结果存入 `$HTTP_CODE` 和 `$HTTP_BODY`
- `api_get <path>` — 发送 GET 请求
- `api_put <path> <json>` — 发送 PUT 请求
- `api_delete <path>` — 发送 DELETE 请求
- `assert_http_code <code>` — 断言 HTTP 状态码
- `assert_json_field <jq_path> <expected>` — 断言 JSON 字段值（用 python3 解析）
- `assert_body_contains <string>` — 断言响应体包含指定字符串
- `run_test <name> <function>` — 运行测试并打印 PASS/FAIL
- `ensure_login` — 确保已登录（调用 /sign/login）
- `json_val <jq_path>` — 从 $HTTP_BODY 提取字段值
- `section <title>` — 打印分组标题
- `print_summary` — 打印测试汇总

### 测试脚本模板
```bash
#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/config.sh"

# 清理旧 cookie
rm -f "$COOKIE_JAR"

section "模块名称"
ensure_login

test_xxx() {
    api_post "/path" '{"key":"value"}'
    assert_http_code 200 && assert_json_field ".status" "200"
}
run_test "测试描述" test_xxx

print_summary
```

### 关键约定

- **Cookie 管理**：所有请求通过 `-b "$COOKIE_JAR" -c "$COOKIE_JAR"` 管理 Shiro 会话
- **加密接口**：若接口参数需要 AES 加密（使用了 `encryptUtil.getKeyWord`），在脚本中加注释说明，并提供绕过方案（如直接用 `/common/` 端点代替 `/dataList/` 端点）
- **通用查询替代**：`/dataList/getSomeRecords` 需要加密参数，测试时优先使用 `/common/getSomeRecords/{tblName}`（无需加密）
- **默认账号**：`15505096851 / Axt-1234`（见 SignController 注释）
- **端口**：默认 8111
- **颜色输出**：PASS 绿色、FAIL 红色、SKIP 黄色
- **退出码**：有失败测试时 exit 1

### 本项目可测试接口总览

**无需加密的接口**（直接测试）：
- `POST /sign/login` — 登录
- `POST /sign/logout` — 登出
- `GET /sign/info` — 获取登录用户信息
- `POST /sign/getUserListIsNotDelete` — 用户列表
- `POST /sign/getUserRoles` — 用户角色
- `POST /sign/saveUserRoles` — 保存用户角色
- `POST /sign/getDepartment` — 获取部门
- `POST /internshipProcess/addNewInternship` — 新增实习项目
- `POST /internshipProcess/auditProcess` — 审核推进
- `POST /internshipProcess/activateProcess` — 激活流程
- `POST /internshipProcess/getVerifyUserIds` — 获取审核人
- `POST /internshipProcess/getAvailableUsersForInternship` — 可选用户
- `POST /internshipProcess/initTeacherStudentByInternshipId` — 初始化师生关系
- `POST /internshipProcess/initInternalTutorByInternshipId` — 校内导师初始化
- `POST /internshipProcess/initEnterpriseTutorByInternshipId` — 企业导师初始化
- `POST /internshipProcess/listExternalInternshipCollegeStats` — 学院实习汇总
- `POST /internshipProcess/listApprovedExternalInternshipPosts` — 已审核岗位列表
- `POST /internshipProcess/getExternalInternshipStudentPostBreakdown` — 学生选岗情况
- `POST /diary/submit` — 提交/保存日志
- `POST /diary/periods` — 学生端期次列表
- `POST /diary/internship-periods` — 老师端期次定义列表
- `POST /diary/generatePeriods` — 生成期次
- `POST /diary/period/save` — 新增/编辑期次
- `POST /diary/period/delete` — 删除期次
- `POST /diary/init-by-internship` — 批量初始化日志占位
- `POST /diary/period-students` — 老师查看某期学生日志
- `GET /common/getSomeRecords/{tblName}` — 通用查询（无需加密）
- `PUT /common/saveOneRecord?tblName=Xxx` — 通用保存
- `DELETE /common/deleteRecordByDelflag?tblName=Xxx&id=N` — 通用软删除
- `POST /common/getKey` — 获取加密密钥

**需要加密的接口**（脚本中标注说明）：
- `POST /dataList/getSomeRecords` — keyWords、searchKey、reg 需加密
- `POST /dataList/editOneNode` — keyWords 需加密
- `POST /dataList/delOneOrManyNodes` — keyWords、ids 需加密
- `POST /internshipProcess/deleteNewInternship` — ids 需加密
- `POST /internshipPost/StuSelPost` — StudentId、oldPostId、newPostId 需加密
