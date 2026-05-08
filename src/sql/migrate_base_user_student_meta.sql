-- 已有库一次性执行：为 base_user 增加入学年份(start_year)、毕业年份(end_year)、学制，并重建依赖视图
-- 若列已存在，请先调整或跳过对应语句
-- 若上一版已添加 enrollment_year / graduation_year，请先执行：
--   ALTER TABLE `base_user` CHANGE COLUMN `enrollment_year` `start_year` smallint(5) unsigned DEFAULT NULL COMMENT '入学年份';
--   ALTER TABLE `base_user` CHANGE COLUMN `graduation_year` `end_year` smallint(5) unsigned DEFAULT NULL COMMENT '毕业年份';

USE `internship`;

ALTER TABLE `base_user`
  ADD COLUMN `start_year` smallint(5) unsigned DEFAULT NULL COMMENT '入学年份' AFTER `major_id`,
  ADD COLUMN `end_year` smallint(5) unsigned DEFAULT NULL COMMENT '毕业年份' AFTER `start_year`,
  ADD COLUMN `school_length` tinyint(3) unsigned DEFAULT NULL COMMENT '学制（年）' AFTER `end_year`;

DROP VIEW IF EXISTS `view_base_user`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `view_base_user` AS select `u`.`address` AS `ADDRESS`,`u`.`BIRTH` AS `BIRTH`,`u`.`DEPARTMENT_ID` AS `DEPARTMENT_ID`,`u`.`email` AS `EMAIL`,`u`.`FIRST_LOGIN` AS `FIRST_LOGIN`,`u`.`id_card` AS `ID_CARD`,`u`.`JOB_ID` AS `JOB_ID`,`u`.`name` AS `NAME`,`u`.`ID` AS `ID`,`u`.`IS_DELETED` AS `IS_DELETED`,`u`.`nick_name` AS `NICK_NAME`,`u`.`password` AS `PASSWORD`,`u`.`phone` AS `PHONE`,`u`.`postal_code` AS `POSTAL_CODE`,`u`.`REGISTER_TIME` AS `REGISTER_TIME`,`u`.`remarks` AS `REMARKS`,`u`.`sex` AS `SEX`,`u`.`theme_color` AS `THEME_COLOR`,`u`.`work_id` AS `WORK_ID`,`u`.`account` AS `ACCOUNT`,`u`.`CREATE_TIME` AS `CREATE_TIME`,`u`.`UPDATE_TIME` AS `UPDATE_TIME`,`p`.`name` AS `JOB_NAME`,`department_getAllParentNames`(`u`.`DEPARTMENT_ID`) AS `DEPARTMENT_NAME`,`department_getSchoolId`(`u`.`DEPARTMENT_ID`) AS `SCHOOL_ID`,`u`.`code` AS `CODE`,`u`.`major_id` AS `major_id`,`u`.`start_year` AS `start_year`,`u`.`end_year` AS `end_year`,`u`.`school_length` AS `school_length`,`p`.`code` AS `job_code` from ((`base_user` `u` left join `base_department` `d` on((`u`.`DEPARTMENT_ID` = `d`.`ID`))) left join `base_job_position` `p` on((`u`.`JOB_ID` = `p`.`ID`)));

DROP VIEW IF EXISTS `view_base_user_internship`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `view_base_user_internship` AS select `bu`.`ID` AS `ID`,`bu`.`IS_DELETED` AS `IS_DELETED`,`bu`.`address` AS `address`,`bu`.`BIRTH` AS `BIRTH`,`bu`.`DEPARTMENT_ID` AS `DEPARTMENT_ID`,`bu`.`email` AS `email`,`bu`.`FIRST_LOGIN` AS `FIRST_LOGIN`,`bu`.`id_card` AS `id_card`,`bu`.`JOB_ID` AS `JOB_ID`,`bu`.`name` AS `name`,`bu`.`nick_name` AS `nick_name`,`bu`.`password` AS `password`,`bu`.`phone` AS `phone`,`bu`.`postal_code` AS `postal_code`,`bu`.`REGISTER_TIME` AS `REGISTER_TIME`,`bu`.`remarks` AS `remarks`,`bu`.`sex` AS `sex`,`bu`.`theme_color` AS `theme_color`,`bu`.`work_id` AS `work_id`,`bu`.`account` AS `account`,`bu`.`CREATE_TIME` AS `CREATE_TIME`,`bu`.`UPDATE_TIME` AS `UPDATE_TIME`,`bu`.`code` AS `code`,`bu`.`major_id` AS `major_id`,`bu`.`start_year` AS `start_year`,`bu`.`end_year` AS `end_year`,`bu`.`school_length` AS `school_length` from `base_user` `bu` where (not(exists(select 1 from `rel_intership_user` `riu` where (`riu`.`user_id` = `bu`.`ID`))));
