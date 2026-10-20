"use client";

import { Alert, AlertAction, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import { FieldGroup } from "@/components/ui/field";
import { FormDialog } from "@/features/shared/form-dialog";
import { FormFooter } from "@/features/shared/form-footer";
import { FormSelect } from "@/features/shared/form-fields";
import { ErrorState, LoadingState } from "@/features/shared/states";
import { notify } from "@/features/shared/use-action";
import { useComponent } from "@/features/workspace/workspace-context";
import { useApi, useRevision } from "@/hooks/use-api";
import { api, errorMessage } from "@/lib/api";
import type { RecordItem } from "@/lib/api-types";
import { date } from "@/lib/format";
import { required, rules } from "@/lib/form";
import { IconCar } from "@tabler/icons-react";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import Link from "next/link";
import { useState } from "react";
import { CandidateSummary, enrollWarning } from "./autocharge-panel";
import { autoChargePath, type AutoChargeCandidate, type VehicleId } from "./autocharge-types";

export function AutoChargeOffer({
  vid,
  revision: external = 0,
  onEnrolled,
}: {
  vid?: string | null;
  revision?: number;
  onEnrolled?: () => void;
}) {
  const cars = useComponent("cars");
  const [revision, refresh] = useRevision();
  const [open, setOpen] = useState(false);
  const { data } = useApi<AutoChargeCandidate[]>(
    cars ? "account/autocharge/candidates" : null,
    `${revision}:${external}`,
  );
  const candidates = (data ?? []).filter((candidate) => !vid || candidate.vid === vid);
  if (!candidates.length) {
    return null;
  }
  const latest = candidates[0];
  return (
    <>
      <Alert>
        <IconCar />
        <AlertTitle>Recharge automatique disponible</AlertTitle>
        <AlertDescription>
          {candidates.length > 1
            ? `${candidates.length} véhicules se sont identifiés lors de vos dernières sessions.`
            : `Votre véhicule s’est identifié auprès de la borne ${latest.station_id} le ${date(latest.started_at)}.`}{" "}
          Associez-le à l’un de vos véhicules pour démarrer vos prochaines recharges sans badge.
        </AlertDescription>
        <AlertAction>
          <Button size="sm" variant="outline" onClick={() => setOpen(true)}>
            Activer
          </Button>
        </AlertAction>
      </Alert>
      <FormDialog
        open={open}
        onOpenChange={setOpen}
        title="Activer la recharge automatique"
        description="Associez l’identifiant transmis par la borne à l’un de vos véhicules."
      >
        <OfferBody
          candidates={candidates}
          onEnrolled={() => {
            setOpen(false);
            refresh();
            onEnrolled?.();
          }}
        />
      </FormDialog>
    </>
  );
}

function OfferBody({
  candidates,
  onEnrolled,
}: {
  candidates: AutoChargeCandidate[];
  onEnrolled: () => void;
}) {
  const { data, loading, error } = useApi<RecordItem[] | { items: RecordItem[] }>("account/cars");
  const owned = Array.isArray(data) ? data : (data?.items ?? []);
  if (error && !data) {
    return <ErrorState error={error} />;
  }
  if (loading && !data) {
    return <LoadingState rows={2} />;
  }
  if (!owned.length) {
    return (
      <div className="flex flex-col gap-4">
        <p className="text-sm text-muted-foreground">
          Ajoutez d’abord votre véhicule, puis associez-lui cet identifiant depuis sa fiche.
        </p>
        <Link href="/cars" className={buttonVariants({ className: "w-fit" })}>
          Mes véhicules
        </Link>
      </div>
    );
  }
  return <OfferForm candidates={candidates} owned={owned} onEnrolled={onEnrolled} />;
}

function OfferForm({
  candidates,
  owned,
  onEnrolled,
}: {
  candidates: AutoChargeCandidate[];
  owned: RecordItem[];
  onEnrolled: () => void;
}) {
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: {
      transactionId: String(candidates[0].transaction_id),
      carId: owned.length === 1 ? owned[0].id : "",
    },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        await api<VehicleId>(autoChargePath(value.carId, true), {
          method: "POST",
          body: JSON.stringify({ transactionId: Number(value.transactionId) }),
        });
        notify("Recharge automatique activée pour ce véhicule.");
        onEnrolled();
      } catch (failure) {
        setError(errorMessage(failure, "Impossible d’activer la recharge automatique."));
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
        {candidates.length > 1 ? (
          <form.Field name="transactionId">
            {(field) => (
              <FormSelect
                field={field}
                label="Session"
                options={candidates.map((candidate) => ({
                  value: String(candidate.transaction_id),
                  label: `${candidate.station_id} · ${date(candidate.started_at)} · ${candidate.vid}`,
                }))}
              />
            )}
          </form.Field>
        ) : (
          <div className="rounded-lg border p-3 text-sm">
            <CandidateSummary candidate={candidates[0]} />
          </div>
        )}
        <form.Field
          name="carId"
          validators={{ onDynamic: rules(required("Choisissez un véhicule.")) }}
        >
          {(field) => (
            <FormSelect
              field={field}
              label="Véhicule"
              options={owned.map((car) => ({ value: car.id, label: car.name }))}
            />
          )}
        </form.Field>
        <p className="text-sm text-muted-foreground">{enrollWarning}</p>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => <FormFooter error={error} submitting={submitting} label="Activer" />}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}
