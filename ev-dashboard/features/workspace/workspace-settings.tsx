"use client";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Field,
  FieldContent,
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { CodeticsMark } from "@/components/codetics-mark";
import { FormText } from "@/features/shared/form-fields";
import { FormFooter } from "@/features/shared/form-footer";
import { PageHeader } from "@/features/shared/page-header";
import { LoadingState } from "@/features/shared/states";
import { notify, notifyError, useAction } from "@/features/shared/use-action";
import { api, errorMessage, upload } from "@/lib/api";
import { componentKeys, type ComponentKey, type Components, type Workspace } from "@/lib/api-types";
import { maxLength, required, rules } from "@/lib/form";
import { componentLabels } from "@/lib/labels";
import { IconPhotoUp, IconTrash } from "@tabler/icons-react";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import Image from "next/image";
import { useId, useState } from "react";
import { useWorkspaceSession, workspaceLogo } from "./workspace-context";

const nameRule = "Le nom de l’espace doit contenir entre 1 et 200 caractères.";

const camel: Record<ComponentKey, string> = {
  organization: "organization",
  pricing: "pricing",
  billing: "billing",
  roaming: "roaming",
  smart_charging: "smartCharging",
  statistics: "statistics",
  assets: "assets",
  cars: "cars",
  public_charging: "publicCharging",
};

export function componentsInput(components: Components) {
  return Object.fromEntries(componentKeys.map((key) => [camel[key], components[key]]));
}

function NameForm({ workspace, onSaved }: { workspace: Workspace; onSaved: () => void }) {
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: { name: workspace.name },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        await api("workspace", {
          method: "PUT",
          body: JSON.stringify({ name: value.name.trim() }),
        });
        notify("Le nom de l’espace a été enregistré.");
        onSaved();
      } catch (failure) {
        setError(errorMessage(failure, "Impossible d’enregistrer l’espace."));
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
        <form.Field
          name="name"
          validators={{ onDynamic: rules(required(nameRule), maxLength(200, nameRule)) }}
        >
          {(field) => <FormText field={field} label="Nom de l’espace" maxLength={200} required />}
        </form.Field>
        <Field>
          <FieldLabel htmlFor="workspace-slug">Identifiant de connexion</FieldLabel>
          <Input id="workspace-slug" value={workspace.slug} readOnly />
          <FieldDescription>
            Utilisé pour la connexion et dans les liens des QR codes. Il est permanent.
          </FieldDescription>
        </Field>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => <FormFooter error={error} submitting={submitting} />}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}

function Logo({
  workspace,
  revision,
  onSaved,
}: {
  workspace: Workspace;
  revision: number;
  onSaved: () => void;
}) {
  const id = useId();
  const action = useAction();
  const logo = workspaceLogo(workspace, revision);

  async function choose(file: File | undefined) {
    if (!file) {
      return;
    }
    if (!["image/png", "image/jpeg", "image/webp"].includes(file.type)) {
      notifyError("Format non pris en charge", "Choisissez une image PNG, JPEG ou WebP.");
      return;
    }
    if (file.size > 256 * 1024) {
      notifyError("Image trop volumineuse", "Le logo ne doit pas dépasser 256 Ko.");
      return;
    }
    await action.run("upload", () => upload("workspace/logo", file, file.type, "PUT"), {
      success: "Logo enregistré.",
      onDone: onSaved,
    });
  }

  return (
    <div className="flex flex-wrap items-center gap-5">
      <div className="flex size-20 items-center justify-center rounded-xl border bg-muted/40">
        {logo ? (
          <Image
            src={logo}
            alt="Logo de l’espace"
            width={72}
            height={72}
            unoptimized
            className="size-16 object-contain"
          />
        ) : (
          <CodeticsMark />
        )}
      </div>
      <div className="flex flex-col gap-2">
        <div className="flex flex-wrap gap-2">
          <Button
            variant="outline"
            disabled={action.busy}
            render={<label htmlFor={id} />}
            nativeButton={false}
          >
            <IconPhotoUp data-icon="inline-start" />
            {logo ? "Remplacer le logo" : "Téléverser un logo"}
          </Button>
          <input
            id={id}
            type="file"
            accept="image/png,image/jpeg,image/webp"
            className="sr-only"
            onChange={(event) => {
              void choose(event.target.files?.[0]);
              event.target.value = "";
            }}
          />
          {logo && (
            <Button
              variant="ghost"
              disabled={action.busy}
              onClick={() =>
                action.run("delete", () => api("workspace/logo", { method: "DELETE" }), {
                  success: "Logo retiré.",
                  onDone: onSaved,
                })
              }
            >
              <IconTrash data-icon="inline-start" />
              Retirer
            </Button>
          )}
        </div>
        <p className="text-xs text-muted-foreground">
          PNG, JPEG ou WebP, 256 Ko au plus. Affiché dans la navigation, sur la page de connexion et
          sur les portails de recharge.
        </p>
      </div>
    </div>
  );
}

export function ComponentToggles({
  value,
  onChange,
  disabled,
}: {
  value: Components;
  onChange: (next: Components) => void;
  disabled?: boolean;
}) {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {componentKeys.map((key) => (
        <Field key={key} orientation="horizontal">
          <Switch
            id={`component-${key}`}
            checked={value[key]}
            disabled={disabled}
            onCheckedChange={(checked) => onChange({ ...value, [key]: checked === true })}
          />
          <FieldContent>
            <FieldLabel htmlFor={`component-${key}`}>
              {componentLabels[key]?.label ?? key}
            </FieldLabel>
            <FieldDescription>{componentLabels[key]?.description}</FieldDescription>
          </FieldContent>
        </Field>
      ))}
    </div>
  );
}

function ComponentsForm({ components, onSaved }: { components: Components; onSaved: () => void }) {
  const [draft, setDraft] = useState<Components | null>(null);
  const action = useAction();
  const value = draft ?? components;
  return (
    <div className="flex flex-col gap-5">
      <ComponentToggles value={value} onChange={setDraft} />
      <div className="flex gap-2">
        <Button
          disabled={!draft || action.busy}
          onClick={() =>
            action.run(
              "components",
              () =>
                api("workspace/components", {
                  method: "PUT",
                  body: JSON.stringify(componentsInput(value)),
                }),
              {
                success: "Fonctionnalités enregistrées.",
                onDone: () => {
                  setDraft(null);
                  onSaved();
                },
              },
            )
          }
        >
          Enregistrer
        </Button>
        {draft && (
          <Button variant="ghost" onClick={() => setDraft(null)}>
            Annuler
          </Button>
        )}
      </div>
    </div>
  );
}

export function WorkspaceSettings() {
  const { workspace, components, refreshWorkspace, revision } = useWorkspaceSession();
  return (
    <>
      <PageHeader
        title="Espace client"
        description="L’identité de votre réseau et les fonctionnalités proposées à vos utilisateurs."
      />
      {!workspace ? (
        <LoadingState rows={4} />
      ) : (
        <div className="grid items-start gap-6 xl:grid-cols-[1fr_1.4fr]">
          <div className="flex flex-col gap-6">
            <Card>
              <CardHeader>
                <CardTitle>Identité</CardTitle>
                <CardDescription>
                  Nom affiché aux utilisateurs et identifiant de connexion.
                </CardDescription>
              </CardHeader>
              <CardContent>
                <NameForm key={workspace.name} workspace={workspace} onSaved={refreshWorkspace} />
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>Logo</CardTitle>
              </CardHeader>
              <CardContent>
                <Logo workspace={workspace} revision={revision} onSaved={refreshWorkspace} />
              </CardContent>
            </Card>
          </div>
          <Card>
            <CardHeader>
              <CardTitle>Fonctionnalités</CardTitle>
              <CardDescription>
                Les fonctionnalités désactivées disparaissent de la navigation de tous les
                utilisateurs.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <ComponentsForm components={components} onSaved={refreshWorkspace} />
            </CardContent>
          </Card>
        </div>
      )}
    </>
  );
}
