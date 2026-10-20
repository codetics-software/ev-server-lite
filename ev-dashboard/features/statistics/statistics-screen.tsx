"use client";

import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldLabel } from "@/components/ui/field";
import {
  Table,
  TableBody,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { ExportButton } from "@/features/shared/csv-actions";
import { EntityPicker } from "@/features/shared/entity-picker";
import { OptionSelect } from "@/features/shared/form-fields";
import { PageHeader } from "@/features/shared/page-header";
import { EmptyState, ErrorState, LoadingState } from "@/features/shared/states";
import { isAdmin, isStaff, useWorkspaceSession } from "@/features/workspace/workspace-context";
import { useApi } from "@/hooks/use-api";
import { query } from "@/lib/api";
import { money, monthLabels, number } from "@/lib/format";
import { IconChartBar } from "@tabler/icons-react";
import { useState } from "react";
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts";

const pressed =
  "aria-pressed:border-primary aria-pressed:bg-primary aria-pressed:text-primary-foreground";

type Metric = "consumption" | "usage" | "inactivity" | "sessions" | "pricing";

type Statistics = {
  metric: Metric;
  year: number;
  unit: string;
  currency?: string;
  groups: { key: string; label: string; months: number[]; total: number }[];
  months: number[];
  total: number;
};

const metrics: { value: Metric; label: string; description: string }[] = [
  { value: "consumption", label: "Consommation", description: "Énergie délivrée par mois (kWh)." },
  { value: "usage", label: "Utilisation", description: "Durée de branchement par mois (heures)." },
  {
    value: "inactivity",
    label: "Inactivité",
    description: "Temps branché sans recharger par mois (heures).",
  },
  { value: "sessions", label: "Sessions", description: "Nombre de sessions par mois." },
  { value: "pricing", label: "Revenus", description: "Montant facturé par mois." },
];

const palette = [
  "var(--chart-1)",
  "var(--chart-2)",
  "var(--chart-3)",
  "var(--chart-4)",
  "var(--chart-5)",
];

export function StatisticsScreen() {
  const { user, components } = useWorkspaceSession();
  const staff = isStaff(user);
  const currentYear = new Date().getFullYear();
  const [metric, setMetric] = useState<Metric>("consumption");
  const [year, setYear] = useState(String(currentYear));
  const [groupBy, setGroupBy] = useState("station");
  const [siteId, setSiteId] = useState("");
  const [siteAreaId, setSiteAreaId] = useState("");
  const [stationId, setStationId] = useState("");
  const [userId, setUserId] = useState("");
  const years = useApi<number[]>("statistics/years");
  const parameters = {
    year,
    groupBy,
    siteId,
    siteAreaId,
    stationId,
    userId,
  };
  const { data, loading, error } = useApi<Statistics>(`statistics/${metric}${query(parameters)}`);
  const yearOptions = (years.data?.length ? years.data : [currentYear]).map((value) => ({
    value: String(value),
    label: String(value),
  }));
  const groupOptions = [
    { value: "station", label: "Par borne" },
    ...(components.organization
      ? [
          { value: "site", label: "Par site" },
          { value: "site-area", label: "Par zone" },
        ]
      : []),
    ...(isAdmin(user) ? [{ value: "user", label: "Par utilisateur" }] : []),
  ];
  const format = (value: unknown) =>
    metric === "pricing"
      ? money(value, data?.currency)
      : number(value, metric === "sessions" ? 0 : 1);
  const current = metrics.find((item) => item.value === metric)!;

  return (
    <>
      <PageHeader
        title="Statistiques"
        description={
          staff
            ? "Consommation, utilisation et revenus de votre réseau, mois par mois."
            : "Votre consommation et vos dépenses de recharge, mois par mois."
        }
        actions={
          <ExportButton
            path={`statistics/${metric}/export`}
            parameters={parameters}
            filename={`statistiques-${metric}-${year}`}
          />
        }
      />
      <ToggleGroup
        value={[metric]}
        onValueChange={(value) => {
          const next = value[0] as Metric | undefined;
          if (next) {
            setMetric(next);
          }
        }}
        variant="outline"
        className="flex-wrap"
        aria-label="Indicateur"
      >
        {metrics
          .filter((item) => item.value !== "pricing" || components.pricing)
          .map((item) => (
            <ToggleGroupItem key={item.value} value={item.value} className={pressed}>
              {item.label}
            </ToggleGroupItem>
          ))}
      </ToggleGroup>
      <div className="flex flex-wrap items-end gap-3">
        <Field className="w-32">
          <FieldLabel htmlFor="stats-year" className="text-xs text-muted-foreground">
            Année
          </FieldLabel>
          <OptionSelect id="stats-year" value={year} onChange={setYear} options={yearOptions} />
        </Field>
        {staff && (
          <Field className="w-44">
            <FieldLabel htmlFor="stats-group" className="text-xs text-muted-foreground">
              Regroupement
            </FieldLabel>
            <OptionSelect
              id="stats-group"
              value={groupBy}
              onChange={setGroupBy}
              options={groupOptions}
            />
          </Field>
        )}
        {staff && components.organization && (
          <>
            <Field className="w-full sm:w-48">
              <FieldLabel htmlFor="stats-site" className="text-xs text-muted-foreground">
                Site
              </FieldLabel>
              <EntityPicker
                id="stats-site"
                source="sites"
                value={siteId}
                onChange={(value) => {
                  setSiteId(value);
                  setSiteAreaId("");
                }}
                placeholder="Tous"
              />
            </Field>
            <Field className="w-full sm:w-48">
              <FieldLabel htmlFor="stats-area" className="text-xs text-muted-foreground">
                Zone
              </FieldLabel>
              <EntityPicker
                id="stats-area"
                source="site-areas"
                filters={{ siteId: siteId || undefined }}
                value={siteAreaId}
                onChange={setSiteAreaId}
                placeholder="Toutes"
              />
            </Field>
          </>
        )}
        {staff && (
          <Field className="w-full sm:w-48">
            <FieldLabel htmlFor="stats-station" className="text-xs text-muted-foreground">
              Borne
            </FieldLabel>
            <EntityPicker
              id="stats-station"
              source="stations"
              value={stationId}
              onChange={setStationId}
              placeholder="Toutes"
            />
          </Field>
        )}
        {isAdmin(user) && (
          <Field className="w-full sm:w-48">
            <FieldLabel htmlFor="stats-user" className="text-xs text-muted-foreground">
              Utilisateur
            </FieldLabel>
            <EntityPicker
              id="stats-user"
              source="users"
              value={userId}
              onChange={setUserId}
              placeholder="Tous"
            />
          </Field>
        )}
      </div>
      {error && !data ? (
        <ErrorState error={error} />
      ) : loading && !data ? (
        <LoadingState rows={6} />
      ) : !data || !data.groups.length ? (
        <EmptyState
          icon={<IconChartBar />}
          title="Aucune donnée pour cette période"
          description="Les statistiques sont calculées à partir des sessions terminées."
        />
      ) : (
        <StatisticsView data={data} label={current} format={format} />
      )}
    </>
  );
}

function StatisticsView({
  data,
  label,
  format,
}: {
  data: Statistics;
  label: { label: string; description: string };
  format: (value: unknown) => string;
}) {
  const sorted = [...data.groups].sort((left, right) => right.total - left.total);
  const top = sorted.slice(0, 4);
  const rest = sorted.slice(4);
  const series = [
    ...top.map((group, index) => ({ key: `g${index}`, label: group.label, months: group.months })),
    ...(rest.length
      ? [
          {
            key: "other",
            label: `Autres (${rest.length})`,
            months: monthLabels.map((_, month) =>
              rest.reduce((sum, group) => sum + (group.months[month] ?? 0), 0),
            ),
          },
        ]
      : []),
  ];
  const config: ChartConfig = Object.fromEntries(
    series.map((item, index) => [
      item.key,
      { label: item.label, color: palette[index % palette.length] },
    ]),
  );
  const rows = monthLabels.map((month, index) => ({
    month,
    ...Object.fromEntries(series.map((item) => [item.key, item.months[index] ?? 0])),
  }));

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle>
            {label.label} {data.year} · {format(data.total)}{" "}
            {data.unit && data.unit !== data.currency ? data.unit : ""}
          </CardTitle>
          <CardDescription>{label.description}</CardDescription>
        </CardHeader>
        <CardContent>
          <ChartContainer config={config} className="aspect-auto h-80 w-full">
            <BarChart data={rows} margin={{ left: 4, right: 4, top: 8 }}>
              <CartesianGrid vertical={false} />
              <XAxis dataKey="month" tickLine={false} axisLine={false} />
              <YAxis
                width={56}
                tickLine={false}
                axisLine={false}
                tickFormatter={(value: number) => number(value, 0)}
              />
              <ChartTooltip
                content={
                  <ChartTooltipContent
                    formatter={(value, name) => (
                      <span className="flex w-full justify-between gap-4">
                        <span className="text-muted-foreground">
                          {String(config[String(name)]?.label ?? name)}
                        </span>
                        <span className="font-medium tabular-nums">{format(value)}</span>
                      </span>
                    )}
                  />
                }
              />
              <ChartLegend content={<ChartLegendContent />} />
              {series.map((item, index) => (
                <Bar
                  key={item.key}
                  dataKey={item.key}
                  stackId="total"
                  fill={`var(--color-${item.key})`}
                  radius={index === series.length - 1 ? [4, 4, 0, 0] : 0}
                  isAnimationActive={false}
                />
              ))}
            </BarChart>
          </ChartContainer>
        </CardContent>
      </Card>
      <Table aria-label={`${label.label} par mois`}>
        <TableHeader>
          <TableRow>
            <TableHead className="sticky left-0 bg-card">Regroupement</TableHead>
            {monthLabels.map((month) => (
              <TableHead key={month} className="text-right">
                {month}
              </TableHead>
            ))}
            <TableHead className="text-right">Total</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {sorted.map((group) => (
            <TableRow key={group.key}>
              <TableCell className="sticky left-0 max-w-56 truncate bg-card font-medium">
                {group.label}
              </TableCell>
              {group.months.map((value, index) => (
                <TableCell key={index} className="text-right tabular-nums">
                  {value ? format(value) : "—"}
                </TableCell>
              ))}
              <TableCell className="text-right font-medium tabular-nums">
                {format(group.total)}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
        <TableFooter>
          <TableRow>
            <TableCell className="sticky left-0 bg-muted">Total</TableCell>
            {data.months.map((value, index) => (
              <TableCell key={index} className="text-right tabular-nums">
                {format(value)}
              </TableCell>
            ))}
            <TableCell className="text-right tabular-nums">{format(data.total)}</TableCell>
          </TableRow>
        </TableFooter>
      </Table>
    </>
  );
}
