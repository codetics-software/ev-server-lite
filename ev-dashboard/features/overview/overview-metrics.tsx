import type { NetworkOverview } from "./overview-types";

export function OverviewMetrics({ data }: { data: NetworkOverview }) {
  const energy = Number(data.transactions.energy_kwh).toLocaleString("fr-FR", {
    maximumFractionDigits: 1,
  });
  const metrics = [
    {
      label: "Bornes de recharge",
      value: data.stations.total,
      detail: `${data.stations.online} connectées`,
    },
    {
      label: "Sessions en cours",
      value: data.transactions.active,
      detail: `${data.transactions.total} sessions enregistrées`,
    },
    {
      label: "Énergie délivrée",
      value: energy,
      unit: "kWh",
      detail: "Sessions terminées · depuis l’origine",
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
