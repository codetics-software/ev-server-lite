"use client";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import { FieldGroup } from "@/components/ui/field";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ConfirmButton } from "@/features/shared/confirm-button";
import { useLabels } from "@/features/shared/entity-picker";
import { FormFooter } from "@/features/shared/form-footer";
import { FormDialog } from "@/features/shared/form-dialog";
import { FormText, OptionSelect } from "@/features/shared/form-fields";
import { PageHeader } from "@/features/shared/page-header";
import { deleteRecord, field } from "@/features/shared/records";
import { SecretReveal } from "@/features/shared/secret-reveal";
import { EmptyState, ErrorState, LoadingState } from "@/features/shared/states";
import { useAction } from "@/features/shared/use-action";
import { canWrite, isAdmin, useWorkspace } from "@/features/workspace/workspace-context";
import { useApi, useRevision } from "@/hooks/use-api";
import { api, errorMessage, query } from "@/lib/api";
import type { Row } from "@/lib/api-types";
import { date, display, kw, number, time } from "@/lib/format";
import { IconChartLine, IconKey, IconPlugConnected, IconRefresh } from "@tabler/icons-react";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Area, CartesianGrid, ComposedChart, Line, XAxis, YAxis } from "recharts";
import { AssetForm, connectionTypes } from "./asset-form";
import { useWindow } from "./smart-charging-panel";
import { useRecord } from "./use-record";

type Point = { at: string; power_kw: number | null; energy_kwh: number | null; soc: number | null };

const config = {
  power_kw: { label: "Puissance (kW)", color: "var(--chart-3)" },
  soc: { label: "Charge (%)", color: "var(--chart-5)" },
} satisfies ChartConfig;

function AssetConsumption({ id, revision }: { id: string; revision: number }) {
  const [hours, setHours] = useState("24");
  const range = useWindow(hours);
  const { data, loading, error } = useApi<Point[]>(
    `assets/${id}/consumptions${query(range)}`,
    revision,
  );
  const points = (data ?? []).map((point) => ({ ...point, time: new Date(point.at).valueOf() }));
  const hasSoc = points.some((point) => point.soc != null);
  return (
    <Card>
      <CardHeader>
        <CardTitle>Mesures</CardTitle>
        <CardDescription>Puissance relevée sur l’équipement.</CardDescription>
        <CardAction>
          <OptionSelect
            value={hours}
            onChange={setHours}
            className="w-48"
            label="Période"
            options={[
              { value: "6", label: "6 dernières heures" },
              { value: "24", label: "24 dernières heures" },
              { value: "168", label: "7 derniers jours" },
            ]}
          />
        </CardAction>
      </CardHeader>
      <CardContent>
        {error && !data ? (
          <ErrorState error={error} />
        ) : loading && !data ? (
          <LoadingState rows={3} />
        ) : !points.length ? (
          <EmptyState icon={<IconChartLine />} title="Aucune mesure sur cette période" />
        ) : (
          <ChartContainer config={config} className="aspect-auto h-72 w-full">
            <ComposedChart data={points} margin={{ left: 4, right: 4, top: 8 }}>
              <CartesianGrid vertical={false} />
              <XAxis
                dataKey="time"
                type="number"
                scale="time"
                domain={["dataMin", "dataMax"]}
                tickFormatter={(value: number) => time(new Date(value).toISOString())}
                tickLine={false}
                axisLine={false}
                minTickGap={40}
              />
              <YAxis yAxisId="power" width={48} tickLine={false} axisLine={false} />
              {hasSoc && (
                <YAxis
                  yAxisId="soc"
                  orientation="right"
                  domain={[0, 100]}
                  width={36}
                  tickLine={false}
                  axisLine={false}
                />
              )}
              <ChartTooltip
                content={
                  <ChartTooltipContent
                    labelFormatter={(_, payload) =>
                      date(new Date(Number(payload?.[0]?.payload?.time ?? 0)).toISOString())
                    }
                    formatter={(value, name) => (
                      <span className="flex w-full justify-between gap-4">
                        <span className="text-muted-foreground">
                          {config[name as keyof typeof config]?.label ?? String(name)}
                        </span>
                        <span className="font-medium tabular-nums">{number(value, 1)}</span>
                      </span>
                    )}
                  />
                }
              />
              <ChartLegend content={<ChartLegendContent />} />
              <Area
                yAxisId="power"
                dataKey="power_kw"
                type="monotone"
                stroke="var(--color-power_kw)"
                fill="var(--color-power_kw)"
                fillOpacity={0.25}
                isAnimationActive={false}
              />
              {hasSoc && (
                <Line
                  yAxisId="soc"
                  dataKey="soc"
                  type="monotone"
                  stroke="var(--color-soc)"
                  dot={false}
                  isAnimationActive={false}
                />
              )}
            </ComposedChart>
          </ChartContainer>
        )}
      </CardContent>
    </Card>
  );
}

type ConnectionStatus = {
  configured: boolean;
  has_password: boolean;
  has_client_secret: boolean;
  has_api_key: boolean;
  has_push_token: boolean;
  last_success_at: string | null;
  last_attempt_at: string | null;
  last_error: string | null;
};

function StatusLine({ status }: { status: ConnectionStatus | null }) {
  if (!status) {
    return null;
  }
  const secrets = [
    status.has_password ? "mot de passe" : null,
    status.has_api_key ? "clé d’API" : null,
    status.has_client_secret ? "secret client" : null,
    status.has_push_token ? "jeton d’envoi" : null,
  ].filter(Boolean);
  return (
    <dl className="grid gap-3 rounded-lg border p-3 text-sm sm:grid-cols-3">
      <div>
        <dt className="text-xs text-muted-foreground">Secrets enregistrés</dt>
        <dd>{secrets.length ? secrets.join(", ") : "Aucun"}</dd>
      </div>
      <div>
        <dt className="text-xs text-muted-foreground">Dernier relevé réussi</dt>
        <dd>{date(status.last_success_at)}</dd>
      </div>
      <div>
        <dt className="text-xs text-muted-foreground">Dernière tentative</dt>
        <dd>{date(status.last_attempt_at)}</dd>
      </div>
      {status.last_error && <p className="text-destructive sm:col-span-3">{status.last_error}</p>}
    </dl>
  );
}

function ConnectionSecrets({ id }: { id: string }) {
  const [error, setError] = useState("");
  const [revision, refreshStatus] = useRevision();
  const status = useApi<ConnectionStatus>(`assets/${id}/connection`, revision);
  const action = useAction();
  const form = useForm({
    defaultValues: { password: "", apiKey: "", clientSecret: "" },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value, formApi }) => {
      setError("");
      try {
        await api(`assets/${id}/connection`, {
          method: "PUT",
          body: JSON.stringify({
            password: value.password || undefined,
            apiKey: value.apiKey || undefined,
            clientSecret: value.clientSecret || undefined,
          }),
        });
        formApi.reset();
        refreshStatus();
        await action.run(
          "check",
          () =>
            api<{ ok: boolean; message: string }>(`assets/${id}/connection/check`, {
              method: "POST",
            }),
          {
            success: (result) =>
              result?.ok
                ? "Secrets enregistrés, connexion réussie."
                : `Secrets enregistrés. ${result?.message ?? ""}`,
          },
        );
      } catch (failure) {
        setError(errorMessage(failure, "Impossible d’enregistrer les secrets."));
      }
    },
  });
  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void form.handleSubmit();
      }}
    >
      <FieldGroup>
        <StatusLine status={status.data} />
        <p className="text-sm text-muted-foreground">
          Les secrets sont chiffrés par le serveur et ne sont jamais réaffichés. Laissez vide pour
          conserver la valeur actuelle.
        </p>
        <div className="grid gap-4 sm:grid-cols-3">
          <form.Field name="password">
            {(field) => (
              <FormText
                field={field}
                label="Mot de passe"
                type="password"
                autoComplete="new-password"
              />
            )}
          </form.Field>
          <form.Field name="apiKey">
            {(field) => (
              <FormText field={field} label="Clé d’API" type="password" autoComplete="off" />
            )}
          </form.Field>
          <form.Field name="clientSecret">
            {(field) => (
              <FormText field={field} label="Secret client" type="password" autoComplete="off" />
            )}
          </form.Field>
        </div>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <FormFooter error={error} submitting={submitting} label="Enregistrer et tester" />
          )}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}

export function AssetDetailScreen({ id }: { id: string }) {
  const user = useWorkspace();
  const router = useRouter();
  const admin = isAdmin(user);
  const { data: asset, error, loading, refresh } = useRecord("assets", id);
  const [revision, refreshPoints] = useRevision();
  const [token, setToken] = useState<{ token: string; url?: string } | null>(null);
  const areas = useLabels("site-areas");
  const action = useAction();

  if (error && !asset) {
    return (
      <>
        <PageHeader title="Équipement" back={{ href: "/assets", label: "Équipements" }} />
        <ErrorState error={error} onRetry={refresh} />
      </>
    );
  }
  if (loading && !asset) {
    return <LoadingState />;
  }
  if (!asset) {
    return null;
  }
  const connection = (field<Row>(asset, "connection") ?? {}) as Row;
  const connectionType = String(connection.type ?? "NONE");

  return (
    <>
      <PageHeader
        back={{ href: "/assets", label: "Équipements" }}
        title={asset.name}
        description={[
          field(asset, "siteAreaId") ? areas.get(String(field(asset, "siteAreaId"))) : "Sans zone",
          kw(field(asset, "maximumPowerKw")),
          connectionTypes.find((item) => item.value === connectionType)?.label,
        ]
          .filter(Boolean)
          .join(" · ")}
        actions={
          <>
            {canWrite(user) && connectionType !== "NONE" && connectionType !== "PUSH" && (
              <Button
                variant="outline"
                disabled={action.busy}
                onClick={() =>
                  action.run(
                    "refresh",
                    () => api(`assets/${id}/consumptions/refresh`, { method: "POST" }),
                    {
                      success: "Dernière mesure récupérée.",
                      onDone: refreshPoints,
                    },
                  )
                }
              >
                <IconRefresh data-icon="inline-start" />
                Relever maintenant
              </Button>
            )}
            {admin && connectionType !== "NONE" && connectionType !== "PUSH" && (
              <Button
                variant="outline"
                disabled={action.busy}
                onClick={() =>
                  action.run(
                    "check",
                    () =>
                      api<{ ok: boolean; message: string }>(`assets/${id}/connection/check`, {
                        method: "POST",
                      }),
                    {
                      success: (result) =>
                        result?.ok ? "Connexion réussie." : display(result?.message),
                    },
                  )
                }
              >
                <IconPlugConnected data-icon="inline-start" />
                Tester la connexion
              </Button>
            )}
            {admin && connectionType === "PUSH" && (
              <Button
                variant="outline"
                disabled={action.busy}
                onClick={() =>
                  action.run(
                    "token",
                    () =>
                      api<{ token: string; url?: string }>(`assets/${id}/push-token`, {
                        method: "POST",
                      }),
                    {
                      onDone: (result) => setToken(result ?? null),
                    },
                  )
                }
              >
                <IconKey data-icon="inline-start" />
                Générer un jeton d’envoi
              </Button>
            )}
            {admin && (
              <ConfirmButton
                title={`Supprimer ${asset.name} ?`}
                description="L’équipement et ses mesures sont supprimés."
                confirmLabel="Supprimer"
                onConfirm={() =>
                  action.run("delete", () => deleteRecord("assets", asset.id), {
                    success: "Équipement supprimé.",
                    onDone: () => router.replace("/assets"),
                  })
                }
              >
                Supprimer
              </ConfirmButton>
            )}
          </>
        }
      />
      <Tabs defaultValue="measures">
        <TabsList variant="line">
          <TabsTrigger value="measures">Mesures</TabsTrigger>
          {admin && <TabsTrigger value="properties">Propriétés</TabsTrigger>}
          {admin && connectionType !== "NONE" && connectionType !== "PUSH" && (
            <TabsTrigger value="secrets">Identifiants de connexion</TabsTrigger>
          )}
        </TabsList>
        <TabsContent value="measures" className="pt-4">
          <AssetConsumption id={asset.id} revision={revision} />
        </TabsContent>
        {admin && (
          <TabsContent value="properties" className="max-w-3xl pt-4">
            <AssetForm key={asset.revision} record={asset} onSaved={refresh} />
          </TabsContent>
        )}
        {admin && (
          <TabsContent value="secrets" className="max-w-3xl pt-4">
            <ConnectionSecrets id={asset.id} />
          </TabsContent>
        )}
      </Tabs>
      <FormDialog
        open={token !== null}
        onOpenChange={(open) => !open && setToken(null)}
        title="Jeton d’envoi des mesures"
      >
        {token && (
          <SecretReveal
            title="Jeton de l’équipement"
            message="L’équipement envoie ses mesures avec l’en-tête Authorization: Asset <jeton>. Il n’est affiché qu’une fois."
            secret={token.token}
            extra={[
              {
                label: "Adresse d’envoi",
                value:
                  token.url ??
                  `${window.location.origin}/api/v1/public/assets/${asset.id}/consumptions`,
              },
            ]}
          />
        )}
      </FormDialog>
    </>
  );
}
