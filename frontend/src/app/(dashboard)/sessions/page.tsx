import { SessionsTable } from "@/features/auth/components/sessions-table";

export const metadata = {
  title: "Quản lý phiên | PWB",
  description: "Quản lý các thiết bị và trình duyệt đang đăng nhập vào tài khoản",
};

export default function SessionsPage() {
  return (
    <div className="border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-black rounded-xl p-4 sm:p-6 md:p-8">
      <SessionsTable />
    </div>
  );
}
