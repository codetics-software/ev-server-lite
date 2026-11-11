"use client";

import { Alert, AlertAction, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ConfirmButton } from "@/features/shared/confirm-button";
import { DataTable, type Column } from "@/features/shared/data-table";
import { OptionSelect } from "@/features/shared/form-fields";
import { CopyField } from "@/features/shared/secret-reveal";
import { EmptyState, ErrorState, LoadingState } from "@/features/shared/states";
import { StateBadge, ToneBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { useWorkspaceSession } from "@/features/workspace/workspace-context";
import { useApi, useRevision } from "@/hooks/use-api";
import { api } from "@/lib/api";
import type { Row } from "@/lib/api-types";
import { date } from "@/lib/format";
import {
  IconCertificate,
  IconHistory,
  IconKey,
  IconPin,
  IconPinnedOff,
  IconRefresh,
} from "@tabler/icons-react";
import { useState, type ReactNode } from "react";
import { sendCommand } from "./station-actions";
import {
  certificateExpiry,
  ocppUrl,
  profileLabel,
  type SecurityCapabilities,
  type SecurityChange,
} from "./station-security";
import {
  certificateCommand,
  InstallCertificate,
  ProfileChange,
  SecretDialog,
  unpinStation,
  type RevealedSecret,
} from "./station-security-dialogs";
import type { ProvisionedStation, Station } from "./station-types";
import { t, translated } from "@/lib/i18n";

function Detail({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-1 border-b py-2.5 text-sm last:border-0 sm:grid-cols-[12rem_1fr]">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="min-w-0 break-words">{children}</dd>
    </div>
  );
}

export function StationSecurityPanel({
  station,
  capabilities,
  capabilitiesError,
  capabilitiesLoading,
  onRetryCapabilities,
  admin,
  operate,
  onChanged,
  onPin,
  onShowCommands,
}: {
  station: Station;
  capabilities: SecurityCapabilities | null;
  capabilitiesError: Error | null;
  capabilitiesLoading: boolean;
  onRetryCapabilities: () => void;
  admin: boolean;
  operate: boolean;
  onChanged: () => void;
  onPin: (initial: string) => void;
  onShowCommands: () => void;
}) {
  const [queued, setQueued] = useState<string | null>(null);
  const [secret, setSecret] = useState<RevealedSecret | null>(null);
  const action = useAction();
  const path = `stations/${encodeURIComponent(station.id)}`;
  const expiry = certificateExpiry(station.certificate_expires_at);
  const pending = station.password_rotation_pending === true;

  function changed(change: SecurityChange) {
    if (change.command_id) {
      setQueued(change.message);
    }
    onChanged();
  }

  function queuedCommand(message: string) {
    setQueued(message);
    onChanged();
  }

  if (capabilitiesError && !capabilities) {
    return <ErrorState error={capabilitiesError} onRetry={onRetryCapabilities} />;
  }
  if (capabilitiesLoading && !capabilities) {
    return <LoadingState rows={4} />;
  }

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      {queued && (
        <Alert>
          <IconHistory />
          <AlertTitle>{t("Commande en file d’attente")}</AlertTitle>
          <AlertDescription>{queued}</AlertDescription>
          <AlertAction>
            <Button size="sm" variant="outline" onClick={onShowCommands}>
              {t("Voir les commandes")}
            </Button>
          </AlertAction>
        </Alert>
      )}
      <Card>
        <CardHeader>
          <CardTitle>{t("Profil de sécurité")}</CardTitle>
          <CardDescription>
            {t(
              "La borne peut se connecter avec un profil plus fort que celui configuré, jamais plus faible.",
            )}
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <dl>
            <Detail label={t("Profil configuré")}>
              <span className="flex flex-wrap items-center gap-2">
                {profileLabel(station.security_profile ?? 1)}
                {station.security_profile === 0 && (
                  <ToneBadge tone="danger">{t("Sans authentification")}</ToneBadge>
                )}
              </span>
            </Detail>
            <Detail label={t("Dernière connexion")}>
              {station.connection_security_profile == null
                ? t("Jamais connectée")
                : `${profileLabel(station.connection_security_profile)} · ${station.connected ? t("connectée") : t("hors ligne")}`}
            </Detail>
            {station.requested_security_profile != null && (
              <Detail label={t("Changement en attente")}>
                <ToneBadge tone="progress">
                  {t("Profil {current} → {requested} en attente", {
                    current: station.security_profile ?? 1,
                    requested: station.requested_security_profile,
                  })}
                </ToneBadge>
                <span className="mt-1 block text-muted-foreground">
                  {t(
                    "Appliqué dès que la borne se reconnecte avec ce profil ; l’ancien reste accepté d’ici là.",
                  )}
                </span>
              </Detail>
            )}
            <Detail label={t("Empreinte épinglée")}>
              {station.pinned_certificate_fingerprint ? (
                <CopyField
                  value={station.pinned_certificate_fingerprint}
                  label={t("Empreinte épinglée")}
                />
              ) : (
                t("Aucune")
              )}
            </Detail>
          </dl>
          {admin ? (
            <div className="flex flex-wrap gap-2">
              <ProfileChange station={station} capabilities={capabilities} onChanged={changed} />
              <Button
                variant="outline"
                onClick={() => onPin(station.pinned_certificate_fingerprint ?? "")}
              >
                <IconPin data-icon="inline-start" />
                {station.pinned_certificate_fingerprint
                  ? t("Modifier l’empreinte")
                  : t("Épingler une empreinte")}
              </Button>
              {station.pinned_certificate_fingerprint && (
                <ConfirmButton
                  title={t("Retirer l’empreinte épinglée ?")}
                  description={t(
                    "Un certificat client dont le nom commun diffère de l’identifiant de la borne ne sera plus accepté.",
                  )}
                  confirmLabel={t("Retirer")}
                  variant="outline"
                  disabled={action.busy}
                  onConfirm={() =>
                    action.run("unpin", () => unpinStation(station), {
                      success: t("Empreinte retirée."),
                      failure: t("L’empreinte n’a pas été retirée"),
                      onDone: changed,
                    })
                  }
                >
                  <IconPinnedOff data-icon="inline-start" />
                  {t("Retirer l’empreinte")}
                </ConfirmButton>
              )}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">
              {t("Seul un administrateur peut modifier la sécurité de la borne.")}
            </p>
          )}
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>{t("Certificat client")}</CardTitle>
          <CardDescription>
            {t("Dernier certificat présenté par la borne ou signé par le serveur.")}
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {station.certificate_fingerprint || station.certificate_subject ? (
            <dl>
              <Detail label={t("Sujet")}>
                <span className="break-all">{station.certificate_subject ?? "—"}</span>
              </Detail>
              <Detail label={t("Expiration")}>
                <span className="flex flex-wrap items-center gap-2">
                  {station.certificate_expires_at
                    ? t("Expire le {date}", { date: date(station.certificate_expires_at) })
                    : "—"}
                  {expiry && <ToneBadge tone={expiry.tone}>{expiry.label}</ToneBadge>}
                </span>
              </Detail>
              {station.certificate_fingerprint && (
                <Detail label={t("Empreinte SHA-256")}>
                  <CopyField
                    value={station.certificate_fingerprint}
                    label={t("Empreinte SHA-256")}
                  />
                </Detail>
              )}
            </dl>
          ) : (
            <p className="text-sm text-muted-foreground">{t("Aucun certificat client")}</p>
          )}
          {admin && (
            <div className="flex flex-wrap gap-2">
              <ConfirmButton
                title={
                  station.certificate_fingerprint
                    ? t("Renouveler le certificat client ?")
                    : t("Demander un certificat client ?")
                }
                description={t(
                  "La borne génère une demande de signature (SignCertificate) que le serveur signe avec son autorité, puis lui renvoie le certificat.",
                )}
                confirmLabel={t("Demander")}
                destructive={false}
                variant="outline"
                disabled={!capabilities?.certificate_signing || action.busy}
                onConfirm={() =>
                  action.run(
                    "sign",
                    () => certificateCommand(station, "ExtendedTriggerMessage", {}),
                    {
                      success: t("Demande de certificat envoyée à la borne."),
                      failure: t("La demande de certificat n’a pas été envoyée"),
                      onDone: () =>
                        queuedCommand(
                          t(
                            "La borne va envoyer une demande de signature ; le certificat signé lui est ensuite transmis.",
                          ),
                        ),
                    },
                  )
                }
              >
                <IconCertificate data-icon="inline-start" />
                {station.certificate_fingerprint
                  ? t("Renouveler le certificat")
                  : t("Demander un certificat client")}
              </ConfirmButton>
              {(station.certificate_fingerprint || station.pinned_certificate_fingerprint) && (
                <ConfirmButton
                  title={t("Révoquer le certificat de la borne ?")}
                  description={t(
                    "Le certificat signé par le serveur et l’empreinte épinglée ne sont plus acceptés, et la borne est déconnectée. Une borne au profil 3 ne pourra plus se reconnecter sans nouveau certificat.",
                  )}
                  confirmLabel={t("Révoquer")}
                  disabled={action.busy}
                  onConfirm={() =>
                    action.run(
                      "revoke-certificate",
                      () => api<Station>(`${path}/certificate/revoke`, { method: "POST" }),
                      {
                        success: t("Certificat révoqué."),
                        failure: t("Le certificat n’a pas été révoqué"),
                        onDone: onChanged,
                      },
                    )
                  }
                >
                  {t("Révoquer le certificat")}
                </ConfirmButton>
              )}
              {!capabilities?.certificate_signing && (
                <p className="basis-full text-sm text-muted-foreground">
                  {t(
                    "Aucune autorité de signature n’est configurée sur le serveur : la borne ne peut pas obtenir de certificat signé.",
                  )}
                </p>
              )}
            </div>
          )}
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>{t("Mot de passe OCPP")}</CardTitle>
          <CardDescription>
            {t("Mot de passe d’authentification Basic de la borne (AuthorizationKey).")}
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {pending ? (
            <Alert>
              <IconKey />
              <AlertTitle>{t("Mot de passe en cours de renouvellement")}</AlertTitle>
              <AlertDescription>
                {t(
                  "L’ancien et le nouveau mot de passe sont acceptés jusqu’à la reconnexion de la borne avec le nouveau. Si la borne a refusé la modification, saisissez le nouveau mot de passe sur place, puis révoquez l’ancien.",
                )}
              </AlertDescription>
            </Alert>
          ) : (
            <p className="text-sm text-muted-foreground">
              {t("Aucun renouvellement en attente : seul le mot de passe actuel est accepté.")}
            </p>
          )}
          {admin ? (
            <div className="flex flex-wrap gap-2">
              <ConfirmButton
                title={t("Renouveler le mot de passe OCPP ?")}
                description={`${t("Le nouveau mot de passe est envoyé à la borne. L’ancien reste accepté jusqu’à sa reconnexion avec le nouveau.")}${pending ? ` ${t("Le mot de passe déjà en attente est remplacé.")}` : ""}`}
                confirmLabel={t("Renouveler")}
                destructive={false}
                variant="outline"
                disabled={!station.connected || action.busy}
                onConfirm={() =>
                  action.run(
                    "rotate",
                    () => api<ProvisionedStation>(`${path}/secret/remote`, { method: "POST" }),
                    {
                      failure: t("Le mot de passe n’a pas été renouvelé"),
                      onDone: (result) => {
                        setSecret({ secret: result, remote: true });
                        onChanged();
                      },
                    },
                  )
                }
              >
                <IconRefresh data-icon="inline-start" />
                {t("Renouveler le mot de passe OCPP")}
              </ConfirmButton>
              {pending && (
                <>
                  <ConfirmButton
                    title={t("Annuler le renouvellement ?")}
                    description={t(
                      "Le nouveau mot de passe en attente ne sera plus accepté ; seul le mot de passe actuel restera valide. Si la borne a déjà enregistré le nouveau mot de passe, elle ne pourra plus se connecter : le serveur refuse donc l’annulation dès que la commande a été transmise. Utilisez alors « Révoquer l’ancien mot de passe ».",
                    )}
                    confirmLabel={t("Annuler le renouvellement")}
                    variant="outline"
                    disabled={action.busy}
                    onConfirm={() =>
                      action.run(
                        "cancel-rotation",
                        () => api<Station>(`${path}/secret/pending`, { method: "DELETE" }),
                        {
                          success: t("Renouvellement annulé."),
                          failure: t("Le renouvellement n’a pas été annulé"),
                          onDone: onChanged,
                        },
                      )
                    }
                  >
                    {t("Annuler")}
                  </ConfirmButton>
                  <ConfirmButton
                    title={t("Révoquer l’ancien mot de passe ?")}
                    description={t(
                      "Seul le nouveau mot de passe reste accepté et la connexion en cours est fermée. Si la borne n’a pas enregistré le nouveau mot de passe, elle ne pourra plus se connecter tant qu’il n’aura pas été saisi sur place.",
                    )}
                    confirmLabel={t("Révoquer")}
                    disabled={action.busy}
                    onConfirm={() =>
                      action.run(
                        "revoke-previous",
                        () => api<Station>(`${path}/secret/revoke-previous`, { method: "POST" }),
                        {
                          success: t("Ancien mot de passe révoqué."),
                          failure: t("L’ancien mot de passe n’a pas été révoqué"),
                          onDone: onChanged,
                        },
                      )
                    }
                  >
                    {t("Révoquer l’ancien mot de passe")}
                  </ConfirmButton>
                </>
              )}
              <ConfirmButton
                title={t("Renouveler le mot de passe sur place ?")}
                description={t(
                  "À utiliser lorsque vous êtes devant la borne : l’ancien mot de passe ne fonctionne plus immédiatement et la borne est déconnectée jusqu’à ce que le nouveau y soit saisi.",
                )}
                confirmLabel={t("Renouveler sur place")}
                variant="outline"
                disabled={action.busy}
                onConfirm={() =>
                  action.run(
                    "rotate-local",
                    () => api<ProvisionedStation>(`${path}/secret`, { method: "POST" }),
                    {
                      failure: t("Le mot de passe n’a pas été renouvelé"),
                      onDone: (result) => {
                        setSecret({ secret: result, remote: false });
                        onChanged();
                      },
                    },
                  )
                }
              >
                {t("Renouveler sur place")}
              </ConfirmButton>
              {!station.connected && (
                <p className="basis-full text-sm text-muted-foreground">
                  {t(
                    "La borne est hors ligne : le renouvellement à distance n’est possible que lorsqu’elle est connectée. Utilisez le renouvellement sur place.",
                  )}
                </p>
              )}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">
              {t("Seul un administrateur peut modifier la sécurité de la borne.")}
            </p>
          )}
        </CardContent>
      </Card>
      {operate && <InstalledCertificates station={station} admin={admin} />}
      <Commissioning station={station} publicUrl={capabilities?.ocpp_public_url} />
      <SecretDialog value={secret} online={station.connected} onClose={() => setSecret(null)} />
    </div>
  );
}

function Commissioning({ station, publicUrl }: { station: Station; publicUrl?: string | null }) {
  const { workspace } = useWorkspaceSession();
  const url = ocppUrl(workspace?.slug, station.id, publicUrl);
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("Fiche de mise en service")}</CardTitle>
        <CardDescription>
          {t(
            "Informations à transmettre à l’installateur. Le mot de passe n’est affiché qu’à sa création.",
          )}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <dl>
          <Detail label={t("Adresse OCPP")}>
            <CopyField value={url} label={t("Adresse OCPP")} />
            <span className="mt-1 block text-xs text-muted-foreground">
              {t("OCPP 1.6 JSON, sous-protocole ocpp1.6.")}
              {!publicUrl &&
                ` ${t("Adaptez le nom d’hôte si le serveur OCPP est publié à une autre adresse que ce tableau de bord.")}`}
            </span>
          </Detail>
          <Detail label={t("Identifiant de la borne")}>
            <CopyField value={station.id} label={t("Identifiant de la borne")} />
          </Detail>
          <Detail label={t("Profil attendu")}>{profileLabel(station.security_profile ?? 1)}</Detail>
        </dl>
      </CardContent>
    </Card>
  );
}

type CertificateHash = {
  hashAlgorithm: string;
  issuerNameHash: string;
  issuerKeyHash: string;
  serialNumber: string;
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function certificateHashes(result: unknown): CertificateHash[] {
  if (!isRecord(result) || !Array.isArray(result.certificateHashData)) {
    return [];
  }
  return result.certificateHashData.filter(
    (item): item is CertificateHash =>
      isRecord(item) &&
      ["hashAlgorithm", "issuerNameHash", "issuerKeyHash", "serialNumber"].every(
        (key) => typeof item[key] === "string",
      ),
  );
}

const installedTypes = translated([
  { value: "CentralSystemRootCertificate", label: "Racines du serveur" },
  { value: "ManufacturerRootCertificate", label: "Racines du fabricant" },
]);

function InstalledCertificates({ station, admin }: { station: Station; admin: boolean }) {
  const [revision, refresh] = useRevision();
  const [type, setType] = useState(installedTypes[0].value);
  const action = useAction();
  const { data, error, loading } = useApi<Row[] | { items: Row[] }>(
    `commands?stationId=${encodeURIComponent(station.id)}`,
    revision,
  );
  const latest = (Array.isArray(data) ? data : (data?.items ?? []))
    .filter(
      (command) =>
        command.station_id === station.id && command.action === "GetInstalledCertificateIds",
    )
    .sort((left, right) => String(right.created_at).localeCompare(String(left.created_at)))[0];
  const hashes = certificateHashes(latest?.result);
  const status = isRecord(latest?.result) ? latest.result.status : null;
  const columns: Column<CertificateHash>[] = [
    {
      id: "issuer",
      header: t("Émetteur (empreinte du nom)"),
      cell: (hash) => <code className="break-all text-xs">{hash.issuerNameHash}</code>,
    },
    {
      id: "serial",
      header: t("Numéro de série"),
      cell: (hash) => <code className="break-all text-xs">{hash.serialNumber}</code>,
    },
    {
      id: "actions",
      header: t("Actions"),
      srOnlyHeader: true,
      align: "right",
      cell: (hash) =>
        admin && (
          <ConfirmButton
            title={t("Supprimer ce certificat ?")}
            description={t(
              "Supprimer le certificat racine du serveur peut empêcher la borne de se reconnecter en TLS.",
            )}
            confirmLabel={t("Supprimer")}
            size="sm"
            variant="ghost"
            disabled={action.busy}
            onConfirm={() =>
              action.run(
                "delete-certificate",
                () =>
                  certificateCommand(station, "DeleteCertificate", { certificateHashData: hash }),
                {
                  success: t("Suppression du certificat demandée à la borne."),
                  failure: t("La suppression n’a pas été envoyée"),
                  onDone: () => setTimeout(refresh, 3000),
                },
              )
            }
          >
            {t("Supprimer")}
          </ConfirmButton>
        ),
    },
  ];
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("Certificats installés")}</CardTitle>
        <CardDescription>
          {t("Autorités de confiance installées sur la borne (GetInstalledCertificateIds).")}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center gap-2">
          <OptionSelect
            value={type}
            onChange={setType}
            options={installedTypes}
            label={t("Type de certificat")}
            className="w-full sm:w-56"
          />
          <Button
            variant="outline"
            disabled={action.busy}
            onClick={() =>
              action.run(
                "list-certificates",
                () =>
                  sendCommand(station.id, "GetInstalledCertificateIds", { certificateType: type }),
                {
                  success: t("Lecture des certificats demandée à la borne."),
                  failure: t("La lecture n’a pas été envoyée"),
                  onDone: () => setTimeout(refresh, 3000),
                },
              )
            }
          >
            {t("Lire les certificats")}
          </Button>
          <Button variant="ghost" size="icon" aria-label={t("Actualiser")} onClick={refresh}>
            <IconRefresh />
          </Button>
          {admin && <InstallCertificate station={station} onQueued={refresh} />}
        </div>
        {!station.connected && (
          <p className="rounded-lg border border-dashed p-3 text-sm text-muted-foreground">
            {t("La borne est hors ligne : la demande sera envoyée dès sa reconnexion ou expirera.")}
          </p>
        )}
        {error && !data ? (
          <ErrorState error={error} onRetry={refresh} />
        ) : loading && !data ? (
          <LoadingState rows={2} />
        ) : !latest ? (
          <EmptyState
            icon={<IconCertificate />}
            title={t("Certificats non lus")}
            description={t("Demandez la lecture : la liste apparaît dès la réponse de la borne.")}
          />
        ) : (
          <>
            <p className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
              {t("Dernière lecture du {date}", { date: date(latest.created_at) })}
              <StateBadge state={latest.state} />
              {status === "NotFound" && t("Aucun certificat de ce type.")}
            </p>
            {hashes.length > 0 && (
              <DataTable
                label={t("Certificats installés")}
                rows={hashes}
                columns={columns}
                rowKey={(hash) => `${hash.issuerNameHash}:${hash.serialNumber}`}
              />
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
