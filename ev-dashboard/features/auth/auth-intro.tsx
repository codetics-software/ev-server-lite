import { CodeticsMark } from "@/components/codetics-mark";
import Link from "next/link";
import { t } from "@/lib/i18n";

export function AuthIntro() {
  return (
    <aside className="auth-intro">
      <Link href="https://codetics.fr" className="flex items-center gap-3 text-lg font-semibold">
        <CodeticsMark />
        Codetics
      </Link>
      <div className="flex max-w-sm flex-col gap-6">
        <div>
          <h1>{t("Supervisez votre réseau de recharge.")}</h1>
          <p>{t("Vos bornes, vos sessions et vos commandes à distance dans un même espace.")}</p>
        </div>
        <dl className="flex flex-col gap-5 text-sm">
          <div>
            <dt className="font-medium">{t("Gestion des bornes")}</dt>
            <dd className="mt-1 text-muted-foreground">
              {t("Connexions, état des connecteurs et commandes OCPP.")}
            </dd>
          </div>
          <div>
            <dt className="font-medium">{t("Historique des sessions")}</dt>
            <dd className="mt-1 text-muted-foreground">
              {t("Autorisations, horaires et énergie mesurée.")}
            </dd>
          </div>
          <div>
            <dt className="font-medium">{t("Votre réseau")}</dt>
            <dd className="mt-1 text-muted-foreground">
              {t("Sites, utilisateurs et accès à la recharge.")}
            </dd>
          </div>
        </dl>
      </div>
      <span className="text-xs text-muted-foreground">Kevin Vavelin · Codetics</span>
    </aside>
  );
}
