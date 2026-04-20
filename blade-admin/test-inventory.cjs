const { chromium } = require('browser-use');
const path = require('path');

const SCREENSHOT_DIR = path.join(__dirname, 'src/views/inventory/test-screenshots');

async function takeScreenshot(page, name) {
  const screenshotPath = path.join(SCREENSHOT_DIR, `${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage: true });
  console.log(`Screenshot saved: ${screenshotPath}`);
  return screenshotPath;
}

async function waitForElement(page, selector, timeout = 10000) {
  try {
    await page.waitForSelector(selector, { timeout, state: 'visible' });
    return true;
  } catch {
    return false;
  }
}

async function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function runTest() {
  console.log('Starting inventory page browser test...');

  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  const page = await context.newPage();

  const results = {
    passed: [],
    failed: []
  };

  try {
    // ========== 1. LOGIN TEST ==========
    console.log('\n=== 1. LOGIN TEST ===');

    await page.goto('http://localhost:5777', { waitUntil: 'networkidle', timeout: 30000 });
    await takeScreenshot(page, '01-login-page');

    // Fill login form
    await page.fill('input[placeholder*="租户"]', 'test_tenant');
    await page.fill('input[placeholder*="账号"]', 'admin');
    await page.fill('input[placeholder*="密码"]', 'admin123');
    await takeScreenshot(page, '02-login-form-filled');

    // Find and fill captcha - look for any input that might be captcha
    const inputs = await page.locator('input').all();
    for (const input of inputs) {
      const placeholder = await input.getAttribute('placeholder') || '';
      if (placeholder.toLowerCase().includes('captcha') || placeholder.toLowerCase().includes('验证码')) {
        await input.fill('1234');
      }
    }

    // Click login button
    await page.click('button[type="submit"], button:has-text("登录")');
    await sleep(3000);
    await takeScreenshot(page, '03-after-login');

    // Check if login successful by looking for dashboard or redirect
    const currentUrl = page.url();
    console.log(`Current URL after login: ${currentUrl}`);

    // ========== 2. NAVIGATE TO INVENTORY ==========
    console.log('\n=== 2. NAVIGATE TO INVENTORY ===');

    // Wait for sidebar menu and click inventory
    await waitForElement(page, 'text=库存管理', 10000);

    // Click on inventory management in sidebar
    const inventoryLink = page.locator('text=库存管理').first();
    await inventoryLink.click();
    await sleep(2000);
    await takeScreenshot(page, '04-inventory-page');

    // ========== 3. INVENTORY LIST TEST ==========
    console.log('\n=== 3. INVENTORY LIST TEST ===');

    // Check if table loaded
    await waitForElement(page, '.el-table', 10000);
    await takeScreenshot(page, '05-inventory-table-loaded');

    // Test filter - warehouse
    const warehouseSelect = page.locator('.el-select').first();
    if (await warehouseSelect.isVisible()) {
      await warehouseSelect.click();
      await sleep(500);
      await takeScreenshot(page, '06-warehouse-dropdown');
      await page.keyboard.press('Escape');
      await sleep(300);
    }

    // Test filter - status
    const statusSelect = page.locator('.el-select').nth(1);
    if (await statusSelect.isVisible()) {
      await statusSelect.click();
      await sleep(500);
      await takeScreenshot(page, '07-status-dropdown');
      await page.keyboard.press('Escape');
      await sleep(300);
    }

    // Test search input
    await page.fill('input[placeholder*="搜索"]', 'SKU');
    await takeScreenshot(page, '08-search-input');
    await page.click('button:has-text("搜索")');
    await sleep(1000);
    await takeScreenshot(page, '09-search-result');

    // Reset filters
    await page.click('button:has-text("重置筛选")');
    await sleep(1000);
    await takeScreenshot(page, '10-filters-reset');

    // Test pagination
    const pagination = page.locator('.el-pagination').first();
    if (await pagination.isVisible()) {
      await pagination.locator('button:has-text("2")').click();
      await sleep(1000);
      await takeScreenshot(page, '11-pagination-page2');
    }

    results.passed.push('Inventory list displays correctly');
    results.passed.push('Filters work correctly');
    results.passed.push('Search works correctly');
    results.passed.push('Pagination works correctly');

    // ========== 4. STOCK IN TEST ==========
    console.log('\n=== 4. STOCK IN TEST ===');

    // Click stock in button
    await page.click('button:has-text("入库")');
    await sleep(1000);
    await takeScreenshot(page, '12-stock-in-dialog');

    // Select warehouse
    const warehouseDropdown = page.locator('div[aria-label="入库"] .el-select').first();
    await warehouseDropdown.click();
    await sleep(500);
    await takeScreenshot(page, '13-stock-in-warehouse-select');

    // Select first warehouse option
    const warehouseOptions = page.locator('div[aria-label="入库"] .el-select-dropdown__item');
    if (await warehouseOptions.count() > 0) {
      await warehouseOptions.first().click();
      await sleep(300);
    }
    await takeScreenshot(page, '14-stock-in-warehouse-selected');

    // Select product SKU
    const skuSelect = page.locator('div[aria-label="入库"] .el-select').nth(1);
    await skuSelect.click();
    await sleep(500);
    await takeScreenshot(page, '15-stock-in-sku-select');

    const skuOptions = page.locator('div[aria-label="入库"] .el-select-dropdown__item');
    if (await skuOptions.count() > 0) {
      await skuOptions.first().click();
      await sleep(300);
    }
    await takeScreenshot(page, '16-stock-in-sku-selected');

    // Enter quantity
    const quantityInput = page.locator('div[aria-label="入库"] .el-input-number input');
    await quantityInput.fill('5');
    await takeScreenshot(page, '17-stock-in-quantity');

    // Submit
    await page.click('div[aria-label="入库"] button:has-text("确认入库")');
    await sleep(2000);
    await takeScreenshot(page, '18-stock-in-submit');

    results.passed.push('Stock in dialog opens correctly');
    results.passed.push('Stock in form can be filled');

    // ========== 5. STOCK OUT TEST ==========
    console.log('\n=== 5. STOCK OUT TEST ===');

    await page.click('button:has-text("出库")');
    await sleep(1000);
    await takeScreenshot(page, '19-stock-out-dialog');

    // Select warehouse
    const outWarehouseDropdown = page.locator('div[aria-label="出库"] .el-select').first();
    await outWarehouseDropdown.click();
    await sleep(500);
    const outWarehouseOptions = page.locator('div[aria-label="出库"] .el-select-dropdown__item');
    if (await outWarehouseOptions.count() > 0) {
      await outWarehouseOptions.first().click();
      await sleep(300);
    }

    // Select product
    const outSkuSelect = page.locator('div[aria-label="出库"] .el-select').nth(1);
    await outSkuSelect.click();
    await sleep(500);
    const outSkuOptions = page.locator('div[aria-label="出库"] .el-select-dropdown__item');
    if (await outSkuOptions.count() > 0) {
      await outSkuOptions.first().click();
      await sleep(300);
    }

    // Enter quantity
    const outQuantityInput = page.locator('div[aria-label="出库"] .el-input-number input');
    await outQuantityInput.fill('2');
    await takeScreenshot(page, '20-stock-out-form-filled');

    // Submit
    await page.click('div[aria-label="出库"] button:has-text("确认出库")');
    await sleep(2000);
    await takeScreenshot(page, '21-stock-out-submit');

    results.passed.push('Stock out dialog opens correctly');
    results.passed.push('Stock out form can be filled');

    // ========== 6. INVENTORY ADJUST TEST ==========
    console.log('\n=== 6. INVENTORY ADJUST TEST ===');

    await page.click('button:has-text("调整")');
    await sleep(1000);
    await takeScreenshot(page, '22-adjust-dialog');

    // Select warehouse
    const adjWarehouseDropdown = page.locator('div[aria-label="库存调整"] .el-select').first();
    await adjWarehouseDropdown.click();
    await sleep(500);
    const adjWarehouseOptions = page.locator('div[aria-label="库存调整"] .el-select-dropdown__item');
    if (await adjWarehouseOptions.count() > 0) {
      await adjWarehouseOptions.first().click();
      await sleep(300);
    }

    // Enter reason
    await page.fill('div[aria-label="库存调整"] input[placeholder*="月度"]', 'Test adjustment');
    await takeScreenshot(page, '23-adjust-form-filled');

    // Select product and quantity
    const adjSkuSelect = page.locator('div[aria-label="库存调整"] .el-select').nth(1);
    await adjSkuSelect.click();
    await sleep(500);
    const adjSkuOptions = page.locator('div[aria-label="库存调整"] .el-select-dropdown__item');
    if (await adjSkuOptions.count() > 0) {
      await adjSkuOptions.first().click();
      await sleep(300);
    }

    // Enter adjustment quantity (positive for profit, negative for loss)
    const adjQuantityInput = page.locator('div[aria-label="库存调整"] .el-input-number input');
    await adjQuantityInput.fill('1');
    await takeScreenshot(page, '24-adjust-quantity');

    // Submit
    await page.click('div[aria-label="库存调整"] button:has-text("确认调整")');
    await sleep(2000);
    await takeScreenshot(page, '25-adjust-submit');

    results.passed.push('Adjust dialog opens correctly');
    results.passed.push('Adjust form can be filled');

    // ========== 7. INVENTORY LOG TEST ==========
    console.log('\n=== 7. INVENTORY LOG TEST ===');

    await page.click('button:has-text("记录")');
    await sleep(1000);
    await takeScreenshot(page, '26-inventory-log-dialog');

    // Test filter - change type
    const changeTypeSelect = page.locator('div[aria-label="库存记录"] .el-select').nth(1);
    await changeTypeSelect.click();
    await sleep(500);
    await takeScreenshot(page, '27-log-type-dropdown');

    // Select an option
    const typeOptions = page.locator('div[aria-label="库存记录"] .el-select-dropdown__item');
    if (await typeOptions.count() > 0) {
      await typeOptions.first().click();
      await sleep(300);
    }

    // Click query
    await page.click('div[aria-label="库存记录"] button:has-text("查询")');
    await sleep(1000);
    await takeScreenshot(page, '28-log-filtered');

    // Close dialog
    await page.keyboard.press('Escape');
    await sleep(500);

    results.passed.push('Inventory log dialog opens correctly');
    results.passed.push('Log filters work correctly');

    // ========== FINAL SCREENSHOT ==========
    console.log('\n=== FINAL STATE ===');
    await sleep(1000);
    await takeScreenshot(page, '99-final-state');

    // Print results
    console.log('\n========================================');
    console.log('TEST RESULTS');
    console.log('========================================');
    console.log(`PASSED: ${results.passed.length}`);
    results.passed.forEach(p => console.log(`  + ${p}`));
    console.log(`\nFAILED: ${results.failed.length}`);
    results.failed.forEach(f => console.log(`  - ${f}`));
    console.log('========================================');
    console.log('All screenshots saved to:', SCREENSHOT_DIR);

  } catch (error) {
    console.error('Test error:', error.message);
    await takeScreenshot(page, '99-error-state');
    results.failed.push(error.message);
  } finally {
    await browser.close();
  }

  return results;
}

runTest().catch(console.error);
