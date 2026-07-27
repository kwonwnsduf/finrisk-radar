import { AppShell } from "@/components/layout/app-shell";
import { SubscriptionSettings } from "@/components/subscription/subscription-settings";

export default function SubscriptionSettingsPage() {
  return <AppShell><div><h1 className="mb-6 text-2xl font-bold">구독 설정</h1><SubscriptionSettings /></div></AppShell>;
}
