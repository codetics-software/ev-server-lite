"use client";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { StationPhotos } from "./station-photos";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { ReportErrorButton } from "@/features/notifications/report-error-dialog";
import { SessionsList } from "@/features/sessions/sessions-list";
import { useLabels } from "@/features/shared/entity-picker";
import { PageHeader } from "@/features/shared/page-header";
import { ErrorState, LoadingState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { canWrite, isAdmin, useWorkspaceSession } from "@/features/workspace/workspace-context";
import { useApi, useRevision } from "@/hooks/use-api";
import { api } from "@/lib/api";
import { date, display, relative } from "@/lib/format";
import { IconDots, IconRefresh, IconTemplate, IconTrash } from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { StationCommandForm } from "./station-command-form";
import { StationActionDialog, StationActionMenu, type ActionRequest } from "./station-actions";
import { StationAutoCharge } from "./station-autocharge";
import { StationCharging } from "./station-charging";
import { StationCommandsLog } from "./station-commands-log";
import { StationConnectorsPanel } from "./station-connectors-panel";
import { StationEvents } from "./station-events";
import { StationParameters } from "./station-parameters";
import { StationOcppSettings } from "./station-ocpp-settings";
import { StationProperties } from "./station-properties";
import type { SecurityCapabilities } from "./station-security";
import { PinDialog } from "./station-security-dialogs";
import { StationSecurityPanel } from "./station-security-panel";
import type { Station } from "./station-types";
import { t } from "@/lib/i18n";

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-sm">{value}</dd>
    </div>
  );
}

export function StationDetailScreen({ id }: { id: string }) {
  const { user } = useWorkspaceSession();
  const router = useRouter();
  const [revision, refresh] = useRevision();
  const [commands, refreshCommands] = useRevision();
  const [request, setRequest] = useState<ActionRequest | null>(null);
  const [pin, setPin] = useState<string | null>(null);
  const [capabilityRevision, refreshCapabilities] = useRevision();
  const [deleting, setDeleting] = useState(false);
  const [tab, setTab] = useState("connectors");
  const action = useAction();
  const path = `stations/${encodeURIComponent(id)}`;
  const { data: station, error, loading } = useApi<Station>(path, revision);
  const areas = useLabels("site-areas");
  const sites = useLabels("sites");
  const operate = canWrite(user);
  const admin = isAdmin(user);
  const capabilities = useApi<SecurityCapabilities>(
    "stations/security/capabilities",
    capabilityRevision,
  );
  const securityChanged = () => {
    refresh();
    refreshCommands();
  };

  useEffect(() => {
    const timer = setInterval(refresh, 15000);
    return () => clearInterval(timer);
  }, [refresh]);

  if (error && !station) {
    return (
      <>
        <PageHeader title={id} back={{ href: "/stations", label: t("Bornes") }} />
        <ErrorState error={error} onRetry={refresh} />
      </>
    );
  }
  if (loading && !station) {
    return <LoadingState />;
  }
  if (!station) {
    return null;
  }

  return (
    <>
      <PageHeader
        back={{ href: "/stations", label: t("Bornes") }}
        title={station.name}
        description={[
          station.id,
          [station.vendor, station.model].filter(Boolean).join(" ") || t("Matériel inconnu"),
          `OCPP ${station.ocpp_version ?? "1.6"}`,
        ].join(" · ")}
        meta={
          <>
            <ToneBadge
              tone={!station.enabled ? "neutral" : station.connected ? "success" : "danger"}
            >
              {!station.enabled
                ? t("Désactivée")
                : station.connected
                  ? t("Connectée")
                  : t("Hors ligne")}
            </ToneBadge>
            {station.public && <ToneBadge tone="progress">{t("Publique")}</ToneBadge>}
            {station.auto_charge && (
              <ToneBadge tone="progress">{t("Recharge automatique")}</ToneBadge>
            )}
            {station.security_profile === 0 && (
              <ToneBadge tone="danger">{t("Sans authentification")}</ToneBadge>
            )}
            {station.requested_security_profile != null && (
              <ToneBadge tone="progress">
                {t("Profil {current} → {requested} en attente", {
                  current: station.security_profile ?? 1,
                  requested: station.requested_security_profile,
                })}
              </ToneBadge>
            )}
            {station.password_rotation_pending && (
              <ToneBadge tone="warning">{t("Mot de passe en cours de renouvellement")}</ToneBadge>
            )}
            <span className="text-xs text-muted-foreground">
              {t("Dernier signal {time}", { time: relative(station.last_seen) })}
            </span>
          </>
        }
        actions={
          <>
            <Button variant="outline" size="icon" aria-label={t("Actualiser")} onClick={refresh}>
              <IconRefresh />
            </Button>
            <ReportErrorButton
              user={user}
              stationId={station.id}
              variant="outline"
              label={t("Signaler")}
            />
            {operate && <StationActionMenu onSelect={setRequest} />}
            {admin && (
              <DropdownMenu>
                <DropdownMenuTrigger
                  render={<Button variant="outline" size="icon" aria-label={t("Plus d’options")} />}
                >
                  <IconDots />
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-64">
                  <DropdownMenuGroup>
                    <DropdownMenuItem
                      onClick={() =>
                        action.run(
                          "template",
                          () =>
                            api<{ template_id: string | null }>(`${path}/template/apply`, {
                              method: "POST",
                            }),
                          {
                            success: (result) =>
                              result?.template_id
                                ? t("Modèle de configuration appliqué.")
                                : t("Aucun modèle ne correspond à cette borne."),
                            onDone: refresh,
                          },
                        )
                      }
                    >
                      <IconTemplate />
                      {t("Appliquer le modèle correspondant")}
                    </DropdownMenuItem>
                    <DropdownMenuItem variant="destructive" onClick={() => setDeleting(true)}>
                      <IconTrash />
                      {t("Supprimer la borne")}
                    </DropdownMenuItem>
                  </DropdownMenuGroup>
                </DropdownMenuContent>
              </DropdownMenu>
            )}
          </>
        }
      />
      <dl className="grid grid-cols-2 gap-4 border-y py-4 sm:grid-cols-3 lg:grid-cols-6">
        <Info
          label={t("Site · zone")}
          value={
            station.site_area_id
              ? `${sites.get(station.site_id ?? "") ?? "—"} · ${areas.get(station.site_area_id) ?? "—"}`
              : t("Sans zone")
          }
        />
        <Info label={t("Micrologiciel")} value={display(station.firmware_version)} />
        <Info
          label={t("Mise à jour")}
          value={station.firmware_status ? display(station.firmware_status) : "—"}
        />
        <Info label={t("Diagnostic")} value={display(station.diagnostics_status)} />
        {station.log_status && <Info label={t("Journaux")} value={display(station.log_status)} />}
        <Info label={t("Dernier démarrage")} value={date(station.last_boot)} />
        <Info label={t("Dernier contact")} value={date(station.last_seen)} />
      </dl>
      <Tabs value={tab} onValueChange={(value) => setTab(String(value))}>
        <div className="overflow-x-auto">
          <TabsList variant="line">
            <TabsTrigger value="connectors">{t("Connecteurs")}</TabsTrigger>
            <TabsTrigger value="properties">{t("Propriétés")}</TabsTrigger>
            <TabsTrigger value="security">{t("Sécurité")}</TabsTrigger>
            <TabsTrigger value="parameters">{t("Paramètres OCPP")}</TabsTrigger>
            <TabsTrigger value="charging">{t("Limitation et profils")}</TabsTrigger>
            <TabsTrigger value="sessions">{t("Sessions")}</TabsTrigger>
            <TabsTrigger value="events">{t("Événements")}</TabsTrigger>
            <TabsTrigger value="commands">{t("Commandes")}</TabsTrigger>
            <TabsTrigger value="photos">{t("Photos")}</TabsTrigger>
            {admin && <TabsTrigger value="advanced">{t("Avancé")}</TabsTrigger>}
          </TabsList>
        </div>
        <TabsContent value="connectors" className="pt-4">
          <StationConnectorsPanel station={station} canOperate={operate} onAction={setRequest} />
        </TabsContent>
        <TabsContent value="properties" className="flex flex-col gap-6 pt-4">
          <StationProperties
            key={`${station.name}:${station.site_area_id}:${station.connectors?.length}`}
            station={station}
            canEdit={operate}
            onSaved={refresh}
          />
          <StationAutoCharge
            station={station}
            canEdit={admin}
            onChanged={() => {
              refresh();
              refreshCommands();
            }}
            onShowCommands={() => setTab("commands")}
          />
          <StationOcppSettings
            key={`${station.heartbeat_interval}:${station.data_transfer_vendors?.join(",")}`}
            station={station}
            capabilities={capabilities.data}
            canEdit={operate}
            onChanged={securityChanged}
            onShowCommands={() => setTab("commands")}
          />
        </TabsContent>
        <TabsContent value="security" className="pt-4">
          <StationSecurityPanel
            station={station}
            capabilities={capabilities.data}
            capabilitiesError={capabilities.error}
            capabilitiesLoading={capabilities.loading}
            onRetryCapabilities={refreshCapabilities}
            admin={admin}
            operate={operate}
            onChanged={securityChanged}
            onPin={setPin}
            onShowCommands={() => setTab("commands")}
          />
        </TabsContent>
        <TabsContent value="parameters" className="pt-4">
          <StationParameters stationId={station.id} canEdit={operate} />
        </TabsContent>
        <TabsContent value="charging" className="pt-4">
          <StationCharging station={station} canEdit={operate} />
        </TabsContent>
        <TabsContent value="sessions" className="pt-4">
          <SessionsList status={null} fixed={{ stationId: station.id }} />
        </TabsContent>
        <TabsContent value="events" className="pt-4">
          <StationEvents stationId={station.id} onPin={admin ? setPin : undefined} />
        </TabsContent>
        <TabsContent value="commands" className="pt-4">
          <StationCommandsLog stationId={station.id} revision={commands} />
        </TabsContent>
        <TabsContent value="photos" className="pt-4">
          <StationPhotos stationId={station.id} canDelete={operate} />
        </TabsContent>
        {admin && (
          <TabsContent value="advanced" className="max-w-2xl pt-4">
            <p className="mb-4 text-sm text-muted-foreground">
              {t(
                "Envoi d’une commande OCPP 1.6 brute. Réservé aux administrateurs : préférez les actions dédiées.",
              )}
            </p>
            <StationCommandForm station={station.id} />
          </TabsContent>
        )}
      </Tabs>
      <StationActionDialog
        station={station}
        request={request}
        onClose={() => setRequest(null)}
        onSent={() => setTimeout(refresh, 3000)}
      />
      {admin && (
        <PinDialog
          station={station}
          initial={pin}
          onClose={() => setPin(null)}
          onChanged={securityChanged}
        />
      )}
      <AlertDialog open={deleting} onOpenChange={setDeleting}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {t("Supprimer la borne {name} ?", { name: station.name })}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {t(
                "La borne disparaît des listes et ne peut plus se connecter. L’historique des sessions est conservé. Impossible tant qu’une session est en cours.",
              )}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t("Annuler")}</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={action.busy}
              onClick={() =>
                action.run("delete", () => api(path, { method: "DELETE" }), {
                  success: t("Borne supprimée."),
                  onDone: () => router.replace("/stations"),
                })
              }
            >
              {t("Supprimer")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
