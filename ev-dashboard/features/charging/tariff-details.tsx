import type { Tariff } from "./portal-types";

export function TariffDetails({ tariff }: { tariff: Tariff }) {
  const price = (value: number) =>
    new Intl.NumberFormat("fr-FR", {
      style: "currency",
      currency: tariff.currency,
      maximumFractionDigits: 6,
    }).format(value);
  return (
    <dl className="divide-y text-sm">
      {[
        ["Énergie", tariff.energyPrice, "/ kWh"],
        ["Durée de recharge", tariff.timePrice, "/ heure"],
        ["Frais de session", tariff.flatPrice, ""],
      ].map(
        ([label, value, unit]) =>
          Number(value) > 0 && (
            <div key={String(label)} className="flex justify-between gap-4 py-3">
              <dt>{label}</dt>
              <dd className="text-right tabular-nums">
                {price(Number(value))} {unit}
              </dd>
            </div>
          ),
      )}
    </dl>
  );
}
