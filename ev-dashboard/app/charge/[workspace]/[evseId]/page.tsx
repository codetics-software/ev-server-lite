import { PortalScreen } from "@/features/charging/portal-screen";

export default async function Page({
  params,
}: {
  params: Promise<{ workspace: string; evseId: string }>;
}) {
  const { workspace, evseId } = await params;
  return <PortalScreen workspace={workspace} evseId={evseId} />;
}
