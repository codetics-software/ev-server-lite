import type { NetworkOverview } from "./overview-types";

export function OverviewMetrics({ data }: { data: NetworkOverview }) {
  const energy = Number(data.transactions.energy_kwh).toLocaleString("en-GB", {
    maximumFractionDigits: 1,
  });
  const metrics = [
    {
      label: "Charging stations",
      value: data.stations.total,
      detail: `${data.stations.online} connected`,
    },
    {
      label: "Active sessions",
      value: data.transactions.active,
      detail: `${data.transactions.total} sessions recorded`,
    },
    {
      label: "Energy delivered",
      value: energy,
      unit: "kWh",
      detail: "Completed sessions · all time",
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
