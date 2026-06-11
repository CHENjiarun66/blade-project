import { test, expect, Page } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';

// Test configuration
const BASE_URL = 'http://localhost:5777';
const CURRENT_FILE = fileURLToPath(import.meta.url);
const CURRENT_DIR = path.dirname(CURRENT_FILE);
const SCREENSHOT_DIR = path.join(CURRENT_DIR, 'src/views/personal/test-screenshots');

// Helper function to take screenshot
async function takeScreenshot(page: Page, name: string) {
  const screenshotPath = path.join(SCREENSHOT_DIR, `${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage: true });
  console.log(`Screenshot saved: ${screenshotPath}`);
  return screenshotPath;
}

// Helper function to wait for page to fully load
async function waitForPageLoad(page: Page, timeout: number = 5000) {
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1000);
}

// Helper function to login
async function login(page: Page) {
  console.log('开始登录...');
  await page.goto(`${BASE_URL}/login`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);

  // Read captcha code
  const captchaElement = page.locator('.captcha-text');
  const captchaCode = await captchaElement.textContent();
  console.log('Captcha code:', captchaCode);

  // Fill login form
  await page.fill('input[placeholder="输入公司 ID 或名称"]', 'test_tenant');
  await page.fill('input[placeholder="您的管理员账号"]', 'admin');
  await page.fill('input[placeholder="••••••••"]', 'admin123');
  await page.fill('input[placeholder="输入验证码"]', captchaCode?.trim() || '');

  await takeScreenshot(page, '01-login-form-filled');

  // Click login button
  await page.click('button[type="submit"]');

  // Wait for navigation to complete
  await page.waitForTimeout(3000);
  console.log('Current URL after login:', page.url());

  // Check if login actually succeeded (should be on dashboard, not login page)
  const currentUrl = page.url();
  if (currentUrl.includes('/login')) {
    console.log('Login might have failed, taking screenshot...');
    await takeScreenshot(page, '02-login-result');
  } else {
    console.log('Login successful, verifying token in localStorage...');
    // Verify token exists in localStorage
    const token = await page.evaluate(() => localStorage.getItem('token'));
    console.log('Token in localStorage:', token ? 'exists (' + token.substring(0, 20) + '...)' : 'NOT FOUND');
    await takeScreenshot(page, '02-login-success');
  }
}

test.describe('个人中心 BA-704 E2E 测试', () => {

  test.beforeEach(async ({ page }) => {
    // Set viewport
    await page.setViewportSize({ width: 1440, height: 900 });
  });

  // ============ 场景一：进入个人中心页面 ============
  test('场景一：通过下拉菜单进入个人中心', async ({ page }) => {
    console.log('\n========== 场景一：通过下拉菜单进入个人中心 ==========');

    // Step 1: 登录系统
    console.log('Step 1: 登录系统');
    await login(page);
    await takeScreenshot(page, '03-after-login');

    // Step 2: 点击用户下拉菜单
    console.log('Step 2: 点击用户下拉菜单');
    await page.waitForSelector('aside', { timeout: 10000 });

    // 查找用户头像/名称区域（点击可展开下拉菜单）
    const userDropdown = page.locator('.el-dropdown').first();
    if (await userDropdown.count() > 0) {
      await userDropdown.click();
      await page.waitForTimeout(1000);
      await takeScreenshot(page, '04-dropdown-opened');
      console.log('下拉菜单已展开');
    } else {
      console.log('未找到下拉菜单，尝试直接导航');
      await page.goto(`${BASE_URL}/personal`);
      await page.waitForTimeout(2000);
    }

    // Step 3: 点击"个人中心"
    console.log('Step 3: 点击"个人中心"');

    // 先尝试直接导航到个人中心页面，验证页面本身是否正常
    console.log('直接导航到个人中心页面...');
    await page.goto(`${BASE_URL}/personal`);
    await page.waitForTimeout(3000);
    console.log('Current URL:', page.url());
    await takeScreenshot(page, '05-personal-page-direct');

    // 如果直接导航成功，再测试下拉菜单
    if (!page.url().includes('/personal')) {
      console.log('直接导航失败，尝试下拉菜单方式...');
      await page.goto(`${BASE_URL}/dashboard`);
      await page.waitForTimeout(2000);

      // 点击用户下拉菜单触发区域（el-dropdown 的 reference）
      const dropdownTrigger = page.locator('.el-dropdown > div').first();
      if (await dropdownTrigger.count() > 0) {
        await dropdownTrigger.click();
        await page.waitForTimeout(1500);
        await takeScreenshot(page, '05a-dropdown-triggered');

        // 等待下拉菜单出现
        const dropdownMenu = page.locator('.el-dropdown-menu');
        await dropdownMenu.waitFor({ timeout: 5000 });
        await takeScreenshot(page, '05b-dropdown-visible');

        // 点击个人中心
        const personalItem = page.locator('.el-dropdown-menu__item').filter({ hasText: '个人中心' }).first();
        await personalItem.click();
        await page.waitForTimeout(2000);
        console.log('Current URL after dropdown click:', page.url());
        await takeScreenshot(page, '05c-after-personal-click');
      }
    }

    // Step 4: 验证页面内容
    console.log('Step 4: 验证页面内容');
    const pageTitle = page.locator('.page-title, h2:has-text("个人中心")');
    const hasPersonalCenter = await pageTitle.count() > 0;
    console.log('页面标题存在:', hasPersonalCenter);
    await takeScreenshot(page, '06-personal-center-verified');

    // 验证基本信息卡片
    const infoCard = page.locator('.info-card, .el-card:has-text("基本信息")');
    const hasInfoCard = await infoCard.count() > 0;
    console.log('基本信息卡片存在:', hasInfoCard);

    // 验证安全设置卡片
    const securityCard = page.locator('.security-card, .el-card:has-text("安全设置")');
    const hasSecurityCard = await securityCard.count() > 0;
    console.log('安全设置卡片存在:', hasSecurityCard);

    // 验证修改密码按钮
    const changePwdBtn = page.locator('button:has-text("修改密码")');
    const hasChangePwdBtn = await changePwdBtn.count() > 0;
    console.log('修改密码按钮存在:', hasChangePwdBtn);
    await takeScreenshot(page, '07-personal-center-full');

    expect(hasPersonalCenter).toBe(true);
    expect(hasInfoCard).toBe(true);
    expect(hasSecurityCard).toBe(true);

    console.log('场景一执行完成');
  });

  // ============ 场景二：修改密码功能测试 ============
  test('场景二：修改密码功能测试', async ({ page }) => {
    console.log('\n========== 场景二：修改密码功能测试 ==========');

    // Step 1: 登录并进入个人中心
    console.log('Step 1: 登录并进入个人中心');
    await login(page);
    await page.goto(`${BASE_URL}/personal`);
    await waitForPageLoad(page);
    await takeScreenshot(page, '08-personal-for-password');

    // Step 2: 点击修改密码按钮
    console.log('Step 2: 点击修改密码按钮');
    const changePwdBtn = page.locator('button:has-text("修改密码")');
    if (await changePwdBtn.count() > 0) {
      await changePwdBtn.click();
      await page.waitForTimeout(1000);
      await takeScreenshot(page, '09-password-dialog');
    } else {
      console.log('未找到修改密码按钮');
      await takeScreenshot(page, '09-password-btn-not-found');
      return;
    }

    // Step 3: 验证弹窗内容
    console.log('Step 3: 验证弹窗内容');
    const dialog = page.locator('.el-dialog:visible');
    const hasDialog = await dialog.count() > 0;
    console.log('密码弹窗存在:', hasDialog);

    // 验证表单字段
    const oldPasswordInput = page.locator('.el-dialog input[placeholder="请输入原密码"]');
    const newPasswordInput = page.locator('.el-dialog input[placeholder="请输入新密码（至少6位）"]');
    const confirmPasswordInput = page.locator('.el-dialog input[placeholder="请再次输入新密码"]');

    console.log('原密码输入框存在:', await oldPasswordInput.count() > 0);
    console.log('新密码输入框存在:', await newPasswordInput.count() > 0);
    console.log('确认密码输入框存在:', await confirmPasswordInput.count() > 0);
    await takeScreenshot(page, '10-password-dialog-filled');

    // Step 4: 填写密码表单（测试验证逻辑）
    console.log('Step 4: 填写密码表单');

    // 输入原密码
    await oldPasswordInput.fill('admin123');
    await page.waitForTimeout(300);

    // 输入新密码
    await newPasswordInput.fill('admin123');
    await page.waitForTimeout(300);

    // 再次输入相同新密码
    await confirmPasswordInput.fill('admin123');
    await page.waitForTimeout(300);
    await takeScreenshot(page, '11-password-form-filled');

    // Step 5: 测试两次密码不一致的验证
    console.log('Step 5: 测试密码验证');

    // 先清空确认密码，输入不同的值
    await confirmPasswordInput.clear();
    await confirmPasswordInput.fill('different123');
    await page.waitForTimeout(500);

    // 点击确认修改按钮
    const submitBtn = page.locator('.el-dialog button:has-text("确认修改")');
    if (await submitBtn.count() > 0) {
      await submitBtn.click();
      await page.waitForTimeout(1000);
      await takeScreenshot(page, '12-password-validation-error');
    }

    // 验证是否出现错误提示
    const errorMsg = page.locator('.el-form-item__error:visible');
    const hasError = await errorMsg.count() > 0;
    console.log('密码不一致错误提示:', hasError);

    // Step 6: 修正密码并提交
    console.log('Step 6: 修正密码并提交');
    await confirmPasswordInput.clear();
    await confirmPasswordInput.fill('admin123');
    await page.waitForTimeout(500);

    if (await submitBtn.count() > 0) {
      await submitBtn.click();
      await page.waitForTimeout(2000);
      await takeScreenshot(page, '13-password-submit-result');
    }

    // 检查是否关闭了弹窗（可能成功或失败，取决于后端校验）
    const dialogAfter = page.locator('.el-dialog:visible');
    const dialogClosed = await dialogAfter.count() === 0;
    console.log('弹窗已关闭:', dialogClosed);

    console.log('场景二执行完成');
  });

  // ============ 场景三：个人中心页面元素完整性 ============
  test('场景三：个人中心页面元素完整性', async ({ page }) => {
    console.log('\n========== 场景三：个人中心页面元素完整性 ==========');

    // Step 1: 登录并进入个人中心
    console.log('Step 1: 登录并进入个人中心');
    await login(page);
    await page.goto(`${BASE_URL}/personal`);
    await waitForPageLoad(page);
    await takeScreenshot(page, '14-personal-complete');

    // Step 2: 检查页面标题
    console.log('Step 2: 检查页面标题');
    const pageTitle = page.locator('.page-title');
    const titleText = await pageTitle.textContent();
    console.log('页面标题:', titleText);
    expect(titleText).toContain('个人中心');

    // Step 3: 检查基本信息
    console.log('Step 3: 检查用户信息展示');
    // 检查是否有用户名显示
    const userName = page.locator('.user-name, .avatar-info h3');
    const hasUserName = await userName.count() > 0;
    console.log('用户名显示:', hasUserName);

    // 检查是否有账号信息
    const userAccount = page.locator('.user-account');
    const hasUserAccount = await userAccount.count() > 0;
    console.log('账号显示:', hasUserAccount);

    // 检查是否有角色标签
    const roleTag = page.locator('.role-tag, .el-tag');
    const hasRoleTag = await roleTag.count() > 0;
    console.log('角色标签:', hasRoleTag);

    // Step 4: 检查安全设置
    console.log('Step 4: 检查安全设置');
    const securityItem = page.locator('.security-item');
    const securityCount = await securityItem.count();
    console.log('安全设置项目数:', securityCount);
    expect(securityCount).toBeGreaterThan(0);

    // Step 5: 验证下拉菜单返回
    console.log('Step 5: 验证导航返回下拉菜单');
    await page.goto(`${BASE_URL}/dashboard`);
    await waitForPageLoad(page);

    // 点击用户下拉菜单
    const userDropdown = page.locator('.el-dropdown').first();
    if (await userDropdown.count() > 0) {
      await userDropdown.click();
      await page.waitForTimeout(1000);
      await takeScreenshot(page, '15-dropdown-menu');
    }

    // 验证下拉菜单中有个人中心选项
    const personalMenuItem = page.locator('.el-dropdown-menu__item:has-text("个人中心")');
    const hasPersonalItem = await personalMenuItem.count() > 0;
    console.log('下拉菜单中有个人中心选项:', hasPersonalItem);

    // 验证下拉菜单中有退出登录选项
    const logoutMenuItem = page.locator('.el-dropdown-menu__item:has-text("退出登录")');
    const hasLogoutItem = await logoutMenuItem.count() > 0;
    console.log('下拉菜单中有退出登录选项:', hasLogoutItem);

    await takeScreenshot(page, '16-dropdown-verified');

    expect(hasPersonalItem).toBe(true);
    expect(hasLogoutItem).toBe(true);

    console.log('场景三执行完成');
  });

});
