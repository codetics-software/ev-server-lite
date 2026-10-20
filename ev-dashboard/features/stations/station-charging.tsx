"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
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
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { FieldGroup, FieldLegend, FieldSet } from "@/components/ui/field";
import { ConfirmButton } from "@/features/shared/confirm-button";
import { FormSelect, FormText, type Option } from "@/features/shared/form-fields";
import { EmptyState, ErrorState, LoadingState } from "@/features/shared/states";
import { StateBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { useApi, useRevision } from "@/hooks/use-api";
import { api, errorMessage } from "@/lib/api";
import { date, display } from "@/lib/format";
import { integerBetween, matches, optionalNumber, required, rules } from "@/lib/form";
import { IconChartLine, IconPlus, IconTrash } from "@tabler/icons-react";
import { revalidateLogic, useForm, useStore } from "@tanstack/react-form";
import { useState } from "react";
import { ProfileChart } from "./profile-chart";
import type { ChargingProfile, OcppChargingProfile, Station } from "./station-types";

function targets(station: Station): Option[] {
  return [
    { value: "0", label: "Toute la borne" },
    ...(station.connectors ?? []).map((connector) => ({
      value: String(connector.connector_id),
      label: `Connecteur ${connector.connector_id}`,
    })),
  ];
}

export function StationCharging({ station, canEdit }: { station: Station; canEdit: boolean }) {
  const [revision, refresh] = useRevision();
  const { data, loading, error } = useApi<ChargingProfile[]>(
    `charging-profiles?stationId=${encodeURIComponent(station.id)}`,
    revision,
  );
  const [creating, setCreating] = useState(false);
  const action = useAction();
  const profiles = data ?? [];

  return (
    <div className="flex flex-col gap-6">
      {canEdit && <PowerLimit station={station} />}
      <section className="flex flex-col gap-4" aria-labelledby="profiles-heading">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 id="profiles-heading" className="text-base font-semibold">
              Profils de recharge
            </h2>
            <p className="text-sm text-muted-foreground">
              Programmes de puissance envoyés à la borne (SetChargingProfile).
            </p>
          </div>
          {canEdit && (
            <Button variant="outline" onClick={() => setCreating(true)}>
              <IconPlus data-icon="inline-start" />
              Nouveau profil
            </Button>
          )}
        </div>
        {error && !data ? (
          <ErrorState error={error} onRetry={refresh} />
        ) : loading && !data ? (
          <LoadingState rows={2} />
        ) : !profiles.length ? (
          <EmptyState
            icon={<IconChartLine />}
            title="Aucun profil de recharge"
            description="Sans profil, la borne délivre sa puissance maximale."
          />
        ) : (
          <div className="grid gap-4 lg:grid-cols-2">
            {profiles.map((profile) => (
              <Card key={profile.id}>
                <CardHeader>
                  <CardTitle>
                    Profil {profile.profile_id} ·{" "}
                    {profile.connector_id ? `connecteur ${profile.connector_id}` : "toute la borne"}
                  </CardTitle>
                  <CardDescription>
                    {display(profile.purpose)} · niveau {profile.stack_level} ·{" "}
                    {date(profile.created_at)}
                  </CardDescription>
                  <CardAction className="flex items-center gap-2">
                    {profile.command_state && <StateBadge state={profile.command_state} />}
                    {canEdit && (
                      <ConfirmButton
                        title="Supprimer ce profil ?"
                        description="La borne reçoit une demande de suppression du profil."
                        confirmLabel="Supprimer"
                        variant="ghost"
                        size="icon-sm"
                        ariaLabel="Supprimer le profil"
                        onConfirm={() =>
                          action.run(
                            profile.id,
                            () => api(`charging-profiles/${profile.id}`, { method: "DELETE" }),
                            { success: "Profil supprimé.", onDone: refresh },
                          )
                        }
                      >
                        <IconTrash />
                      </ConfirmButton>
                    )}
                  </CardAction>
                </CardHeader>
                <CardContent>
                  <ProfileChart profile={profile.profile} />
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </section>
      <Dialog open={creating} onOpenChange={setCreating}>
        <DialogContent className="sm:max-w-2xl">
          {creating && (
            <ProfileForm
              station={station}
              onDone={() => {
                setCreating(false);
                refresh();
              }}
            />
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

function PowerLimit({ station }: { station: Station }) {
  const action = useAction();
  const form = useForm({
    defaultValues: { connectorId: "0", limitKw: "" },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      await action.run(
        "limit",
        () =>
          api(`stations/${encodeURIComponent(station.id)}/power-limit`, {
            method: "PUT",
            body: JSON.stringify({
              connectorId: Number(value.connectorId),
              limitKw: Number(value.limitKw),
            }),
          }),
        { success: "Limitation de puissance envoyée à la borne.", failure: "Limitation refusée" },
      );
    },
  });
  return (
    <Card>
      <CardHeader>
        <CardTitle>Limiter la puissance</CardTitle>
        <CardDescription>
          Applique immédiatement un plafond de puissance à la borne ou à un connecteur.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void form.handleSubmit();
          }}
          className="grid items-end gap-4 sm:grid-cols-[1fr_12rem_auto]"
        >
          <form.Field name="connectorId">
            {(field) => <FormSelect field={field} label="Cible" options={targets(station)} />}
          </form.Field>
          <form.Field
            name="limitKw"
            validators={{
              onDynamic: rules(
                required("Indiquez une puissance."),
                optionalNumber(0, 10000, "Puissance invalide."),
              ),
            }}
          >
            {(field) => <FormText field={field} label="Puissance (kW)" type="number" min={0} />}
          </form.Field>
          <form.Subscribe selector={(state) => state.isSubmitting}>
            {(submitting) => (
              <Button type="submit" disabled={submitting}>
                {submitting ? "Envoi…" : "Appliquer"}
              </Button>
            )}
          </form.Subscribe>
        </form>
      </CardContent>
    </Card>
  );
}

type PeriodDraft = { start: string; limit: string };

function nextProfileId() {
  return String(Math.floor(Date.now() / 1000) % 100000);
}

function seconds(clock: string) {
  const [hours, minutes] = clock.split(":").map(Number);
  return (hours || 0) * 3600 + (minutes || 0) * 60;
}

function buildProfile(value: {
  profileId: string;
  stackLevel: string;
  purpose: string;
  kind: string;
  unit: string;
  periods: PeriodDraft[];
}): OcppChargingProfile {
  const recurring = value.kind === "Recurring";
  return {
    chargingProfileId: Number(value.profileId),
    stackLevel: Number(value.stackLevel),
    chargingProfilePurpose: value.purpose,
    chargingProfileKind: value.kind,
    ...(recurring ? { recurrencyKind: "Daily" } : {}),
    chargingSchedule: {
      ...(recurring
        ? {
            duration: 86400,
            startSchedule: new Date(new Date().setHours(0, 0, 0, 0)).toISOString(),
          }
        : {}),
      chargingRateUnit: value.unit === "A" ? "A" : "W",
      chargingSchedulePeriod: value.periods
        .filter((period) => period.limit !== "")
        .map((period) => ({
          startPeriod: seconds(period.start),
          limit: value.unit === "A" ? Number(period.limit) : Number(period.limit) * 1000,
        }))
        .sort((left, right) => left.startPeriod - right.startPeriod),
    },
  };
}

function ProfileForm({ station, onDone }: { station: Station; onDone: () => void }) {
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: {
      connectorId: "0",
      profileId: nextProfileId(),
      stackLevel: "1",
      purpose: "TxDefaultProfile",
      kind: "Recurring",
      unit: "W",
      periods: [
        { start: "00:00", limit: "22" },
        { start: "08:00", limit: "11" },
        { start: "20:00", limit: "22" },
      ] as PeriodDraft[],
    },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        await api("charging-profiles", {
          method: "POST",
          body: JSON.stringify({
            stationId: station.id,
            connectorId: Number(value.connectorId),
            profile: buildProfile(value),
          }),
        });
        onDone();
      } catch (failure) {
        setError(errorMessage(failure, "Le profil n’a pas pu être envoyé."));
      }
    },
  });
  const values = useStore(form.store, (state) => state.values);
  const unitLabel = values.unit === "A" ? "Intensité (A)" : "Puissance (kW)";

  return (
    <form
      noValidate
      className="flex flex-col gap-5"
      onSubmit={(event) => {
        event.preventDefault();
        void form.handleSubmit();
      }}
    >
      <DialogHeader>
        <DialogTitle>Nouveau profil de recharge</DialogTitle>
        <DialogDescription>{station.name} · programme de puissance OCPP 1.6</DialogDescription>
      </DialogHeader>
      <FieldGroup>
        <div className="grid gap-4 sm:grid-cols-3">
          <form.Field name="connectorId">
            {(field) => <FormSelect field={field} label="Cible" options={targets(station)} />}
          </form.Field>
          <form.Field name="purpose">
            {(field) => (
              <FormSelect
                field={field}
                label="Usage"
                options={[
                  { value: "TxDefaultProfile", label: "Profil par défaut" },
                  { value: "ChargePointMaxProfile", label: "Limite de la borne" },
                ]}
              />
            )}
          </form.Field>
          <form.Field
            name="stackLevel"
            validators={{ onDynamic: rules(integerBetween(0, 99, "Niveau entre 0 et 99.")) }}
          >
            {(field) => <FormText field={field} label="Niveau de priorité" type="number" min={0} />}
          </form.Field>
          <form.Field name="kind">
            {(field) => (
              <FormSelect
                field={field}
                label="Répétition"
                options={[
                  { value: "Recurring", label: "Chaque jour" },
                  { value: "Absolute", label: "Une seule fois, à partir de maintenant" },
                ]}
              />
            )}
          </form.Field>
          <form.Field name="unit">
            {(field) => (
              <FormSelect
                field={field}
                label="Unité"
                options={[
                  { value: "W", label: "Puissance (kW)" },
                  { value: "A", label: "Intensité (A)" },
                ]}
              />
            )}
          </form.Field>
          <form.Field
            name="profileId"
            validators={{ onDynamic: rules(integerBetween(1, 2147483647, "Numéro invalide.")) }}
          >
            {(field) => <FormText field={field} label="Numéro du profil" type="number" min={1} />}
          </form.Field>
        </div>
        <form.Field name="periods" mode="array">
          {(periods) => (
            <FieldSet>
              <FieldLegend variant="label">Périodes</FieldLegend>
              {periods.state.value.map((_, index) => (
                <div key={index} className="grid items-end gap-3 sm:grid-cols-[1fr_1fr_auto]">
                  <form.Field
                    name={`periods[${index}].start`}
                    validators={{
                      onDynamic: rules(matches(/^([01]\d|2[0-3]):[0-5]\d$/, "Heure invalide.")),
                    }}
                  >
                    {(field) => <FormText field={field} label="Début" type="time" />}
                  </form.Field>
                  <form.Field
                    name={`periods[${index}].limit`}
                    validators={{ onDynamic: rules(optionalNumber(0, 10000, "Valeur invalide.")) }}
                  >
                    {(field) => <FormText field={field} label={unitLabel} type="number" min={0} />}
                  </form.Field>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={`Retirer la période ${index + 1}`}
                    disabled={periods.state.value.length <= 1}
                    onClick={() => periods.removeValue(index)}
                  >
                    <IconTrash />
                  </Button>
                </div>
              ))}
              <Button
                type="button"
                variant="outline"
                className="w-fit"
                onClick={() => periods.pushValue({ start: "12:00", limit: "" })}
              >
                <IconPlus data-icon="inline-start" />
                Ajouter une période
              </Button>
            </FieldSet>
          )}
        </form.Field>
        <ProfileChart profile={buildProfile(values)} className="h-40 w-full" />
        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
      </FieldGroup>
      <DialogFooter>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <Button type="submit" disabled={submitting}>
              {submitting ? "Envoi…" : "Envoyer le profil"}
            </Button>
          )}
        </form.Subscribe>
      </DialogFooter>
    </form>
  );
}
