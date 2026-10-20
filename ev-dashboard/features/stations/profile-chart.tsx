"use client";

import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import { number } from "@/lib/format";
import { Area, AreaChart, CartesianGrid, XAxis, YAxis } from "recharts";
import type { OcppChargingProfile } from "./station-types";

const config = { limit: { label: "Limite", color: "var(--chart-3)" } } satisfies ChartConfig;

function clock(seconds: number) {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`;
}

export function profilePoints(profile: OcppChargingProfile) {
  const schedule = profile.chargingSchedule;
  const unit = schedule?.chargingRateUnit ?? "W";
  const periods = [...(schedule?.chargingSchedulePeriod ?? [])].sort(
    (left, right) => left.startPeriod - right.startPeriod,
  );
  const end =
    schedule?.duration ??
    (profile.recurrencyKind === "Daily" || profile.chargingProfileKind === "Recurring"
      ? 86400
      : Math.max(3600, (periods.at(-1)?.startPeriod ?? 0) + 3600));
  const value = (limit: number) => (unit === "W" ? limit / 1000 : limit);
  const points = periods.map((period) => ({
    start: period.startPeriod,
    limit: value(period.limit),
  }));
  if (points.length) {
    points.push({ start: end, limit: points[points.length - 1].limit });
  }
  const step = Math.max(1, Math.ceil(end / 3600 / 6)) * 3600;
  const ticks = Array.from({ length: Math.floor(end / step) + 1 }, (_, index) => index * step);
  return { points, unit: unit === "W" ? "kW" : "A", ticks };
}

export function ProfileChart({
  profile,
  className = "h-48 w-full",
}: {
  profile: OcppChargingProfile;
  className?: string;
}) {
  const { points, unit, ticks } = profilePoints(profile);
  if (!points.length) {
    return <p className="text-sm text-muted-foreground">Aucune période définie.</p>;
  }
  return (
    <ChartContainer config={config} className={`aspect-auto ${className}`}>
      <AreaChart data={points} margin={{ left: 0, right: 12, top: 8 }}>
        <CartesianGrid vertical={false} />
        <XAxis
          dataKey="start"
          type="number"
          domain={[0, "dataMax"]}
          ticks={ticks}
          tickFormatter={clock}
          tickLine={false}
          axisLine={false}
        />
        <YAxis
          width={48}
          tickLine={false}
          axisLine={false}
          tickFormatter={(value: number) => `${number(value, 1)}`}
          unit={` ${unit}`}
        />
        <ChartTooltip
          content={
            <ChartTooltipContent
              labelFormatter={(_, payload) => clock(Number(payload?.[0]?.payload?.start ?? 0))}
              formatter={(value) => `${number(value, 1)} ${unit}`}
            />
          }
        />
        <Area
          dataKey="limit"
          type="stepAfter"
          stroke="var(--color-limit)"
          fill="var(--color-limit)"
          fillOpacity={0.25}
          strokeWidth={2}
          isAnimationActive={false}
        />
      </AreaChart>
    </ChartContainer>
  );
}
