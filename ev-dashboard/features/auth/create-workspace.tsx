"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api";
import Link from "next/link";
import { useState, type FormEvent } from "react";
import { AuthIntro } from "./auth-intro";

export function CreateWorkspace() {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [created, setCreated] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError("");
    const form = new FormData(event.currentTarget);
    try {
      await api("workspaces", { method: "POST", body: JSON.stringify(Object.fromEntries(form)) });
      setCreated(true);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Impossible de créer l’espace.");
    } finally {
      setPending(false);
    }
  }
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
              <form onSubmit={submit}>
                <FieldGroup>
                  {[
                    ["name", "Nom de l’espace", "text"],
                    ["slug", "Identifiant de l’espace", "text"],
                    ["administratorName", "Votre nom", "text"],
                    ["email", "Adresse électronique", "email"],
                    ["password", "Mot de passe", "password"],
                  ].map(([name, label, type]) => (
                    <Field key={name}>
                      <FieldLabel htmlFor={name}>{label}</FieldLabel>
                      <Input
                        id={name}
                        name={name}
                        type={type}
                        required
                        maxLength={name === "password" ? 128 : name === "slug" ? 63 : 200}
                        minLength={name === "password" ? 16 : name === "slug" ? 3 : 1}
                        pattern={name === "slug" ? "[a-z0-9][a-z0-9-]{1,61}[a-z0-9]" : undefined}
                        autoComplete={
                          name === "password"
                            ? "new-password"
                            : name === "email"
                              ? "email"
                              : undefined
                        }
                      />
                    </Field>
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
