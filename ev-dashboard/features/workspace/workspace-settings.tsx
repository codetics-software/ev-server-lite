"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { useApi } from "@/hooks/use-api";
import { api } from "@/lib/api";
import { useState, type FormEvent } from "react";

type Workspace = { id: string; slug: string; name: string; active: boolean };

export function WorkspaceSettings() {
  const { data, error } = useApi<Workspace>("workspace");
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState("");
  const [failure, setFailure] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setPending(true);
    setFailure("");
    setMessage("");
    try {
      await api("workspace", { method: "PUT", body: JSON.stringify({ name: form.get("name") }) });
      setMessage("Le nom de l’espace a été enregistré.");
    } catch (error) {
      setFailure(error instanceof Error ? error.message : "Impossible d’enregistrer l’espace.");
    } finally {
      setPending(false);
    }
  }
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
      {data && (
        <form onSubmit={submit} className="max-w-xl">
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="workspace-name">Nom de l’espace</FieldLabel>
              <Input
                id="workspace-name"
                name="name"
                defaultValue={data.name}
                required
                maxLength={200}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="workspace-slug">Identifiant de connexion</FieldLabel>
              <Input id="workspace-slug" value={data.slug} readOnly />
              <FieldDescription>
                Cet identifiant est utilisé pour la connexion et dans les liens des QR codes. Il est
                permanent.
              </FieldDescription>
            </Field>
            <p className="text-sm text-muted-foreground">
              Vos bornes, utilisateurs, sessions, tarifs et paiements appartiennent à cet espace
              client. Vous pouvez utiliser la même adresse de supervision que les autres espaces.
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
      )}
    </>
  );
}
