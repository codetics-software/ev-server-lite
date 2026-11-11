import type { Tariff } from "./portal-types";
import { getIntlLocale, t } from "@/lib/i18n";

export function TariffDetails({ tariff }: { tariff: Tariff }) {
  const price = (value: number) =>
    new Intl.NumberFormat(getIntlLocale(), {
      style: "currency",
      currency: tariff.currency,
      maximumFractionDigits: 6,
    }).format(value);
  return (
    <dl className="divide-y text-sm">
      {[
        [t("Énergie"), tariff.energyPrice, "/ kWh"],
        [t("Durée de recharge"), tariff.timePrice, "/ heure"],
        [t("Frais de session"), tariff.flatPrice, ""],
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
