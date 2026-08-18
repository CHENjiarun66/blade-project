import { test, expect, Page } from '@playwright/test';

const BASE_URL = 'http://localhost:5777';

async function login(page: Page) {
  await page.goto(`${BASE_URL}/login`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1500);
  const captcha = await page.locator('.captcha-text').textContent();
  await page.fill('input[placeholder="输入公司 ID 或名称"]', 'test_tenant');
  await page.fill('input[placeholder="您的管理员账号"]', 'admin');
  await page.fill('input[placeholder="••••••••"]', 'admin123');
  await page.fill('input[placeholder="输入验证码"]', captcha?.trim() || '');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(3000);
  expect(page.url()).not.toContain('/login');
}

async function gotoSystem(page: Page) {
  await page.click('a[href="/system"]');
  await page.waitForTimeout(1500);
  expect(page.url()).toContain('/system');
}

// 确认弹窗按钮：ElMessageBox 未配中文 locale，主按钮是 primary（英文 OK）
async function clickConfirm(page: Page) {
  const box = page.locator('.el-message-box').last();
  await expect(box).toBeVisible();
  const confirmBtn = box.locator('.el-message-box__btns .el-button--primary');
  await expect(confirmBtn).toBeVisible();
  await confirmBtn.click();
}

// 展开 el-tree 节点
async function expandTreeNode(page: Page, root: any, nodeText: string) {
  const node = root.locator('.el-tree-node', { hasText: nodeText }).first();
  await expect(node).toBeVisible();
  const expanded = await node.evaluate((el: Element) => el.classList.contains('is-expanded'));
  if (!expanded) {
    await node.locator('.el-tree-node__expand-icon').click();
    await page.waitForTimeout(400);
    await expect(node).toHaveClass(/is-expanded/);
  }
}

test.describe('BA-701~703 权限页面最终验收', () => {
  test('用户管理 CRUD：新建/搜索/编辑/重置密码/删除', async ({ page }) => {
    const suffix = Date.now();
    const username = `u${String(suffix).slice(-8)}`;
    const password = 'Passw0rd!';
    const newNickname = `昵称${String(suffix).slice(-8)}`;

    await login(page);
    await gotoSystem(page);

    const userTable = page.locator('.el-table:visible').first();
    await expect(userTable).toBeVisible();

    // ---- 新建用户 ----
    await page.click('button:has-text("新建用户")');
    const userDialog = page.locator('.el-dialog:visible').filter({ hasText: '新建用户' });
    await expect(userDialog).toBeVisible();
    await userDialog.locator('input[placeholder="请输入用户名"]').fill(username);
    await expect(userDialog.locator('input[placeholder="请输入用户名"]')).toHaveValue(username);
    await userDialog.locator('input[placeholder="请输入密码"]').fill(password);
    await expect(userDialog.locator('input[placeholder="请输入密码"]')).toHaveValue(password);
    await userDialog.locator('input[placeholder="请输入昵称"]').fill(`用户${String(suffix).slice(-8)}`);
    // 分配角色：选择"销售员"
    await userDialog.locator('.el-select').click();
    const salesOption = page.locator('.el-select-dropdown__item', { hasText: '销售员' }).last();
    await expect(salesOption).toBeVisible();
    await salesOption.click();
    await page.waitForTimeout(600);
    // 关闭下拉（multiple select 选择后不自动收起，可能遮挡"确定"按钮）
    await page.keyboard.press('Escape');
    await page.waitForTimeout(300);
    await userDialog.locator('button:has-text("确定")').click();
    await expect(page.locator('.el-message--success').last()).toContainText('创建成功');
    await page.waitForTimeout(800);

    // ---- 搜索验证（keyword 修复点）----
    await page.fill('input[placeholder="搜索用户名/昵称"]', username);
    await page.click('button:has-text("搜索")');
    await page.waitForTimeout(1200);
    const createdRow = userTable.locator('.el-table__row', { hasText: username });
    await expect(createdRow).toBeVisible();

    // ---- 编辑用户 ----
    await createdRow.locator('button:has-text("编辑")').click();
    const editDialog = page.locator('.el-dialog:visible').filter({ hasText: '编辑用户' });
    await expect(editDialog).toBeVisible();
    await editDialog.locator('input[placeholder="请输入昵称"]').fill(newNickname);
    await expect(editDialog.locator('input[placeholder="请输入昵称"]')).toHaveValue(newNickname);
    await editDialog.locator('button:has-text("确定")').click();
    await expect(page.locator('.el-message--success').last()).toContainText('更新成功');
    await page.waitForTimeout(800);
    await expect(userTable.locator('.el-table__row', { hasText: newNickname })).toBeVisible();

    // ---- 重置密码 ----
    await createdRow.locator('button:has-text("重置密码")').click();
    const resetDialog = page.locator('.el-dialog:visible').filter({ hasText: '重置密码' });
    await expect(resetDialog).toBeVisible();
    await resetDialog.locator('input[placeholder="请输入新密码"]').fill('NewPass123!');
    await resetDialog.locator('input[placeholder="请确认新密码"]').fill('NewPass123!');
    await resetDialog.locator('button:has-text("确定重置")').click();
    await expect(page.locator('.el-message--success').last()).toContainText('密码重置成功');
    await page.waitForTimeout(600);

    // ---- 删除用户 ----
    await createdRow.locator('button:has-text("删除")').click();
    await clickConfirm(page);
    await expect(page.locator('.el-message--success').last()).toContainText('删除成功');
    await page.waitForTimeout(800);
    await expect(userTable.locator('.el-table__row', { hasText: username })).toHaveCount(0);

    // 清理搜索
    await page.fill('input[placeholder="搜索用户名/昵称"]', '');
    await page.click('button:has-text("搜索")');
    await page.waitForTimeout(600);
    console.log(`用户管理 CRUD 验收通过：${username}`);
  });

  test('角色管理 CRUD + 分配权限半选保存验证 + 角色删除保护', async ({ page }) => {
    const suffix = Date.now();
    const roleName = `角色${String(suffix).slice(-8)}`;
    const roleCode = `R_ACC_${String(suffix).slice(-8)}`;
    const editedDesc = `描述${String(suffix).slice(-8)}`;

    await login(page);
    await gotoSystem(page);

    await page.click('text=角色管理');
    await page.waitForTimeout(1200);
    const roleTable = page.locator('.el-table:visible').first();
    await expect(roleTable).toBeVisible();

    // ---- 新建角色 ----
    await page.click('button:has-text("新建角色")');
    const roleDialog = page.locator('.el-dialog:visible').filter({ hasText: '新建角色' });
    await expect(roleDialog).toBeVisible();
    await roleDialog.locator('input[placeholder="请输入角色名称"]').fill(roleName);
    await expect(roleDialog.locator('input[placeholder="请输入角色名称"]')).toHaveValue(roleName);
    await roleDialog.locator('input[placeholder="请输入角色编码，如 ROLE_ADMIN"]').fill(roleCode);
    await expect(roleDialog.locator('input[placeholder="请输入角色编码，如 ROLE_ADMIN"]')).toHaveValue(roleCode);
    await roleDialog.locator('button:has-text("确定")').click();
    await expect(page.locator('.el-message--success').last()).toContainText('创建成功');
    await page.waitForTimeout(800);
    const createdRoleRow = roleTable.locator('.el-table__row', { hasText: roleName });
    await expect(createdRoleRow).toBeVisible();

    // ---- 编辑角色（改描述）----
    await createdRoleRow.locator('button:has-text("编辑")').click();
    const editRoleDialog = page.locator('.el-dialog:visible').filter({ hasText: '编辑角色' });
    await expect(editRoleDialog).toBeVisible();
    await editRoleDialog.locator('textarea').fill(editedDesc);
    await expect(editRoleDialog.locator('textarea')).toHaveValue(editedDesc);
    await editRoleDialog.locator('button:has-text("确定")').click();
    await expect(page.locator('.el-message--success').last()).toContainText('更新成功');
    await page.waitForTimeout(800);

    // ---- 分配权限：勾选部分子权限（父节点半选）并真实保存 ----
    await createdRoleRow.locator('button:has-text("分配权限")').click();
    const permDialog = page.locator('.el-dialog:visible').filter({ hasText: '分配权限' });
    await expect(permDialog).toBeVisible();
    const permTree = permDialog.locator('.el-tree');
    await expect(permTree).toBeVisible();
    await expandTreeNode(page, permTree, '订单管理');
    // 定位"订单管理"节点及其子节点"新建订单"（新角色权限为空，勾选后父节点半选）
    const orderNode = permTree.locator('.el-tree-node', { hasText: '订单管理' }).first();
    await expect(orderNode).toBeVisible();
    const childNode = orderNode.locator(':scope > .el-tree-node__children > .el-tree-node').filter({ hasText: '新建订单' }).first();
    await expect(childNode).toBeVisible();
    // 勾选子权限"新建订单"
    await childNode.locator(':scope > .el-tree-node__content .el-checkbox').click();
    await page.waitForTimeout(500);
    // 硬断言：子节点已勾选
    await expect(childNode.locator(':scope > .el-tree-node__content .el-checkbox input')).toBeChecked();
    // 硬断言：父节点"订单管理"半选（Element Plus 半选体现在 input.indeterminate）
    const orderParentInput = orderNode.locator(':scope > .el-tree-node__content .el-checkbox input');
    await expect(orderParentInput).toHaveJSProperty('indeterminate', true);
    // 真实保存
    await permDialog.locator('button:has-text("确定分配")').click();
    await expect(page.locator('.el-message--success').last()).toContainText('权限分配成功');
    await page.waitForTimeout(800);

    // ---- 重新打开验证：半选父节点已持久化（合并 getHalfCheckedKeys 修复点）----
    await createdRoleRow.locator('button:has-text("分配权限")').click();
    const permDialog2 = page.locator('.el-dialog:visible').filter({ hasText: '分配权限' });
    await expect(permDialog2).toBeVisible();
    const permTree2 = permDialog2.locator('.el-tree');
    await expect(permTree2).toBeVisible();
    await expandTreeNode(page, permTree2, '订单管理');
    const orderNode2 = permTree2.locator('.el-tree-node', { hasText: '订单管理' }).first();
    await expect(orderNode2).toBeVisible();
    // 硬断言：父节点半选状态保留
    await expect(orderNode2.locator(':scope > .el-tree-node__content .el-checkbox input')).toHaveJSProperty('indeterminate', true);
    // 硬断言：子权限"新建订单"仍勾选
    const childNode2 = orderNode2.locator(':scope > .el-tree-node__children > .el-tree-node').filter({ hasText: '新建订单' }).first();
    await expect(childNode2.locator(':scope > .el-tree-node__content .el-checkbox input')).toBeChecked();
    await permDialog2.locator('button:has-text("取消")').click();
    await page.waitForTimeout(600);

    // ---- 角色删除保护：删除已分配给用户的 ROLE_ADMIN 应被拒绝 ----
    const adminRoleRow = roleTable.locator('.el-table__row', { hasText: '系统管理员' }).first();
    await expect(adminRoleRow).toBeVisible();
    await adminRoleRow.locator('button:has-text("删除")').click();
    await clickConfirm(page);
    await page.waitForTimeout(1500);
    await expect(page.locator('.el-message--error').last()).toContainText('已分配给');
    await page.waitForTimeout(600);

    // ---- 删除自己创建的角色（未分配用户，应成功）----
    await createdRoleRow.locator('button:has-text("删除")').click();
    await clickConfirm(page);
    await expect(page.locator('.el-message--success').last()).toContainText('删除成功');
    await page.waitForTimeout(800);
    await expect(roleTable.locator('.el-table__row', { hasText: roleName })).toHaveCount(0);

    console.log(`角色管理 CRUD + 半选权限保存 + 删除保护 验收通过：${roleName}`);
  });

  test('权限配置：权限树渲染 + 删除有子权限节点被保护', async ({ page }) => {
    await login(page);
    await gotoSystem(page);

    await page.click('text=权限配置');
    await page.waitForTimeout(1200);
    const permTree = page.locator('.permission-tree').first();
    await expect(permTree).toBeVisible();
    const treeNodes = await permTree.locator('.el-tree-node').count();
    expect(treeNodes).toBeGreaterThan(0);
    console.log(`权限树节点数: ${treeNodes}`);

    // 删除有子权限的"系统管理"一级节点 → 应被拒绝
    const systemNode = permTree.locator('.el-tree-node', { hasText: '系统管理' }).first();
    await expect(systemNode).toBeVisible();
    const deleteBtn = systemNode.locator(':scope > .el-tree-node__content button:has-text("删除")');
    await expect(deleteBtn).toBeVisible();
    await deleteBtn.click();
    await page.waitForTimeout(600);
    await clickConfirm(page);
    await page.waitForTimeout(1500);
    await expect(page.locator('.el-message--error').last()).toContainText('子权限');
    await page.waitForTimeout(600);

    // 权限树仍完整
    await expect(permTree.locator('.el-tree-node', { hasText: '系统管理' }).first()).toBeVisible();
    console.log('权限配置 Tab 删除保护验收通过');
  });
});
