-- ============================================================================
-- DATA-OUTLET-003（本地建议包）：初始用户-档口授权建议报告（只读）
-- ============================================================================
-- 用途：为「生产副本」初始授权提供逐用户建议，输出 outletScope / peopleScope /
--       默认档口 / 多档口 / 既有绑定 / decision。
--
-- 安全约束（强制）：
--   1. 本文件只包含 SELECT，不包含任何 INSERT / UPDATE / DELETE。
--   2. 只允许在「生产库副本」上执行，禁止连接 NAS / 生产。
--   3. 不自动授权：decision 恒为 NEEDS_REVIEW；任何写入由人工按确认模板在控制台完成。
--   4. 无法从角色/既有绑定推断时输出 NEEDS_REVIEW，不得猜测。
--
-- 用法：在副本库执行本文件，将结果导出为 CSV，与
--       scripts/outlet-user-outlet-authorization-template.csv 一并交付人工确认。
-- ============================================================================

WITH user_roles AS (
  SELECT
    u.id            AS user_id,
    u.tenant_id     AS tenant_id,
    u.username      AS username,
    u.nickname      AS nickname,
    GROUP_CONCAT(DISTINCT r.role_code ORDER BY r.role_code) AS role_codes
  FROM sys_user u
  LEFT JOIN sys_user_role ur ON ur.user_id = u.id
  LEFT JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
  WHERE u.deleted = 0 AND u.status = 1
  GROUP BY u.id, u.tenant_id, u.username, u.nickname
),
perms AS (
  SELECT
    r.tenant_id AS tenant_id,
    ur.user_id  AS user_id,
    MAX(CASE WHEN p.code = 'data:outlet:all' THEN 1 ELSE 0 END)        AS has_outlet_all,
    MAX(CASE WHEN p.code = 'data:order:peopleAll' THEN 1 ELSE 0 END)   AS has_people_all,
    MAX(CASE WHEN p.code = 'data:outlet:unassigned' THEN 1 ELSE 0 END) AS has_unassigned
  FROM sys_user_role ur
  JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
  JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.deleted = 0 AND rp.tenant_id = r.tenant_id
  JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0 AND p.status = 1
  GROUP BY r.tenant_id, ur.user_id
),
bindings AS (
  SELECT
    suo.tenant_id AS tenant_id,
    suo.user_id   AS user_id,
    GROUP_CONCAT(DISTINCT o.outlet_code ORDER BY o.outlet_code) AS existing_outlet_codes,
    MAX(CASE WHEN suo.is_default = 1 THEN o.outlet_code END)    AS existing_default_outlet_code,
    COUNT(DISTINCT o.outlet_code)                               AS existing_outlet_count
  FROM sys_user_outlet suo
  JOIN sales_outlet o ON o.id = suo.outlet_id AND o.deleted = 0 AND o.status = 1
  WHERE suo.deleted = 0 AND suo.status = 1
  GROUP BY suo.tenant_id, suo.user_id
)
SELECT
  ur.tenant_id,
  ur.user_id,
  ur.username,
  ur.nickname,
  ur.role_codes,
  COALESCE(p.has_outlet_all, 0)    AS has_outlet_all,
  COALESCE(p.has_people_all, 0)    AS has_people_all,
  COALESCE(p.has_unassigned, 0)    AS has_unassigned,
  b.existing_outlet_codes,
  b.existing_default_outlet_code,
  CASE
    WHEN COALESCE(p.has_outlet_all, 0) = 1 THEN 'ALL_SUGGESTED'
    WHEN b.existing_outlet_count IS NOT NULL THEN 'ASSIGNED_SUGGESTED'
    ELSE 'NEEDS_REVIEW'
  END                              AS suggested_outlet_scope,
  CASE
    WHEN COALESCE(p.has_people_all, 0) = 1 THEN 'ALL_USERS_SUGGESTED'
    WHEN ur.role_codes IS NOT NULL THEN 'SELF_SUGGESTED'
    ELSE 'NEEDS_REVIEW'
  END                              AS suggested_people_scope,
  CASE
    WHEN b.existing_default_outlet_code IS NOT NULL THEN b.existing_default_outlet_code
    WHEN b.existing_outlet_count = 1 THEN b.existing_outlet_codes
    ELSE 'NEEDS_REVIEW'
  END                              AS suggested_default_outlet_code,
  'NEEDS_REVIEW'                   AS decision,
  CASE
    WHEN COALESCE(p.has_outlet_all, 0) = 1
      THEN '角色拥有 data:outlet:all，建议 ALL；仍需人工确认后授权'
    WHEN b.existing_outlet_count IS NOT NULL
      THEN '已有档口绑定，建议沿用；默认档口与人员范围需人工确认'
    ELSE '无法从角色/既有绑定推断档口范围，必须人工确认'
  END                              AS reason
FROM user_roles ur
LEFT JOIN perms p ON p.user_id = ur.user_id AND p.tenant_id = ur.tenant_id
LEFT JOIN bindings b ON b.user_id = ur.user_id AND b.tenant_id = ur.tenant_id
ORDER BY ur.tenant_id, ur.username;
