"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { useApi } from "@/hooks/use-api";
import { api } from "@/lib/api";
import { isInvalid, maxLength, required, rules } from "@/lib/form";
import { revalidateLogic, useForm, useStore } from "@tanstack/react-form";
import { useState } from "react";

type Workspace = { id: string; slug: string; name: string; active: boolean };

const nameRule = "Le nom de l’espace doit contenir entre 1 et 200 caractères.";

export function WorkspaceSettings() {
  const { data, error } = useApi<Workspace>("workspace");
  const [failure, setFailure] = useState("");
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Espace client</h1>
          <p>L’identité de votre réseau dans la supervision et les portails de recharge.</p>
        </div>
      </div>
      {(error || failure) && (
        <Alert variant="destructive">
          <AlertDescription>{failure || error?.message}</AlertDescription>
        </Alert>
      )}
      {data && <WorkspaceForm workspace={data} onFailure={setFailure} />}
    </>
  );
}

function WorkspaceForm({
  workspace,
  onFailure,
}: {
  workspace: Workspace;
  onFailure: (message: string) => void;
}) {
  const [message, setMessage] = useState("");
  const form = useForm({
    defaultValues: { name: workspace.name },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      onFailure("");
      setMessage("");
      try {
        await api("workspace", { method: "PUT", body: JSON.stringify({ name: value.name }) });
        setMessage("Le nom de l’espace a été enregistré.");
      } catch (error) {
        onFailure(error instanceof Error ? error.message : "Impossible d’enregistrer l’espace.");
      }
    },
  });
  const pending = useStore(form.store, (state) => state.isSubmitting);

  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void form.handleSubmit();
      }}
      className="max-w-xl"
    >
      <FieldGroup>
        <form.Field
          name="name"
          validators={{ onDynamic: rules(required(nameRule), maxLength(200, nameRule)) }}
        >
          {(field) => {
            const invalid = isInvalid(field);
            return (
              <Field data-invalid={invalid}>
                <FieldLabel htmlFor="workspace-name">Nom de l’espace</FieldLabel>
                <Input
                  id="workspace-name"
                  name={field.name}
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(event) => field.handleChange(event.target.value)}
                  aria-invalid={invalid}
                  required
                  maxLength={200}
                />
                {invalid && <FieldError errors={field.state.meta.errors} />}
              </Field>
            );
          }}
        </form.Field>
        <Field>
          <FieldLabel htmlFor="workspace-slug">Identifiant de connexion</FieldLabel>
          <Input id="workspace-slug" value={workspace.slug} readOnly />
          <FieldDescription>
            Cet identifiant est utilisé pour la connexion et dans les liens des QR codes. Il est
            permanent.
          </FieldDescription>
        </Field>
        <p className="text-sm text-muted-foreground">
          Vos bornes, utilisateurs, sessions, tarifs et paiements appartiennent à cet espace client.
          Vous pouvez utiliser la même adresse de supervision que les autres espaces.
        </p>
        {message && (
          <p role="status" className="text-sm">
            {message}
          </p>
        )}
        <Button type="submit" disabled={pending}>
          {pending ? "Enregistrement…" : "Enregistrer"}
        </Button>
      </FieldGroup>
    </form>
  );
}
