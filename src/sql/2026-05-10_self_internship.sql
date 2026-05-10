-- =====================================================================
-- 自主实习功能 (Student Self Declaration) 所需 DDL
-- 日期：2026-05-10
-- 依赖：base_process_type 里已存在 code='EXTERNAL_STUDENT_SELF_DECLARATION' 的记录 (id=17)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. rel_stu_internship_post 加 4 个自主实习字段
-- ---------------------------------------------------------------------
ALTER TABLE `rel_stu_internship_post`
  ADD COLUMN `self_company_name` varchar(200)  DEFAULT NULL COMMENT '自主实习单位名称',
  ADD COLUMN `self_post_name`    varchar(200)  DEFAULT NULL COMMENT '自主实习岗位名称',
  ADD COLUMN `self_address`      varchar(500)  DEFAULT NULL COMMENT '自主实习地址',
  ADD COLUMN `self_remarks`      varchar(1000) DEFAULT NULL COMMENT '自主实习备注/说明';


-- ---------------------------------------------------------------------
-- 2. 重建 view_rel_stu_internship_post，暴露 self_* 4 列
-- ---------------------------------------------------------------------
DROP VIEW IF EXISTS `view_rel_stu_internship_post`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `view_rel_stu_internship_post` AS
SELECT DISTINCT
    `view_main_internship_post`.`code`             AS `internship_post_code`,
    `view_main_internship_post`.`name`             AS `internship_post_name`,
    `view_main_internship_post`.`remarks`          AS `internship_post_remarks`,
    `view_main_internship_post`.`all_person_num`   AS `all_person_num`,
    `view_main_internship_post`.`now_person_num`   AS `now_person_num`,
    `view_main_internship_post`.`internship_name`  AS `internship_name`,
    `view_main_internship_post`.`company_name`     AS `company_name`,
    `view_main_internship_post`.`company_id`       AS `company_id`,
    `view_base_user`.`NAME`                        AS `student_name`,
    `view_base_user`.`ACCOUNT`                     AS `student_account`,
    `view_base_user`.`DEPARTMENT_ID`               AS `DEPARTMENT_ID`,
    `view_base_user`.`DEPARTMENT_NAME`             AS `DEPARTMENT_NAME`,
    `rel_stu_internship_post`.`id`                       AS `id`,
    `rel_stu_internship_post`.`create_time`              AS `create_time`,
    `rel_stu_internship_post`.`is_deleted`               AS `is_deleted`,
    `rel_stu_internship_post`.`update_time`              AS `update_time`,
    `rel_stu_internship_post`.`current_verify_type_id`   AS `current_verify_type_id`,
    `rel_stu_internship_post`.`internship_post_id`       AS `internship_post_id`,
    `rel_stu_internship_post`.`student_id`               AS `student_id`,
    `rel_stu_internship_post`.`self_company_name`        AS `self_company_name`,
    `rel_stu_internship_post`.`self_post_name`           AS `self_post_name`,
    `rel_stu_internship_post`.`self_address`             AS `self_address`,
    `rel_stu_internship_post`.`self_remarks`             AS `self_remarks`
FROM ((`view_base_user` JOIN `view_main_internship_post`)
      JOIN `rel_stu_internship_post`
        ON (((`rel_stu_internship_post`.`student_id`       = `view_base_user`.`ID`)
         AND (`rel_stu_internship_post`.`internship_post_id` = `view_main_internship_post`.`id`))));


-- ---------------------------------------------------------------------
-- 3. 重建 view_verify_process_rel_stu_internship_post （暴露 self_*）
-- ---------------------------------------------------------------------
DROP VIEW IF EXISTS `view_verify_process_rel_stu_internship_post`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `view_verify_process_rel_stu_internship_post` AS
SELECT DISTINCT
    `main_verify_process`.`id`              AS `id`,
    `main_verify_process`.`create_time`     AS `create_time`,
    `main_verify_process`.`is_deleted`      AS `is_deleted`,
    `main_verify_process`.`update_time`     AS `update_time`,
    `main_verify_process`.`create_user_id`  AS `create_user_id`,
    `main_verify_process`.`is_audit`        AS `is_audit`,
    `main_verify_process`.`reason`          AS `reason`,
    `main_verify_process`.`relation_id`     AS `relation_id`,
    `main_verify_process`.`table_name`      AS `table_name`,
    `main_verify_process`.`verify_user_id`  AS `verify_user_id`,
    `view_rel_process_internship`.`internship_id`        AS `internship_id`,
    `view_rel_process_internship`.`process_type_id`      AS `process_type_id`,
    `view_rel_process_internship`.`verify_type_id`       AS `verify_type_id`,
    `view_rel_process_internship`.`internship_code`      AS `internship_code`,
    `view_rel_process_internship`.`internship_remarks`   AS `internship_remarks`,
    `view_rel_process_internship`.`internship_name`      AS `internship_name`,
    `view_rel_process_internship`.`internship_type_name` AS `internship_type_name`,
    `view_rel_process_internship`.`int_type_name`        AS `int_type_name`,
    `view_rel_process_internship`.`university_name`      AS `university_name`,
    `createbaseuser`.`name`                  AS `create_user_name`,
    (SELECT GROUP_CONCAT(`base_user`.`name` SEPARATOR '，')
        FROM `base_user`
        WHERE FIND_IN_SET(`base_user`.`ID`, REPLACE(`main_verify_process`.`verify_user_id`, '|', ','))) AS `verify_user_name`,
    `view_rel_process_internship`.`end_time`             AS `end_time`,
    `view_rel_process_internship`.`start_time`           AS `start_time`,
    `view_rel_process_internship`.`major_ids`            AS `major_ids`,
    `view_rel_process_internship`.`major_names`          AS `major_names`,
    `view_rel_stu_internship_post`.`internship_post_code`    AS `internship_post_code`,
    `view_rel_stu_internship_post`.`internship_post_name`    AS `internship_post_name`,
    `view_rel_stu_internship_post`.`internship_post_remarks` AS `internship_post_remarks`,
    `view_rel_stu_internship_post`.`all_person_num`          AS `all_person_num`,
    `view_rel_stu_internship_post`.`now_person_num`          AS `now_person_num`,
    `view_rel_stu_internship_post`.`company_name`            AS `company_name`,
    `view_rel_stu_internship_post`.`company_id`              AS `company_id`,
    `view_rel_stu_internship_post`.`student_name`            AS `student_name`,
    `view_rel_stu_internship_post`.`student_account`         AS `student_account`,
    `view_rel_stu_internship_post`.`DEPARTMENT_ID`           AS `DEPARTMENT_ID`,
    `view_rel_stu_internship_post`.`DEPARTMENT_NAME`         AS `DEPARTMENT_NAME`,
    `view_rel_stu_internship_post`.`student_id`              AS `student_id`,
    `view_rel_stu_internship_post`.`internship_post_id`      AS `internship_post_id`,
    `view_rel_stu_internship_post`.`self_company_name`       AS `self_company_name`,
    `view_rel_stu_internship_post`.`self_post_name`          AS `self_post_name`,
    `view_rel_stu_internship_post`.`self_address`            AS `self_address`,
    `view_rel_stu_internship_post`.`self_remarks`            AS `self_remarks`,
    `main_verify_process`.`process_id`       AS `process_id`
FROM (((`main_verify_process`
        JOIN `view_rel_process_internship`    ON (`main_verify_process`.`process_id` = `view_rel_process_internship`.`id`))
    LEFT JOIN `base_user` `createbaseuser`    ON (`main_verify_process`.`create_user_id` = `createbaseuser`.`ID`))
        JOIN `view_rel_stu_internship_post`   ON (`main_verify_process`.`relation_id` = `view_rel_stu_internship_post`.`id`))
WHERE (`main_verify_process`.`table_name` = 'RelStuInternshipPost');


-- ---------------------------------------------------------------------
-- 4. 重建 view_verify_process_rel_stu_internship_post_merge （暴露 self_*）
-- ---------------------------------------------------------------------
DROP VIEW IF EXISTS `view_verify_process_rel_stu_internship_post_merge`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `view_verify_process_rel_stu_internship_post_merge` AS
SELECT
    `mvp`.`id`                        AS `id`,
    `mvp`.`create_time`               AS `create_time`,
    `mvp`.`is_deleted`                AS `is_deleted`,
    `mvp`.`update_time`               AS `update_time`,
    `mvp`.`create_user_id`            AS `create_user_id`,
    `mvp`.`is_audit`                  AS `is_audit`,
    `mvp`.`reason`                    AS `reason`,
    `mvp`.`relation_id`               AS `relation_id`,
    `mvp`.`table_name`                AS `table_name`,
    `mvp`.`verify_user_id`            AS `verify_user_id`,
    `mvp`.`process_id`                AS `process_id`,
    `vrpi`.`internship_id`            AS `internship_id`,
    `vrpi`.`process_type_id`          AS `process_type_id`,
    `vrpi`.`verify_type_id`           AS `verify_type_id`,
    `vrpi`.`internship_code`          AS `internship_code`,
    `vrpi`.`internship_remarks`       AS `internship_remarks`,
    `vrpi`.`internship_name`          AS `internship_name`,
    `vrpi`.`internship_type_name`     AS `internship_type_name`,
    `vrpi`.`int_type_name`            AS `int_type_name`,
    `vrpi`.`university_name`          AS `university_name`,
    `vrpi`.`end_time`                 AS `end_time`,
    `vrpi`.`start_time`               AS `start_time`,
    `vrpi`.`major_ids`                AS `major_ids`,
    `vrpi`.`major_names`              AS `major_names`,
    `vrpi`.`process_type_code`        AS `process_type_code`,
    `rsi`.`current_verify_type_id`    AS `current_verify_type_id`,
    `createbaseuser`.`name`           AS `create_user_name`,
    (SELECT GROUP_CONCAT(`bu`.`name` SEPARATOR '，')
        FROM `internship`.`base_user` `bu`
        WHERE FIND_IN_SET(`bu`.`ID`, REPLACE(`mvp`.`verify_user_id`, '|', ','))) AS `verify_user_name`,
    `vrsi`.`internship_post_id`       AS `internship_post_id`,
    `vrsi`.`internship_post_code`     AS `internship_post_code`,
    `vrsi`.`internship_post_name`     AS `internship_post_name`,
    `vrsi`.`internship_post_remarks`  AS `internship_post_remarks`,
    `vrsi`.`all_person_num`           AS `all_person_num`,
    `vrsi`.`now_person_num`           AS `now_person_num`,
    `vrsi`.`company_name`             AS `company_name`,
    `vrsi`.`company_id`               AS `company_id`,
    `vrsi`.`student_name`             AS `student_name`,
    `vrsi`.`student_account`          AS `student_account`,
    `vrsi`.`student_id`               AS `student_id`,
    `vrsi`.`DEPARTMENT_ID`            AS `DEPARTMENT_ID`,
    `vrsi`.`DEPARTMENT_NAME`          AS `DEPARTMENT_NAME`,
    `vrsi`.`self_company_name`        AS `self_company_name`,
    `vrsi`.`self_post_name`           AS `self_post_name`,
    `vrsi`.`self_address`             AS `self_address`,
    `vrsi`.`self_remarks`             AS `self_remarks`,
    (CASE
        WHEN (`rsi`.`current_verify_type_id` > `vrpi`.`verify_type_id`) THEN NULL
        WHEN (`rsi`.`current_verify_type_id` = 2) THEN `vrpi`.`verify_first_role_name`
        WHEN (`rsi`.`current_verify_type_id` = 3) THEN `vrpi`.`verify_second_role_name`
        WHEN (`rsi`.`current_verify_type_id` = 4) THEN `vrpi`.`verify_third_role_name`
        WHEN (`rsi`.`current_verify_type_id` = 5) THEN `vrpi`.`verify_fourth_role_name`
        WHEN (`rsi`.`current_verify_type_id` = 6) THEN `vrpi`.`verify_fifth_role_name`
        ELSE NULL
    END)                              AS `current_role_name`,
    (`rsi`.`current_verify_type_id` > `vrpi`.`verify_type_id`) AS `is_all_verified`
FROM (((((`internship`.`main_verify_process` `mvp`
          JOIN (SELECT `main_verify_process`.`process_id`  AS `process_id`,
                       `main_verify_process`.`relation_id` AS `relation_id`,
                       MAX(`main_verify_process`.`id`)     AS `max_id`
                FROM `internship`.`main_verify_process`
                WHERE ((`main_verify_process`.`table_name` = 'RelStuInternshipPost')
                  AND  (`main_verify_process`.`is_deleted` = 0))
                GROUP BY `main_verify_process`.`process_id`, `main_verify_process`.`relation_id`) `latest`
              ON (`mvp`.`id` = `latest`.`max_id`))
          JOIN `internship`.`view_rel_process_internship` `vrpi`
              ON (`mvp`.`process_id` = `vrpi`.`id`))
          JOIN `internship`.`rel_stu_internship_post` `rsi`
              ON (((`mvp`.`relation_id` = `rsi`.`id`) AND (`rsi`.`is_deleted` = 0))))
     LEFT JOIN `internship`.`base_user` `createbaseuser`
              ON (`mvp`.`create_user_id` = `createbaseuser`.`ID`))
          JOIN `internship`.`view_rel_stu_internship_post` `vrsi`
              ON (`mvp`.`relation_id` = `vrsi`.`id`))
WHERE (`mvp`.`table_name` = 'RelStuInternshipPost');


-- ---------------------------------------------------------------------
-- 5. 重建 view_external_internship_college_stats
--    过滤自主实习岗位（code='SELF_INTERNSHIP'），避免污染"岗位数"和"招聘人数"
--    注意：allPersonNum=-1 的自主岗位 max(all_person_num) 也会被 SUM 吞进 total_recruitment_headcount
-- ---------------------------------------------------------------------
DROP VIEW IF EXISTS `view_external_internship_college_stats`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `view_external_internship_college_stats` AS
SELECT
    `vmi`.`id`                AS `internship_id`,
    `vmi`.`name`              AS `internship_name`,
    `vmi`.`create_time`       AS `internship_create_time`,
    `dep`.`ID`                AS `department_id`,
    (SELECT COUNT(DISTINCT `m`.`user_id`)
        FROM (`internship`.`view_verify_process_rel_intership_user_merge` `m`
              JOIN `internship`.`view_base_user` `u` ON (`u`.`ID` = `m`.`user_id`))
        WHERE ((`m`.`internship_id` = `vmi`.`id`)
          AND (`m`.`is_deleted` = 0)
          AND (`m`.`job_code` = 'STUDENT')
          AND (`u`.`DEPARTMENT_ID` = `dep`.`ID`))) AS `signup_student_count`,
    (SELECT COUNT(DISTINCT `m`.`user_id`)
        FROM (`internship`.`view_verify_process_rel_intership_user_merge` `m`
              JOIN `internship`.`view_base_user` `u` ON (`u`.`ID` = `m`.`user_id`))
        WHERE ((`m`.`internship_id` = `vmi`.`id`)
          AND (`m`.`is_deleted` = 0)
          AND (`m`.`job_code` = 'SCHOOL_TEACHER')
          AND (`u`.`DEPARTMENT_ID` = `dep`.`ID`))) AS `signup_teacher_count`,
    (SELECT COUNT(DISTINCT `vmip1`.`internship_post_id`)
        FROM `internship`.`view_verify_process_internship_post_merge` `vmip1`
        WHERE ((`vmip1`.`internship_id` = `vmi`.`id`)
          AND (`vmip1`.`is_deleted` = 0)
          AND (`vmip1`.`is_audit` = 1)
          AND (`vmip1`.`internship_post_id` IS NOT NULL)
          AND (`vmip1`.`internship_post_code` <> 'SELF_INTERNSHIP' OR `vmip1`.`internship_post_code` IS NULL))) AS `post_signup_count`,
    COALESCE((SELECT SUM(`agg`.`ap`)
        FROM (SELECT `vmip`.`internship_id`       AS `internship_id`,
                     `vmip`.`internship_post_id`  AS `internship_post_id`,
                     MAX(`vmip`.`all_person_num`) AS `ap`
              FROM `internship`.`view_verify_process_internship_post_merge` `vmip`
              WHERE ((`vmip`.`is_deleted` = 0)
                AND (`vmip`.`is_audit` = 1)
                AND (`vmip`.`internship_post_id` IS NOT NULL)
                AND (`vmip`.`internship_post_code` <> 'SELF_INTERNSHIP' OR `vmip`.`internship_post_code` IS NULL))
              GROUP BY `vmip`.`internship_id`, `vmip`.`internship_post_id`) `agg`
        WHERE (`agg`.`internship_id` = `vmi`.`id`)), 0) AS `total_recruitment_headcount`,
    (SELECT COUNT(DISTINCT `vmip3`.`internship_post_id`)
        FROM `internship`.`view_verify_process_internship_post_merge` `vmip3`
        WHERE ((`vmip3`.`internship_id` = `vmi`.`id`)
          AND (`vmip3`.`is_deleted` = 0)
          AND (`vmip3`.`is_audit` IN (-(1), 0))
          AND (`vmip3`.`internship_post_id` IS NOT NULL)
          AND (`vmip3`.`internship_post_code` <> 'SELF_INTERNSHIP' OR `vmip3`.`internship_post_code` IS NULL))) AS `pending_audit_post_count`,
    (SELECT COUNT(DISTINCT `s`.`student_id`)
        FROM `internship`.`view_verify_process_rel_stu_internship_post_merge` `s`
        WHERE ((`s`.`internship_id` = `vmi`.`id`)
          AND (`s`.`is_deleted` = 0)
          AND (`s`.`DEPARTMENT_ID` = `dep`.`ID`)
          AND (`s`.`student_id` IS NOT NULL)
          AND (TRIM(`s`.`student_id`) <> ''))) AS `student_with_post_selection_count`
FROM (`internship`.`view_main_internship` `vmi`
      JOIN `internship`.`base_department` `dep`)
WHERE ((`vmi`.`is_deleted` = 0)
   AND (`vmi`.`int_type_name` = '校外实习')
   AND (`dep`.`IS_DELETED` = 0));


-- ---------------------------------------------------------------------
-- 6. 【模板】为每种「校外实习」实习类型补 EXTERNAL_STUDENT_SELF_DECLARATION 流程配置
-- 说明：process_type_id=17 对应 EXTERNAL_STUDENT_SELF_DECLARATION（已存在）。
--       <校外实习类型id> 替换为 base_internship_type.id 中所有 int_type_name='校外实习' 的记录
--       角色 id 按贵校实际配置替换
-- ---------------------------------------------------------------------
-- 示例（请按实际情况替换或生成动态 SQL）：
-- INSERT INTO `rel_process_internship_type`
--   (`create_time`, `is_deleted`, `update_time`, `internship_type_id`, `process_type_id`,
--    `verify_type_id`, `verify_first_role_id`, `verify_second_role_id`, `verify_third_role_id`,
--    `verify_fourth_role_id`, `verify_fifth_role_id`, `the_order`)
-- VALUES
--   (NOW(), b'0', NOW(), <校外实习类型id>, 17,
--    3, 13, 14, 0, 0, 0, 12);


-- ---------------------------------------------------------------------
-- 7. 【模板】新增两条菜单 sys_menu（父节点 id、level、角色按实际填）
-- ---------------------------------------------------------------------
-- INSERT INTO `sys_menu` (`create_time`, `is_deleted`, `update_time`, `code`, `name`, `remarks`,
--                         `parent_id`, `the_level`, `is_leaf`, `child_num`, `icon`, `path`, `component`, `hidden`)
-- VALUES
--   (NOW(), b'0', NOW(), 'STU_SELF_DECLARATION', '自主实习申请', NULL,
--    <父菜单id>, <父level+1>, b'1', 0, '', '/stuSelfDeclaration', 'internship-process/StuSelfDeclaration', b'0'),
--   (NOW(), b'0', NOW(), 'STU_SELF_DECLARATION_VERIFY', '自主实习审核', NULL,
--    <父菜单id>, <父level+1>, b'1', 0, '', '/stuSelfDeclarationVerify', 'internship-process/StuSelfDeclarationVerify', b'0');

-- 绑定到角色（rel_role_menu），示例：
-- INSERT INTO `rel_role_menu` (`create_time`, `is_deleted`, `update_time`, `role_id`, `menu_id`)
-- VALUES (NOW(), b'0', NOW(), <学生角色id=8>, <上一步插入的菜单id>);
