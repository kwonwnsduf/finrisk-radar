import { AppShell } from "@/components/layout/app-shell";
import { NotificationList } from "@/components/notifications/notification-list";

export default function NotificationsPage() {
  return (
    <AppShell>
      <NotificationList />
    </AppShell>
  );
}
