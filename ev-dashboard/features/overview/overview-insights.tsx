"use client";

import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import { ToneBadge } from "@/features/shared/status-badge";
import { useWorkspaceSession } from "@/features/workspace/workspace-context";
import { useApi } from "@/hooks/use-api";
import { monthLabels, number } from "@/lib/format";
import { IconArrowUpRight } from "@tabler/icons-react";
import Link from "next/link";
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts";

const config = { value: { label: "Énergie (kWh)", color: "var(--chart-3)" } } satisfies ChartConfig;

export function YearConsumption({ revision }: { revision: number }) {
  const year = new Date().getFullYear();
  const { data } = useApi<{ months: number[]; total: number }>(
    `statistics/consumption?year=${year}&groupBy=station`,
    revision,
  );
  if (!data) {
    return null;
  }
  const rows = monthLabels.map((month, index) => ({ month, value: data.months[index] ?? 0 }));
  return (
    <section className="operational-panel" aria-labelledby="consumption-heading">
      <div className="flex items-baseline justify-between gap-3">
        <h2 id="consumption-heading">Énergie délivrée en {year}</h2>
        <Link href="/statistics" className="text-xs text-muted-foreground hover:text-foreground">
          {number(data.total, 0)} kWh · statistiques
        </Link>
      </div>
      <ChartContainer config={config} className="aspect-auto h-56 w-full">
        <BarChart data={rows} margin={{ left: 0, right: 0, top: 8 }}>
          <CartesianGrid vertical={false} />
          <XAxis dataKey="month" tickLine={false} axisLine={false} />
          <YAxis
            width={44}
            tickLine={false}
            axisLine={false}
            tickFormatter={(value: number) => number(value, 0)}
          />
          <ChartTooltip
            content={<ChartTooltipContent formatter={(value) => `${number(value, 1)} kWh`} />}
          />
          <Bar
            dataKey="value"
            fill="var(--color-value)"
            radius={[4, 4, 0, 0]}
            isAnimationActive={false}
          />
        </BarChart>
      </ChartContainer>
    </section>
  );
}

function Anomaly({ href, label, count }: { href: string; label: string; count: number | null }) {
  return (
    <Link className="operation-link" href={href}>
      <div>
        <strong>{label}</strong>
        <span>{count === null ? "Indisponible" : count ? "À examiner" : "Aucune anomalie"}</span>
      </div>
      <span className="flex items-center gap-2">
        {count !== null && <ToneBadge tone={count ? "danger" : "success"}>{count}</ToneBadge>}
        <IconArrowUpRight aria-hidden="true" />
      </span>
    </Link>
  );
}

export function Anomalies({ revision }: { revision: number }) {
  const { components } = useWorkspaceSession();
  const stations = useApi<unknown[]>("stations/in-error", revision);
  const sessions = useApi<{ total: number } | unknown[]>(
    "transactions/in-error?offset=0&limit=1",
    revision,
  );
  const sessionCount = sessions.data
    ? Array.isArray(sessions.data)
      ? sessions.data.length
      : sessions.data.total
    : null;
  const assets = useApi<unknown[]>(components.assets ? "assets/in-error" : null, revision);
  return (
    <section className="operational-panel" aria-labelledby="anomalies-heading">
      <h2 id="anomalies-heading">Anomalies</h2>
      <Anomaly
        href="/stations"
        label="Bornes en erreur"
        count={stations.data ? stations.data.length : null}
      />
      <Anomaly href="/transactions" label="Sessions en erreur" count={sessionCount} />
      {components.assets && (
        <Anomaly
          href="/assets"
          label="Équipements en erreur"
          count={assets.data ? assets.data.length : null}
        />
      )}
    </section>
  );
}
