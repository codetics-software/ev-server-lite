import { sections } from "@/features/workspace/navigation";
import { ResourceScreen } from "@/features/workspace/resource-screen";
import { notFound } from "next/navigation";

const resourceSections = sections.filter(
  (section) =>
    section.id !== "overview" &&
    section.id !== "account" &&
    section.id !== "stations" &&
    section.id !== "transactions" &&
    section.id !== "evses" &&
    section.id !== "payments" &&
    section.id !== "workspace",
);

export function generateStaticParams() {
  return resourceSections.map(({ id }) => ({ section: id }));
}

export default async function ResourceRoute({ params }: { params: Promise<{ section: string }> }) {
  const { section } = await params;
  if (!resourceSections.some((item) => item.id === section)) {
    notFound();
  }
  return <ResourceScreen key={section} section={section} />;
}
