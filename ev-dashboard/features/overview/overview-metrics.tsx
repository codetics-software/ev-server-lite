import type { NetworkOverview } from "./overview-types";
import { getIntlLocale, t, tp } from "@/lib/i18n";

export function OverviewMetrics({ data }: { data: NetworkOverview }) {
  const energy = Number(data.transactions.energy_kwh).toLocaleString(getIntlLocale(), {
    maximumFractionDigits: 1,
  });
  const metrics = [
    {
      label: t("Bornes de recharge"),
      value: data.stations.total,
      detail: t("{count} connectées", { count: data.stations.online }),
    },
    {
      label: t("Sessions en cours"),
      value: data.transactions.active,
      detail: tp(
        data.transactions.total,
        "{count} session enregistrée",
        "{count} sessions enregistrées",
      ),
    },
    {
      label: t("Énergie délivrée"),
      value: energy,
      unit: "kWh",
      detail: t("Sessions terminées · depuis l’origine"),
    },
  ];

  return (
    <dl className="overview-metrics">
      {metrics.map(({ label, value, unit, detail }) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>
            {value}
            <span>{unit}</span>
          </dd>
          <p>{detail}</p>
        </div>
      ))}
    </dl>
  );
}
