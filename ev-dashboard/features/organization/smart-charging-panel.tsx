"use client";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import { DataTable, type Column } from "@/features/shared/data-table";
import { OptionSelect } from "@/features/shared/form-fields";
import { EmptyState, ErrorState, LoadingState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { useApi, useRevision } from "@/hooks/use-api";
import { api, query } from "@/lib/api";
import { date, kw, number, time } from "@/lib/format";
import { IconChartLine, IconPlayerPlay } from "@tabler/icons-react";
import Link from "next/link";
import { useMemo, useState } from "react";
import { Area, CartesianGrid, ComposedChart, Line, XAxis, YAxis } from "recharts";

type Plan = {
  enabled: boolean;
  maximum_power_kw: number | null;
  available_power_kw: number | null;
  computed_at: string | null;
  allocations: {
    station_id: string;
    connector_id: number;
    transaction_id: number | null;
    limit_kw: number;
  }[];
};

type Point = {
  at: string;
  power_kw: number | null;
  limit_kw: number | null;
  assets_power_kw: number | null;
};

const config = {
  power_kw: { label: "Bornes (kW)", color: "var(--chart-3)" },
  assets_power_kw: { label: "Équipements (kW)", color: "var(--chart-1)" },
  limit_kw: { label: "Limite (kW)", color: "var(--chart-5)" },
} satisfies ChartConfig;

const ranges = [
  { value: "6", label: "6 dernières heures" },
  { value: "24", label: "24 dernières heures" },
  { value: "168", label: "7 derniers jours" },
];

export function useWindow(hours: string) {
  return useMemo(() => {
    const to = new Date();
    const from = new Date(to.valueOf() - Number(hours) * 3600000);
    return { from: from.toISOString(), to: to.toISOString() };
  }, [hours]);
}

export function ConsumptionPanel({
  path,
  title,
  description,
}: {
  path: string;
  title: string;
  description: string;
}) {
  const [hours, setHours] = useState("24");
  const range = useWindow(hours);
  const { data, loading, error } = useApi<Point[]>(`${path}${query(range)}`);
  const points = (data ?? []).map((point) => ({ ...point, time: new Date(point.at).valueOf() }));
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
        <CardAction>
          <OptionSelect
            value={hours}
            onChange={setHours}
            options={ranges}
            className="w-48"
            label="Période"
          />
        </CardAction>
      </CardHeader>
      <CardContent>
        {error && !data ? (
          <ErrorState error={error} />
        ) : loading && !data ? (
          <LoadingState rows={3} />
        ) : !points.length ? (
          <EmptyState icon={<IconChartLine />} title="Aucune mesure sur cette période" />
        ) : (
          <ChartContainer config={config} className="aspect-auto h-72 w-full">
            <ComposedChart data={points} margin={{ left: 4, right: 4, top: 8 }}>
              <CartesianGrid vertical={false} />
              <XAxis
                dataKey="time"
                type="number"
                scale="time"
                domain={["dataMin", "dataMax"]}
                tickFormatter={(value: number) =>
                  Number(hours) > 24
                    ? new Date(value).toLocaleDateString("fr-FR", {
                        day: "2-digit",
                        month: "2-digit",
                      })
                    : time(new Date(value).toISOString())
                }
                tickLine={false}
                axisLine={false}
                minTickGap={40}
              />
              <YAxis width={48} tickLine={false} axisLine={false} />
              <ChartTooltip
                content={
                  <ChartTooltipContent
                    labelFormatter={(_, payload) =>
                      date(new Date(Number(payload?.[0]?.payload?.time ?? 0)).toISOString())
                    }
                    formatter={(value, name) => (
                      <span className="flex w-full justify-between gap-4">
                        <span className="text-muted-foreground">
                          {config[name as keyof typeof config]?.label ?? String(name)}
                        </span>
                        <span className="font-medium tabular-nums">{number(value, 1)}</span>
                      </span>
                    )}
                  />
                }
              />
              <ChartLegend content={<ChartLegendContent />} />
              <Area
                dataKey="assets_power_kw"
                type="monotone"
                stackId="load"
                stroke="var(--color-assets_power_kw)"
                fill="var(--color-assets_power_kw)"
                fillOpacity={0.3}
                isAnimationActive={false}
              />
              <Area
                dataKey="power_kw"
                type="monotone"
                stackId="load"
                stroke="var(--color-power_kw)"
                fill="var(--color-power_kw)"
                fillOpacity={0.35}
                isAnimationActive={false}
              />
              <Line
                dataKey="limit_kw"
                type="stepAfter"
                stroke="var(--color-limit_kw)"
                strokeDasharray="5 4"
                strokeWidth={2}
                dot={false}
                isAnimationActive={false}
              />
            </ComposedChart>
          </ChartContainer>
        )}
      </CardContent>
    </Card>
  );
}

export function SmartChargingPanel({
  siteAreaId,
  canTrigger,
}: {
  siteAreaId: string;
  canTrigger: boolean;
}) {
  const [revision, refresh] = useRevision();
  const { data, loading, error } = useApi<Plan>(
    `site-areas/${siteAreaId}/smart-charging`,
    revision,
  );
  const action = useAction();
  const columns: Column<Plan["allocations"][number]>[] = [
    {
      id: "station",
      header: "Borne",
      sort: (row) => row.station_id,
      cell: (row) => (
        <Link
          className="hover:text-primary dark:hover:text-sidebar-primary"
          href={`/stations/${encodeURIComponent(row.station_id)}`}
        >
          {row.station_id} · connecteur {row.connector_id}
        </Link>
      ),
    },
    {
      id: "session",
      header: "Session",
      cell: (row) =>
        row.transaction_id ? (
          <Link
            className="hover:text-primary dark:hover:text-sidebar-primary"
            href={`/transactions/${row.transaction_id}`}
          >
            n° {row.transaction_id}
          </Link>
        ) : (
          "—"
        ),
    },
    {
      id: "limit",
      header: "Puissance allouée",
      align: "right",
      sort: (row) => row.limit_kw,
      cell: (row) => kw(row.limit_kw),
    },
  ];
  return (
    <Card>
      <CardHeader>
        <CardTitle>Plan de recharge intelligente</CardTitle>
        <CardDescription>
          {data?.computed_at
            ? `Calculé le ${date(data.computed_at)}`
            : "Répartition de la puissance entre les sessions."}
        </CardDescription>
        <CardAction>
          {canTrigger && (
            <Button
              variant="outline"
              disabled={action.busy}
              onClick={() =>
                action.run(
                  "trigger",
                  () => api(`site-areas/${siteAreaId}/smart-charging/trigger`, { method: "POST" }),
                  {
                    success: "Nouveau calcul demandé.",
                    onDone: () => setTimeout(refresh, 2000),
                  },
                )
              }
            >
              <IconPlayerPlay data-icon="inline-start" />
              Recalculer
            </Button>
          )}
        </CardAction>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {error && !data ? (
          <ErrorState error={error} onRetry={refresh} />
        ) : loading && !data ? (
          <LoadingState rows={2} />
        ) : data ? (
          <>
            <dl className="grid grid-cols-3 gap-4">
              <div>
                <dt className="text-xs text-muted-foreground">État</dt>
                <dd className="mt-1">
                  <ToneBadge tone={data.enabled ? "success" : "neutral"}>
                    {data.enabled ? "Active" : "Inactive"}
                  </ToneBadge>
                </dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Puissance de la zone</dt>
                <dd className="mt-1 text-lg font-medium tabular-nums">
                  {kw(data.maximum_power_kw)}
                </dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Disponible pour les bornes</dt>
                <dd className="mt-1 text-lg font-medium tabular-nums">
                  {kw(data.available_power_kw)}
                </dd>
              </div>
            </dl>
            {data.allocations.length ? (
              <DataTable
                label="Allocations"
                rows={data.allocations}
                columns={columns}
                rowKey={(row) => `${row.station_id}-${row.connector_id}`}
              />
            ) : (
              <p className="text-sm text-muted-foreground">
                Aucune session à piloter actuellement.
              </p>
            )}
          </>
        ) : null}
      </CardContent>
    </Card>
  );
}
