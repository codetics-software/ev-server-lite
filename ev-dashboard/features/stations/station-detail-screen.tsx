"use client";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
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
import { SecretReveal } from "@/features/shared/secret-reveal";
import { ErrorState, LoadingState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { canWrite, isAdmin, useWorkspaceSession } from "@/features/workspace/workspace-context";
import { useApi, useRevision } from "@/hooks/use-api";
import { api } from "@/lib/api";
import { date, display, relative } from "@/lib/format";
import { IconDots, IconKey, IconRefresh, IconTemplate, IconTrash } from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { StationCommandForm } from "./station-command-form";
import { StationActionDialog, StationActionMenu, type ActionRequest } from "./station-actions";
import { StationCharging } from "./station-charging";
import { StationCommandsLog } from "./station-commands-log";
import { StationConnectorsPanel } from "./station-connectors-panel";
import { StationEvents } from "./station-events";
import { StationParameters } from "./station-parameters";
import { StationProperties } from "./station-properties";
import type { ProvisionedStation, Station } from "./station-types";

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
  const [request, setRequest] = useState<ActionRequest | null>(null);
  const [secret, setSecret] = useState<ProvisionedStation | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [tab, setTab] = useState("connectors");
  const action = useAction();
  const path = `stations/${encodeURIComponent(id)}`;
  const { data: station, error, loading } = useApi<Station>(path, revision);
  const areas = useLabels("site-areas");
  const sites = useLabels("sites");
  const operate = canWrite(user);
  const admin = isAdmin(user);

  useEffect(() => {
    const timer = setInterval(refresh, 15000);
    return () => clearInterval(timer);
  }, [refresh]);

  if (error && !station) {
    return (
      <>
        <PageHeader title={id} back={{ href: "/stations", label: "Bornes" }} />
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
        back={{ href: "/stations", label: "Bornes" }}
        title={station.name}
        description={`${station.id} · ${[station.vendor, station.model].filter(Boolean).join(" ") || "Matériel inconnu"} · OCPP ${station.ocpp_version ?? "1.6"}`}
        meta={
          <>
            <ToneBadge
              tone={!station.enabled ? "neutral" : station.connected ? "success" : "danger"}
            >
              {!station.enabled ? "Désactivée" : station.connected ? "Connectée" : "Hors ligne"}
            </ToneBadge>
            {station.public && <ToneBadge tone="progress">Publique</ToneBadge>}
            <span className="text-xs text-muted-foreground">
              Dernier signal {relative(station.last_seen)}
            </span>
          </>
        }
        actions={
          <>
            <Button variant="outline" size="icon" aria-label="Actualiser" onClick={refresh}>
              <IconRefresh />
            </Button>
            <ReportErrorButton
              user={user}
              stationId={station.id}
              variant="outline"
              label="Signaler"
            />
            {operate && <StationActionMenu onSelect={setRequest} />}
            {admin && (
              <DropdownMenu>
                <DropdownMenuTrigger
                  render={<Button variant="outline" size="icon" aria-label="Plus d’options" />}
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
                                ? "Modèle de configuration appliqué."
                                : "Aucun modèle ne correspond à cette borne.",
                            onDone: refresh,
                          },
                        )
                      }
                    >
                      <IconTemplate />
                      Appliquer le modèle correspondant
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      onClick={() =>
                        action.run(
                          "secret",
                          () => api<ProvisionedStation>(`${path}/secret`, { method: "POST" }),
                          { onDone: setSecret },
                        )
                      }
                    >
                      <IconKey />
                      Renouveler le mot de passe OCPP
                    </DropdownMenuItem>
                    <DropdownMenuItem variant="destructive" onClick={() => setDeleting(true)}>
                      <IconTrash />
                      Supprimer la borne
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
          label="Site · zone"
          value={
            station.site_area_id
              ? `${sites.get(station.site_id ?? "") ?? "—"} · ${areas.get(station.site_area_id) ?? "—"}`
              : "Sans zone"
          }
        />
        <Info label="Micrologiciel" value={display(station.firmware_version)} />
        <Info
          label="Mise à jour"
          value={station.firmware_status ? display(station.firmware_status) : "—"}
        />
        <Info label="Diagnostic" value={display(station.diagnostics_status)} />
        <Info label="Dernier démarrage" value={date(station.last_boot)} />
        <Info label="Dernier contact" value={date(station.last_seen)} />
      </dl>
      <Tabs value={tab} onValueChange={(value) => setTab(String(value))}>
        <div className="overflow-x-auto">
          <TabsList variant="line">
            <TabsTrigger value="connectors">Connecteurs</TabsTrigger>
            <TabsTrigger value="properties">Propriétés</TabsTrigger>
            <TabsTrigger value="parameters">Paramètres OCPP</TabsTrigger>
            <TabsTrigger value="charging">Limitation et profils</TabsTrigger>
            <TabsTrigger value="sessions">Sessions</TabsTrigger>
            <TabsTrigger value="events">Événements</TabsTrigger>
            <TabsTrigger value="commands">Commandes</TabsTrigger>
            {admin && <TabsTrigger value="advanced">Avancé</TabsTrigger>}
          </TabsList>
        </div>
        <TabsContent value="connectors" className="pt-4">
          <StationConnectorsPanel station={station} canOperate={operate} onAction={setRequest} />
        </TabsContent>
        <TabsContent value="properties" className="pt-4">
          <StationProperties
            key={`${station.name}:${station.site_area_id}:${station.connectors?.length}`}
            station={station}
            canEdit={operate}
            onSaved={refresh}
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
          <StationEvents stationId={station.id} />
        </TabsContent>
        <TabsContent value="commands" className="pt-4">
          <StationCommandsLog stationId={station.id} revision={0} />
        </TabsContent>
        {admin && (
          <TabsContent value="advanced" className="max-w-2xl pt-4">
            <p className="mb-4 text-sm text-muted-foreground">
              Envoi d’une commande OCPP 1.6 brute. Réservé aux administrateurs : préférez les
              actions dédiées.
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
      <Dialog open={secret !== null} onOpenChange={(open) => !open && setSecret(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Nouveau mot de passe OCPP</DialogTitle>
            <DialogDescription>
              L’ancien mot de passe ne fonctionne plus : configurez la borne avec celui-ci.
            </DialogDescription>
          </DialogHeader>
          {secret && (
            <SecretReveal
              title="Mot de passe de la borne"
              message={secret.message}
              secret={secret.secret}
            />
          )}
          <DialogFooter>
            <Button onClick={() => setSecret(null)}>Terminer</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <AlertDialog open={deleting} onOpenChange={setDeleting}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Supprimer la borne {station.name} ?</AlertDialogTitle>
            <AlertDialogDescription>
              La borne disparaît des listes et ne peut plus se connecter. L’historique des sessions
              est conservé. Impossible tant qu’une session est en cours.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Annuler</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={action.busy}
              onClick={() =>
                action.run("delete", () => api(path, { method: "DELETE" }), {
                  success: "Borne supprimée.",
                  onDone: () => router.replace("/stations"),
                })
              }
            >
              Supprimer
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
