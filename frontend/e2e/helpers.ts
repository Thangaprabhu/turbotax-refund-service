import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";

/** Unique enough across repeated local test runs without needing a DB reset between them. */
export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}@example.com`;
}

/** Matches the app's SSN regex (^\d{3}-\d{2}-\d{4}$) while staying unique per call --
 *  taxId is globally unique server-side (hashed), so a fixed value would collide on rerun. */
export function uniqueSsn(): string {
  const n = Date.now() % 1_000_000;
  const a = String(200 + (n % 700)).padStart(3, "0");
  const b = String(10 + (n % 89)).padStart(2, "0");
  const c = String(1000 + Math.floor(Math.random() * 9000)).padStart(4, "0");
  return `${a}-${b}-${c}`;
}

/** Matches the app's EIN regex (^\d{2}-\d{7}$), unique per call for the same reason as uniqueSsn(). */
export function uniqueEin(): string {
  const a = String(10 + Math.floor(Math.random() * 89)).padStart(2, "0");
  const b = String(Date.now() % 10_000_000).padStart(7, "0");
  return `${a}-${b}`;
}

export const SEED_ACCOUNT = { email: "pagination-demo@example.com", password: "password123" };

export async function register(page: Page, email: string, password = "password123") {
  await page.goto("/register");
  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[name="password"]').fill(password);
  await page.locator('input[name="confirmPassword"]').fill(password);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL("/");
}

export async function login(page: Page, email: string, password = "password123") {
  await page.goto("/login");
  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL("/");
}
