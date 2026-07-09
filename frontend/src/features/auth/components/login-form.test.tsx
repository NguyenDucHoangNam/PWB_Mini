import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { LoginForm } from "./login-form";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/login",
}));

// Mock GIS so we never call the real Google script.
Object.defineProperty(window, "google", {
  configurable: true,
  writable: true,
  value: {
    accounts: {
      id: {
        initialize: vi.fn(),
        prompt: vi.fn(),
        renderButton: vi.fn(),
      },
    },
  },
});

// Mock the UI checkbox used inside LoginForm to avoid the broken base-ui
// dependency breaking tests.
vi.mock("@/components/ui/checkbox", () => ({
  Checkbox: ({ checked, onCheckedChange, ...props }: { checked?: boolean; onCheckedChange?: (b: boolean) => void }) => (
    <input
      type="checkbox"
      checked={!!checked}
      onChange={(e) => onCheckedChange?.(e.target.checked)}
      {...props}
    />
  ),
}));

// Mock the API hooks so login submit doesn't make real network calls.
const loginMutate = vi.fn();
vi.mock("../api/login", () => ({
  useLogin: () => ({ mutate: loginMutate, isPending: false }),
  useLoginWithGoogle: () => ({ mutate: vi.fn(), isPending: false }),
}));

import enMessages from "@/../messages/en.json";
import { NextIntlClientProvider } from "next-intl";

function renderForm() {
  return render(
    <NextIntlClientProvider locale="en" messages={enMessages as never}>
      <LoginForm />
    </NextIntlClientProvider>
  );
}

describe("LoginForm", () => {
  it("renders the Continue with Google button", () => {
    renderForm();
    expect(screen.getByText(/Continue with Google/i)).toBeInTheDocument();
  });

  it("calls login mutation with username and password", async () => {
    renderForm();
    const username = screen.getByLabelText(/Username or Email/i) as HTMLInputElement;
    const password = document.querySelector('input[name="password"], input[type="password"]') as HTMLInputElement;
    fireEvent.change(username, { target: { value: "u@x.com" } });
    fireEvent.change(password, { target: { value: "secret123" } });
    const submit = screen.getByRole("button", { name: /LOG IN/i });
    fireEvent.click(submit);
    await waitFor(() => {
      expect(loginMutate).toHaveBeenCalled();
    });
  });
});