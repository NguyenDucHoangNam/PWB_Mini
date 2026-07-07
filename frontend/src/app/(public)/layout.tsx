import { PublicLayout } from "@/components/layout/public-layout";

export default function PublicRouteLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return <PublicLayout>{children}</PublicLayout>;
}
