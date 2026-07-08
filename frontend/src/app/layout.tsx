import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getMessages } from "next-intl/server";
import { AppProviders } from "@/providers/app-providers";
import { NetworkStatusBanner } from "@/components/network-status-banner";
import { SessionTimeoutWarning } from "@/components/session-timeout-warning";
import { GoogleIdentityScript } from "@/components/google-identity-script";
import "./globals.css";

const GOOGLE_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "PWB MiNi — Real-time Audio Collaboration Platform",
  description:
    "A secure real-time audio collaboration and demo sharing platform for music producers.",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const locale = await getLocale();
  const messages = await getMessages();

  return (
    <html
      lang={locale}
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <body className="min-h-full flex flex-col">
        <GoogleIdentityScript />
        <NextIntlClientProvider locale={locale} messages={messages}>
          <AppProviders>
            <NetworkStatusBanner />
            <SessionTimeoutWarning />
            {children}
          </AppProviders>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
