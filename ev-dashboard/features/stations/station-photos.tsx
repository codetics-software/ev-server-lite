// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

"use client";

import { Button } from "@/components/ui/button";
import { ErrorState, LoadingState } from "@/features/shared/states";
import { useAction } from "@/features/shared/use-action";
import { useApi, useRevision } from "@/hooks/use-api";
import { api } from "@/lib/api";
import { date } from "@/lib/format";
import { IconPhoto, IconTrash } from "@tabler/icons-react";
import { t } from "@/lib/i18n";

type StationPhoto = {
  id: string;
  station_id: string;
  content_type: string;
  created_at: string;
  mine: boolean;
  path: string;
};

export function StationPhotos({ stationId, canDelete }: { stationId: string; canDelete: boolean }) {
  const [revision, refresh] = useRevision();
  const { data, loading, error } = useApi<StationPhoto[]>(
    `account/stations/${encodeURIComponent(stationId)}/photos`,
    revision,
  );
  const action = useAction();
  if (error && !data) {
    return <ErrorState error={error} onRetry={refresh} />;
  }
  if (loading && !data) {
    return <LoadingState rows={2} />;
  }
  if (!data?.length) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <IconPhoto className="size-4" />
        {t("Aucune photo envoyée par les conducteurs pour cette borne.")}
      </div>
    );
  }
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {data.map((photo) => (
        <figure key={photo.id} className="flex flex-col gap-2 rounded-xl border bg-background p-3">
          <img
            src={`/api/backend${photo.path}`}
            alt={t("Photo de la borne, envoyée le {date}", { date: date(photo.created_at) })}
            className="aspect-video w-full rounded-md object-cover"
            loading="lazy"
          />
          <figcaption className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
            {date(photo.created_at)}
            {canDelete && (
              <Button
                variant="outline"
                size="sm"
                disabled={action.busy}
                onClick={() =>
                  action.run(
                    photo.id,
                    () =>
                      api(`account/stations/${encodeURIComponent(stationId)}/photos/${photo.id}`, {
                        method: "DELETE",
                      }),
                    { success: t("Photo supprimée."), onDone: refresh },
                  )
                }
              >
                <IconTrash data-icon="inline-start" />
                {t("Supprimer")}
              </Button>
            )}
          </figcaption>
        </figure>
      ))}
    </div>
  );
}
