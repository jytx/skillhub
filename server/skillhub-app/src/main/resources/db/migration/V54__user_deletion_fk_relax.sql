-- V54__user_deletion_fk_relax.sql
-- 支持删除用户：将「历史归因」类可空外键改为 ON DELETE SET NULL。
-- 这些列仅记录"是谁做的"，删除用户账号时保留历史行、把归因置空，
-- 而不是阻止删除或连带删除业务数据。
-- 约束名沿用 PostgreSQL 内联 REFERENCES 的默认命名 <table>_<column>_fkey。

ALTER TABLE audit_log             DROP CONSTRAINT audit_log_actor_user_id_fkey;
ALTER TABLE audit_log             ADD CONSTRAINT audit_log_actor_user_id_fkey
    FOREIGN KEY (actor_user_id) REFERENCES user_account(id) ON DELETE SET NULL;

ALTER TABLE namespace             DROP CONSTRAINT namespace_created_by_fkey;
ALTER TABLE namespace             ADD CONSTRAINT namespace_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES user_account(id) ON DELETE SET NULL;

ALTER TABLE review_task           DROP CONSTRAINT review_task_reviewed_by_fkey;
ALTER TABLE review_task           ADD CONSTRAINT review_task_reviewed_by_fkey
    FOREIGN KEY (reviewed_by) REFERENCES user_account(id) ON DELETE SET NULL;

ALTER TABLE promotion_request     DROP CONSTRAINT promotion_request_reviewed_by_fkey;
ALTER TABLE promotion_request     ADD CONSTRAINT promotion_request_reviewed_by_fkey
    FOREIGN KEY (reviewed_by) REFERENCES user_account(id) ON DELETE SET NULL;

ALTER TABLE profile_change_request DROP CONSTRAINT profile_change_request_reviewer_id_fkey;
ALTER TABLE profile_change_request ADD CONSTRAINT profile_change_request_reviewer_id_fkey
    FOREIGN KEY (reviewer_id) REFERENCES user_account(id) ON DELETE SET NULL;

ALTER TABLE label_definition      DROP CONSTRAINT label_definition_created_by_fkey;
ALTER TABLE label_definition      ADD CONSTRAINT label_definition_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES user_account(id) ON DELETE SET NULL;

ALTER TABLE skill_label           DROP CONSTRAINT skill_label_created_by_fkey;
ALTER TABLE skill_label           ADD CONSTRAINT skill_label_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES user_account(id) ON DELETE SET NULL;

ALTER TABLE skill_rating          DROP CONSTRAINT skill_rating_moderated_by_fkey;
ALTER TABLE skill_rating          ADD CONSTRAINT skill_rating_moderated_by_fkey
    FOREIGN KEY (moderated_by) REFERENCES user_account(id) ON DELETE SET NULL;

ALTER TABLE skill                 DROP CONSTRAINT skill_hidden_by_fkey;
ALTER TABLE skill                 ADD CONSTRAINT skill_hidden_by_fkey
    FOREIGN KEY (hidden_by) REFERENCES user_account(id) ON DELETE SET NULL;

ALTER TABLE skill_version         DROP CONSTRAINT skill_version_yanked_by_fkey;
ALTER TABLE skill_version         ADD CONSTRAINT skill_version_yanked_by_fkey
    FOREIGN KEY (yanked_by) REFERENCES user_account(id) ON DELETE SET NULL;
