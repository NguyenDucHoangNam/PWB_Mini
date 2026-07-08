"use client";

import Script from "next/script";

export function GoogleIdentityScript() {
  return (
    <Script
      id="google-gsi"
      src="https://accounts.google.com/gsi/client"
      strategy="lazyOnload"
      onLoad={() => {
        console.log("Google Identity Services loaded");
      }}
    />
  );
}
