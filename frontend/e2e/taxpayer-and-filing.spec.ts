import { test, expect } from "@playwright/test";
import { register, uniqueEin, uniqueEmail, uniqueSsn } from "./helpers";

test.describe("Taxpayer and filing creation", () => {
  test("creating a taxpayer shows it in the dashboard list and detail page", async ({ page }) => {
    await register(page, uniqueEmail("taxpayer"));

    await page.getByRole("button", { name: "Add Taxpayer" }).click();
    await page.locator('input[name="taxId"]').fill(uniqueSsn());
    await page.locator('input[name="displayName"]').fill("Jane E2E Doe");
    await page.locator('input[name="stateOfReg"]').fill("CA");
    await page.getByRole("button", { name: "Register" }).click();

    const taxpayerLink = page.getByRole("link", { name: /Jane E2E Doe/ });
    await expect(taxpayerLink).toBeVisible();
    await expect(taxpayerLink).toContainText("Individual");
    await expect(taxpayerLink).toContainText("CA");

    await taxpayerLink.click();
    await expect(page.getByRole("heading", { name: "Jane E2E Doe" })).toBeVisible();
    await expect(page.getByText("No filings yet. Submit one to start tracking your refund.")).toBeVisible();
  });

  test("creating a business taxpayer captures entity type", async ({ page }) => {
    await register(page, uniqueEmail("business"));

    await page.getByRole("button", { name: "Add Taxpayer" }).click();
    await page.getByLabel("Business (EIN)").check();
    await page.locator('input[name="taxId"]').fill(uniqueEin());
    await page.locator('input[name="displayName"]').fill("Acme E2E LLC");
    await page.locator('input[name="entityType"]').fill("LLC");
    await page.locator('input[name="stateOfReg"]').fill("NY");
    await page.getByRole("button", { name: "Register" }).click();

    const taxpayerLink = page.getByRole("link", { name: /Acme E2E LLC/ });
    await expect(taxpayerLink).toContainText("LLC");
    await expect(taxpayerLink).toContainText("NY");
  });

  test("submitting a filing shows it with a Received status and expands to show details", async ({ page }) => {
    await register(page, uniqueEmail("filing"));

    await page.getByRole("button", { name: "Add Taxpayer" }).click();
    await page.locator('input[name="taxId"]').fill(uniqueSsn());
    await page.locator('input[name="displayName"]').fill("Filer E2E");
    await page.getByRole("button", { name: "Register" }).click();
    await page.getByRole("link", { name: /Filer E2E/ }).click();

    await page.getByRole("button", { name: "Add Filing" }).click();
    await page.locator('select[name="formType"]').selectOption("F1040");
    await page.locator('input[name="taxYear"]').fill("2024");
    await page.locator('input[name="jurisdiction"]').fill("FEDERAL");
    await page.locator('input[name="filingDate"]').fill("2024-04-01");
    await page.getByRole("button", { name: "Submit" }).click();

    const filingRow = page.getByText("F1040").locator("xpath=ancestor::button[1]");
    await expect(filingRow).toContainText("2024");
    await expect(filingRow).toContainText("FEDERAL");
    await expect(filingRow.getByText("Received")).toBeVisible();

    // RECEIVED filings deliberately show no AI prediction yet (too early for a signal).
    await filingRow.click();
    await expect(page.getByText("AI Prediction")).not.toBeVisible();
  });

  test("submitting a duplicate filing for the same year/form/jurisdiction shows an error", async ({ page }) => {
    await register(page, uniqueEmail("dupfiling"));

    await page.getByRole("button", { name: "Add Taxpayer" }).click();
    await page.locator('input[name="taxId"]').fill(uniqueSsn());
    await page.locator('input[name="displayName"]').fill("Dup Filer E2E");
    await page.getByRole("button", { name: "Register" }).click();
    await page.getByRole("link", { name: /Dup Filer E2E/ }).click();

    for (let i = 0; i < 2; i++) {
      await page.getByRole("button", { name: "Add Filing" }).click();
      await page.locator('select[name="formType"]').selectOption("F1040");
      await page.locator('input[name="taxYear"]').fill("2024");
      await page.locator('input[name="jurisdiction"]').fill("FEDERAL");
      await page.locator('input[name="filingDate"]').fill("2024-04-01");
      await page.getByRole("button", { name: "Submit" }).click();
    }

    await expect(page.getByText("Failed to submit filing.")).toBeVisible();
  });
});
