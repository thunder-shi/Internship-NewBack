# 实体与视图参考

## 实体公共字段组

### 组一：BaseInfo（所有表默认包含）

| 字段       | 类型         | 说明             |
| ---------- | ------------ | ---------------- |
| id         | int unsigned | 主键，自增       |
| createTime | timestamp    | 创建时间         |
| updateTime | timestamp    | 更新时间         |
| isDeleted  | bit(1)       | 删除标记，默认 0 |

### 组二：NameRemarkInfo（继承组一）

code, name, remarks — 类型属性型数据表基础字段

### 组三：NameRemarkOrderInfo（继承组二）

theOrder — 排序字段

### 组四：BaseTreeInfo（树形结构，继承组二+组三）

parentId, theLevel, isLeaf, childNum

### 组五：VerifyConfigInfo（审核配置，继承组一）

verifyTypeId, verifyFirstRoleId ~ verifyFifthRoleId（含此组的表需要审核）

### 组六：VerifyProcessInfo（审核进度）

currentVerifyTypeId（含此组的表记录当前审核进度）

---

## 业务实体

| 实体                     | 表名                        | 关键字段                                                                                                  |
| ------------------------ | --------------------------- | --------------------------------------------------------------------------------------------------------- |
| MainInternship           | main_internship             | internshipTypeId, creatorId, cron, studentNum                                                             |
| MainInternshipPost       | main_internship_post        | internshipId, postTypeId, allPersonNum, nowPersonNum                                                      |
| MainVerifyProcess        | main_verify_process         | relationId, createUserId, verifyUserId, isAudit, reason, tableName                                        |
| MainDiary                | main_diary                  | relationId, tableName, periodId, content, submit, currentVerifyTypeId                                     |
| MainDiaryPeriod          | main_diary_period           | internshipId, periodIndex, beginTime, endTime, name                                                       |
| MainSign                 | main_sign                   | stuInternshipId, address, signType, imgId, verifyFirstRoleId~verifyFifthRoleId                            |
| MainLeave                | main_leave                  | stuInternshipId, startTime, endTime, remarks, currentVerifyTypeId, verifyFirstRoleId~verifyFifthRoleId（表22，BaseInfo+VerifyConfigInfo）    |
| RelProcessInternship     | rel_process_internship      | internshipId, processTypeId, verifyFirstRoleId~verifyFifthRoleId, startTime, endTime, currentVerifyTypeId |
| RelProcessInternshipType | rel_process_internship_type | internshipTypeId, processTypeId, 多级审核角色                                                             |
| RelTeacherStudent        | rel_teacher_student         | teacherId, studentId, relInternshipId                                                                     |
| RelTitleTeacher          | rel_title_teacher           | internshipId, teacherId, isLimit                                                                          |
| RelTitleStudent          | rel_title_student           | titleId, stuId, currentVerifyTypeId                                                                       |
| RelIntershipUser         | rel_intership_user          | internshipId, userId, currentVerifyTypeId                                                                 |
| RelStuInternshipPost     | rel_stu_internship_post     | studentId, internshipPostId, currentVerifyTypeId, selfCompanyName/selfPostName/selfAddress/selfRemarks（自主实习 4 字段）                                      |
| RelInterMajor            | rel_inter_major             | internshipId, majorId                                                                                     |
| RelInterTypeMajor        | rel_inter_type_major        | internshipTypeId, majorId                                                                                 |
| RelPostMajor             | rel_post_major              | postTypeId, majorId                                                                                       |

## 基础实体

| 实体               | 表名                 | 说明                                                   |
| ------------------ | -------------------- | ------------------------------------------------------ |
| BaseUser           | base_user            | phone, account, password, departmentId, jobId, majorId |
| BaseDepartment     | base_department      | name, parentId, departTypeId, schoolId                 |
| BaseInternshipType | base_internship_type | 实习类型                                               |
| BasePostType       | base_post_type       | 岗位类型                                               |
| BaseProcessType    | base_process_type    | 流程类型                                               |
| BaseIntType        | base_int_type        | 整数类型字典                                           |
| BaseVerifyType     | base_verify_type     | 审核类型                                               |
| BaseDepartType     | base_depart_type     | 部门类型                                               |
| BaseMajor          | base_major           | name, code, schoolId                                   |
| BaseJobPosition    | base_job_position    | 职位                                                   |

## 系统实体

| 实体        | 表名          | 说明                                                                           |
| ----------- | ------------- | ------------------------------------------------------------------------------ |
| SysRole     | sys_role      | name, code                                                                     |
| SysMenu     | sys_menu      | name, path, component, icon, parentId, permission                              |
| SysArea     | sys_area      | name, code, parentId, level                                                    |
| SysLogger   | sys_logger    | userId, action, detail                                                         |
| SysOssFile  | sys_oss_file  | fileName(原始名), name(MinIO存储名), ossPath, fileSize, relationIds, tableName |
| RelRoleMenu | rel_role_menu | roleId, menuId                                                                 |
| RelUserRole | rel_user_role | userId, roleId                                                                 |

## 视图实体（共 40 个）

**视图列名规则**：数据库视图使用下划线格式，JPA 自动映射到驼峰字段；部分视图用 AS 别名输出驼峰格式，注意区分。

| 实体                                       | 视图名                                            | 说明                                                                                |
| ------------------------------------------ | ------------------------------------------------- | ----------------------------------------------------------------------------------- |
| ViewMainInternship                         | view_main_internship                              | 实习项目（含类型、创建人）                                                          |
| ViewMainInternshipPost                     | view_main_internship_post                         | 实习岗位（含岗位类型、公司）                                                        |
| ViewMainDiary                              | view_main_diary                                   | 日志（含学生、岗位、课题，含 studentAccount）                                       |
| ViewMainSign                               | view_main_sign                                    | 打卡（含学生、岗位详情，含 studentAccount）                                         |
| ViewRelProcessInternship                   | view_rel_process_internship                       | 实习流程（含流程类型、审核角色）                                                    |
| ViewRelProcessInternshipType               | view_rel_process_internship_type                  | 实习类型流程                                                                        |
| ViewBaseUser                               | view_base_user                                    | 用户（含部门、职位、专业）                                                          |
| ViewBaseDepartment                         | view_base_department                              | 部门（含父级、部门类型）                                                            |
| ViewBaseInternshipType                     | view_base_internship_type                         | 实习类型                                                                            |
| ViewBasePostType                           | view_base_post_type                               | 岗位类型                                                                            |
| ViewUserRoleDetail                         | view_user_role_detail                             | 用户角色详情（含角色权限）                                                          |
| ViewRelTeacherStudent                      | view_rel_teacher_student                          | 师生关系（校外导师，含 studentAccount）                                             |
| ViewRelTitleTeacher                        | view_rel_title_teacher                            | 课题-导师（校内）                                                                   |
| ViewRelTitleStudent                        | view_rel_title_student                            | 课题-学生选题（含 studentAccount）                                                  |
| ViewRelTitleTeacherStudent                 | view_rel_title_teacher_student                    | 校内师生综合（isAudit 来自 RelTitleTeacher，含 studentAccount）                     |
| ViewRelStuInternshipPost                   | view_rel_stu_internship_post                      | 学生选岗（含学生、岗位、公司，含 studentAccount）                                   |
| ViewRelIntershipUser                       | view_rel_intership_user                           | 学生-实习项目关联（含审核信息）                                                     |
| ViewVerifyProcessInternship                | view_verify_process_internship                    | 实习计划审核                                                                        |
| ViewVerifyProcessInternshipMerge           | view_verify_process_internship_merge              | 实习计划审核综合（最新记录，含 isAllVerified）                                      |
| ViewVerifyProcessInternshipPost            | view_verify_process_internship_post               | 岗位审核                                                                            |
| ViewVerifyProcessInternshipPostMerge       | view_verify_process_internship_post_merge         | 岗位审核综合（含 isAllVerified）                                                    |
| ViewVerifyProcessRelStuInternshipPost      | view_verify_process_rel_stu_internship_post       | 学生选岗审核（含 studentAccount）                                                   |
| ViewVerifyProcessRelStuInternshipPostMerge | view_verify_process_rel_stu_internship_post_merge | 学生选岗审核综合（含 isAllVerified，含 studentAccount）                             |
| ViewVerifyProcessRelIntershipUserMerge     | view_verify_process_rel_intership_user_merge      | 学生入项审核综合（含 isAllVerified）                                                |
| ViewVerifyProcessRelTeacherStudent         | view_verify_process_rel_teacher_student           | 校外导师分配审核                                                                    |
| ViewVerifyProcessRelEntTeacherStudentMerge | view_verify_process_rel_ass_teacher_student_merge | 企业/校外导师师生审核综合（字段同原 merge）                                         |
| ViewVerifyProcessRelIntTeacherStudentMerge | view_verify_process_rel_int_teacher_student_merge | 校内导师师生审核综合（业务查询常限 processTypeCode=EXTERNAL_ASSIGN_INTERNAL_TUTOR） |
| ViewVerifyProcessRelTitleStudent           | view_verify_process_rel_title_student             | 校内选题审核（含 studentAccount）                                                   |
| ViewVerifyProcessRelTitleStudentMerge      | view_verify_process_rel_title_student_merge       | 校内选题审核综合（含 isAllVerified，含 studentAccount）                             |
| ViewVerifyProcessRelTitleTeacher           | view_verify_process_rel_title_teacher             | 课题-导师审核                                                                       |
| ViewVerifyProcessRelTitleTeacherMerge      | view_verify_process_rel_title_teacher_merge       | 课题-导师审核综合（含 isAllVerified）                                               |
| ViewVerifyMainDiary                        | view_verify_main_diary                            | 日志审核（含 studentAccount）                                                       |
| ViewVerifyMainDiaryMerge                   | view_verify_main_diary_merge                      | 日志审核综合（最新记录，含 isAllVerified，含 studentAccount）                       |
| ViewVerifyMainSign                         | view_verify_main_sign                             | 打卡审核（含 studentAccount）                                                       |
| ViewVerifyMainSignMerge                    | view_verify_main_sign_merge                       | 打卡审核综合（最新记录，含 isAllVerified，含 studentAccount）                       |
| ViewExternalInternshipCollegeStats         | view_external_internship_college_stats            | 校外实习学院汇总统计                                                                |
| ViewExternalInternshipCollegeStatsId       | view_external_internship_college_stats_id         | 校外实习学院统计辅助（ID列）                                                        |
| ViewLeaveUniversalDetails                  | view_leave_universal_details                      | 请假业务全量视图（抹平校内/校外差异，含 studentAccount、internshipMode、relationTable） |
| ViewLeaveAuditFlow                         | view_leave_audit_flow                             | 请假审核流向视图（MainVerifyProcess + BaseVerifyType 聚合，含 verifyTypeOrder、nextVerifyLevel） |
| ViewAuditorTodoList                        | view_auditor_todo_list                            | 导师/审核员待办视图（MainLeave 当前待审记录，含 studentAccount、teacherName）       |
