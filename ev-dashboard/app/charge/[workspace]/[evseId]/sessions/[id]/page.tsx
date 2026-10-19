import { SessionScreen } from "@/features/charging/session-screen";

export default async function Page({
  params,
}: {
  params: Promise<{ workspace: string; evseId: string; id: string }>;
}) {
  const { workspace, evseId, id } = await params;
  return <SessionScreen id={id} portalPath={`/charge/${workspace}/${evseId}`} />;
}
