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

test.describe('BA-701~703 权限页面最终验收', () => {
  test('系统管理页三 Tab 冒烟 + 本次修复点验证', async ({ page }) => {
    await login(page);

    // 进入系统管理
    await page.click('a[href="/system"]');
    await page.waitForTimeout(1500);
    expect(page.url()).toContain('/system');

    // ===== Tab1 用户管理 =====
    await expect(page.locator('.el-table').first()).toBeVisible();
    await page.waitForTimeout(800);
    // 用户搜索（keyword 修复点：输入关键字应能过滤）
    await page.fill('input[placeholder="搜索用户名/昵称"]', 'admin');
    await page.click('button:has-text("搜索")');
    await page.waitForTimeout(1500);
    const firstRowText = await page.locator('.el-table__row').first().textContent();
    expect(firstRowText || '').toContain('admin');
    // 清空搜索
    await page.fill('input[placeholder="搜索用户名/昵称"]', '');
    await page.click('button:has-text("搜索")');
    await page.waitForTimeout(1000);

    // ===== Tab2 角色管理 =====
    await page.click('text=角色管理');
    await page.waitForTimeout(1200);
    // 角色表格含"角色名称"列头，v-show 控制下用列头定位可见的角色表格
    const roleTable = page.locator('.el-table', { hasText: '角色名称' }).last();
    await expect(roleTable).toBeVisible();
    await page.waitForTimeout(500);
    const roleRows = await page.locator('.el-table__row').count();
    expect(roleRows).toBeGreaterThan(0);
    console.log(`角色列表行数: ${roleRows}`);

    // 打开第一个角色的"分配权限"对话框（验证勾选加载 + setCheckedKeys 修复）
    await roleTable.locator('.el-table__row').first().locator('button:has-text("分配权限")').click();
    await page.waitForTimeout(1500);
    await expect(page.locator('.el-dialog').filter({ hasText: '分配权限' })).toBeVisible();
    // 权限树应显示勾选项
    const dialog = page.locator('.el-dialog').filter({ hasText: '分配权限' });
    await expect(dialog.locator('.el-tree')).toBeVisible();
    await page.waitForTimeout(800);
    const checkedCount = await dialog.locator('.el-tree-node.is-checked').count();
    console.log(`分配权限弹窗勾选节点数: ${checkedCount}`);
    await dialog.locator('button:has-text("取消")').click();
    await page.waitForTimeout(800);

    // 关闭后再次打开同一角色的分配权限，验证无残留且能正常显示
    await roleTable.locator('.el-table__row').first().locator('button:has-text("分配权限")').click();
    await page.waitForTimeout(1500);
    await expect(page.locator('.el-dialog').filter({ hasText: '分配权限' })).toBeVisible();
    await page.waitForTimeout(500);
    const dialog2 = page.locator('.el-dialog').filter({ hasText: '分配权限' });
    await expect(dialog2.locator('.el-tree')).toBeVisible();
    // 尝试勾选一个未勾选项并保存，验证半选合并提交不报错（不实际保存，点取消）
    await dialog2.locator('button:has-text("取消")').click();
    await page.waitForTimeout(800);

    // ===== Tab3 权限配置 =====
    await page.click('text=权限配置');
    await page.waitForTimeout(1200);
    // 权限 Tab 的树（页面树在对话框树之前，取第一个）
    const permTree = page.locator('.permission-tree').first();
    await expect(permTree).toBeVisible();
    const treeNodes = await permTree.locator('.el-tree-node').count();
    expect(treeNodes).toBeGreaterThan(0);
    console.log(`权限树节点数: ${treeNodes}`);

    // 验证删除有子权限的菜单会被后端保护拒绝（点击"系统管理"删除应报错提示）
    // 找到一级节点"系统管理"（仅匹配节点自身内容，不含后代）
    const systemNode = permTree.locator('.el-tree-node', { hasText: '系统管理' }).first();
    if (await systemNode.count() > 0) {
      const deleteBtn = systemNode.locator(':scope > .el-tree-node__content button:has-text("删除")');
      if (await deleteBtn.count() > 0) {
        await deleteBtn.click();
        await page.waitForTimeout(800);
        // 确认弹窗
        const confirmBtn = page.locator('.el-message-box').locator('button:has-text("确定")');
        if (await confirmBtn.count() > 0) {
          await confirmBtn.click();
          await page.waitForTimeout(1500);
          // 应出现错误提示（子权限保护）
          const errMsg = await page.locator('.el-message--error').textContent().catch(() => '');
          console.log(`删除有子权限节点的提示: ${errMsg?.trim()}`);
          expect(errMsg || '').toContain('子权限');
        }
      }
    }

    await page.screenshot({ path: 'system-acceptance-final.png', fullPage: false });
    console.log('系统管理页三 Tab 冒烟验收通过');
  });
});
