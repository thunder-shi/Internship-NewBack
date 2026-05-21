# API 接口参考

## 用户认证 (/sign)

| 接口 | 方法 | 权限 | 说明 |
|------|------|------|------|
| /sign/login | POST | 匿名 | 用户登录（支持 rememberMe） |
| /sign/logout | POST | 匿名 | 用户登出 |
| /sign/info | GET | 认证 | 获取当前登录用户信息 |
| /sign/editUserInfo/{userId} | POST | 认证 | 编辑用户信息（触发审核人刷新） |
| /sign/editPassword | POST | 认证 | 修改密码 |
| /sign/oss/uploadAvatar | POST | 认证 | 上传头像 |
| /sign/saveUserRoles | POST | 认证 | 设置用户角色（触发审核人刷新） |
| /sign/getUserListIsNotDelete | POST | 认证 | 获取用户列表（未删除） |
| /sign/getUserRoles | POST | 认证 | 获取用户角色列表 |
| /sign/getDepartment | POST | 认证 | 获取部门列表 |

## 通用 CRUD (/dataList)

| 接口 | 说明 | 参数 |
|------|------|------|
| /dataList/getSomeRecords | 条件查询 | tblName, searchKey(密文), reg, andor, sort, page, size |
| /dataList/editOneNode | 新增/编辑 | tblName, json（含 id 则更新） |
| /dataList/delOneOrManyNodes | 软删除 | tblName, ids |
| /dataList/changeTwoNodes | 交换顺序 | tblName, id1, id2 |
| /dataList/changeNodeOrder | 调整顺序 | tblName, id, newOrder |

**reg 格式**：`{"字段名": "EQ|GT|LT|LIKE|RANGE"}`  
**andor 格式**：`{"条件1|条件2": "OR"}`（默认 AND）

## 通用树形数据 (/dataTree)

readAllTreeNodes, editOneNode, delOneNode, delManyNode, changeTwoNodes, getAllParentIndex, getNearestParent, getAllBrotherIndex, getFirstParent, getAllChildIndex, commonSearch — 均 POST

## 实习项目 (/internshipProcess)

### 项目管理
| 接口 | 说明 | 参数 |
|------|------|------|
| /internshipProcess/addNewInternship | 新增实习项目（校外项目自动创建 SELF_INTERNSHIP 虚拟岗位） | internshipTypeId, name 等 |
| /internshipProcess/deleteNewInternship | 删除实习项目及关联流程 | internshipId |

### 自主实习
| 接口 | 说明 | 参数 |
|------|------|------|
| /internshipProcess/createSelfInternshipPost | 幂等创建自主实习虚拟岗位（code=SELF_INTERNSHIP, allPersonNum=-1, companyId=null）；若存在 EXTERNAL_ENTERPRISE_POST_DECLARATION 流程则追加自动通过审核 | internshipId |
| /internshipProcess/applySelfInternship | 学生申请自主实习（同学生同项目只能 1 条；SAVE/SUBMIT/PASS/BACK 拒绝；NOTPASS 重投 update-in-place 清附件）。不与企业岗位互斥 | internshipId, selfCompanyName, selfPostName, selfAddress, selfRemarks |

### 审核流程
| 接口 | 说明 | 参数 |
|------|------|------|
| /internshipProcess/auditProcess | 审核（支持批量） | node: {id, isAudit, reason} |
| /internshipProcess/activateProcess | 手动激活流程 | processId |
| /internshipProcess/getVerifyUserIds | 获取审核人ID串 | verifyRoleId, createUserId, internshipId |
| /internshipProcess/getLatestRejectedTitleSelection | 查询最近一条选题不通过记录 | stuId |
| /internshipProcess/acknowledgeRejectedTitleSelection | 确认知晓并删除不通过选题记录 | relationId, stuId |

### 师生关系
| 接口 | 说明 | 参数 |
|------|------|------|
| /internshipProcess/initTeacherStudentByInternshipId | 校内导师分配（Int 合并视图未分配行 + 补审核） | internshipId, processId, createUserId, verifyUserId, currentVerifyTypeId? |
| /internshipProcess/initInternalTutorByInternshipId | 校内导师初始化（均衡分配+增量补建） | internshipId, processId, createUserId, verifyUserId |
| /internshipProcess/getAvailableUsersForInternship | 获取可选用户列表 | internshipId, jobCode, departmentId? |
| /internshipProcess/listAssignableTeachers | 可分配老师列表（入项审核通过） | internshipId, departmentId, jobCode（SCHOOL_TEACHER / COMPANY_TUTOR） |
| /internshipProcess/listAssignableStudents | 可分配学生列表（岗位和选岗均通过） | internshipId, departmentId |
| /internshipProcess/manualAssignTeacherStudent | 手动分配老师与学生 | internshipId, processId, createUserId, verifyUserId, teacherId, studentIds |

### 统计查询
| 接口 | 说明 | 参数 |
|------|------|------|
| /internshipProcess/listExternalInternshipCollegeStats | 校外实习项目报名汇总 | departmentId? |
| /internshipProcess/listApprovedExternalInternshipPosts | 校外实习已审核岗位 | internshipId |
| /internshipProcess/getExternalInternshipStudentPostBreakdown | 校外实习学生选岗情况 | internshipId, status?, departmentId? |
| /internshipProcess/listInternalInternshipCollegeStats | 校内实习报名与选题汇总 | departmentId? |
| /internshipProcess/getInternalInternshipTitleSelectionBreakdown | 校内实习学生选题情况 | internshipId, status?, departmentId? |
| /internshipProcess/listInternalInternshipTeachersNotSubmittedTopic | 校内实习未提交题目的教师 | internshipId, departmentId? |

## 实习日志 (/diary)

| 接口 | 说明 | 参数 |
|------|------|------|
| /diary/submit | 提交/保存日志 | relationId, tableName, periodId, title?, content, submit |
| /diary/submitBatch | 批量提交/保存日志 | nodes: [{ relationId, tableName, periodId, title?, content, submit }] |
| /diary/periods | 期次列表（学生端，含 diary 状态） | relationId, tableName |
| /diary/internship-periods | 实习项目所有期次（老师端） | internshipId |
| /diary/generatePeriods | 生成/重新生成期次 | internshipId, reportStartTime, reportEndTime, cron 或 periodNum |
| /diary/period/save | 新增/编辑单条期次 | id?, internshipId?, beginTime, endTime |
| /diary/period/delete | 删除期次（存在已提交日志时拒绝） | ids |
| /diary/init-by-internship | 批量初始化学生日志占位记录（幂等） | internshipId |
| /diary/period-students | 老师查看某期学生日志 | internshipId, periodId, userId? |

## 其他接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /internshipPost/StuSelPost | POST | 学生选择/更换实习岗位（StudentId/oldPostId/newPostId 均为密文） |
| /internshipPost/StuSelPostBatch | POST | 学生批量报名岗位（StudentId + internshipPostIds 均为密文数组，单条失败不阻断） |
| /main-sign/submit-audit | POST | 提交打卡审核（幂等） node: {signId} |
| /main-leave/submit-audit | POST | 提交请假审核（幂等，仅按 verifyFirstRoleId 解析审核人，解析不到则系统自动通过） node: {leaveId, processId?, processTypeCode?} |
| /importAndExport/importExcel | POST | 导入 Excel |
| /importAndExport/exportExcel | POST | 导出 Excel |
| /importAndExport/downloadTemplate | GET | 下载导入模板 |
| /role/editRolePermissions | POST | 编辑角色权限 |
| /role/getRolePermissions | POST | 获取角色权限 |

## 通用工具 (/common)

| 接口 | 方法 | 权限 | 说明 |
|------|------|------|------|
| /common/getKey | POST | 匿名 | 获取 AES 加密密钥（5分钟过期） |
| /common/minio/upload | POST | 认证 | 上传文件（multipart/form-data），最多5个，单文件≤20MB |
| /common/minio/file/{id} | GET | 认证 | 预览文件（字节流，同校权限校验） |
| /common/minio/preview/{id} | GET | 认证 | 获取文件预览链接（presigned URL，10分钟，同校权限校验） |
| /common/minio/download/{id} | GET | 认证 | 下载文件（返回 presigned URL，有效期10分钟，同校权限校验） |
| /common/minio/deleteFile | DELETE | 认证 | 删除文件（ossFileIds 数组，仅限自己上传的文件） |
