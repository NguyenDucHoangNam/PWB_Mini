"use client";

import Script from "next/script";

export function GoogleIdentityScript() {
  return (
    <Script
      id="google-gsi"
      src="https://accounts.google.com/gsi/client"
      strategy="afterInteractive"
      async
      defer
    />
  );
}
