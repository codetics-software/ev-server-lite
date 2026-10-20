import { genericSections } from "@/features/resources/config/generic-sections";
import { ResourceScreen } from "@/features/workspace/resource-screen";
import { notFound } from "next/navigation";

export function generateStaticParams() {
  return genericSections.map((section) => ({ section }));
}

export default async function ResourceRoute({ params }: { params: Promise<{ section: string }> }) {
  const { section } = await params;
  if (!genericSections.includes(section)) {
    notFound();
  }
  return <ResourceScreen key={section} section={section} />;
}
