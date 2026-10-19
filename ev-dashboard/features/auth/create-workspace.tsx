"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api";
import { email, isInvalid, lengthBetween, matches, maxLength, required, rules } from "@/lib/form";
import { revalidateLogic, useForm, useStore } from "@tanstack/react-form";
import Link from "next/link";
import { useState } from "react";
import { AuthIntro } from "./auth-intro";

const inputs = [
  {
    name: "name",
    label: "Nom de l’espace",
    type: "text",
    validate: rules(
      required("Le nom de l’espace est requis."),
      maxLength(200, "Le nom de l’espace contient au plus 200 caractères."),
    ),
  },
  {
    name: "slug",
    label: "Identifiant de l’espace",
    type: "text",
    validate: rules(
      required("L’identifiant de l’espace est requis."),
      matches(
        /^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$/,
        "L’identifiant doit contenir de 3 à 63 lettres minuscules, chiffres ou tirets.",
      ),
    ),
  },
  {
    name: "administratorName",
    label: "Votre nom",
    type: "text",
    validate: rules(
      required("Le nom de l’administrateur est requis."),
      maxLength(200, "Le nom contient au plus 200 caractères."),
    ),
  },
  {
    name: "email",
    label: "Adresse électronique",
    type: "email",
    validate: rules(required("Renseignez votre adresse électronique."), email()),
  },
  {
    name: "password",
    label: "Mot de passe",
    type: "password",
    validate: rules(
      lengthBetween(16, 128, "Le mot de passe doit contenir entre 16 et 128 caractères."),
    ),
  },
] as const;

export function CreateWorkspace() {
  const [error, setError] = useState("");
  const [created, setCreated] = useState(false);
  const form = useForm({
    defaultValues: { name: "", slug: "", administratorName: "", email: "", password: "" },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        await api("workspaces", { method: "POST", body: JSON.stringify(value) });
        setCreated(true);
      } catch (failure) {
        setError(failure instanceof Error ? failure.message : "Impossible de créer l’espace.");
      }
    },
  });
  const pending = useStore(form.store, (state) => state.isSubmitting);
  return (
    <main className="grid min-h-svh lg:grid-cols-2">
      <AuthIntro />
      <section className="flex items-center justify-center p-5 sm:p-10">
        <Card className="w-full max-w-md">
          <CardHeader>
            <CardDescription>Codetics Supervision</CardDescription>
            <CardTitle className="text-2xl">Créer votre espace client</CardTitle>
            <CardDescription>Votre réseau et votre compte administrateur.</CardDescription>
          </CardHeader>
          <CardContent>
            {created ? (
              <p role="status" className="text-sm">
                Votre espace est créé. Consultez votre messagerie pour vérifier votre adresse avant
                de vous connecter.
              </p>
            ) : (
              <form
                noValidate
                onSubmit={(event) => {
                  event.preventDefault();
                  void form.handleSubmit();
                }}
              >
                <FieldGroup>
                  {inputs.map(({ name, label, type, validate }) => (
                    <form.Field key={name} name={name} validators={{ onDynamic: validate }}>
                      {(field) => {
                        const invalid = isInvalid(field);
                        return (
                          <Field data-invalid={invalid}>
                            <FieldLabel htmlFor={name}>{label}</FieldLabel>
                            <Input
                              id={name}
                              name={field.name}
                              type={type}
                              value={field.state.value}
                              onBlur={field.handleBlur}
                              onChange={(event) => field.handleChange(event.target.value)}
                              aria-invalid={invalid}
                              required
                              maxLength={name === "password" ? 128 : name === "slug" ? 63 : 200}
                              minLength={name === "password" ? 16 : name === "slug" ? 3 : 1}
                              pattern={
                                name === "slug" ? "[a-z0-9][a-z0-9-]{1,61}[a-z0-9]" : undefined
                              }
                              autoComplete={
                                name === "password"
                                  ? "new-password"
                                  : name === "email"
                                    ? "email"
                                    : undefined
                              }
                            />
                            {invalid && <FieldError errors={field.state.meta.errors} />}
                          </Field>
                        );
                      }}
                    </form.Field>
                  ))}
                  <p className="text-xs text-muted-foreground">
                    L’identifiant permanent contient 3 à 63 lettres minuscules, chiffres ou tirets.
                    Le mot de passe contient au moins 16 caractères.
                  </p>
                  {error && (
                    <Alert variant="destructive">
                      <AlertDescription>{error}</AlertDescription>
                    </Alert>
                  )}
                  <Button type="submit" disabled={pending}>
                    {pending ? "Création…" : "Créer mon espace"}
                  </Button>
                </FieldGroup>
              </form>
            )}
            <Link
              href="/auth/login"
              className="mt-5 block text-center text-sm text-muted-foreground"
            >
              Retour à la connexion
            </Link>
          </CardContent>
        </Card>
      </section>
    </main>
  );
}
