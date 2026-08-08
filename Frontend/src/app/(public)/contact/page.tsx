import type { Metadata } from "next";
import { ContactContent } from "@/features/contact/components/contact-content";

export const metadata: Metadata = {
  title: "Contact | Producer Workbench",
  description:
    "Ask a question, report a bug, or send feedback about Producer Workbench — and see what is already answered.",
};

export default function ContactPage() {
  return <ContactContent />;
}
