import { AssetDetailScreen } from "@/features/organization/asset-detail-screen";

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <AssetDetailScreen id={id} />;
}
