-- Student internship termination.
-- Apply before deploying the backend code in this change.

INSERT INTO base_process_type
  (create_time, is_deleted, update_time, code, name, remarks, the_order)
SELECT NOW(), b'0', NOW(), 'STUDENT_INTERNSHIP_TERMINATION', '终止学生实习', NULL, 18
WHERE NOT EXISTS (
  SELECT 1 FROM base_process_type
  WHERE code = 'STUDENT_INTERNSHIP_TERMINATION' AND is_deleted = 0
);

CREATE TABLE IF NOT EXISTS main_internship_termination (
  id int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '编号',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  is_deleted bit(1) NOT NULL DEFAULT b'0' COMMENT '删除标记',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  verify_type_id int(10) unsigned DEFAULT NULL COMMENT '审核类型',
  verify_fifth_role_id int(10) unsigned DEFAULT '0' COMMENT '第五级审核角色',
  verify_first_role_id int(10) unsigned DEFAULT '0' COMMENT '第一级审核角色',
  verify_fourth_role_id int(10) unsigned DEFAULT '0' COMMENT '第四级审核角色',
  verify_second_role_id int(10) unsigned DEFAULT '0' COMMENT '第二级审核角色',
  verify_third_role_id int(10) unsigned DEFAULT '0' COMMENT '第三级审核角色',
  internship_id int(10) unsigned NOT NULL COMMENT '实习项目ID',
  student_id int(10) unsigned NOT NULL COMMENT '学生ID',
  relation_table varchar(50) NOT NULL COMMENT 'RelStuInternshipPost or RelTitleStudent',
  relation_id int(10) unsigned NOT NULL COMMENT '实习关系ID',
  terminate_date datetime DEFAULT NULL COMMENT '终止日期',
  reason_type varchar(50) DEFAULT NULL COMMENT '终止原因类型',
  reason varchar(1000) DEFAULT NULL COMMENT '终止原因',
  attachment_ids varchar(255) DEFAULT NULL COMMENT '附件ID，逗号分隔',
  apply_user_id int(10) unsigned NOT NULL COMMENT '发起人ID',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0待审核 1已通过 2不通过 3退回 4取消',
  current_verify_type_id int(11) DEFAULT '1' COMMENT '当前审核级别',
  approved_time datetime DEFAULT NULL COMMENT '通过时间',
  approved_by int(10) unsigned DEFAULT NULL COMMENT '最终审核人',
  version int(11) NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (id),
  KEY idx_mit_relation_status (relation_table, relation_id, status, is_deleted),
  KEY idx_mit_internship_student (internship_id, student_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='终止学生实习申请';

ALTER TABLE rel_stu_internship_post
  ADD COLUMN internship_status tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1终止审核中 2已终止',
  ADD COLUMN termination_id int(10) unsigned DEFAULT 0 COMMENT '终止申请ID',
  ADD COLUMN terminated_time datetime DEFAULT NULL COMMENT '终止通过时间',
  ADD KEY idx_rsip_termination_status (internship_status, termination_id);

ALTER TABLE rel_title_student
  ADD COLUMN internship_status tinyint NOT NULL DEFAULT 0 COMMENT '0正常 1终止审核中 2已终止',
  ADD COLUMN termination_id int(10) unsigned DEFAULT 0 COMMENT '终止申请ID',
  ADD COLUMN terminated_time datetime DEFAULT NULL COMMENT '终止通过时间',
  ADD KEY idx_rts_termination_status (internship_status, termination_id);

INSERT INTO rel_process_internship_type
  (create_time, is_deleted, update_time, internship_type_id, process_type_id, verify_type_id,
   verify_fifth_role_id, verify_first_role_id, verify_fourth_role_id, verify_second_role_id,
   verify_third_role_id, remarks, code, name, the_order)
SELECT NOW(), b'0', NOW(), bit.id, bpt.id, 1,
       0, 0, 0, 0, 0, NULL, NULL, NULL, 18
FROM base_internship_type bit
JOIN base_process_type bpt ON bpt.code = 'STUDENT_INTERNSHIP_TERMINATION' AND bpt.is_deleted = 0
WHERE bit.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1
    FROM rel_process_internship_type rpit
    WHERE rpit.internship_type_id = bit.id
      AND rpit.process_type_id = bpt.id
      AND rpit.is_deleted = 0
  );

INSERT INTO rel_process_internship
  (create_time, is_deleted, update_time, internship_id, process_type_id, verify_type_id,
   verify_fifth_role_id, verify_first_role_id, verify_fourth_role_id, verify_second_role_id,
   verify_third_role_id, end_time, start_time, code, name, remarks, the_order,
   current_verify_type_id, version)
SELECT NOW(), b'0', NOW(), mi.id, bpt.id, 1,
       0, 0, 0, 0, 0, NULL, NULL, NULL, NULL, NULL, 18, 1, 0
FROM main_internship mi
JOIN base_process_type bpt ON bpt.code = 'STUDENT_INTERNSHIP_TERMINATION' AND bpt.is_deleted = 0
WHERE mi.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1
    FROM rel_process_internship rpi
    WHERE rpi.internship_id = mi.id
      AND rpi.process_type_id = bpt.id
      AND rpi.is_deleted = 0
  );

CREATE OR REPLACE VIEW view_student_internship_termination_candidate AS
SELECT
  rsip.id AS id,
  rsip.create_time AS create_time,
  rsip.is_deleted AS is_deleted,
  rsip.update_time AS update_time,
  mip.internship_id AS internship_id,
  mi.name AS internship_name,
  rsip.student_id AS student_id,
  stu.name AS student_name,
  stu.account AS student_account,
  stu.department_id AS department_id,
  stu.department_name AS department_name,
  'RelStuInternshipPost' AS relation_table,
  rsip.id AS relation_id,
  'EXTERNAL' AS internship_mode,
  mip.name AS post_name,
  NULL AS title_name,
  rts_teacher.teacher_id AS teacher_id,
  teacher.name AS teacher_name,
  rsip.internship_status AS internship_status,
  rsip.termination_id AS termination_id
FROM rel_stu_internship_post rsip
JOIN main_internship_post mip ON mip.id = rsip.internship_post_id AND mip.is_deleted = 0
JOIN main_internship mi ON mi.id = mip.internship_id AND mi.is_deleted = 0
JOIN view_base_user stu ON stu.id = rsip.student_id
LEFT JOIN (
  SELECT rel_internship_id, MIN(NULLIF(teacher_id, 0)) AS teacher_id
  FROM rel_teacher_student
  WHERE is_deleted = 0
  GROUP BY rel_internship_id
) rts_teacher ON rts_teacher.rel_internship_id = rsip.id
LEFT JOIN base_user teacher ON teacher.id = rts_teacher.teacher_id
WHERE rsip.is_deleted = 0
  AND EXISTS (
    SELECT 1
    FROM main_verify_process mvp
    WHERE mvp.relation_id = rsip.id
      AND mvp.table_name = 'RelStuInternshipPost'
      AND mvp.is_audit = 1
      AND mvp.is_deleted = 0
  )
UNION ALL
SELECT
  rts.id + 100000000 AS id,
  rts.create_time AS create_time,
  rts.is_deleted AS is_deleted,
  rts.update_time AS update_time,
  COALESCE(rts.internship_id, rtt.internship_id) AS internship_id,
  mi.name AS internship_name,
  rts.stu_id AS student_id,
  stu.name AS student_name,
  stu.account AS student_account,
  stu.department_id AS department_id,
  stu.department_name AS department_name,
  'RelTitleStudent' AS relation_table,
  rts.id AS relation_id,
  'INTERNAL' AS internship_mode,
  NULL AS post_name,
  rtt.name AS title_name,
  rtt.teacher_id AS teacher_id,
  teacher.name AS teacher_name,
  rts.internship_status AS internship_status,
  rts.termination_id AS termination_id
FROM rel_title_student rts
JOIN rel_title_teacher rtt ON rtt.id = rts.title_id AND rtt.is_deleted = 0
JOIN main_internship mi ON mi.id = COALESCE(rts.internship_id, rtt.internship_id) AND mi.is_deleted = 0
JOIN view_base_user stu ON stu.id = rts.stu_id
LEFT JOIN base_user teacher ON teacher.id = rtt.teacher_id
WHERE rts.is_deleted = 0
  AND rts.is_final = 1;

CREATE OR REPLACE VIEW view_verify_student_internship_termination_merge AS
SELECT
  mvp.id AS id,
  mvp.create_time AS create_time,
  mvp.is_deleted AS is_deleted,
  mvp.update_time AS update_time,
  mvp.id AS verify_process_id,
  mit.id AS termination_id,
  mvp.create_user_id AS create_user_id,
  create_user.name AS create_user_name,
  mvp.verify_user_id AS verify_user_id,
  (
    SELECT GROUP_CONCAT(bu.name ORDER BY bu.id SEPARATOR ',')
    FROM base_user bu
    WHERE mvp.verify_user_id IS NOT NULL
      AND TRIM(mvp.verify_user_id) <> ''
      AND FIND_IN_SET(bu.id, REPLACE(mvp.verify_user_id, '|', ',')) > 0
  ) AS verify_user_name,
  mvp.is_audit AS is_audit,
  mvp.reason AS audit_reason,
  mvp.process_id AS process_id,
  mit.internship_id AS internship_id,
  mi.name AS internship_name,
  mit.student_id AS student_id,
  cand.student_name AS student_name,
  cand.student_account AS student_account,
  cand.department_id AS department_id,
  cand.department_name AS department_name,
  mit.relation_table AS relation_table,
  mit.relation_id AS relation_id,
  cand.internship_mode AS internship_mode,
  cand.post_name AS post_name,
  cand.title_name AS title_name,
  cand.teacher_id AS teacher_id,
  cand.teacher_name AS teacher_name,
  mit.terminate_date AS terminate_date,
  mit.reason_type AS reason_type,
  mit.reason AS reason,
  mit.attachment_ids AS attachment_ids,
  mit.status AS status,
  mit.current_verify_type_id AS current_verify_type_id,
  bvt.name AS current_verify_type_name,
  CASE
    WHEN mit.current_verify_type_id > vrpi.verify_type_id THEN NULL
    WHEN mit.current_verify_type_id = 2 THEN vrpi.verify_first_role_name
    WHEN mit.current_verify_type_id = 3 THEN vrpi.verify_second_role_name
    WHEN mit.current_verify_type_id = 4 THEN vrpi.verify_third_role_name
    WHEN mit.current_verify_type_id = 5 THEN vrpi.verify_fourth_role_name
    WHEN mit.current_verify_type_id = 6 THEN vrpi.verify_fifth_role_name
    ELSE NULL
  END AS current_role_name,
  (mit.current_verify_type_id > vrpi.verify_type_id) AS is_all_verified
FROM main_verify_process mvp
JOIN (
  SELECT relation_id, table_name, MAX(id) AS max_id
  FROM main_verify_process
  WHERE table_name = 'MainInternshipTermination'
    AND is_deleted = 0
  GROUP BY relation_id, table_name
) latest ON latest.max_id = mvp.id
JOIN main_internship_termination mit ON mit.id = mvp.relation_id AND mit.is_deleted = 0
LEFT JOIN view_student_internship_termination_candidate cand
  ON cand.relation_table = mit.relation_table AND cand.relation_id = mit.relation_id
LEFT JOIN main_internship mi ON mi.id = mit.internship_id
LEFT JOIN base_user create_user ON create_user.id = mvp.create_user_id
LEFT JOIN view_rel_process_internship vrpi ON vrpi.id = mvp.process_id
LEFT JOIN base_verify_type bvt ON bvt.id = mit.current_verify_type_id
WHERE mvp.table_name = 'MainInternshipTermination'
  AND mvp.is_deleted = 0;
