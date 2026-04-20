import { test, expect, Page } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';

// Test configuration
const BASE_URL = 'http://localhost:5777';
const CURRENT_FILE = fileURLToPath(import.meta.url);
const CURRENT_DIR = path.dirname(CURRENT_FILE);
const SCREENSHOT_DIR = path.join(CURRENT_DIR, 'src/views/orders/test-screenshots');

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

  await takeScreenshot(page, 'login-form-filled');

  // Click login button
  await page.click('button[type="submit"]');

  // Wait for navigation
  await page.waitForTimeout(3000);
  console.log('Current URL after login:', page.url());

  // Check if login was successful by looking at the URL
  const currentUrl = page.url();
  if (currentUrl.includes('/login')) {
    console.log('Login might have failed, taking screenshot...');
    await takeScreenshot(page, 'login-result');
  }
}

// Helper function to navigate to a menu item
async function navigateToMenu(page: Page, menuText: string) {
  console.log(`Navigating to menu: ${menuText}`);

  // Wait for the sidebar to be visible
  await page.waitForSelector('aside', { timeout: 10000 });

  // Try different selectors for menu items
  const menuSelectors = [
    `aside >> text=${menuText}`,
    `aside a:has-text("${menuText}")`,
    `nav >> text=${menuText}`,
    `a[href*="${menuText}"]`
  ];

  for (const selector of menuSelectors) {
    const element = page.locator(selector).first();
    if (await element.count() > 0) {
      try {
        await element.click({ timeout: 3000 });
        await page.waitForTimeout(1500);
        console.log(`Clicked menu: ${menuText} using selector: ${selector}`);
        return true;
      } catch (e) {
        console.log(`Selector ${selector} found but click failed, trying next...`);
      }
    }
  }

  // If menu item has a parent with children, it might need to be expanded
  const allLinks = page.locator('aside a, nav a');
  const count = await allLinks.count();
  console.log(`Found ${count} links in sidebar`);

  // Try to click by href
  const hrefMap: Record<string, string> = {
    '仪表盘': '/dashboard',
    '订单管理': '/orders',
    '库存': '/inventory',
    '商品': '/products',
    '客户管理': '/clients'
  };

  if (hrefMap[menuText]) {
    const hrefSelector = `a[href="${hrefMap[menuText]}"]`;
    const element = page.locator(hrefSelector).first();
    if (await element.count() > 0) {
      await element.click();
      await page.waitForTimeout(1500);
      console.log(`Clicked menu: ${menuText} using href selector`);
      return true;
    }
  }

  console.log(`Could not find or click menu: ${menuText}`);
  await takeScreenshot(page, `nav-fail-${menuText}`);
  return false;
}

test.describe('库存订单联动 E2E 测试', () => {

  test.beforeEach(async ({ page }) => {
    // Set viewport
    await page.setViewportSize({ width: 1440, height: 900 });
  });

  // ============ 场景一：完整订单流程库存测试 ============
  test('场景一：完整订单流程库存测试', async ({ page }) => {
    console.log('\n========== 场景一：完整订单流程库存测试 ==========');

    // Step 1: 登录系统
    console.log('Step 1: 登录系统');
    await login(page);
    await takeScreenshot(page, 'scenario1-01-logged-in');

    // Step 2: 进入库存管理查看初始库存
    console.log('Step 2: 进入库存管理');
    await navigateToMenu(page, '库存');
    await waitForPageLoad(page);
    await takeScreenshot(page, 'scenario1-02-inventory');

    // Step 3: 记录初始库存
    console.log('Step 3: 记录初始库存');
    const firstRow = page.locator('.el-table__body tr').first();
    if (await firstRow.count() > 0) {
      const cells = await firstRow.locator('td').allTextContents();
      console.log('First inventory row:', cells.slice(0, 6));
    }
    await takeScreenshot(page, 'scenario1-03-inventory-initial');

    // Step 4: 进入订单管理
    console.log('Step 4: 进入订单管理');
    await navigateToMenu(page, '订单管理');
    await waitForPageLoad(page);
    await takeScreenshot(page, 'scenario1-04-orders');

    // Step 5: 创建新订单
    console.log('Step 5: 创建新订单');
    await page.click('button:has-text("新建订单")');
    await waitForPageLoad(page);
    await takeScreenshot(page, 'scenario1-05-new-order');

    // Step 6: 填写客户信息
    console.log('Step 6: 填写客户信息');
    await page.fill('input[placeholder*="电话"]', '13800138000');
    await page.waitForTimeout(500);

    // 检查是否有已匹配客户，如果有则直接使用
    const matchedCustomer = page.locator('text=已匹配客户');
    if (await matchedCustomer.count() === 0) {
      await page.fill('input[placeholder*="名称"]', '测试客户');
    }
    await takeScreenshot(page, 'scenario1-06-customer');

    // Step 7: 先选择档口（这会加载库存）
    console.log('Step 7: 选择档口以加载库存');
    const warehouseSelect = page.locator('.el-select').first();
    await warehouseSelect.click();
    await page.waitForTimeout(500);

    const warehouseOptions = page.locator('.el-select-dropdown__item');
    if (await warehouseOptions.count() > 0) {
      await warehouseOptions.first().click();
    }
    await page.waitForTimeout(2000); // 等待库存加载
    await takeScreenshot(page, 'scenario1-07-warehouse-selected');

    // Step 8: 添加商品
    console.log('Step 8: 添加商品');
    await page.click('button:has-text("添加商品")');
    await waitForPageLoad(page);
    await takeScreenshot(page, 'scenario1-08-product-dialog');

    // 等待商品列表加载
    await page.waitForTimeout(2000);

    // 查找有库存的商品行（绿色显示"可用: X"）
    const dialogContent = page.locator('.product-dialog .max-h-\\[400px\\]');
    const productRows = dialogContent.locator('.border.border-gray-200');

    let foundProductWithStock = false;
    const rowCount = await productRows.count();
    console.log(`Found ${rowCount} products in dialog`);

    // 先尝试找一个有可用库存的行
    for (let i = 0; i < rowCount; i++) {
      const row = productRows.nth(i);
      const rowText = await row.textContent();

      // 检查这行是否有可用库存（文字中包含"可用:"而不是"无货"）
      if (rowText && rowText.includes('可用:') && !rowText.includes('无货')) {
        console.log(`Found product row ${i} with stock`);
        await row.click();
        await page.waitForTimeout(1500);

        // 在展开的行内找启用的输入框
        const enabledInputs = row.locator('input[type="number"]:not([disabled])');
        const inputCount = await enabledInputs.count();

        if (inputCount > 0) {
          await enabledInputs.first().fill('2');
          foundProductWithStock = true;
          await takeScreenshot(page, 'scenario1-09-product-selected');
        }
        break;
      }
    }

    // 如果没找到有库存的，展开第一行看看详情
    if (!foundProductWithStock && rowCount > 0) {
      console.log('没有找到有库存的商品行，展开第一行查看');
      await productRows.first().click();
      await page.waitForTimeout(1500);
      await takeScreenshot(page, 'scenario1-09-first-product-expanded');
    }

    // 点击批量添加（如果找到了有库存的商品）
    if (foundProductWithStock) {
      const batchAddBtn = page.locator('.product-dialog button:has-text("批量添加")');
      if (await batchAddBtn.count() > 0) {
        await batchAddBtn.click();
        await page.waitForTimeout(1500);
        await takeScreenshot(page, 'scenario1-10-product-added');
      }
    } else {
      // 没有找到有库存的商品，关闭对话框继续测试
      console.log('没有找到有库存的商品，关闭对话框');
      const closeBtn = page.locator('.product-dialog .el-dialog__headerbutton, .product-dialog button:has-text("取消"), .el-overlay-dialog button[aria-label="Close"]');
      if (await closeBtn.count() > 0) {
        await closeBtn.first().click();
      } else {
        // 按ESC关闭
        await page.keyboard.press('Escape');
      }
      await page.waitForTimeout(500);
    }

    // Step 9: 选择已付全款并填写金额
    console.log('Step 9: 填写支付信息');
    await page.click('label:has-text("已付全款")');
    await page.waitForTimeout(500);

    const paidInput = page.locator('input[type="number"]').first();
    await paidInput.fill('1000');
    await takeScreenshot(page, 'scenario1-11-payment');

    // Step 10: 提交订单
    console.log('Step 10: 提交订单');
    const submitBtn = page.locator('button:has-text("确认订单并收款")');
    if (await submitBtn.count() > 0) {
      await submitBtn.click();
    } else {
      await page.click('button:has-text("保存订单")');
    }
    await page.waitForTimeout(3000);

    const currentUrl = page.url();
    console.log('当前URL:', currentUrl);
    await takeScreenshot(page, 'scenario1-11-order-created');

    if (currentUrl.includes('/orders/') && !currentUrl.includes('/new')) {
      console.log('订单创建成功！');

      // Step 11: 验证库存被预留 - 返回库存页面查看
      console.log('Step 11: 验证库存预留');
      await navigateToMenu(page, '库存');
      await waitForPageLoad(page);
      await takeScreenshot(page, 'scenario1-12-inventory-reserved');

      // Step 12: 返回订单创建配货计划
      console.log('Step 12: 创建配货计划');
      await navigateToMenu(page, '订单管理');
      await waitForPageLoad(page);

      // 点击订单进入详情
      const orderRow = page.locator('.el-table__body tr').first();
      if (await orderRow.count() > 0) {
        await orderRow.click();
        await page.waitForTimeout(2000);
      }
      await takeScreenshot(page, 'scenario1-13-order-detail');

      // 创建配货计划
      const createPlanBtn = page.locator('button:has-text("创建配货计划")');
      if (await createPlanBtn.count() > 0) {
        await createPlanBtn.click();
        await page.waitForTimeout(1000);

        // 选择仓库
        const planWarehouseSelect = page.locator('.el-dialog .el-select').first();
        if (await planWarehouseSelect.count() > 0) {
          await planWarehouseSelect.click();
          await page.waitForTimeout(500);
          await page.locator('.el-select-dropdown__item').first().click();
        }

        await takeScreenshot(page, 'scenario1-14-delivery-plan');
        await page.waitForTimeout(500);

        // 确认配货计划
        await page.click('button:has-text("保存")');
        await page.waitForTimeout(2000);
      }
      await takeScreenshot(page, 'scenario1-15-plan-created');

      // Step 13: 确认出库
      console.log('Step 13: 确认出库');
      const deliverBtn = page.locator('button:has-text("发货")');
      if (await deliverBtn.count() > 0) {
        await deliverBtn.click();
        await page.waitForTimeout(1000);

        // 确认对话框
        const confirmBtn = page.locator('.el-message-box__wrapper button:has-text("确认")');
        if (await confirmBtn.count() > 0) {
          await confirmBtn.click();
        }
        await page.waitForTimeout(2000);
      }
      await takeScreenshot(page, 'scenario1-16-delivered');

      // Step 14: 验证库存扣减
      console.log('Step 14: 验证库存扣减');
      await navigateToMenu(page, '库存');
      await waitForPageLoad(page);
      await takeScreenshot(page, 'scenario1-17-inventory-final');

    } else {
      console.log('订单创建可能失败，请检查截图');
      await takeScreenshot(page, 'scenario1-error');
    }

    console.log('场景一执行完成');
  });

  // ============ 场景二：库存不足场景测试 ============
  test('场景二：库存不足场景测试', async ({ page }) => {
    console.log('\n========== 场景二：库存不足场景测试 ==========');

    // Step 1: 登录
    console.log('Step 1: 登录');
    await login(page);

    // Step 2: 进入库存管理
    console.log('Step 2: 进入库存管理');
    await navigateToMenu(page, '库存');
    await waitForPageLoad(page);
    await takeScreenshot(page, 'scenario2-01-inventory');

    // Step 3: 筛选预警状态
    console.log('Step 3: 筛选预警商品');
    const statusSelect = page.locator('.el-select').nth(1);
    if (await statusSelect.count() > 0) {
      await statusSelect.click();
      await page.waitForTimeout(500);

      const warningOption = page.locator('.el-select-dropdown__item').filter({ hasText: '预警' });
      if (await warningOption.count() > 0) {
        await warningOption.first().click();
        await page.waitForTimeout(1000);
      }
    }
    await takeScreenshot(page, 'scenario2-02-warning-filter');

    // Step 4: 进入新建订单
    console.log('Step 4: 进入新建订单');
    await navigateToMenu(page, '订单管理');
    await waitForPageLoad(page);

    await page.click('button:has-text("新建订单")');
    await waitForPageLoad(page);
    await takeScreenshot(page, 'scenario2-03-new-order');

    // Step 5: 先选择档口
    console.log('Step 5: 选择档口');
    const warehouseSelect = page.locator('.el-select').first();
    await warehouseSelect.click();
    await page.waitForTimeout(500);
    await page.locator('.el-select-dropdown__item').first().click();
    await page.waitForTimeout(2000);

    // Step 6: 添加商品
    console.log('Step 6: 添加商品尝试');
    await page.click('button:has-text("添加商品")');
    await waitForPageLoad(page);

    // 等待商品列表加载
    await page.waitForTimeout(2000);
    await takeScreenshot(page, 'scenario2-04-product-dialog');

    // 展开商品
    const dialogContent = page.locator('.product-dialog .max-h-\[400px\]');
    const firstProductInDialog = dialogContent.locator('.border.border-gray-200').first();

    if (await firstProductInDialog.count() > 0) {
      await firstProductInDialog.click();
      await page.waitForTimeout(1500);
      await takeScreenshot(page, 'scenario2-05-product-expanded');

      // 尝试输入超大量
      const enabledInputs = firstProductInDialog.locator('input[type="number"]:not([disabled])');
      if (await enabledInputs.count() > 0) {
        await enabledInputs.first().fill('9999');
        await takeScreenshot(page, 'scenario2-06-over-quantity');

        // 批量添加
        const batchAddBtn = page.locator('.product-dialog button:has-text("批量添加")');
        if (await batchAddBtn.count() > 0) {
          await batchAddBtn.click();
          await page.waitForTimeout(2000);
        }
        await takeScreenshot(page, 'scenario2-07-add-result');
      }
    }

    console.log('场景二执行完成');
  });

  // ============ 场景三：取消订单库存释放测试 ============
  test('场景三：取消订单库存释放测试', async ({ page }) => {
    console.log('\n========== 场景三：取消订单库存释放测试 ==========');

    // Step 1: 登录
    console.log('Step 1: 登录');
    await login(page);

    // Step 2: 查看库存
    console.log('Step 2: 查看库存');
    await navigateToMenu(page, '库存');
    await waitForPageLoad(page);
    await takeScreenshot(page, 'scenario3-01-inventory-before');

    // Step 3: 创建订单
    console.log('Step 3: 创建订单');
    await navigateToMenu(page, '订单管理');
    await waitForPageLoad(page);

    await page.click('button:has-text("新建订单")');
    await waitForPageLoad(page);

    // 填写客户信息
    await page.fill('input[placeholder*="电话"]', '13900139000');
    await page.waitForTimeout(500);
    const matchedCustomer = page.locator('text=已匹配客户');
    if (await matchedCustomer.count() === 0) {
      await page.fill('input[placeholder*="名称"]', '取消测试客户');
    }

    // 选择档口
    await page.locator('.el-select').first().click();
    await page.waitForTimeout(500);
    await page.locator('.el-select-dropdown__item').first().click();
    await page.waitForTimeout(2000);

    // 添加商品
    await page.click('button:has-text("添加商品")');
    await waitForPageLoad(page);
    await page.waitForTimeout(2000);

    const dialogContent = page.locator('.product-dialog .max-h-\[400px\]');
    const firstProductInDialog = dialogContent.locator('.border.border-gray-200').first();
    if (await firstProductInDialog.count() > 0) {
      await firstProductInDialog.click();
      await page.waitForTimeout(1500);

      const enabledInputs = firstProductInDialog.locator('input[type="number"]:not([disabled])');
      if (await enabledInputs.count() > 0) {
        await enabledInputs.first().fill('1');
        await page.click('.product-dialog button:has-text("批量添加")');
        await page.waitForTimeout(1500);
      }
    }

    // 选择已付全款
    await page.click('label:has-text("已付全款")');
    await page.waitForTimeout(500);
    const paidInput = page.locator('input[type="number"]').first();
    await paidInput.fill('500');
    await takeScreenshot(page, 'scenario3-02-order-ready');

    // 提交订单
    const submitBtn = page.locator('button:has-text("确认订单并收款")');
    if (await submitBtn.count() > 0) {
      await submitBtn.click();
    }
    await page.waitForTimeout(3000);

    const currentUrl = page.url();
    console.log('当前URL:', currentUrl);
    await takeScreenshot(page, 'scenario3-03-order-created');

    if (currentUrl.includes('/orders/') && !currentUrl.includes('/new')) {
      console.log('订单创建成功');

      // Step 4: 验证库存预留
      console.log('Step 4: 验证库存预留');
      await navigateToMenu(page, '库存');
      await waitForPageLoad(page);
      await takeScreenshot(page, 'scenario3-04-inventory-reserved');

      // Step 5: 取消订单
      console.log('Step 5: 取消订单');
      await navigateToMenu(page, '订单管理');
      await waitForPageLoad(page);

      const orderRow = page.locator('.el-table__body tr').first();
      if (await orderRow.count() > 0) {
        await orderRow.click();
        await page.waitForTimeout(2000);
      }
      await takeScreenshot(page, 'scenario3-05-order-detail');

      const cancelBtn = page.locator('button:has-text("取消订单")');
      if (await cancelBtn.count() > 0) {
        await cancelBtn.click();
        await page.waitForTimeout(1000);

        // 填写取消原因
        const textarea = page.locator('.el-dialog textarea, textarea[placeholder*="取消原因"]');
        if (await textarea.count() > 0) {
          await textarea.fill('E2E测试取消订单');
        }
        await takeScreenshot(page, 'scenario3-06-cancel-dialog');

        // 确认取消
        await page.click('button:has-text("确认取消")');
        await page.waitForTimeout(2000);
        await takeScreenshot(page, 'scenario3-07-after-cancel');
      }

      // Step 6: 验证库存释放
      console.log('Step 6: 验证库存释放');
      await navigateToMenu(page, '库存');
      await waitForPageLoad(page);
      await takeScreenshot(page, 'scenario3-08-inventory-released');
    }

    console.log('场景三执行完成');
  });

  // ============ 场景四：库存预警测试 ============
  test('场景四：库存预警测试', async ({ page }) => {
    console.log('\n========== 场景四：库存预警测试 ==========');

    // Step 1: 登录
    console.log('Step 1: 登录');
    await login(page);

    // Step 2: 进入库存管理
    console.log('Step 2: 进入库存管理');
    await navigateToMenu(page, '库存');
    await waitForPageLoad(page);
    await takeScreenshot(page, 'scenario4-01-inventory');

    // Step 3: 查看预警商品
    console.log('Step 3: 筛选预警状态');
    const statusSelect = page.locator('.el-select').nth(1);
    if (await statusSelect.count() > 0) {
      await statusSelect.click();
      await page.waitForTimeout(500);

      const warningOption = page.locator('.el-select-dropdown__item').filter({ hasText: '预警' });
      if (await warningOption.count() > 0) {
        await warningOption.first().click();
        await page.waitForTimeout(1000);
        await takeScreenshot(page, 'scenario4-02-warning-items');
      }
    }

    // 查看是否有预警商品
    const warningRows = page.locator('.el-table__body tr');
    const warningCount = await warningRows.count();
    console.log('预警商品数量:', warningCount);

    if (warningCount > 0) {
      // 点击查看明细
      const viewBtn = page.locator('button:has-text("明细")').first();
      if (await viewBtn.count() > 0) {
        await viewBtn.click();
        await page.waitForTimeout(1000);
        await takeScreenshot(page, 'scenario4-03-inventory-log');
      }
    }

    // Step 4: 入库少量商品
    console.log('Step 4: 入库操作');
    await page.click('button:has-text("入库")');
    await page.waitForTimeout(1500);
    await takeScreenshot(page, 'scenario4-04-stock-in-dialog');

    // 选择仓库 - 点击select打开下拉
    const warehouseSelect = page.locator('.el-dialog .el-select').first();
    if (await warehouseSelect.count() > 0) {
      await warehouseSelect.click();
      await page.waitForTimeout(1000);

      // 等待下拉框出现，选择第一个可见且未选中的项
      const warehouseDropdown = page.locator('.el-select-dropdown:visible');
      if (await warehouseDropdown.count() > 0) {
        const item = page.locator('.el-select-dropdown:visible .el-select-dropdown__item:not(.is-selected)').first();
        if (await item.count() > 0) {
          await item.click();
        } else {
          // 如果没有未选中的，直接选第一个
          await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click();
        }
      }
      await page.waitForTimeout(500);
    }
    await takeScreenshot(page, 'scenario4-05-warehouse-selected');

    // 选择商品 - 需要重新点击select触发下拉
    const skuSelect = page.locator('.el-dialog .el-select').nth(1);
    if (await skuSelect.count() > 0) {
      await skuSelect.click();
      await page.waitForTimeout(1000);

      const skuDropdown = page.locator('.el-select-dropdown:visible');
      if (await skuDropdown.count() > 0) {
        const skuItem = page.locator('.el-select-dropdown:visible .el-select-dropdown__item:not(.is-selected)').first();
        if (await skuItem.count() > 0) {
          await skuItem.click();
        } else {
          await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click();
        }
      }
      await page.waitForTimeout(500);
    }
    await takeScreenshot(page, 'scenario4-06-sku-selected');

    // 输入少量数量
    const qtyInput = page.locator('.el-dialog input[type="number"], .el-dialog .el-input-number input');
    if (await qtyInput.count() > 0) {
      await qtyInput.first().fill('1');
    }
    await takeScreenshot(page, 'scenario4-07-low-stock-in');

    // 确认入库
    await page.click('button:has-text("确认入库")');
    await page.waitForTimeout(2000);
    await takeScreenshot(page, 'scenario4-08-after-stock-in');

    console.log('场景四执行完成');
  });

});
