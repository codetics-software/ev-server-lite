import { CodeticsMark } from "@/components/codetics-mark";
import Link from "next/link";

export function AuthIntro() {
  return (
    <aside className="auth-intro">
      <Link href="https://codetics.fr" className="flex items-center gap-3 text-lg font-semibold">
        <CodeticsMark />
        Codetics
      </Link>
      <div className="flex max-w-sm flex-col gap-6">
        <div>
          <h1>Supervisez votre réseau de recharge.</h1>
          <p>Vos bornes, vos sessions et vos commandes à distance dans un même espace.</p>
        </div>
        <dl className="flex flex-col gap-5 text-sm">
          <div>
            <dt className="font-medium">Gestion des bornes</dt>
            <dd className="mt-1 text-muted-foreground">
              Connexions, état des connecteurs et commandes OCPP.
            </dd>
          </div>
          <div>
            <dt className="font-medium">Historique des sessions</dt>
            <dd className="mt-1 text-muted-foreground">
              Autorisations, horaires et énergie mesurée.
            </dd>
          </div>
          <div>
            <dt className="font-medium">Votre réseau</dt>
            <dd className="mt-1 text-muted-foreground">
              Sites, utilisateurs et accès à la recharge.
            </dd>
          </div>
        </dl>
      </div>
      <span className="text-xs text-muted-foreground">Kevin Vavelin · Codetics</span>
    </aside>
  );
}
