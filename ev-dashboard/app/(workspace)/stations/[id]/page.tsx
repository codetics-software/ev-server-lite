import { StationDetailScreen } from "@/features/stations/station-detail-screen";

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <StationDetailScreen id={decodeURIComponent(id)} />;
}
