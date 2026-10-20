import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { IconAlertTriangle, IconInbox, IconRefresh } from "@tabler/icons-react";
import type { ReactNode } from "react";

export function ErrorState({
  error,
  title = "Impossible de charger les données",
  onRetry,
}: {
  error: Error | string | null | undefined;
  title?: string;
  onRetry?: () => void;
}) {
  if (!error) {
    return null;
  }
  return (
    <Alert variant="destructive">
      <IconAlertTriangle />
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription className="flex flex-wrap items-center justify-between gap-3">
        <span>{typeof error === "string" ? error : error.message}</span>
        {onRetry && (
          <Button variant="outline" size="sm" onClick={onRetry}>
            <IconRefresh data-icon="inline-start" />
            Réessayer
          </Button>
        )}
      </AlertDescription>
    </Alert>
  );
}

export function EmptyState({
  title,
  description,
  action,
  icon,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <Empty className="min-h-48 border">
      <EmptyHeader>
        <EmptyMedia variant="icon">{icon ?? <IconInbox />}</EmptyMedia>
        <EmptyTitle>{title}</EmptyTitle>
        {description && <EmptyDescription>{description}</EmptyDescription>}
      </EmptyHeader>
      {action && <EmptyContent>{action}</EmptyContent>}
    </Empty>
  );
}

export function LoadingState({ rows = 5 }: { rows?: number }) {
  return (
    <div className="flex flex-col gap-2" aria-busy="true" aria-label="Chargement">
      <Skeleton className="h-9 w-full" />
      {Array.from({ length: rows }, (_, index) => (
        <Skeleton key={index} className="h-11 w-full" />
      ))}
    </div>
  );
}

export function ListState<T>({
  data,
  items,
  loading,
  error,
  onRetry,
  empty,
  children,
}: {
  data: unknown;
  items: T[];
  loading: boolean;
  error: Error | null;
  onRetry?: () => void;
  empty: ReactNode;
  children: ReactNode;
}) {
  if (error && !data) {
    return <ErrorState error={error} onRetry={onRetry} />;
  }
  if (loading && !data) {
    return <LoadingState />;
  }
  if (!items.length) {
    return <>{empty}</>;
  }
  return <>{children}</>;
}
