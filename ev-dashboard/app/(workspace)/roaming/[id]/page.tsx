import { PartnerDetailScreen } from "@/features/roaming/partner-detail-screen";

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <PartnerDetailScreen id={id} />;
}
