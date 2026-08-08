/// <reference types="vitest" />
import { defineConfig } from "vitest/config";
import path from "node:path";

export default defineConfig({
  test: {
    environment: "jsdom",
    globals: true,
    include: ["src/**/*.test.{ts,tsx}"],
    setupFiles: ["./vitest.setup.ts"],
    // Next inlines NEXT_PUBLIC_* at build time; under vitest they are read from process.env at
    // runtime instead, so anything the components branch on has to be declared here. Without this
    // the Google container in login-form.tsx is never rendered — the component treats a missing
    // client id as "Google Sign-In is not configured" and returns nothing for that branch.
    env: {
      NEXT_PUBLIC_GOOGLE_CLIENT_ID: "test-client-id.apps.googleusercontent.com",
    },
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});