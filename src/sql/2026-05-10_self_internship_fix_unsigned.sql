-- =====================================================================
-- 自主实习功能 修复：允许 all_person_num 存 -1（无限招哨兵值）
-- 日期：2026-05-10
-- 背景：main_internship_post.all_person_num 原为 int unsigned，
--       自主实习虚拟岗位 allPersonNum=-1 时 MySQL 拒插，导致 addNewInternship
--       的 hook 抛异常并把整个事务标记 rollback-only。
-- 影响：现有数据全部 >=0，改为 signed int 无兼容风险。
-- =====================================================================

ALTER TABLE `main_internship_post`
  MODIFY COLUMN `all_person_num` int DEFAULT '0' COMMENT '岗位人数';
