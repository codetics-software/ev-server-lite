"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { useApi } from "@/hooks/use-api";
import { display, type User } from "@/lib/api";

import { StationCommandForm } from "./station-command-form";
import { StationConnectors } from "./station-connectors";

import type { StationDetails } from "./station-types";

export function StationDetail({ station, user }: { station: string; user: User }) {
  const { data, error, loading } = useApi<StationDetails>(
    `stations/${encodeURIComponent(station)}`,
  );

  if (loading) {
    return <Skeleton className="h-40 w-full" />;
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <AlertDescription>{error.message}</AlertDescription>
      </Alert>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant="secondary">{data?.connected ? "Connectée" : "Déconnectée"}</Badge>
        <span className="text-sm text-muted-foreground">
          {display(data?.vendor)} · {display(data?.model)}
        </span>
      </div>
      <StationConnectors connectors={data?.connectors ?? []} />
      {(user.role === "ADMIN" || user.role === "OPERATOR") && (
        <StationCommandForm station={station} />
      )}
    </div>
  );
}
