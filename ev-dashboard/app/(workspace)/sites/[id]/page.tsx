import { SiteDetailScreen } from "@/features/organization/site-detail-screen";

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <SiteDetailScreen id={id} />;
}
