import { buttonVariants } from "@/components/ui/button";
import { IconArrowLeft } from "@tabler/icons-react";
import Link from "next/link";
import type { ReactNode } from "react";

export function PageHeader({
  title,
  description,
  actions,
  back,
  meta,
}: {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  back?: { href: string; label: string };
  meta?: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-3">
      {back && (
        <Link
          href={back.href}
          className={buttonVariants({ variant: "ghost", size: "sm", className: "-ml-2 w-fit" })}
        >
          <IconArrowLeft data-icon="inline-start" />
          {back.label}
        </Link>
      )}
      <div className="page-heading">
        <div className="min-w-0">
          <h1 className="break-words">{title}</h1>
          {description && <p>{description}</p>}
          {meta && <div className="mt-3 flex flex-wrap items-center gap-2">{meta}</div>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </div>
  );
}
