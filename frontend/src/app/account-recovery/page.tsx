import { MinimalLayout } from "@/components/layout/minimal-layout";
import { AccountRecoveryPage } from "@/features/auth/components/account-recovery-page";

export const metadata = {
  title: "Khôi phục tài khoản | PWB MiNi",
  description: "Trang phục hồi tài khoản đang trong trạng thái chờ xóa",
};

export default function RecoveryRoutePage() {
  return (
    <MinimalLayout>
      <AccountRecoveryPage />
    </MinimalLayout>
  );
}
