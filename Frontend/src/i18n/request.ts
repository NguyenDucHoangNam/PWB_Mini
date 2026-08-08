import { getRequestConfig } from "next-intl/server";
import { cookies } from "next/headers";

import { DEFAULT_LOCALE } from "@/lib/config";

export default getRequestConfig(async () => {
  const cookieStore = await cookies();
  const locale = cookieStore.get("locale")?.value || DEFAULT_LOCALE;

  return {
    locale,
    messages: (await import(`../../messages/${locale}.json`)).default,
  };
});
