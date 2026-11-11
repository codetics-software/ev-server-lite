"use client";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldLabel } from "@/components/ui/field";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ConfirmButton } from "@/features/shared/confirm-button";
import { DataTable, type Column } from "@/features/shared/data-table";
import { FormDialog } from "@/features/shared/form-dialog";
import { OptionSelect } from "@/features/shared/form-fields";
import { PageHeader } from "@/features/shared/page-header";
import { Pager } from "@/features/shared/pager";
import { EmptyState, ErrorState, ListState, LoadingState } from "@/features/shared/states";
import { StateBadge, ToneBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { usePaged } from "@/features/shared/use-paged";
import { useApi, useRevision } from "@/hooks/use-api";
import { api } from "@/lib/api";
import { date, display } from "@/lib/format";
import { ocpiModuleLabels } from "@/lib/labels";
import {
  IconArrowDown,
  IconArrowUp,
  IconBroadcast,
  IconDatabase,
  IconHistory,
} from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { PartnerEditForm, RegisterForm } from "./partner-forms";
import {
  partnerStatus,
  pullModules,
  pushModules,
  type JobResult,
  type OcpiObject,
  type Partner,
} from "./roaming-types";
import { t } from "@/lib/i18n";

const moduleLabel = (module: string) => ocpiModuleLabels[module] ?? module;

function Jobs({ partnerId, revision }: { partnerId: string; revision: number }) {
  const { data, loading, error } = useApi<JobResult[]>(`ocpi/partners/${partnerId}/jobs`, revision);
  const items = data ?? [];
  const columns: Column<JobResult>[] = [
    {
      id: "module",
      header: t("Module"),
      sort: (job) => job.module,
      cell: (job) =>
        job.direction
          ? `${moduleLabel(job.module)} · ${t(job.direction === "PULL" || job.direction === "RECEIVER" ? "récupération" : "envoi")}`
          : moduleLabel(job.module),
    },
    {
      id: "started",
      header: t("Début"),
      sort: (job) => job.started_at,
      cell: (job) => date(job.started_at),
    },
    { id: "total", header: t("Total"), align: "right", cell: (job) => job.total },
    { id: "success", header: t("Réussis"), align: "right", cell: (job) => job.success },
    {
      id: "failure",
      header: t("Échecs"),
      align: "right",
      cell: (job) => (job.failure ? <ToneBadge tone="danger">{job.failure}</ToneBadge> : 0),
    },
    {
      id: "errors",
      header: t("Erreurs"),
      cell: (job) =>
        job.errors?.length ? (
          <span className="block max-w-80 truncate text-xs text-muted-foreground">
            {job.errors.join(" · ")}
          </span>
        ) : (
          "—"
        ),
    },
  ];
  return (
    <ListState
      data={data}
      items={items}
      loading={loading}
      error={error}
      empty={
        <EmptyState
          icon={<IconHistory />}
          title={t("Aucune synchronisation")}
          description={t("Lancez un envoi ou une récupération pour voir son résultat.")}
        />
      }
    >
      <DataTable
        label={t("Synchronisations")}
        rows={items}
        columns={columns}
        rowKey={(job, index) => job.id ?? `${job.module}-${job.started_at}-${index}`}
      />
    </ListState>
  );
}

function Objects({ partnerId }: { partnerId: string }) {
  const [module, setModule] = useState("locations");
  const [direction, setDirection] = useState("SENDER");
  const [selected, setSelected] = useState<OcpiObject | null>(null);
  const list = usePaged<OcpiObject>(`ocpi/partners/${partnerId}/objects`, { module, direction });
  const columns: Column<OcpiObject>[] = [
    {
      id: "path",
      header: t("Objet"),
      sort: (item) => item.path,
      cell: (item) => <code className="text-xs">{item.path}</code>,
    },
    {
      id: "updated",
      header: t("Mise à jour"),
      sort: (item) => item.last_updated ?? "",
      cell: (item) => date(item.last_updated),
    },
  ];
  return (
    <section className="flex flex-col gap-4" aria-label={t("Objets OCPI")}>
      <div className="flex flex-wrap gap-3">
        <OptionSelect
          value={module}
          onChange={setModule}
          label={t("Module")}
          className="w-full sm:w-60"
          options={Object.keys(ocpiModuleLabels).map((value) => ({
            value,
            label: moduleLabel(value),
          }))}
        />
        <OptionSelect
          value={direction}
          onChange={setDirection}
          label={t("Sens")}
          className="w-full sm:w-60"
          options={[
            { value: "SENDER", label: t("Publiés au partenaire") },
            { value: "RECEIVER", label: t("Reçus du partenaire") },
          ]}
        />
      </div>
      <ListState
        data={list.data}
        items={list.items}
        loading={list.loading}
        error={list.error}
        onRetry={list.refresh}
        empty={
          <EmptyState
            icon={<IconDatabase />}
            title={t("Aucun objet")}
            description={t("Aucun objet échangé pour ce module et ce sens.")}
          />
        }
      >
        <DataTable
          label={t("Objets")}
          rows={list.items}
          columns={columns}
          rowKey={(item) => item.path}
          onRowClick={setSelected}
        />
      </ListState>
      <Pager
        offset={list.offset}
        count={list.items.length}
        total={list.total}
        pageSize={list.pageSize}
        loading={list.loading}
        onChange={list.setOffset}
      />
      <FormDialog
        open={selected !== null}
        onOpenChange={(open) => !open && setSelected(null)}
        title={selected?.path ?? ""}
        description={date(selected?.last_updated)}
        wide
      >
        <pre className="max-h-[60svh] overflow-auto rounded-lg bg-muted p-4 text-xs">
          {JSON.stringify(selected?.data, null, 2)}
        </pre>
      </FormDialog>
    </section>
  );
}

export function PartnerDetailScreen({ id }: { id: string }) {
  const router = useRouter();
  const [revision, refresh] = useRevision();
  const [jobsRevision, refreshJobs] = useRevision();
  const { data, loading, error } = useApi<Partner[]>("ocpi/partners", revision);
  const [registering, setRegistering] = useState(false);
  const [pushChoice, setPushModule] = useState("");
  const [pullChoice, setPullModule] = useState("");
  const action = useAction();
  const partner = data?.find((item) => item.id === id);

  if (error && !data) {
    return <ErrorState error={error} onRetry={refresh} />;
  }
  if (loading && !data) {
    return <LoadingState />;
  }
  if (!partner) {
    return (
      <>
        <PageHeader
          title={t("Partenaire introuvable")}
          back={{ href: "/roaming", label: t("Partenaires") }}
        />
        <EmptyState
          title={t("Ce partenaire n’existe pas")}
          description={t("Il a peut-être été supprimé.")}
        />
      </>
    );
  }

  const pushModule = pushModules(partner.local_role).includes(pushChoice)
    ? pushChoice
    : pushModules(partner.local_role)[0];
  const pullModule = pullModules(partner.local_role).includes(pullChoice)
    ? pullChoice
    : pullModules(partner.local_role)[0];

  function job(kind: "push" | "pull", module: string) {
    return action.run(
      kind + module,
      () => api<JobResult>(`ocpi/partners/${id}/${kind}/${module}`, { method: "POST" }),
      {
        success: (result) =>
          t("{module} : {success} réussis sur {total}.", {
            module: moduleLabel(module),
            success: result?.success ?? 0,
            total: result?.total ?? 0,
          }),
        onDone: () => {
          refresh();
          refreshJobs();
        },
      },
    );
  }

  return (
    <>
      <PageHeader
        back={{ href: "/roaming", label: t("Partenaires") }}
        title={partner.name}
        description={`${partner.country_code}*${partner.party_id} · ${display(partner.local_role)} · ${partner.remote_url}`}
        meta={
          <>
            <StateBadge state={partnerStatus(partner)} />
            {partner.last_error && (
              <span className="text-xs text-destructive">{partner.last_error}</span>
            )}
          </>
        }
        actions={
          <>
            <Button
              variant="outline"
              disabled={action.busy}
              onClick={() =>
                action.run(
                  "ping",
                  () =>
                    api<{ ok: boolean; message: string }>(`ocpi/partners/${id}/ping`, {
                      method: "POST",
                    }),
                  {
                    success: (result) =>
                      result?.ok ? t("Le partenaire répond.") : display(result?.message),
                    onDone: refresh,
                  },
                )
              }
            >
              <IconBroadcast data-icon="inline-start" />
              {t("Tester la connexion")}
            </Button>
            <Button variant="outline" onClick={() => setRegistering(true)}>
              {partner.registered ? t("Renouveler les identifiants") : t("S’enregistrer")}
            </Button>
            {partner.registered && (
              <ConfirmButton
                title={t("Annuler l’enregistrement ?")}
                description={t(
                  "Les identifiants échangés sont supprimés chez nous et chez le partenaire.",
                )}
                confirmLabel={t("Annuler l’enregistrement")}
                variant="outline"
                onConfirm={() =>
                  action.run(
                    "unregister",
                    () => api(`ocpi/partners/${id}/unregister`, { method: "POST" }),
                    { success: t("Enregistrement annulé."), onDone: refresh },
                  )
                }
              >
                {t("Désenregistrer")}
              </ConfirmButton>
            )}
            {partner.enabled && (
              <ConfirmButton
                title={t("Désactiver {name} ?", { name: partner.name })}
                description={t("Les identifiants sont révoqués et plus aucun échange n’a lieu.")}
                confirmLabel={t("Désactiver")}
                onConfirm={() =>
                  action.run("disable", () => api(`ocpi/partners/${id}`, { method: "DELETE" }), {
                    success: t("Partenaire désactivé."),
                    onDone: () => router.replace("/roaming"),
                  })
                }
              >
                {t("Désactiver")}
              </ConfirmButton>
            )}
          </>
        }
      />
      <dl className="grid grid-cols-2 gap-4 border-y py-4 sm:grid-cols-4">
        {(
          [
            [t("Enregistré"), partner.registered ? t("Oui") : t("Non")],
            [t("Dernier ping"), date(partner.last_ping_at)],
            [t("Dernier envoi"), date(partner.last_push_at)],
            [t("Dernière récupération"), date(partner.last_pull_at)],
          ] as const
        ).map(([label, value]) => (
          <div key={label}>
            <dt className="text-xs text-muted-foreground">{label}</dt>
            <dd className="text-sm">{value}</dd>
          </div>
        ))}
      </dl>
      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t("Envoyer au partenaire")}</CardTitle>
            <CardDescription>
              {t("Publie nos données (sites publics, états, sessions…).")}
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap items-end gap-3">
            <Field className="w-full sm:w-60">
              <FieldLabel htmlFor="push-module">{t("Module")}</FieldLabel>
              <OptionSelect
                id="push-module"
                value={pushModule}
                onChange={setPushModule}
                options={pushModules(partner.local_role).map((value) => ({
                  value,
                  label: moduleLabel(value),
                }))}
              />
            </Field>
            <Button
              disabled={action.busy || !partner.registered}
              onClick={() => job("push", pushModule)}
            >
              <IconArrowUp data-icon="inline-start" />
              {t("Envoyer")}
            </Button>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t("Récupérer du partenaire")}</CardTitle>
            <CardDescription>{t("Importe ses données (badges, tarifs, sites…).")}</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap items-end gap-3">
            <Field className="w-full sm:w-60">
              <FieldLabel htmlFor="pull-module">{t("Module")}</FieldLabel>
              <OptionSelect
                id="pull-module"
                value={pullModule}
                onChange={setPullModule}
                options={pullModules(partner.local_role).map((value) => ({
                  value,
                  label: moduleLabel(value),
                }))}
              />
            </Field>
            <Button
              disabled={action.busy || !partner.registered}
              onClick={() => job("pull", pullModule)}
            >
              <IconArrowDown data-icon="inline-start" />
              {t("Récupérer")}
            </Button>
          </CardContent>
        </Card>
      </div>
      <Tabs defaultValue="jobs">
        <TabsList variant="line">
          <TabsTrigger value="jobs">{t("Synchronisations")}</TabsTrigger>
          <TabsTrigger value="objects">{t("Objets échangés")}</TabsTrigger>
          <TabsTrigger value="settings">{t("Paramètres")}</TabsTrigger>
        </TabsList>
        <TabsContent value="jobs" className="pt-4">
          <Jobs partnerId={id} revision={jobsRevision} />
        </TabsContent>
        <TabsContent value="objects" className="pt-4">
          <Objects partnerId={id} />
        </TabsContent>
        <TabsContent value="settings" className="max-w-2xl pt-4">
          <PartnerEditForm
            key={`${partner.name}:${partner.enabled}:${partner.remote_url}`}
            partner={partner}
            onSaved={refresh}
          />
        </TabsContent>
      </Tabs>
      <FormDialog
        open={registering}
        onOpenChange={setRegistering}
        title={t("Échange des identifiants OCPI")}
        description={partner.name}
      >
        <RegisterForm
          partner={partner}
          onDone={() => {
            setRegistering(false);
            refresh();
          }}
        />
      </FormDialog>
    </>
  );
}
