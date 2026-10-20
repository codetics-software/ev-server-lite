"use client";

import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import { number, time } from "@/lib/format";
import { Area, CartesianGrid, ComposedChart, Line, XAxis, YAxis } from "recharts";
import type { Consumption } from "./session-types";

const config = {
  power_kw: { label: "Puissance (kW)", color: "var(--chart-3)" },
  energy_kwh: { label: "Énergie (kWh)", color: "var(--chart-5)" },
  soc: { label: "Batterie (%)", color: "var(--chart-2)" },
} satisfies ChartConfig;

export function ConsumptionChart({ points }: { points: Consumption[] }) {
  const data = points.map((point) => ({ ...point, time: new Date(point.at).valueOf() }));
  const hasSoc = points.some((point) => point.soc != null);
  return (
    <ChartContainer config={config} className="aspect-auto h-72 w-full">
      <ComposedChart data={data} margin={{ left: 4, right: 4, top: 8 }}>
        <CartesianGrid vertical={false} />
        <XAxis
          dataKey="time"
          type="number"
          scale="time"
          domain={["dataMin", "dataMax"]}
          tickFormatter={(value: number) => time(new Date(value).toISOString())}
          tickLine={false}
          axisLine={false}
          minTickGap={40}
        />
        <YAxis yAxisId="power" width={40} tickLine={false} axisLine={false} />
        <YAxis yAxisId="energy" orientation="right" width={40} tickLine={false} axisLine={false} />
        {hasSoc && <YAxis yAxisId="soc" hide domain={[0, 100]} />}
        <ChartTooltip
          content={
            <ChartTooltipContent
              labelFormatter={(_, payload) =>
                time(new Date(Number(payload?.[0]?.payload?.time ?? 0)).toISOString())
              }
              formatter={(value, name) => (
                <span className="flex w-full justify-between gap-4">
                  <span className="text-muted-foreground">
                    {config[name as keyof typeof config]?.label ?? String(name)}
                  </span>
                  <span className="font-medium tabular-nums">{number(value, 2)}</span>
                </span>
              )}
            />
          }
        />
        <ChartLegend content={<ChartLegendContent />} />
        <Area
          yAxisId="power"
          dataKey="power_kw"
          type="monotone"
          stroke="var(--color-power_kw)"
          fill="var(--color-power_kw)"
          fillOpacity={0.2}
          strokeWidth={2}
          isAnimationActive={false}
        />
        <Line
          yAxisId="energy"
          dataKey="energy_kwh"
          type="monotone"
          stroke="var(--color-energy_kwh)"
          strokeWidth={2}
          dot={false}
          isAnimationActive={false}
        />
        {hasSoc && (
          <Line
            yAxisId="soc"
            dataKey="soc"
            type="monotone"
            stroke="var(--color-soc)"
            strokeDasharray="4 4"
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
          />
        )}
      </ComposedChart>
    </ChartContainer>
  );
}
