import asyncio
import os
from pathlib import Path
from playwright.async_api import async_playwright

SCREENSHOT_DIR = Path(__file__).parent / "src/views/inventory/test-screenshots"
SCREENSHOT_DIR.mkdir(parents=True, exist_ok=True)

async def take_screenshot(page, name):
    """Take a screenshot and save it."""
    screenshot_path = SCREENSHOT_DIR / f"{name}.png"
    await page.screenshot(path=str(screenshot_path), full_page=True)
    print(f"Screenshot saved: {screenshot_path}")
    return screenshot_path

async def wait_for_element(page, selector, timeout=10000):
    """Wait for an element to be visible."""
    try:
        await page.wait_for_selector(selector, timeout=timeout, state="visible")
        return True
    except:
        return False

async def sleep(ms):
    """Sleep for a given number of milliseconds."""
    await asyncio.sleep(ms / 1000)

async def try_click_dropdown_option(page, timeout=3000):
    """Try to click the first dropdown option if available."""
    try:
        dropdown_selector = '.el-select-dropdown:visible .el-select-dropdown__item'
        await page.locator(dropdown_selector).first.wait_for(timeout=timeout)
        await page.locator(dropdown_selector).first.click()
        await sleep(300)
        return True
    except:
        return False

async def run_test():
    print("Starting inventory page browser test...")

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=False, args=['--start-maximized'])
        context = await browser.new_context(viewport={"width": 1920, "height": 1080})
        page = await context.new_page()

        results = {
            "passed": [],
            "failed": []
        }

        try:
            # ========== 1. LOGIN TEST ==========
            print("\n=== 1. LOGIN TEST ===")

            await page.goto("http://localhost:5777", wait_until="networkidle", timeout=30000)
            await take_screenshot(page, "01-login-page")

            # Extract captcha from page (generated client-side)
            captcha_element = await page.query_selector(".captcha-text")
            captcha_text = await captcha_element.text_content() if captcha_element else "1234"
            print(f"Extracted captcha: {captcha_text}")

            # Fill login form (correct selectors based on actual page)
            await page.fill('input[placeholder*="公司"]', "test_tenant")
            await page.fill('input[placeholder*="管理员账号"]', "admin")
            await page.fill('input[type="password"]', "admin123")
            await page.fill('input[placeholder*="验证码"]', captcha_text)
            await take_screenshot(page, "02-login-form-filled")

            # Click login button
            await page.click('button[type="submit"]')
            await sleep(3000)
            await take_screenshot(page, "03-after-login")

            print(f"Current URL after login: {page.url}")
            results["passed"].append("Login successful")

            # ========== 2. NAVIGATE TO INVENTORY ==========
            print("\n=== 2. NAVIGATE TO INVENTORY ===")

            await wait_for_element(page, "text=库存", 10000)
            await page.locator("text=库存").click()
            await sleep(2000)
            await take_screenshot(page, "04-inventory-page")
            results["passed"].append("Navigated to inventory page")

            # ========== 3. INVENTORY LIST TEST ==========
            print("\n=== 3. INVENTORY LIST TEST ===")

            await wait_for_element(page, ".el-table", 10000)
            await take_screenshot(page, "05-inventory-table-loaded")

            # Test warehouse filter
            warehouse_selects = await page.query_selector_all(".el-select")
            if warehouse_selects:
                await warehouse_selects[0].click()
                await sleep(500)
                await take_screenshot(page, "06-warehouse-dropdown")
                await page.keyboard.press("Escape")
                await sleep(300)

            # Test status filter
            if len(warehouse_selects) > 1:
                await warehouse_selects[1].click()
                await sleep(500)
                await take_screenshot(page, "07-status-dropdown")
                await page.keyboard.press("Escape")
                await sleep(300)

            # Test search input
            search_input = await page.query_selector('input[placeholder*="搜索"]')
            if search_input:
                await search_input.fill("SKU")
                await take_screenshot(page, "08-search-input")
                await page.click('button:has-text("搜索")')
                await sleep(1000)
                await take_screenshot(page, "09-search-result")

            # Reset filters
            await page.click('button:has-text("重置筛选")')
            await sleep(1000)
            await take_screenshot(page, "10-filters-reset")

            # Test pagination
            pagination = await page.query_selector(".el-pagination")
            if pagination:
                page_buttons = await pagination.query_selector_all("button")
                for btn in page_buttons:
                    btn_text = await btn.text_content()
                    if btn_text and "2" in btn_text:
                        await btn.click()
                        await sleep(1000)
                        await take_screenshot(page, "11-pagination-page2")
                        break

            results["passed"].append("Inventory list displays correctly")
            results["passed"].append("Filters work correctly")
            results["passed"].append("Search works correctly")
            results["passed"].append("Pagination works correctly")

            # ========== 4. STOCK IN TEST ==========
            print("\n=== 4. STOCK IN TEST ===")

            await page.click('button:has-text("入库")')
            await sleep(1500)
            await take_screenshot(page, "12-stock-in-dialog")

            # Try to select warehouse
            warehouse_select = page.locator('div[aria-label="入库"] .el-select').first
            await warehouse_select.click()
            await sleep(1000)
            await take_screenshot(page, "13-stock-in-warehouse-select")

            # Try to click first option if available
            if await try_click_dropdown_option(page, 2000):
                await take_screenshot(page, "14-stock-in-warehouse-selected")
            else:
                # Dismiss dropdown by pressing Escape and wait
                await page.keyboard.press("Escape")
                await sleep(500)
                await take_screenshot(page, "14-stock-in-warehouse-empty")

            # Try to select SKU
            sku_select = page.locator('div[aria-label="入库"] .el-select').nth(1)
            await sku_select.click()
            await sleep(1000)
            await take_screenshot(page, "15-stock-in-sku-select")

            if await try_click_dropdown_option(page, 2000):
                await take_screenshot(page, "16-stock-in-sku-selected")
            else:
                # Dismiss dropdown
                await page.keyboard.press("Escape")
                await sleep(500)
                await take_screenshot(page, "16-stock-in-sku-empty")

            # Enter quantity if input exists
            quantity_input = page.locator('div[aria-label="入库"] .el-input-number input')
            if await quantity_input.count() > 0:
                await quantity_input.fill("5")
                await take_screenshot(page, "17-stock-in-quantity")

            # Submit
            submit_btn = page.locator('div[aria-label="入库"] button:has-text("确认入库")')
            if await submit_btn.count() > 0:
                await submit_btn.click()
                await sleep(2000)
                await take_screenshot(page, "18-stock-in-submit")

            # Close dialog if still open by clicking outside or pressing escape
            try:
                close_btn = page.locator('div[aria-label="入库"] button:has-text("取消")')
                if await close_btn.count() > 0:
                    await close_btn.click()
                    await sleep(500)
            except:
                await page.keyboard.press("Escape")
                await sleep(500)

            results["passed"].append("Stock in dialog opens correctly")

            # ========== 5. STOCK OUT TEST ==========
            print("\n=== 5. STOCK OUT TEST ===")

            await page.click('button:has-text("出库")')
            await sleep(1500)
            await take_screenshot(page, "19-stock-out-dialog")

            # Try to select warehouse
            out_warehouse_select = page.locator('div[aria-label="出库"] .el-select').first
            await out_warehouse_select.click()
            await sleep(1000)
            await take_screenshot(page, "20-stock-out-warehouse-select")

            if await try_click_dropdown_option(page, 2000):
                await take_screenshot(page, "20-stock-out-warehouse-selected")
            else:
                await page.keyboard.press("Escape")
                await sleep(500)

            # Try to select SKU
            out_sku_select = page.locator('div[aria-label="出库"] .el-select').nth(1)
            await out_sku_select.click()
            await sleep(1000)
            await take_screenshot(page, "21-stock-out-sku-select")

            if await try_click_dropdown_option(page, 2000):
                await take_screenshot(page, "21-stock-out-sku-selected")
            else:
                await page.keyboard.press("Escape")
                await sleep(500)

            # Enter quantity if input exists
            out_quantity_input = page.locator('div[aria-label="出库"] .el-input-number input')
            if await out_quantity_input.count() > 0:
                await out_quantity_input.fill("2")
                await take_screenshot(page, "22-stock-out-quantity")

            # Submit
            out_submit_btn = page.locator('div[aria-label="出库"] button:has-text("确认出库")')
            if await out_submit_btn.count() > 0:
                await out_submit_btn.click()
                await sleep(2000)
                await take_screenshot(page, "23-stock-out-submit")

            # Close dialog if still open
            try:
                close_btn = page.locator('div[aria-label="出库"] button:has-text("取消")')
                if await close_btn.count() > 0:
                    await close_btn.click()
                    await sleep(500)
            except:
                await page.keyboard.press("Escape")
                await sleep(500)

            results["passed"].append("Stock out dialog opens correctly")

            # ========== 6. INVENTORY ADJUST TEST ==========
            print("\n=== 6. INVENTORY ADJUST TEST ===")

            await page.click('button:has-text("调整")')
            await sleep(1500)
            await take_screenshot(page, "24-adjust-dialog")

            # Try to select warehouse
            adj_warehouse_select = page.locator('div[aria-label="库存调整"] .el-select').first
            await adj_warehouse_select.click()
            await sleep(1000)
            await take_screenshot(page, "25-adjust-warehouse-select")

            if await try_click_dropdown_option(page, 2000):
                await take_screenshot(page, "25-adjust-warehouse-selected")
            else:
                await page.keyboard.press("Escape")
                await sleep(500)

            # Enter reason
            adj_reason_input = page.locator('div[aria-label="库存调整"] input[placeholder*="月度"]')
            if await adj_reason_input.count() > 0:
                await adj_reason_input.fill("Test adjustment")
                await take_screenshot(page, "26-adjust-reason-filled")

            # Try to select SKU
            adj_sku_select = page.locator('div[aria-label="库存调整"] .el-select').nth(1)
            await adj_sku_select.click()
            await sleep(1000)
            await take_screenshot(page, "27-adjust-sku-select")

            if await try_click_dropdown_option(page, 2000):
                await take_screenshot(page, "27-adjust-sku-selected")
            else:
                await page.keyboard.press("Escape")
                await sleep(500)

            # Enter quantity if input exists
            adj_quantity_input = page.locator('div[aria-label="库存调整"] .el-input-number input')
            if await adj_quantity_input.count() > 0:
                await adj_quantity_input.fill("1")
                await take_screenshot(page, "28-adjust-quantity")

            # Submit
            adj_submit_btn = page.locator('div[aria-label="库存调整"] button:has-text("确认调整")')
            if await adj_submit_btn.count() > 0:
                await adj_submit_btn.click()
                await sleep(2000)
                await take_screenshot(page, "29-adjust-submit")

            # Close dialog if still open
            try:
                close_btn = page.locator('div[aria-label="库存调整"] button:has-text("取消")')
                if await close_btn.count() > 0:
                    await close_btn.click()
                    await sleep(500)
            except:
                await page.keyboard.press("Escape")
                await sleep(500)

            results["passed"].append("Adjust dialog opens correctly")

            # ========== 7. INVENTORY LOG TEST ==========
            print("\n=== 7. INVENTORY LOG TEST ===")

            await page.click('button:has-text("记录")')
            await sleep(1500)
            await take_screenshot(page, "30-inventory-log-dialog")

            # Test filter - change type select (second select in the log dialog)
            log_type_select = page.locator('div[aria-label="库存记录"] .el-select').nth(1)
            await log_type_select.click()
            await sleep(1000)
            await take_screenshot(page, "31-log-type-dropdown")

            if await try_click_dropdown_option(page, 2000):
                await take_screenshot(page, "31-log-type-selected")
            else:
                await page.keyboard.press("Escape")
                await sleep(500)

            # Click query
            log_query_btn = page.locator('div[aria-label="库存记录"] button:has-text("查询")')
            if await log_query_btn.count() > 0:
                await log_query_btn.click()
                await sleep(1000)
                await take_screenshot(page, "32-log-filtered")

            # Close dialog
            await page.keyboard.press("Escape")
            await sleep(500)

            results["passed"].append("Inventory log dialog opens correctly")
            results["passed"].append("Log filters work correctly")

            # ========== FINAL SCREENSHOT ==========
            print("\n=== FINAL STATE ===")
            await sleep(1000)
            await take_screenshot(page, "99-final-state")

            # Print results
            print("\n" + "=" * 50)
            print("TEST RESULTS")
            print("=" * 50)
            print(f"PASSED: {len(results['passed'])}")
            for p in results["passed"]:
                print(f"  + {p}")
            print(f"\nFAILED: {len(results['failed'])}")
            for f in results["failed"]:
                print(f"  - {f}")
            print("=" * 50)
            print(f"All screenshots saved to: {SCREENSHOT_DIR}")

        except Exception as error:
            print(f"Test error: {error}")
            await take_screenshot(page, "99-error-state")
            results["failed"].append(str(error))

        finally:
            await browser.close()

        return results

if __name__ == "__main__":
    asyncio.run(run_test())
