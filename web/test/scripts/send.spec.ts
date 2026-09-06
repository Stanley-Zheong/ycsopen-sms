import { expect, test } from '@playwright/test';
import { apiResponse, loginAs } from './helpers';

test('WEB-SEND-001 successful manual send displays the message ID', async ({ page }) => {
  await page.route('**/api/v1/sms/send', async (route) => {
    expect(route.request().method()).toBe('POST');
    expect(route.request().postDataJSON()).toEqual({ phoneNumber: '13800138000', templateId: '1001', templateParams: {} });
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiResponse({ messageId: 'msg-001' })) });
  });
  await loginAs(page, 'TENANT_USER');
  await page.getByRole('link', { name: '发送管理' }).click();
  await page.getByPlaceholder('手机号').fill('13800138000');
  await page.getByPlaceholder('模板ID').fill('1001');
  await page.getByRole('button', { name: '发送' }).click();
  await expect(page.getByText('提交成功，消息ID：msg-001')).toBeVisible();
  await expect(page.getByText(/^提交失败/)).toHaveCount(0);
});

test('WEB-SEND-002 rejected manual send displays the backend message', async ({ page }) => {
  await page.route('**/api/v1/sms/send', async (route) => {
    expect(route.request().method()).toBe('POST');
    expect(new URL(route.request().url()).pathname).toBe('/api/v1/sms/send');
    expect(route.request().postDataJSON()).toEqual({ phoneNumber: '13800138000', templateId: '1001', templateParams: {} });
    await route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify(apiResponse(null, '余额不足')) });
  });
  await loginAs(page, 'TENANT_USER');
  await page.getByRole('link', { name: '发送管理' }).click();
  await page.getByPlaceholder('手机号').fill('13800138000');
  await page.getByPlaceholder('模板ID').fill('1001');
  await page.getByRole('button', { name: '发送' }).click();
  await expect(page.getByText('提交失败：余额不足')).toBeVisible();
  await expect(page.getByText(/^提交成功/)).toHaveCount(0);
});
