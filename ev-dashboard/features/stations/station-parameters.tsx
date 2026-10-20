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
import { FieldGroup } from "@/components/ui/field";
import { ExportButton } from "@/features/shared/csv-actions";
import { DataTable, type Column } from "@/features/shared/data-table";
import { FormText } from "@/features/shared/form-fields";
import { SearchInput } from "@/features/shared/search-input";
import { EmptyState, ListState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { useApi, useRevision } from "@/hooks/use-api";
import { api } from "@/lib/api";
import { date } from "@/lib/format";
import { maxLength, rules } from "@/lib/form";
import { IconPencil, IconRefresh, IconSettings } from "@tabler/icons-react";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import { useState } from "react";
import type { StationParameter } from "./station-types";

export function StationParameters({ stationId, canEdit }: { stationId: string; canEdit: boolean }) {
  const [revision, refresh] = useRevision();
  const path = `stations/${encodeURIComponent(stationId)}/parameters`;
  const { data, loading, error } = useApi<StationParameter[]>(path, revision);
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<StationParameter | null>(null);
  const action = useAction();
  const items = (data ?? []).filter((parameter) =>
    `${parameter.key} ${parameter.value ?? ""}`.toLowerCase().includes(search.toLowerCase()),
  );
  const columns: Column<StationParameter>[] = [
    {
      id: "key",
      header: "Paramètre",
      sort: (parameter) => parameter.key,
      cell: (parameter) => <code className="text-xs">{parameter.key}</code>,
    },
    {
      id: "value",
      header: "Valeur",
      sort: (parameter) => parameter.value ?? "",
      cell: (parameter) => (
        <span className="block max-w-96 break-all font-mono text-xs">{parameter.value ?? "—"}</span>
      ),
    },
    {
      id: "access",
      header: "Accès",
      cell: (parameter) =>
        parameter.readonly ? (
          <ToneBadge tone="neutral">Lecture seule</ToneBadge>
        ) : (
          <ToneBadge tone="success">Modifiable</ToneBadge>
        ),
    },
    {
      id: "updated",
      header: "Lu le",
      sort: (parameter) => parameter.updated_at ?? "",
      cell: (parameter) => (
        <span className="text-muted-foreground">{date(parameter.updated_at)}</span>
      ),
    },
    {
      id: "actions",
      header: "Actions",
      srOnlyHeader: true,
      align: "right",
      cell: (parameter) =>
        canEdit &&
        !parameter.readonly && (
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={`Modifier ${parameter.key}`}
            onClick={() => setEditing(parameter)}
          >
            <IconPencil />
          </Button>
        ),
    },
  ];

  return (
    <section className="flex flex-col gap-4" aria-label="Paramètres OCPP">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <SearchInput value={search} onChange={setSearch} label="Filtrer les paramètres" />
        <div className="flex flex-wrap gap-2">
          {canEdit && (
            <Button
              variant="outline"
              disabled={action.busy}
              onClick={() =>
                action.run("refresh", () => api(`${path}/refresh`, { method: "POST" }), {
                  success: "Lecture de la configuration demandée à la borne.",
                  onDone: () => setTimeout(refresh, 4000),
                })
              }
            >
              <IconRefresh data-icon="inline-start" />
              Relire la configuration
            </Button>
          )}
          <ExportButton
            path="stations/parameters/export"
            parameters={{ stationId }}
            filename={`parametres-${stationId}`}
          />
        </div>
      </div>
      <ListState
        data={data}
        items={items}
        loading={loading}
        error={error}
        onRetry={refresh}
        empty={
          <EmptyState
            icon={<IconSettings />}
            title={search ? "Aucun paramètre correspondant" : "Configuration non lue"}
            description={
              search
                ? "Essayez une autre recherche."
                : "Demandez la lecture de la configuration : les valeurs apparaissent dès la réponse de la borne."
            }
          />
        }
      >
        <DataTable
          label="Paramètres OCPP"
          rows={items}
          columns={columns}
          rowKey={(parameter) => parameter.key}
        />
      </ListState>
      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent>
          {editing && (
            <ParameterForm
              path={path}
              parameter={editing}
              onDone={() => {
                setEditing(null);
                setTimeout(refresh, 3000);
              }}
            />
          )}
        </DialogContent>
      </Dialog>
    </section>
  );
}

function ParameterForm({
  path,
  parameter,
  onDone,
}: {
  path: string;
  parameter: StationParameter;
  onDone: () => void;
}) {
  const action = useAction();
  const form = useForm({
    defaultValues: { value: parameter.value ?? "" },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      await action.run(
        "save",
        () =>
          api(path, {
            method: "PUT",
            body: JSON.stringify({ key: parameter.key, value: value.value }),
          }),
        {
          success: "Modification envoyée à la borne.",
          failure: "La modification n’a pas pu être envoyée",
          onDone,
        },
      );
    },
  });
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
        <DialogTitle>Modifier {parameter.key}</DialogTitle>
        <DialogDescription>
          La valeur est enregistrée lorsque la borne accepte la modification (ChangeConfiguration).
        </DialogDescription>
      </DialogHeader>
      <FieldGroup>
        <form.Field name="value" validators={{ onDynamic: rules(maxLength(500)) }}>
          {(field) => <FormText field={field} label="Nouvelle valeur" maxLength={500} />}
        </form.Field>
      </FieldGroup>
      <DialogFooter>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <Button type="submit" disabled={submitting}>
              {submitting ? "Envoi…" : "Envoyer"}
            </Button>
          )}
        </form.Subscribe>
      </DialogFooter>
    </form>
  );
}
