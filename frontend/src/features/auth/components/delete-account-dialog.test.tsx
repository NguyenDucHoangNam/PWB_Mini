import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { DeleteAccountDialog } from "./delete-account-dialog";

// Mock next/navigation to avoid needing an App Router context.
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

// Mock Dialog and UI primitives used by the dialog.
vi.mock("@/components/ui/dialog", () => ({
  Dialog: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DialogContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DialogDescription: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DialogFooter: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DialogHeader: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  DialogTitle: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

// Mock profile API to return a Google-linked user.
vi.mock("../api/profile", () => ({
  useProfile: () => ({
    data: {
      success: true,
      data: {
        username: "u",
        email: "user@gmail.com",
        fullName: "User",
        status: "ACTIVE",
        role: "USER",
        oauthProvider: "GOOGLE",
        avatarUrl: null,
        phone: null,
        deletionRequestedAt: null,
      },
    },
    isLoading: false,
    isError: false,
  }),
  useUpdateProfile: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock("../api/account", () => ({
  useDeleteAccount: () => ({ mutate: vi.fn(), isPending: false }),
}));

import enMessages from "@/../messages/en.json";
import { NextIntlClientProvider } from "next-intl";

function renderWithIntl(ui: React.ReactNode) {
  return render(
    <NextIntlClientProvider locale="en" messages={enMessages as never}>
      {ui}
    </NextIntlClientProvider>
  );
}

describe("DeleteAccountDialog", () => {
  it("uses oauthProvider field to determine Google reauth path", () => {
    renderWithIntl(<DeleteAccountDialog isOpen onClose={() => {}} />);
    // The Google reauth button text should be present, NOT a password label.
    expect(screen.getByText(/RE-AUTHENTICATE WITH GOOGLE/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/Enter password/i)).not.toBeInTheDocument();
  });
});