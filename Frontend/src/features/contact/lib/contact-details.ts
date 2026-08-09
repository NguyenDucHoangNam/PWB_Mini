export const CONTACT_EMAIL = "namnguyenduchoang@gmail.com";

// Shown in the local form people recognise; the tel: href carries the +84 form so the
// link still dials correctly from outside Vietnam.
export const CONTACT_PHONE_DISPLAY = "0919 812 357";
export const CONTACT_PHONE_TEL = "+84919812357";

export const PARTNER_NAME = "HNAM OFFICIAL";

// Sized for the ~320px column at 2x rather than the original 2614px capture.
export const CONTACT_PORTRAIT = {
  src: "/hnam-in-the-mix.webp",
  width: 720,
  height: 932,
} as const;

export type SocialKey = "facebook" | "instagram" | "portfolio";

export const CONTACT_SOCIALS: ReadonlyArray<{
  key: SocialKey;
  href: string;
  handle: string;
}> = [
  {
    key: "facebook",
    href: "https://www.facebook.com/namhoang511/",
    handle: "namhoang511",
  },
  {
    key: "instagram",
    href: "https://www.instagram.com/hnamng.official/",
    handle: "hnamng.official",
  },
  {
    key: "portfolio",
    href: "https://www.hnam-official.online/",
    handle: "hnam-official.online",
  },
];
