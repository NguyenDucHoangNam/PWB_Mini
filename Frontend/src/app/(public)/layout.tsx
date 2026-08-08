import { PublicLayout } from "@/components/layout/public-layout";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Producer Workbench",
  description:
    "A secure real-time audio collaboration and demo sharing platform for music producers.",
};

export default function PublicRouteLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return <PublicLayout>{children}</PublicLayout>;
}
