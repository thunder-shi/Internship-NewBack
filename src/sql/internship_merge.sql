-- ============================================================
-- 0-a. 修复 view_rel_process_internship（现有视图）
--      LEFT JOIN → INNER JOIN view_main_internship，排除已删除的实习项目
--      增加 rel_process_internship.is_deleted = 0 过滤
-- ============================================================
CREATE OR REPLACE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `view_rel_process_internship` AS
SELECT
    rpi.`id`                                 AS `id`,
    rpi.`create_time`                        AS `create_time`,
    rpi.`is_deleted`                         AS `is_deleted`,
    rpi.`update_time`                        AS `update_time`,
    rpi.`internship_id`                      AS `internship_id`,
    rpi.`process_type_id`                    AS `process_type_id`,
    rpi.`verify_type_id`                     AS `verify_type_id`,
    rpi.`verify_fifth_role_id`               AS `verify_fifth_role_id`,
    rpi.`verify_first_role_id`               AS `verify_first_role_id`,
    rpi.`verify_fourth_role_id`              AS `verify_fourth_role_id`,
    rpi.`verify_second_role_id`              AS `verify_second_role_id`,
    rpi.`verify_third_role_id`               AS `verify_third_role_id`,
    rpi.`end_time`                           AS `end_time`,
    rpi.`start_time`                         AS `start_time`,
    vmi.`name`                               AS `internship_name`,
    bpt.`name`                               AS `process_type_name`,
    bpt.`the_order`                          AS `the_order`,
    bvt.`name`                               AS `verify_type_name`,
    sr1.`name`                               AS `verify_first_role_name`,
    sr2.`name`                               AS `verify_second_role_name`,
    sr3.`name`                               AS `verify_third_role_name`,
    sr4.`name`                               AS `verify_fourth_role_name`,
    sr5.`name`                               AS `verify_fifth_role_name`,
    vmi.`code`                               AS `internship_code`,
    vmi.`remarks`                            AS `internship_remarks`,
    vmi.`university_name`                    AS `university_name`,
    vmi.`int_type_name`                      AS `int_type_name`,
    vmi.`internship_type_name`               AS `internship_type_name`,
    vmi.`major_ids`                          AS `major_ids`,
    vmi.`major_names`                        AS `major_names`,
    bpt.`code`                               AS `process_type_code`,
    rpi.`current_verify_type_id`             AS `current_verify_type_id`
FROM `rel_process_internship` rpi
-- 改为 INNER JOIN，排除已删除或不存在的实习项目
INNER JOIN `view_main_internship` vmi
    ON rpi.`internship_id` = vmi.`id` AND vmi.`is_deleted` = 0
LEFT JOIN `base_process_type` bpt
    ON rpi.`process_type_id` = bpt.`id`
LEFT JOIN `base_verify_type` bvt
    ON rpi.`verify_type_id` = bvt.`id`
LEFT JOIN `sys_role` sr1
    ON rpi.`verify_first_role_id` = sr1.`ID`
LEFT JOIN `sys_role` sr2
    ON rpi.`verify_second_role_id` = sr2.`ID`
LEFT JOIN `sys_role` sr3
    ON rpi.`verify_third_role_id` = sr3.`ID`
LEFT JOIN `sys_role` sr4
    ON rpi.`verify_fourth_role_id` = sr4.`ID`
LEFT JOIN `sys_role` sr5
    ON rpi.`verify_fifth_role_id` = sr5.`ID`
WHERE rpi.`is_deleted` = 0;


-- ============================================================
-- 0-b. 修复 view_rel_intership_user（现有视图）
--      current_verify_type_id 改为从 rel_intership_user 读取
-- ============================================================
CREATE OR REPLACE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `view_rel_intership_user` AS
SELECT
    riu.`internship_id`                             AS `internship_id`,
    riu.`user_id`                                   AS `user_id`,
    vbu.`JOB_NAME`                                  AS `JOB_NAME`,
    vbu.`JOB_ID`                                    AS `JOB_ID`,
    riu.`remarks`                                   AS `remarks`,
    riu.`create_time`                               AS `create_time`,
    riu.`update_time`                               AS `update_time`,
    riu.`code`                                      AS `code`,
    riu.`name`                                      AS `name`,
    vbu.`NAME`                                      AS `USER_NAME`,
    mvp.`is_audit`                                  AS `is_audit`,
    mvp.`reason`                                    AS `reason`,
    mvp.`table_name`                                AS `TABLE_NAME`,
    vrpi.`process_type_name`                        AS `process_type_name`,
    vrpi.`process_type_code`                        AS `process_type_code`,
    vrpi.`internship_type_name`                     AS `internship_type_name`,
    vrpi.`end_time`                                 AS `end_time`,
    vrpi.`start_time`                               AS `start_time`,
    vbu.`PHONE`                                     AS `PHONE`,
    mvp.`process_id`                                AS `process_id`,
    mi.`name`                                       AS `INTERNSHIP_NAME`,
    mvp.`verify_user_id`                            AS `VERIFY_USER_ID`,
    mvp.`relation_id`                               AS `relation_id`,
    mvp.`id`                                        AS `id`,
    riu.`id`                                        AS `REL_INTERSHIP_USER_ID`,
    riu.`is_deleted`                                AS `is_deleted`,
    vrpi.`verify_type_id`                           AS `verify_type_id`,
    vrpi.`verify_fifth_role_id`                     AS `verify_fifth_role_id`,
    vrpi.`verify_first_role_id`                     AS `verify_first_role_id`,
    vrpi.`verify_fourth_role_id`                    AS `verify_fourth_role_id`,
    vrpi.`verify_second_role_id`                    AS `verify_second_role_id`,
    vrpi.`verify_third_role_id`                     AS `verify_third_role_id`,
    bvt.`name`                                      AS `CURRENT_VERIFY_TYPE_NAME`,
    mi.`student_num`                                AS `student_num`,
    -- 改为从 rel_intership_user 读取
    riu.`current_verify_type_id`                    AS `current_verify_type_id`
FROM `rel_intership_user` riu
JOIN `view_base_user` vbu
    ON riu.`user_id` = vbu.`ID`
JOIN `main_internship` mi
    ON riu.`internship_id` = mi.`id`
JOIN `main_verify_process` mvp
    ON mvp.`relation_id` = riu.`id`
JOIN `view_rel_process_internship` vrpi
    ON mi.`id` = vrpi.`internship_id`
-- 改为从 rel_intership_user 的 current_verify_type_id 关联
LEFT JOIN `base_verify_type` bvt
    ON riu.`current_verify_type_id` = bvt.`id`
WHERE mvp.`table_name` = 'RelIntershipUser'
  AND vrpi.`process_type_code` IN ('STUDENT_SELECT_INTERNSHIP', 'TEACHER_SELECT_INTERNALSHIP');


-- ============================================================
-- 审核聚合视图（每个业务实体只保留最新一条审核记录）
-- 用途：前端审核列表展示，每个条目仅一行
-- current_verify_type_id 从各业务实体自身表读取
-- ============================================================


-- ----------------------------
-- 1. view_verify_process_internship_merge
--    实习计划审核聚合（table_name = 'MainInternship'）
--    current_verify_type_id 来自 main_internship
-- ----------------------------
CREATE OR REPLACE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `view_verify_process_internship_merge` AS
SELECT
    mvp.`id`                                        AS `id`,
    mvp.`create_time`                               AS `create_time`,
    mvp.`is_deleted`                                AS `is_deleted`,
    mvp.`update_time`                               AS `update_time`,
    mvp.`create_user_id`                            AS `create_user_id`,
    mvp.`is_audit`                                  AS `is_audit`,
    mvp.`reason`                                    AS `reason`,
    mvp.`relation_id`                               AS `relation_id`,
    mvp.`table_name`                                AS `table_name`,
    mvp.`verify_user_id`                            AS `verify_user_id`,
    mvp.`process_id`                                AS `process_id`,

    vrpi.`internship_id`                            AS `internship_id`,
    vrpi.`process_type_id`                          AS `process_type_id`,
    vrpi.`verify_type_id`                           AS `verify_type_id`,
    vrpi.`internship_code`                          AS `internship_code`,
    vrpi.`internship_remarks`                       AS `internship_remarks`,
    vrpi.`internship_name`                          AS `internship_name`,
    vrpi.`internship_type_name`                     AS `internship_type_name`,
    vrpi.`int_type_name`                            AS `int_type_name`,
    vrpi.`university_name`                          AS `university_name`,
    vrpi.`end_time`                                 AS `end_time`,
    vrpi.`start_time`                               AS `start_time`,
    vrpi.`major_ids`                                AS `major_ids`,
    vrpi.`major_names`                              AS `major_names`,
    vrpi.`process_type_code`                        AS `process_type_code`,

    -- current_verify_type_id 从 main_internship 读取
    mi.`current_verify_type_id`                     AS `current_verify_type_id`,

    createbaseuser.`name`                           AS `create_user_name`,
    (
        SELECT GROUP_CONCAT(bu.`name` SEPARATOR '，')
        FROM `base_user` bu
        WHERE FIND_IN_SET(bu.`ID`, REPLACE(mvp.`verify_user_id`, '|', ',')) > 0
    )                                               AS `verify_user_name`,
    bvt.`name`                                      AS `verify_type_name`,

    CASE
        WHEN mi.`current_verify_type_id` > vrpi.`verify_type_id` THEN NULL
        WHEN mi.`current_verify_type_id` = 2 THEN vrpi.`verify_first_role_name`
        WHEN mi.`current_verify_type_id` = 3 THEN vrpi.`verify_second_role_name`
        WHEN mi.`current_verify_type_id` = 4 THEN vrpi.`verify_third_role_name`
        WHEN mi.`current_verify_type_id` = 5 THEN vrpi.`verify_fourth_role_name`
        WHEN mi.`current_verify_type_id` = 6 THEN vrpi.`verify_fifth_role_name`
        ELSE NULL
    END                                             AS `current_role_name`,

    (mi.`current_verify_type_id` > vrpi.`verify_type_id`)
                                                    AS `is_all_verified`

FROM `main_verify_process` mvp
INNER JOIN (
    SELECT `process_id`, MAX(`id`) AS `max_id`
    FROM `main_verify_process`
    WHERE `table_name` = 'MainInternship' AND `is_deleted` = 0
    GROUP BY `process_id`
) latest ON mvp.`id` = latest.`max_id`
JOIN `view_rel_process_internship` vrpi
    ON mvp.`process_id` = vrpi.`id`
-- 从 main_internship 读 current_verify_type_id
JOIN `main_internship` mi
    ON mvp.`relation_id` = mi.`id` AND mi.`is_deleted` = 0
LEFT JOIN `base_user` createbaseuser
    ON mvp.`create_user_id` = createbaseuser.`ID`
JOIN `base_verify_type` bvt
    ON vrpi.`verify_type_id` = bvt.`id`
WHERE mvp.`table_name` = 'MainInternship';


-- ----------------------------
-- 2. view_verify_process_internship_post_merge
--    实习岗位审核聚合（table_name = 'MainInternshipPost'）
--    current_verify_type_id 来自 main_internship_post
-- ----------------------------
DROP TABLE IF EXISTS `view_verify_process_internship_post_merge`;
CREATE OR REPLACE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `view_verify_process_internship_post_merge` AS
SELECT
    mvp.`id`                                        AS `id`,
    mvp.`create_time`                               AS `create_time`,
    mvp.`is_deleted`                                AS `is_deleted`,
    mvp.`update_time`                               AS `update_time`,
    mvp.`create_user_id`                            AS `create_user_id`,
    mvp.`is_audit`                                  AS `is_audit`,
    mvp.`reason`                                    AS `reason`,
    mvp.`relation_id`                               AS `relation_id`,
    mvp.`table_name`                                AS `table_name`,
    mvp.`verify_user_id`                            AS `verify_user_id`,
    mvp.`process_id`                                AS `process_id`,

    vrpi.`internship_id`                            AS `internship_id`,
    vrpi.`process_type_id`                          AS `process_type_id`,
    vrpi.`verify_type_id`                           AS `verify_type_id`,
    vrpi.`internship_code`                          AS `internship_code`,
    vrpi.`internship_remarks`                       AS `internship_remarks`,
    vrpi.`internship_name`                          AS `internship_name`,
    vrpi.`internship_type_name`                     AS `internship_type_name`,
    vrpi.`int_type_name`                            AS `int_type_name`,
    vrpi.`university_name`                          AS `university_name`,
    vrpi.`end_time`                                 AS `end_time`,
    vrpi.`start_time`                               AS `start_time`,
    vrpi.`major_ids`                                AS `major_ids`,
    vrpi.`major_names`                              AS `major_names`,
    vrpi.`process_type_code`                        AS `process_type_code`,

    -- current_verify_type_id 从 main_internship_post 读取
    mip.`current_verify_type_id`                    AS `current_verify_type_id`,

    createbaseuser.`name`                           AS `create_user_name`,
    (
        SELECT GROUP_CONCAT(bu.`name` SEPARATOR '，')
        FROM `base_user` bu
        WHERE FIND_IN_SET(bu.`ID`, REPLACE(mvp.`verify_user_id`, '|', ',')) > 0
    )                                               AS `verify_user_name`,

    -- 来自 view_main_internship_post
    vmip.`name`                                     AS `internship_post_name`,
    vmip.`code`                                     AS `internship_post_code`,
    vmip.`id`                                       AS `internship_post_id`,
    vmip.`all_person_num`                           AS `all_person_num`,
    vmip.`now_person_num`                           AS `now_person_num`,
    vmip.`company_name`                             AS `company_name`,
    vmip.`company_id`                               AS `company_id`,

    CASE
        WHEN mip.`current_verify_type_id` > vrpi.`verify_type_id` THEN NULL
        WHEN mip.`current_verify_type_id` = 2 THEN vrpi.`verify_first_role_name`
        WHEN mip.`current_verify_type_id` = 3 THEN vrpi.`verify_second_role_name`
        WHEN mip.`current_verify_type_id` = 4 THEN vrpi.`verify_third_role_name`
        WHEN mip.`current_verify_type_id` = 5 THEN vrpi.`verify_fourth_role_name`
        WHEN mip.`current_verify_type_id` = 6 THEN vrpi.`verify_fifth_role_name`
        ELSE NULL
    END                                             AS `current_role_name`,

    (mip.`current_verify_type_id` > vrpi.`verify_type_id`)
                                                    AS `is_all_verified`

FROM `main_verify_process` mvp
INNER JOIN (
    SELECT `process_id`, `relation_id`, MAX(`id`) AS `max_id`
    FROM `main_verify_process`
    WHERE `table_name` = 'MainInternshipPost' AND `is_deleted` = 0
    GROUP BY `process_id`, `relation_id`
) latest ON mvp.`id` = latest.`max_id`
JOIN `view_rel_process_internship` vrpi
    ON mvp.`process_id` = vrpi.`id`
-- 从 main_internship_post 读 current_verify_type_id，排除已删除的岗位
JOIN `main_internship_post` mip
    ON mvp.`relation_id` = mip.`id` AND mip.`is_deleted` = 0
LEFT JOIN `base_user` createbaseuser
    ON mvp.`create_user_id` = createbaseuser.`ID`
LEFT JOIN `view_main_internship_post` vmip
    ON mvp.`relation_id` = vmip.`id`
WHERE mvp.`table_name` = 'MainInternshipPost';


-- ----------------------------
-- 3. view_verify_process_rel_stu_internship_merge
--    学生选岗审核聚合（table_name = 'RelStuInternship'）
--    current_verify_type_id 来自 rel_stu_internship
-- ----------------------------
DROP TABLE IF EXISTS `view_verify_process_rel_stu_internship_merge`;
CREATE OR REPLACE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `view_verify_process_rel_stu_internship_merge` AS
SELECT
    mvp.`id`                                        AS `id`,
    mvp.`create_time`                               AS `create_time`,
    mvp.`is_deleted`                                AS `is_deleted`,
    mvp.`update_time`                               AS `update_time`,
    mvp.`create_user_id`                            AS `create_user_id`,
    mvp.`is_audit`                                  AS `is_audit`,
    mvp.`reason`                                    AS `reason`,
    mvp.`relation_id`                               AS `relation_id`,
    mvp.`table_name`                                AS `table_name`,
    mvp.`verify_user_id`                            AS `verify_user_id`,
    mvp.`process_id`                                AS `process_id`,

    vrpi.`internship_id`                            AS `internship_id`,
    vrpi.`process_type_id`                          AS `process_type_id`,
    vrpi.`verify_type_id`                           AS `verify_type_id`,
    vrpi.`internship_code`                          AS `internship_code`,
    vrpi.`internship_remarks`                       AS `internship_remarks`,
    vrpi.`internship_name`                          AS `internship_name`,
    vrpi.`internship_type_name`                     AS `internship_type_name`,
    vrpi.`int_type_name`                            AS `int_type_name`,
    vrpi.`university_name`                          AS `university_name`,
    vrpi.`end_time`                                 AS `end_time`,
    vrpi.`start_time`                               AS `start_time`,
    vrpi.`major_ids`                                AS `major_ids`,
    vrpi.`major_names`                              AS `major_names`,
    vrpi.`process_type_code`                        AS `process_type_code`,

    -- current_verify_type_id 从 rel_stu_internship 读取
    rsi.`current_verify_type_id`                    AS `current_verify_type_id`,

    createbaseuser.`name`                           AS `create_user_name`,
    (
        SELECT GROUP_CONCAT(bu.`name` SEPARATOR '，')
        FROM `base_user` bu
        WHERE FIND_IN_SET(bu.`ID`, REPLACE(mvp.`verify_user_id`, '|', ',')) > 0
    )                                               AS `verify_user_name`,

    -- 来自 view_rel_stu_internship
    vrsi.`internship_post_id`                       AS `internship_post_id`,
    vrsi.`internship_post_code`                     AS `internship_post_code`,
    vrsi.`internship_post_name`                     AS `internship_post_name`,
    vrsi.`internship_post_remarks`                  AS `internship_post_remarks`,
    vrsi.`all_person_num`                           AS `all_person_num`,
    vrsi.`now_person_num`                           AS `now_person_num`,
    vrsi.`company_name`                             AS `company_name`,
    vrsi.`company_id`                               AS `company_id`,
    vrsi.`student_name`                             AS `student_name`,
    vrsi.`student_id`                               AS `student_id`,
    vrsi.`DEPARTMENT_ID`                            AS `DEPARTMENT_ID`,
    vrsi.`DEPARTMENT_NAME`                          AS `DEPARTMENT_NAME`,

    CASE
        WHEN rsi.`current_verify_type_id` > vrpi.`verify_type_id` THEN NULL
        WHEN rsi.`current_verify_type_id` = 2 THEN vrpi.`verify_first_role_name`
        WHEN rsi.`current_verify_type_id` = 3 THEN vrpi.`verify_second_role_name`
        WHEN rsi.`current_verify_type_id` = 4 THEN vrpi.`verify_third_role_name`
        WHEN rsi.`current_verify_type_id` = 5 THEN vrpi.`verify_fourth_role_name`
        WHEN rsi.`current_verify_type_id` = 6 THEN vrpi.`verify_fifth_role_name`
        ELSE NULL
    END                                             AS `current_role_name`,

    (rsi.`current_verify_type_id` > vrpi.`verify_type_id`)
                                                    AS `is_all_verified`

FROM `main_verify_process` mvp
INNER JOIN (
    SELECT `process_id`, `relation_id`, MAX(`id`) AS `max_id`
    FROM `main_verify_process`
    WHERE `table_name` = 'RelStuInternship' AND `is_deleted` = 0
    GROUP BY `process_id`, `relation_id`
) latest ON mvp.`id` = latest.`max_id`
JOIN `view_rel_process_internship` vrpi
    ON mvp.`process_id` = vrpi.`id`
-- 从 rel_stu_internship 读 current_verify_type_id
JOIN `rel_stu_internship` rsi
    ON mvp.`relation_id` = rsi.`id` AND rsi.`is_deleted` = 0
LEFT JOIN `base_user` createbaseuser
    ON mvp.`create_user_id` = createbaseuser.`ID`
JOIN `view_rel_stu_internship` vrsi
    ON mvp.`relation_id` = vrsi.`id`
WHERE mvp.`table_name` = 'RelStuInternship';


-- ----------------------------
-- 4. view_verify_process_rel_teacher_student_merge
--    指导老师安排审核聚合（table_name = 'RelTeacherStudent'）
--    current_verify_type_id 来自 rel_teacher_student
-- ----------------------------
DROP TABLE IF EXISTS `view_verify_process_rel_teacher_student_merge`;
CREATE OR REPLACE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `view_verify_process_rel_teacher_student_merge` AS
SELECT
    mvp.`id`                                        AS `id`,
    mvp.`create_time`                               AS `create_time`,
    mvp.`is_deleted`                                AS `is_deleted`,
    mvp.`update_time`                               AS `update_time`,
    mvp.`create_user_id`                            AS `create_user_id`,
    mvp.`is_audit`                                  AS `is_audit`,
    mvp.`reason`                                    AS `reason`,
    mvp.`relation_id`                               AS `relation_id`,
    mvp.`table_name`                                AS `table_name`,
    mvp.`verify_user_id`                            AS `verify_user_id`,
    mvp.`process_id`                                AS `process_id`,

    -- 来自 rel_teacher_student
    rts.`name`                                      AS `name`,
    rts.`teacher_id`                                AS `teacher_id`,
    rts.`internship_id`                             AS `internship_id`,
    rts.`stu_id`                                    AS `stu_id`,
    rts.`remarks`                                   AS `remarks`,

    -- 来自 main_internship
    mi.`name`                                       AS `internship_name`,

    -- 用户姓名
    bu_teacher.`name`                               AS `teacher_name`,
    bu_create.`name`                                AS `create_user_name`,
    (
        SELECT GROUP_CONCAT(bu.`name` SEPARATOR '，')
        FROM `base_user` bu
        WHERE FIND_IN_SET(bu.`ID`, REPLACE(mvp.`verify_user_id`, '|', ',')) > 0
    )                                               AS `verify_user_name`,

    -- current_verify_type_id 从 rel_teacher_student 读取
    rts.`current_verify_type_id`                    AS `current_verify_type_id`,
    bvt.`name`                                      AS `current_verify_type_name`,

    CASE
        WHEN rts.`current_verify_type_id` > vrpi.`verify_type_id` THEN NULL
        WHEN rts.`current_verify_type_id` = 2 THEN vrpi.`verify_first_role_name`
        WHEN rts.`current_verify_type_id` = 3 THEN vrpi.`verify_second_role_name`
        WHEN rts.`current_verify_type_id` = 4 THEN vrpi.`verify_third_role_name`
        WHEN rts.`current_verify_type_id` = 5 THEN vrpi.`verify_fourth_role_name`
        WHEN rts.`current_verify_type_id` = 6 THEN vrpi.`verify_fifth_role_name`
        ELSE NULL
    END                                             AS `current_role_name`,

    (rts.`current_verify_type_id` > vrpi.`verify_type_id`)
                                                    AS `is_all_verified`

FROM `main_verify_process` mvp
INNER JOIN (
    SELECT `process_id`, `relation_id`, MAX(`id`) AS `max_id`
    FROM `main_verify_process`
    WHERE `table_name` = 'RelTeacherStudent' AND `is_deleted` = 0
    GROUP BY `process_id`, `relation_id`
) latest ON mvp.`id` = latest.`max_id`
-- 从 rel_teacher_student 读 current_verify_type_id
-- 排除已删除的师生关系
JOIN `rel_teacher_student` rts
    ON mvp.`relation_id` = rts.`id` AND rts.`is_deleted` = 0
LEFT JOIN `main_internship` mi
    ON rts.`internship_id` = mi.`id`
LEFT JOIN `base_user` bu_teacher
    ON rts.`teacher_id` = bu_teacher.`ID`
LEFT JOIN `base_user` bu_create
    ON mvp.`create_user_id` = bu_create.`ID`
-- 关联流程配置获取审核角色名
JOIN `view_rel_process_internship` vrpi
    ON mvp.`process_id` = vrpi.`id`
LEFT JOIN `base_verify_type` bvt
    ON rts.`current_verify_type_id` = bvt.`id`
WHERE mvp.`table_name` = 'RelTeacherStudent';


-- ----------------------------
-- 5. view_verify_process_rel_intership_user_merge
--    教师/学生选岗审核聚合（table_name = 'RelIntershipUser'）
--    current_verify_type_id 来自 rel_intership_user
-- ----------------------------
DROP TABLE IF EXISTS `view_verify_process_rel_intership_user_merge`;
CREATE OR REPLACE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `view_verify_process_rel_intership_user_merge` AS
SELECT
    mvp.`id`                                        AS `id`,
    mvp.`create_time`                               AS `create_time`,
    mvp.`is_deleted`                                AS `is_deleted`,
    mvp.`update_time`                               AS `update_time`,
    mvp.`create_user_id`                            AS `create_user_id`,
    mvp.`is_audit`                                  AS `is_audit`,
    mvp.`reason`                                    AS `reason`,
    mvp.`relation_id`                               AS `relation_id`,
    mvp.`table_name`                                AS `table_name`,
    mvp.`verify_user_id`                            AS `verify_user_id`,
    mvp.`process_id`                                AS `process_id`,

    -- 来自 rel_intership_user
    riu.`internship_id`                             AS `internship_id`,
    riu.`user_id`                                   AS `user_id`,
    riu.`code`                                      AS `code`,
    riu.`name`                                      AS `name`,
    riu.`remarks`                                   AS `remarks`,
    riu.`id`                                        AS `rel_intership_user_id`,

    -- 来自 view_base_user
    vbu.`NAME`                                      AS `user_name`,
    vbu.`JOB_NAME`                                  AS `job_name`,
    vbu.`PHONE`                                     AS `phone`,

    -- 来自 main_internship
    mi.`name`                                       AS `internship_name`,
    mi.`student_num`                                AS `student_num`,

    -- 来自 view_rel_process_internship
    vrpi.`process_type_code`                        AS `process_type_code`,
    vrpi.`process_type_name`                        AS `process_type_name`,
    vrpi.`internship_type_name`                     AS `internship_type_name`,
    vrpi.`verify_type_id`                           AS `verify_type_id`,
    vrpi.`end_time`                                 AS `end_time`,
    vrpi.`start_time`                               AS `start_time`,

    -- current_verify_type_id 从 rel_intership_user 读取
    riu.`current_verify_type_id`                    AS `current_verify_type_id`,

    bu_create.`name`                                AS `create_user_name`,
    (
        SELECT GROUP_CONCAT(bu.`name` SEPARATOR '，')
        FROM `base_user` bu
        WHERE FIND_IN_SET(bu.`ID`, REPLACE(mvp.`verify_user_id`, '|', ',')) > 0
    )                                               AS `verify_user_name`,
    bvt.`name`                                      AS `verify_type_name`,

    CASE
        WHEN riu.`current_verify_type_id` > vrpi.`verify_type_id` THEN NULL
        WHEN riu.`current_verify_type_id` = 2 THEN vrpi.`verify_first_role_name`
        WHEN riu.`current_verify_type_id` = 3 THEN vrpi.`verify_second_role_name`
        WHEN riu.`current_verify_type_id` = 4 THEN vrpi.`verify_third_role_name`
        WHEN riu.`current_verify_type_id` = 5 THEN vrpi.`verify_fourth_role_name`
        WHEN riu.`current_verify_type_id` = 6 THEN vrpi.`verify_fifth_role_name`
        ELSE NULL
    END                                             AS `current_role_name`,

    (riu.`current_verify_type_id` > vrpi.`verify_type_id`)
                                                    AS `is_all_verified`

FROM `main_verify_process` mvp
INNER JOIN (
    SELECT `process_id`, `relation_id`, MAX(`id`) AS `max_id`
    FROM `main_verify_process`
    WHERE `table_name` = 'RelIntershipUser' AND `is_deleted` = 0
    GROUP BY `process_id`, `relation_id`
) latest ON mvp.`id` = latest.`max_id`
-- 排除已删除的实习用户关联
JOIN `rel_intership_user` riu
    ON mvp.`relation_id` = riu.`id` AND riu.`is_deleted` = 0
LEFT JOIN `view_base_user` vbu
    ON riu.`user_id` = vbu.`ID`
LEFT JOIN `main_internship` mi
    ON riu.`internship_id` = mi.`id`
JOIN `view_rel_process_internship` vrpi
    ON mvp.`process_id` = vrpi.`id`
LEFT JOIN `base_user` bu_create
    ON mvp.`create_user_id` = bu_create.`ID`
LEFT JOIN `base_verify_type` bvt
    ON vrpi.`verify_type_id` = bvt.`id`
WHERE mvp.`table_name` = 'RelIntershipUser';
