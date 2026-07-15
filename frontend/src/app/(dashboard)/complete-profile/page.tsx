import { CompleteProfileForm } from "@/features/auth/components/complete-profile-form";

export const metadata = {
  title: "Hoàn tất hồ sơ | PWB",
  description: "Đặt username và hoàn tất hồ sơ của bạn",
};

export default function CompleteProfilePage() {
  return <CompleteProfileForm />;
}
