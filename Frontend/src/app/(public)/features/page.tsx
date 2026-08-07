import type { Metadata } from "next";
import { ShowcaseContent } from "@/features/showcase/components/showcase-content";

export const metadata: Metadata = {
  title: "Features | Producer Workbench",
  description:
    "How each module of Producer Workbench is used, and how it is built underneath.",
};

export default function FeaturesPage() {
  return <ShowcaseContent />;
}
