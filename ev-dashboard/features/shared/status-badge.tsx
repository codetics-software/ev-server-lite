import { Badge } from "@/components/ui/badge";
import { display } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { ReactNode } from "react";

export type Tone = "success" | "active" | "progress" | "warning" | "danger" | "neutral";

const tones: Record<Tone, string> = {
  success: "border-primary/25 bg-primary/10 text-primary dark:text-sidebar-primary",
  active: "border-chart-3/40 bg-chart-2/20 text-chart-5 dark:text-chart-1",
  progress: "border-chart-2/40 bg-chart-1/20 text-chart-5 dark:text-chart-2",
  warning: "border-chart-3/60 bg-background text-foreground",
  danger: "border-destructive/30 bg-destructive/10 text-destructive",
  neutral: "border-border bg-muted text-muted-foreground",
};

const dots: Record<Tone, string> = {
  success: "bg-primary dark:bg-sidebar-primary",
  active: "bg-chart-3",
  progress: "bg-chart-2",
  warning: "bg-chart-4",
  danger: "bg-destructive",
  neutral: "bg-muted-foreground/50",
};

export function ToneBadge({
  tone,
  children,
  className,
}: {
  tone: Tone;
  children: ReactNode;
  className?: string;
}) {
  return (
    <Badge variant="outline" className={cn(tones[tone], className)}>
      <span className={cn("size-1.5 rounded-full", dots[tone])} aria-hidden="true" />
      {children}
    </Badge>
  );
}

export function connectorTone(status: unknown): Tone {
  switch (status) {
    case "Available":
      return "success";
    case "Charging":
      return "active";
    case "Preparing":
    case "Finishing":
    case "SuspendedEV":
    case "SuspendedEVSE":
    case "Reserved":
      return "progress";
    case "Faulted":
      return "danger";
    default:
      return "neutral";
  }
}

export function ConnectorStatusBadge({ status }: { status: unknown }) {
  return <ToneBadge tone={connectorTone(status)}>{display(status)}</ToneBadge>;
}

export function stateTone(state: unknown): Tone {
  switch (state) {
    case "SUCCEEDED":
    case "PAID":
    case "REGISTERED":
    case "INVOICED":
    case "COMPLETED":
      return "success";
    case "QUEUED":
    case "SENT":
    case "PENDING":
    case "OPEN":
    case "DRAFT":
      return "progress";
    case "FAILED":
    case "TIMED_OUT":
    case "ERROR":
    case "UNCOLLECTIBLE":
    case "DISPUTED":
      return "danger";
    default:
      return "neutral";
  }
}

export function StateBadge({ state }: { state: unknown }) {
  return <ToneBadge tone={stateTone(state)}>{display(state)}</ToneBadge>;
}

export function BooleanBadge({
  value,
  yes = "Oui",
  no = "Non",
}: {
  value: unknown;
  yes?: string;
  no?: string;
}) {
  return <ToneBadge tone={value ? "success" : "neutral"}>{value ? yes : no}</ToneBadge>;
}

export function ErrorBadges({ errors }: { errors: string[] }) {
  return (
    <div className="flex flex-wrap gap-1">
      {errors.map((error) => (
        <ToneBadge key={error} tone="danger">
          {display(error)}
        </ToneBadge>
      ))}
    </div>
  );
}
