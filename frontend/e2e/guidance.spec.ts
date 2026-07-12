import { test, expect } from "@playwright/test";
import { login, SEED_ACCOUNT } from "./helpers";

// Relies on the seeded pagination-demo@example.com account. Page 1 of this taxpayer's filings
// (sorted taxYear desc, formType, jurisdiction) is deterministic -- verified against the API
// before writing this spec:
//   2026#F1040#FEDERAL  UNDER_REVIEW  (situation: UNDER_REVIEW_INDIVIDUAL_FEDERAL)
//   2025#F1040#CA       FLAGGED       (situation: FLAGGED_INDIVIDUAL_STATE)
const FILINGS_TAXPAYER_ID = "b1a45447-b096-45f4-bb28-02a9d33a7cc4";

test.describe("RAG refund guidance", () => {
  test.beforeEach(async ({ page }) => {
    await login(page, SEED_ACCOUNT.email, SEED_ACCOUNT.password);
    await page.goto(`/taxpayers/${FILINGS_TAXPAYER_ID}`);
  });

  test("an under-review filing shows retrieved guidance with real IRS.gov sources", async ({ page }) => {
    const row = page.locator("main button").filter({ hasText: "Under Review" }).first();
    await row.click();

    await expect(page.getByText("Taking longer than the standard cycle")).toBeVisible();
    // Narrative is retrieved from the knowledge base, not hardcoded -- confirms the RAG call happened.
    await expect(page.getByText(/examined more closely/i)).toBeVisible();

    const sourcesToggle = page.getByText(/^Sources \(\d+\)$/);
    await expect(sourcesToggle).toBeVisible();
    await sourcesToggle.click();

    const sourceLinks = page.locator("details", { has: sourcesToggle }).getByRole("link");
    await expect(sourceLinks.first()).toBeVisible();
    const href = await sourceLinks.first().getAttribute("href");
    expect(href).toContain("irs.gov");
  });

  test("a flagged filing shows the identity-verification-style guidance and action links", async ({ page }) => {
    const row = page.locator("main button").filter({ hasText: "Flagged" }).first();
    await row.click();

    await expect(page.getByText("This return needs attention")).toBeVisible();
    await expect(page.getByRole("link", { name: /Check status on IRS.gov/i })).toHaveAttribute(
      "href",
      /irs\.gov/
    );
  });

  test("a deposited filing shows no guidance card", async ({ page }) => {
    const row = page.locator("main button").filter({ hasText: "Deposited" }).first();
    await row.click();

    await expect(page.getByText("This return needs attention")).not.toBeVisible();
    await expect(page.getByText("Taking longer than the standard cycle")).not.toBeVisible();
  });
});
