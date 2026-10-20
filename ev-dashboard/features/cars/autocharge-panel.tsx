"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { FieldGroup } from "@/components/ui/field";
import { ConfirmButton } from "@/features/shared/confirm-button";
import { DataTable, type Column } from "@/features/shared/data-table";
import { FormFooter } from "@/features/shared/form-footer";
import { FormSelect, FormText } from "@/features/shared/form-fields";
import { field as dataField } from "@/features/shared/records";
import { ErrorState, ListState, LoadingState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { notify, useAction } from "@/features/shared/use-action";
import { canWrite, isAdmin, useWorkspace } from "@/features/workspace/workspace-context";
import { useApi, useRevision } from "@/hooks/use-api";
import { api, errorMessage } from "@/lib/api";
import type { RecordItem } from "@/lib/api-types";
import { date, relative } from "@/lib/format";
import { integerBetween, matches, required, rules } from "@/lib/form";
import { IconTrash } from "@tabler/icons-react";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import { useState } from "react";
import {
  VID_PATTERN,
  autoChargePath,
  type AutoChargeCandidate,
  type VehicleId,
} from "./autocharge-types";

export const enrollWarning =
  "Les bornes dont la recharge automatique est activée démarreront la recharge dès le branchement de ce véhicule, sans badge, aux frais de son conducteur. L’identifiant du véhicule n’est pas un secret : révoquez-le si vous vendez ou prêtez le véhicule.";

export function CandidateSummary({ candidate }: { candidate: AutoChargeCandidate }) {
  return (
    <span className="flex min-w-0 flex-col">
      <span className="font-medium">
        {candidate.station_id} · connecteur {candidate.connector_id}
      </span>
      <span className="text-xs text-muted-foreground">
        Session n° {candidate.transaction_id} · {date(candidate.started_at)} ·{" "}
        <span className="font-mono">{candidate.vid}</span>
      </span>
    </span>
  );
}

export function AutoChargePanel({
  car,
  mine,
  onChanged,
}: {
  car: RecordItem;
  mine: boolean;
  onChanged?: () => void;
}) {
  const user = useWorkspace();
  const [revision, reload] = useRevision();
  const refresh = () => {
    reload();
    onChanged?.();
  };
  const action = useAction();
  const path = autoChargePath(car.id, mine);
  const { data, loading, error } = useApi<VehicleId[]>(path, revision);
  const candidates = useApi<AutoChargeCandidate[]>(
    mine ? "account/autocharge/candidates" : null,
    revision,
  );
  const items = data ?? [];
  const owned = mine || Boolean(dataField(car, "userId"));
  const revocable = mine || canWrite(user);
  const columns: Column<VehicleId>[] = [
    {
      id: "uid",
      header: "Identifiant du véhicule",
      cell: (vehicle) => (
        <span className="flex flex-wrap items-center gap-2">
          <span className="font-mono text-xs">{vehicle.uid}</span>
          {!vehicle.enabled && <ToneBadge tone="neutral">Bloqué</ToneBadge>}
        </span>
      ),
    },
    {
      id: "used",
      header: "Dernière utilisation",
      cell: (vehicle) => (vehicle.last_used_at ? relative(vehicle.last_used_at) : "Jamais"),
    },
    ...(revocable
      ? [
          {
            id: "actions",
            header: "Actions",
            srOnlyHeader: true,
            align: "right" as const,
            cell: (vehicle: VehicleId) => (
              <ConfirmButton
                title="Révoquer cet identifiant ?"
                description="Les bornes ne démarreront plus automatiquement la recharge de ce véhicule. Un badge ou l’application restent nécessaires."
                confirmLabel="Révoquer"
                variant="ghost"
                size="icon-sm"
                ariaLabel={`Révoquer ${vehicle.uid}`}
                onConfirm={() =>
                  action.run(
                    vehicle.uid,
                    () => api(`${path}/${encodeURIComponent(vehicle.uid)}`, { method: "DELETE" }),
                    { success: "Identifiant du véhicule révoqué.", onDone: refresh },
                  )
                }
              >
                <IconTrash />
              </ConfirmButton>
            ),
          },
        ]
      : []),
  ];

  return (
    <section className="flex flex-col gap-4 border-t pt-4" aria-labelledby={`autocharge-${car.id}`}>
      <div>
        <h3 id={`autocharge-${car.id}`} className="text-base font-semibold">
          Recharge automatique
        </h3>
        <p className="text-sm text-muted-foreground">
          Sur les bornes compatibles, la recharge démarre dès le branchement du véhicule, sans
          badge. L’identifiant transmis par le véhicule n’est pas un secret : ne l’associez qu’à vos
          propres véhicules.
        </p>
      </div>
      <ListState
        data={data}
        items={items}
        loading={loading}
        error={error}
        onRetry={refresh}
        empty={
          <p className="text-sm text-muted-foreground">
            Aucun identifiant de véhicule n’est associé.
          </p>
        }
      >
        <DataTable
          label="Identifiants du véhicule"
          rows={items}
          columns={columns}
          rowKey={(vehicle) => vehicle.uid}
        />
      </ListState>
      {!owned ? (
        <Alert>
          <AlertTitle>Conducteur requis</AlertTitle>
          <AlertDescription>
            Attribuez ce véhicule à un conducteur pour activer la recharge automatique : les
            sessions démarrées ainsi sont rattachées à son compte.
          </AlertDescription>
        </Alert>
      ) : mine ? (
        <CandidateList
          carId={car.id}
          candidates={candidates.data}
          loading={candidates.loading}
          error={candidates.error}
          onRetry={refresh}
          onEnrolled={refresh}
        />
      ) : (
        isAdmin(user) && <EnrollForm path={path} onEnrolled={refresh} />
      )}
    </section>
  );
}

function CandidateList({
  carId,
  candidates,
  loading,
  error,
  onRetry,
  onEnrolled,
}: {
  carId: string;
  candidates: AutoChargeCandidate[] | null;
  loading: boolean;
  error: Error | null;
  onRetry: () => void;
  onEnrolled: () => void;
}) {
  const action = useAction();
  if (error && !candidates) {
    return <ErrorState error={error} onRetry={onRetry} />;
  }
  if (loading && !candidates) {
    return <LoadingState rows={1} />;
  }
  if (!candidates?.length) {
    return (
      <p className="text-sm text-muted-foreground">
        Pour associer ce véhicule, branchez-le sur une borne où la recharge automatique est activée
        et démarrez la session avec votre badge ou l’application. La session apparaît ici pendant 24
        heures.
      </p>
    );
  }
  return (
    <div className="flex flex-col gap-2">
      <p className="text-sm font-medium">Sessions récentes ayant identifié un véhicule</p>
      <ul className="flex flex-col gap-2">
        {candidates.map((candidate) => (
          <li
            key={candidate.vid}
            className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3"
          >
            <CandidateSummary candidate={candidate} />
            <ConfirmButton
              title="Associer cet identifiant au véhicule ?"
              description={enrollWarning}
              confirmLabel="Associer"
              destructive={false}
              size="sm"
              disabled={action.busy}
              onConfirm={() =>
                action.run(
                  String(candidate.transaction_id),
                  () =>
                    api<VehicleId>(autoChargePath(carId, true), {
                      method: "POST",
                      body: JSON.stringify({ transactionId: candidate.transaction_id }),
                    }),
                  {
                    success: "Recharge automatique activée pour ce véhicule.",
                    failure: "Association impossible",
                    onDone: onEnrolled,
                  },
                )
              }
            >
              Associer
            </ConfirmButton>
          </li>
        ))}
      </ul>
    </div>
  );
}

const enrollModes = [
  { value: "session", label: "Depuis une session de recharge" },
  { value: "vid", label: "Saisie manuelle de l’identifiant" },
] as const;

function EnrollForm({ path, onEnrolled }: { path: string; onEnrolled: () => void }) {
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: { mode: "session", value: "" },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value, formApi }) => {
      setError("");
      try {
        await api<VehicleId>(path, {
          method: "POST",
          body: JSON.stringify(
            value.mode === "session"
              ? { transactionId: Number(value.value) }
              : { vid: value.value.trim().toUpperCase() },
          ),
        });
        notify("Recharge automatique activée pour ce véhicule.");
        formApi.reset();
        onEnrolled();
      } catch (failure) {
        setError(errorMessage(failure, "Impossible d’associer l’identifiant du véhicule."));
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
        <form.Field name="mode" listeners={{ onChange: () => form.setFieldValue("value", "") }}>
          {(field) => (
            <FormSelect field={field} label="Associer un identifiant" options={enrollModes} />
          )}
        </form.Field>
        <form.Subscribe selector={(state) => state.values.mode}>
          {(mode) => (
            <form.Field
              name="value"
              validators={{
                onDynamic: ({ value, fieldApi }) =>
                  fieldApi.form.getFieldValue("mode") === "session"
                    ? rules(
                        required("Indiquez le numéro de la session."),
                        integerBetween(1, 2147483647, "Numéro de session invalide."),
                      )({ value })
                    : rules(
                        required("Indiquez l’identifiant du véhicule."),
                        matches(
                          VID_PATTERN,
                          "VID: suivi de 12 caractères hexadécimaux, par exemple VID:92D50A693F80.",
                        ),
                      )({ value: value.trim() }),
              }}
            >
              {(field) =>
                mode === "session" ? (
                  <FormText
                    field={field}
                    label="Numéro de session"
                    type="number"
                    min={1}
                    description="Session du conducteur de moins de 24 heures, pendant laquelle la borne a transmis l’identifiant du véhicule."
                  />
                ) : (
                  <FormText
                    field={field}
                    label="Identifiant du véhicule"
                    placeholder="VID:92D50A693F80"
                    maxLength={40}
                    className="font-mono"
                    description="Préférez une session : l’identifiant est alors celui réellement transmis par la borne. Une saisie erronée peut autoriser un autre véhicule."
                  />
                )
              }
            </form.Field>
          )}
        </form.Subscribe>
        <p className="text-sm text-muted-foreground">{enrollWarning}</p>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <FormFooter error={error} submitting={submitting} label="Associer l’identifiant" />
          )}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}
