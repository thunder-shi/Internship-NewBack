-- RelTitleStudent candidate/final-assignment isolation.
-- Run this before deploying the backend code that reads these columns.

ALTER TABLE rel_title_student
  ADD COLUMN source_type varchar(32) NOT NULL DEFAULT 'STUDENT_CANDIDATE' COMMENT 'STUDENT_CANDIDATE or TEACHER_ASSIGN',
  ADD COLUMN is_final tinyint NOT NULL DEFAULT 0 COMMENT '1 means final title assignment',
  ADD COLUMN internship_id int NULL COMMENT 'denormalized internship id from rel_title_teacher.internship_id',
  ADD COLUMN confirmed_by int NULL COMMENT 'final confirmer user id',
  ADD COLUMN confirmed_time datetime NULL COMMENT 'final confirmation time',
  ADD KEY idx_rts_title_final_deleted (title_id, is_final, is_deleted),
  ADD KEY idx_rts_stu_internship_final_deleted (stu_id, internship_id, is_final, is_deleted);

UPDATE rel_title_student rts
JOIN rel_title_teacher rtt ON rtt.id = rts.title_id
SET rts.internship_id = rtt.internship_id
WHERE rts.internship_id IS NULL;

UPDATE rel_title_student rts
SET rts.source_type = 'TEACHER_ASSIGN',
    rts.is_final = 1,
    rts.confirmed_time = COALESCE(rts.confirmed_time, rts.update_time)
WHERE rts.is_deleted = 0
  AND rts.current_verify_type_id = 1
  AND NOT EXISTS (
    SELECT 1
    FROM main_verify_process mvp
    WHERE mvp.relation_id = rts.id
      AND mvp.table_name = 'RelTitleStudent'
      AND mvp.is_deleted = 0
  );

UPDATE rel_title_student rts
JOIN (
  SELECT mvp.relation_id, MAX(mvp.update_time) AS confirmed_time
  FROM main_verify_process mvp
  JOIN rel_process_internship rpi ON rpi.id = mvp.process_id
  JOIN rel_title_student inner_rts ON inner_rts.id = mvp.relation_id
  WHERE mvp.table_name = 'RelTitleStudent'
    AND mvp.is_deleted = 0
    AND mvp.is_audit = 1
    AND inner_rts.current_verify_type_id > COALESCE(rpi.verify_type_id, 1)
  GROUP BY mvp.relation_id
) passed ON passed.relation_id = rts.id
SET rts.source_type = 'STUDENT_CANDIDATE',
    rts.is_final = 1,
    rts.confirmed_time = COALESCE(rts.confirmed_time, passed.confirmed_time)
WHERE rts.is_deleted = 0;

CREATE OR REPLACE VIEW view_rel_title_student AS
SELECT
  rts.id AS id,
  rts.create_time AS create_time,
  rts.is_deleted AS is_deleted,
  rts.update_time AS update_time,
  rts.current_verify_type_id AS current_verify_type_id,
  rts.stu_id AS stu_id,
  rts.title_id AS title_id,
  bu_student.name AS student_name,
  bu_student.account AS student_account,
  rtt.name AS name,
  rtt.remarks AS remarks,
  rts.topic_reasons AS topic_reasons,
  COALESCE(rts.internship_id, rtt.internship_id) AS internship_id,
  rtt.teacher_id AS teacher_id,
  rtt.is_limit AS is_limit,
  bu_teacher.name AS teacher_name,
  rts.code AS code,
  rts.source_type AS source_type,
  rts.is_final AS is_final,
  rts.confirmed_by AS confirmed_by,
  rts.confirmed_time AS confirmed_time
FROM rel_title_student rts
JOIN rel_title_teacher rtt ON rts.title_id = rtt.id
JOIN base_user bu_student ON rts.stu_id = bu_student.ID
JOIN base_user bu_teacher ON rtt.teacher_id = bu_teacher.ID;

CREATE OR REPLACE VIEW view_rel_title_teacher_student AS
SELECT
  rtt.id AS id,
  rtt.create_time AS create_time,
  rtt.update_time AS update_time,
  rtt.is_deleted AS is_deleted,
  rtt.code AS code,
  rtt.name AS name,
  rtt.remarks AS remarks,
  rtt.internship_id AS internship_id,
  rtt.teacher_id AS teacher_id,
  bt.name AS teacher_name,
  rtt.is_limit AS is_limit,
  rts.id AS rel_title_student_id,
  rts.current_verify_type_id AS current_verify_type_id,
  rts.stu_id AS stu_id,
  bs.name AS student_name,
  bs.account AS student_account,
  rts.topic_reasons AS topic_reasons,
  rts.title_id AS title_id,
  rts.source_type AS source_type,
  rts.is_final AS is_final,
  rts.confirmed_by AS confirmed_by,
  rts.confirmed_time AS confirmed_time,
  mvp.is_audit AS is_audit,
  mvp.is_audit AS isAudit
FROM rel_title_teacher rtt
LEFT JOIN rel_title_student rts
  ON rts.title_id = rtt.id
 AND rts.is_deleted = 0
 AND rts.is_final = 1
LEFT JOIN base_user bs ON bs.ID = rts.stu_id
LEFT JOIN base_user bt ON bt.ID = rtt.teacher_id
LEFT JOIN (
  SELECT m1.*
  FROM main_verify_process m1
  JOIN (
    SELECT relation_id, table_name, MAX(id) AS max_id
    FROM main_verify_process
    WHERE table_name = 'RelTitleTeacher'
    GROUP BY relation_id, table_name
  ) m2 ON m1.id = m2.max_id
) mvp ON mvp.relation_id = rtt.id AND mvp.table_name = 'RelTitleTeacher'
WHERE rtt.is_deleted = 0;

CREATE OR REPLACE VIEW view_verify_process_rel_title_student AS
SELECT
  mvp.id AS id,
  mvp.create_time AS create_time,
  mvp.update_time AS update_time,
  mvp.is_deleted AS is_deleted,
  mvp.relation_id AS relation_id,
  mvp.process_id AS process_id,
  mvp.table_name AS table_name,
  mvp.create_user_id AS create_user_id,
  mvp.verify_user_id AS verify_user_id,
  mvp.is_audit AS is_audit,
  mvp.reason AS reason,
  rts.id AS rel_title_student_id,
  rts.current_verify_type_id AS current_verify_type_id,
  rts.stu_id AS stu_id,
  rts.title_id AS title_id,
  rts.topic_reasons AS topic_reasons,
  rts.source_type AS source_type,
  rts.is_final AS is_final,
  rts.confirmed_by AS confirmed_by,
  rts.confirmed_time AS confirmed_time,
  rtt.id AS rel_title_teacher_id,
  COALESCE(rts.internship_id, rtt.internship_id) AS internship_id,
  rtt.teacher_id AS teacher_id,
  rtt.is_limit AS is_limit,
  rtt.code AS code,
  rtt.name AS name,
  rtt.remarks AS remarks,
  bs.name AS student_name,
  bs.account AS student_account,
  bt.name AS teacher_name,
  bcu.name AS create_user_name,
  (SELECT GROUP_CONCAT(bu.name ORDER BY bu.ID ASC SEPARATOR ',')
   FROM base_user bu
   WHERE mvp.verify_user_id IS NOT NULL
     AND TRIM(mvp.verify_user_id) <> ''
     AND FIND_IN_SET(bu.ID, REPLACE(mvp.verify_user_id, '|', ',')) > 0) AS verify_user_name,
  mi.id AS m_internship_id,
  mi.name AS internship_name
FROM main_verify_process mvp
LEFT JOIN rel_title_student rts ON rts.id = mvp.relation_id AND rts.is_deleted = 0
LEFT JOIN rel_title_teacher rtt ON rtt.id = rts.title_id AND rtt.is_deleted = 0
LEFT JOIN base_user bs ON bs.ID = rts.stu_id
LEFT JOIN base_user bt ON bt.ID = rtt.teacher_id
LEFT JOIN base_user bcu ON bcu.ID = mvp.create_user_id
JOIN rel_process_internship rpi ON mvp.process_id = rpi.id
JOIN main_internship mi ON rpi.internship_id = mi.id AND rtt.internship_id = mi.id
WHERE mvp.is_deleted = 0
  AND mvp.table_name = 'RelTitleStudent';

CREATE OR REPLACE VIEW view_verify_process_rel_title_student_merge AS
SELECT
  mvp.id AS id,
  mvp.create_time AS create_time,
  mvp.is_deleted AS is_deleted,
  mvp.update_time AS update_time,
  mvp.create_user_id AS create_user_id,
  mvp.is_audit AS is_audit,
  mvp.reason AS reason,
  rts.topic_reasons AS topic_reasons,
  rts.source_type AS source_type,
  rts.is_final AS is_final,
  rts.confirmed_by AS confirmed_by,
  rts.confirmed_time AS confirmed_time,
  mvp.relation_id AS relation_id,
  mvp.table_name AS table_name,
  mvp.verify_user_id AS verify_user_id,
  mvp.process_id AS process_id,
  bu_create.name AS create_user_name,
  rtt.name AS name,
  rtt.remarks AS remarks,
  COALESCE(rts.internship_id, rtt.internship_id) AS internship_id,
  rtt.teacher_id AS teacher_id,
  mi.name AS internship_name,
  bt.name AS teacher_name,
  (SELECT GROUP_CONCAT(bu.name SEPARATOR ',')
   FROM base_user bu
   WHERE FIND_IN_SET(bu.ID, REPLACE(mvp.verify_user_id, '|', ','))) AS verify_user_name,
  rts.current_verify_type_id AS current_verify_type_id,
  bvt.name AS current_verify_type_name,
  rtt.code AS code,
  mi.id AS m_internship_id,
  CASE
    WHEN rts.current_verify_type_id > vrpi.verify_type_id THEN NULL
    WHEN rts.current_verify_type_id = 2 THEN vrpi.verify_first_role_name
    WHEN rts.current_verify_type_id = 3 THEN vrpi.verify_second_role_name
    WHEN rts.current_verify_type_id = 4 THEN vrpi.verify_third_role_name
    WHEN rts.current_verify_type_id = 5 THEN vrpi.verify_fourth_role_name
    WHEN rts.current_verify_type_id = 6 THEN vrpi.verify_fifth_role_name
    ELSE NULL
  END AS current_role_name,
  (rts.current_verify_type_id > vrpi.verify_type_id) AS is_all_verified,
  rts.id AS rel_title_student_id,
  rts.stu_id AS stu_id,
  bs.name AS student_name,
  bs.account AS student_account,
  rts.title_id AS title_id,
  rtt.id AS rel_title_teacher_id,
  rtt.is_limit AS is_limit,
  mvp.id AS verify_process_id,
  mvp.create_time AS vp_create_time,
  mvp.update_time AS vp_update_time
FROM main_verify_process mvp
JOIN (
  SELECT p.process_id AS process_id, p.relation_id AS relation_id, MAX(p.id) AS max_id
  FROM main_verify_process p
  WHERE p.table_name = 'RelTitleStudent'
    AND p.is_deleted = 0
  GROUP BY p.process_id, p.relation_id
) latest ON mvp.id = latest.max_id
JOIN rel_title_student rts ON mvp.relation_id = rts.id AND rts.is_deleted = 0
JOIN rel_title_teacher rtt ON rts.title_id = rtt.id AND rtt.is_deleted = 0
JOIN view_rel_process_internship vrpi ON mvp.process_id = vrpi.id
LEFT JOIN main_internship mi ON rtt.internship_id = mi.id AND mi.is_deleted = 0
LEFT JOIN base_user bu_create ON bu_create.ID = mvp.create_user_id
LEFT JOIN base_user bt ON bt.ID = rtt.teacher_id
LEFT JOIN base_user bs ON bs.ID = rts.stu_id
LEFT JOIN base_verify_type bvt ON rts.current_verify_type_id = bvt.id
WHERE mvp.table_name = 'RelTitleStudent'
  AND mvp.is_deleted = 0;
