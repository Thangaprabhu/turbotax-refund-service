import { test, expect } from "@playwright/test";
import { login, register, uniqueEmail } from "./helpers";

test.describe("Authentication", () => {
  test("unauthenticated users are redirected to login", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveURL(/\/login$/);
  });

  test("register creates an account and lands on the dashboard", async ({ page }) => {
    await register(page, uniqueEmail("register"));

    await expect(page.getByRole("heading", { name: "Taxpayers" })).toBeVisible();
    await expect(page.getByText("TurboTax Refund Status")).toBeVisible();
  });

  test("registering with an already-used email shows an error and stays on the page", async ({ page }) => {
    const email = uniqueEmail("dup");
    await register(page, email);
    await page.getByRole("button", { name: "Sign out" }).click();
    await expect(page).toHaveURL(/\/login$/);

    await page.goto("/register");
    await page.locator('input[type="email"]').fill(email);
    await page.locator('input[name="password"]').fill("password123");
    await page.locator('input[name="confirmPassword"]').fill("password123");
    await page.getByRole("button", { name: "Create account" }).click();

    await expect(page.getByText("Registration failed. Email may already be in use.")).toBeVisible();
    await expect(page).toHaveURL(/\/register$/);
  });

  test("sign out redirects to login, and logging back in returns to the dashboard", async ({ page }) => {
    const email = uniqueEmail("relogin");
    await register(page, email);

    await page.getByRole("button", { name: "Sign out" }).click();
    await expect(page).toHaveURL(/\/login$/);

    // Signed-out session can no longer reach a protected route.
    await page.goto("/");
    await expect(page).toHaveURL(/\/login$/);

    await login(page, email);
    await expect(page.getByRole("heading", { name: "Taxpayers" })).toBeVisible();
  });

  test("logging in with the wrong password shows an error", async ({ page }) => {
    const email = uniqueEmail("wrongpw");
    await register(page, email);
    await page.getByRole("button", { name: "Sign out" }).click();

    await page.goto("/login");
    await page.locator('input[type="email"]').fill(email);
    await page.locator('input[type="password"]').fill("not-the-password");
    await page.getByRole("button", { name: "Sign in" }).click();

    await expect(page.getByText("Invalid email or password.")).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });
});
