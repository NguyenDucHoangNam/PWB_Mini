import { MinimalLayout } from "@/components/layout/minimal-layout";

export default function ErrorRouteLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return <MinimalLayout>{children}</MinimalLayout>;
}
