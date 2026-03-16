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
    ON mvp.`relation_id` = mi.`id`
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
LEFT JOIN `view_rel_process_internship` vrpi
    ON mvp.`process_id` = vrpi.`id`
-- 从 main_internship_post 读 current_verify_type_id
LEFT JOIN `main_internship_post` mip
    ON mvp.`relation_id` = mip.`id`
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
    ON mvp.`relation_id` = rsi.`id`
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
LEFT JOIN `rel_teacher_student` rts
    ON mvp.`relation_id` = rts.`id`
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
