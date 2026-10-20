import { SessionDetailScreen } from "@/features/sessions/session-detail-screen";

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <SessionDetailScreen id={id} />;
}
