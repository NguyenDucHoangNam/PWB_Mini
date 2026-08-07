import type { Metadata } from "next";
import { TechnicalContent } from "@/features/showcase/components/technical-content";

export const metadata: Metadata = {
  title: "Technical | Producer Workbench",
  description:
    "The constraints behind each Producer Workbench module, the approach taken, and what it cost.",
};

export default function TechnicalPage() {
  return <TechnicalContent />;
}
