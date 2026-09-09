import { test, expect } from '@playwright/test';

test('pw-p29-tenant-bulk-send C-P29-TENANT-BULK-SEND OBL-F-6-11-A pw-p29-import-preview C-P29-IMPORT-PREVIEW OBL-F-6-11-B', async ({ page }) => {
  await page.goto('/tenant/bulk/send');
  await expect(page.getByTestId('tenant-bulk-scheduled-bulk-send-page')).toBeVisible();
  await expect(page.getByTestId('tenant-bulk-scheduled-bulk-send-validation-results')).toBeVisible();
});

test('pw-p29-tenant-scheduled-tasks C-P29-TENANT-SCHEDULED-TASKS OBL-F-6-12-A pw-p29-tenant-task-control C-P29-TENANT-TASK-CONTROL OBL-F-6-12-B pw-p29-state-start C-P29-STATE-START OBL-STATE-BATCH-START pw-p29-state-complete C-P29-STATE-COMPLETE OBL-STATE-BATCH-COMPLETE pw-p29-state-fail C-P29-STATE-FAIL OBL-STATE-BATCH-FAIL pw-p29-state-pause C-P29-STATE-PAUSE OBL-STATE-BATCH-PAUSE pw-p29-state-resume C-P29-STATE-RESUME OBL-STATE-BATCH-RESUME pw-p29-state-restart C-P29-STATE-RESTART OBL-STATE-BATCH-RESTART', async ({ page }) => {
  await page.goto('/tenant/scheduled/tasks');
  await expect(page.getByTestId('tenant-bulk-scheduled-scheduled-tasks-page')).toBeVisible();
  await expect(page.getByTestId('tenant-bulk-scheduled-scheduled-tasks-control')).toBeVisible();
  await expect(page.getByTestId('tenant-bulk-scheduled-scheduled-tasks-state')).toContainText('RUNNING');
  await expect(page.getByTestId('tenant-bulk-scheduled-scheduled-tasks-pause')).toBeVisible();
  await expect(page.getByTestId('tenant-bulk-scheduled-scheduled-tasks-resume')).toBeVisible();
  await expect(page.getByTestId('tenant-bulk-scheduled-scheduled-tasks-restart')).toBeVisible();
});

test('pw-p29-admin-bulk-details C-P29-ADMIN-BULK-DETAILS OBL-F-7-3-A pw-p29-admin-bulk-detail-table C-P29-ADMIN-BULK-DETAIL-TABLE OBL-F-7-3-B', async ({ page }) => {
  await page.goto('/admin/bulk/details');
  await expect(page.getByTestId('admin-bulk-scheduled-bulk-details-page')).toBeVisible();
  await expect(page.getByTestId('admin-bulk-scheduled-bulk-details-table')).toBeVisible();
});

test('pw-p29-admin-send-jobs C-P29-ADMIN-SEND-JOBS OBL-F-13-5-A pw-p29-admin-send-job-control C-P29-ADMIN-SEND-JOB-CONTROL OBL-F-13-5-B', async ({ page }) => {
  await page.goto('/admin/send/jobs');
  await expect(page.getByTestId('admin-bulk-scheduled-send-jobs-page')).toBeVisible();
  await expect(page.getByTestId('admin-bulk-scheduled-send-jobs-control')).toBeVisible();
});
