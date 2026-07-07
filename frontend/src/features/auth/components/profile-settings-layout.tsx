import { ProfileForm } from "./profile-form";
import { ChangePasswordForm } from "./change-password-form";

export function ProfileSettingsLayout() {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-16 items-start font-sans py-4">
      {/* Left Panel: Profile Detail */}
      <div className="border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-black rounded-xl p-6 sm:p-8">
        <ProfileForm />
      </div>

      {/* Right Panel: Change Password */}
      <div className="border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-black rounded-xl p-6 sm:p-8">
        <ChangePasswordForm />
      </div>
    </div>
  );
}
