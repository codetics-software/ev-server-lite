"use client";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ToneBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { api } from "@/lib/api";
import type { CommandReceipt } from "@/lib/api-types";
import { date } from "@/lib/format";
import { t } from "@/lib/i18n";
import type { ReactNode } from "react";
import { certificateExpiry, type Iso15118Capabilities } from "./station-security";
import type { Station } from "./station-types";

function Line({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-1 border-b py-3 last:border-b-0 sm:grid-cols-[14rem_1fr]">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="min-w-0 text-sm break-words">{children}</dd>
    </div>
  );
}

export function StationIso15118({
  station,
  iso,
  admin,
  onQueued,
}: {
  station: Station;
  iso: Iso15118Capabilities | null | undefined;
  admin: boolean;
  onQueued: (message: string) => void;
}) {
  const action = useAction();
  const path = `stations/${encodeURIComponent(station.id)}/iso15118`;
  const cpo = iso?.cpo_chain?.[0];
  const v2gExpiry = certificateExpiry(station.v2g_certificate_expires_at);

  function run(name: string, success: string, failure: string) {
    return action.run(name, () => api<CommandReceipt[]>(`${path}/${name}`, { method: "POST" }), {
      success,
      failure,
      onDone: () => onQueued(success),
    });
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("ISO 15118 · Plug & Charge")}</CardTitle>
        <CardDescription>
          {t(
            "Certificats V2G de la borne, racines des opérateurs de mobilité et contrats des véhicules.",
          )}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {!iso?.enabled ? (
          <p className="rounded-lg border border-dashed p-3 text-sm text-muted-foreground">
            {t(
              "ISO 15118 n’est pas activé sur ce serveur. Déposez les certificats V2G dans le dossier des certificats puis définissez OCPP_ISO15118_ENABLED=true, comme décrit dans la documentation de déploiement.",
            )}
          </p>
        ) : (
          <>
            <dl>
              <Line label={t("Sous-autorité CPO")}>
                {cpo ? (
                  <>
                    <span className="block font-mono text-xs">{cpo.subject}</span>
                    <span className="text-muted-foreground">
                      {t("Expire le {date}", { date: date(cpo.expires_at) })}
                    </span>
                  </>
                ) : (
                  "—"
                )}
              </Line>
              <Line label={t("Racines de confiance")}>
                {t("{v2g} racine(s) V2G, {mo} racine(s) d’opérateurs de mobilité", {
                  v2g: iso.v2g_roots?.length ?? 0,
                  mo: iso.mo_roots?.length ?? 0,
                })}
              </Line>
              <Line label={t("Service de certificats de contrat")}>
                <ToneBadge tone={iso.contract_service ? "success" : "neutral"}>
                  {iso.contract_service ? t("Configuré") : t("Non configuré")}
                </ToneBadge>
              </Line>
              <Line label={t("Certificat V2G de la borne")}>
                {station.v2g_certificate_subject ? (
                  <>
                    <span className="block font-mono text-xs">
                      {station.v2g_certificate_subject}
                    </span>
                    {v2gExpiry && <ToneBadge tone={v2gExpiry.tone}>{v2gExpiry.label}</ToneBadge>}
                  </>
                ) : (
                  t("Aucun certificat V2G émis par ce serveur")
                )}
              </Line>
            </dl>
            {admin && (
              <div className="flex flex-wrap gap-2">
                <Button
                  variant="outline"
                  disabled={action.busy}
                  onClick={() =>
                    run(
                      "install-roots",
                      t("Installation des racines V2G et MO demandée à la borne."),
                      t("L’installation des racines n’a pas été envoyée"),
                    )
                  }
                >
                  {t("Installer les racines V2G et MO")}
                </Button>
                <Button
                  variant="outline"
                  disabled={action.busy}
                  onClick={() =>
                    run(
                      "request-certificate",
                      t("La borne va envoyer une demande de certificat V2G."),
                      t("La demande de certificat n’a pas été envoyée"),
                    )
                  }
                >
                  {t("Demander un certificat V2G")}
                </Button>
                <Button
                  variant="ghost"
                  disabled={action.busy}
                  onClick={() =>
                    run(
                      "installed",
                      t("Lecture des certificats ISO 15118 demandée à la borne."),
                      t("La lecture n’a pas été envoyée"),
                    )
                  }
                >
                  {t("Lire les certificats ISO 15118")}
                </Button>
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
