import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { LoginForm } from "./login-form";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/login",
}));

const renderButtonMock = vi.fn();
Object.defineProperty(window, "google", {
  configurable: true,
  writable: true,
  value: {
    accounts: {
      id: {
        initialize: vi.fn(),
        prompt: vi.fn(),
        renderButton: renderButtonMock,
      },
    },
  },
});

vi.mock("@/components/ui/checkbox", () => ({
  Checkbox: ({
    checked,
    onCheckedChange,
    ...props
  }: {
    checked?: boolean;
    onCheckedChange?: (b: boolean) => void;
  }) => (
    <input
      type="checkbox"
      checked={!!checked}
      onChange={(e) => onCheckedChange?.(e.target.checked)}
      {...props}
    />
  ),
}));

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
    </NextIntlClientProvider>,
  );
}

describe("LoginForm", () => {
  it("renders the Google Sign-In button through GIS into a visible container", () => {
    renderForm();
    const gisContainer = document.querySelector(
      '[data-google-button-container], div.flex.justify-center',
    );
    expect(gisContainer).toBeInTheDocument();
    expect(window.google?.accounts?.id?.renderButton).toBeDefined();
  });

  it("calls login mutation with email and password", async () => {
    renderForm();
    const email = document.querySelector(
      'input[type="email"], input[autocomplete="email"]',
    ) as HTMLInputElement;
    const password = document.querySelector(
      'input[name="password"], input[type="password"]',
    ) as HTMLInputElement;
    fireEvent.change(email, { target: { value: "u@x.com" } });
    fireEvent.change(password, { target: { value: "secret123" } });
    const submit = screen.getByRole("button", { name: /LOG IN/i });
    fireEvent.click(submit);
    await waitFor(() => {
      expect(loginMutate).toHaveBeenCalled();
    });
  });
});
