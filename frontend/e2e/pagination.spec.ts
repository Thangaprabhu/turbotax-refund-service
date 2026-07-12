import { test, expect } from "@playwright/test";
import { login, SEED_ACCOUNT } from "./helpers";

// Relies on the seeded pagination-demo@example.com account: 100 taxpayers, and one of them
// (FILINGS_TAXPAYER_ID) with 104 filings spanning all 6 statuses. See ml/seed_pagination_demo.py.
const FILINGS_TAXPAYER_ID = "b1a45447-b096-45f4-bb28-02a9d33a7cc4";

test.describe("Pagination", () => {
  test.beforeEach(async ({ page }) => {
    await login(page, SEED_ACCOUNT.email, SEED_ACCOUNT.password);
  });

  test("taxpayers list paginates 10 at a time across 10 pages", async ({ page }) => {
    await expect(page.getByText("Showing 1–10 of 100")).toBeVisible();
    await expect(page.getByText("Page 1 of 10")).toBeVisible();

    const prevButton = page.getByRole("button", { name: "Prev" });
    const nextButton = page.getByRole("button", { name: "Next" });
    await expect(prevButton).toBeDisabled();
    await expect(nextButton).toBeEnabled();

    const firstPageFirstRow = await page.locator("main a").first().innerText();

    await nextButton.click();
    await expect(page.getByText("Showing 11–20 of 100")).toBeVisible();
    await expect(page.getByText("Page 2 of 10")).toBeVisible();
    await expect(prevButton).toBeEnabled();

    const secondPageFirstRow = await page.locator("main a").first().innerText();
    expect(secondPageFirstRow).not.toEqual(firstPageFirstRow);

    await prevButton.click();
    await expect(page.getByText("Showing 1–10 of 100")).toBeVisible();
    const backOnFirstPage = await page.locator("main a").first().innerText();
    expect(backOnFirstPage).toEqual(firstPageFirstRow);
  });

  test("taxpayers list reaches a final page with Next disabled", async ({ page }) => {
    const nextButton = page.getByRole("button", { name: "Next" });
    for (let i = 0; i < 9; i++) {
      await nextButton.click();
    }

    await expect(page.getByText("Showing 91–100 of 100")).toBeVisible();
    await expect(page.getByText("Page 10 of 10")).toBeVisible();
    await expect(nextButton).toBeDisabled();
  });

  test("filings list for a taxpayer with 104 filings paginates across 11 pages", async ({ page }) => {
    await page.goto(`/taxpayers/${FILINGS_TAXPAYER_ID}`);

    await expect(page.getByText("Showing 1–10 of 104")).toBeVisible();
    await expect(page.getByText("Page 1 of 11")).toBeVisible();

    const nextButton = page.getByRole("button", { name: "Next" });
    for (let i = 0; i < 10; i++) {
      await nextButton.click();
    }

    // Last page holds the 4-item remainder (104 - 100).
    await expect(page.getByText("Showing 101–104 of 104")).toBeVisible();
    await expect(page.getByText("Page 11 of 11")).toBeVisible();
    await expect(nextButton).toBeDisabled();
  });
});
