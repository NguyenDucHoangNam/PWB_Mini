import { redirect } from "next/navigation";

interface PageProps {
  params: Promise<{ roomCode: string }>;
}

export default async function LiveRoomDashboardPage({ params }: PageProps) {
  const { roomCode } = await params;
  redirect(`/live-room/${roomCode}`);
}
