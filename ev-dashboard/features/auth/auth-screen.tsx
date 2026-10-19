"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { useApi } from "@/hooks/use-api";
import { api } from "@/lib/api";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { AuthIntro } from "./auth-intro";

const titles: Record<string, string> = {
  login: "Se connecter",
  register: "Créer votre compte",
  "forgot-password": "Rétablir votre accès",
  "reset-password": "Choisir un nouveau mot de passe",
  "verify-email": "Vérifier votre adresse électronique",
};

export function AuthScreen({ action }: { action: string }) {
  const router = useRouter();
  const { data: options } = useApi<{ registrationAvailable: boolean }>("public/workspaces/options");
  const { data: providers, loading: providersLoading } =
    useApi<{ id: string; name: string; url: string }[]>("auth/providers");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [pending, setPending] = useState(false);
  const hasEmail = ["login", "register", "forgot-password"].includes(action);
  const hasPassword = ["login", "register", "reset-password"].includes(action);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError("");

    const form = new FormData(event.currentTarget);
    const parameters = new URLSearchParams(window.location.search);
    const payload = {
      tenant: form.get("tenant"),
      email: form.get("email"),
      name: form.get("name"),
      password: form.get("password"),
      token: parameters.get("token"),
    };
    const paths: Record<string, string> = {
      login: "auth/signin",
      register: "auth/signon",
      "forgot-password": "auth/password/reset/request",
      "reset-password": "auth/password/reset",
      "verify-email": "auth/mail/check",
    };

    try {
      await api(paths[action], {
        method: "POST",
        body: JSON.stringify(payload),
      });

      if (action === "login") {
        router.replace("/");
      } else {
        setMessage(
          action === "register" || action === "forgot-password"
            ? "Si votre compte est éligible, vous recevrez un courriel avec les prochaines étapes."
            : "Votre compte a été mis à jour. Vous pouvez vous connecter.",
        );
      }
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Impossible de traiter cette demande.");
    } finally {
      setPending(false);
    }
  }

  return (
    <main className="grid min-h-svh lg:grid-cols-2" aria-busy={providersLoading}>
      <AuthIntro />
      <section className="flex items-center justify-center p-5 sm:p-12">
        <Card className="w-full max-w-sm">
          <CardHeader>
            <CardDescription>Codetics Supervision</CardDescription>
            <CardTitle className="text-2xl">{titles[action]}</CardTitle>
            <CardDescription>Renseignez votre espace client et vos identifiants.</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-5">
            {message ? (
              <Alert>
                <AlertTitle>Demande traitée</AlertTitle>
                <AlertDescription>{message}</AlertDescription>
              </Alert>
            ) : (
              <form onSubmit={submit}>
                <FieldGroup>
                  {["login", "forgot-password"].includes(action) && (
                    <Field>
                      <FieldLabel htmlFor="tenant">Espace client</FieldLabel>
                      <Input
                        id="tenant"
                        name="tenant"
                        autoComplete="organization"
                        placeholder="Identifiant de votre espace"
                        required
                      />
                    </Field>
                  )}
                  {action === "register" && (
                    <Field>
                      <FieldLabel htmlFor="name">Nom complet</FieldLabel>
                      <Input id="name" name="name" autoComplete="name" required />
                    </Field>
                  )}
                  {hasEmail && (
                    <Field>
                      <FieldLabel htmlFor="email">Adresse électronique</FieldLabel>
                      <Input
                        id="email"
                        name="email"
                        type="email"
                        autoComplete="username"
                        placeholder="vous@entreprise.fr"
                        required
                        maxLength={320}
                      />
                    </Field>
                  )}
                  {hasPassword && (
                    <Field>
                      <FieldLabel htmlFor="password">Mot de passe</FieldLabel>
                      <Input
                        id="password"
                        name="password"
                        type="password"
                        autoComplete={action === "login" ? "current-password" : "new-password"}
                        minLength={action === "login" ? undefined : 16}
                        maxLength={128}
                        required
                      />
                    </Field>
                  )}
                  {error && (
                    <Alert variant="destructive">
                      <AlertDescription>{error}</AlertDescription>
                    </Alert>
                  )}
                  <Button type="submit" className="w-full" disabled={pending}>
                    {pending
                      ? "Veuillez patienter…"
                      : action === "login"
                        ? "Se connecter"
                        : action === "verify-email"
                          ? "Vérifier l’adresse"
                          : "Continuer"}
                  </Button>
                </FieldGroup>
              </form>
            )}
            {action === "login" && (
              <Link
                href="/auth/forgot-password"
                className="text-center text-sm text-muted-foreground hover:text-foreground"
              >
                Mot de passe oublié ?
              </Link>
            )}
            {action === "login" && Boolean(providers?.length) && (
              <>
                <Separator />
                <div className="flex flex-col gap-2">
                  {providers?.map((provider) => (
                    <Button
                      key={provider.id}
                      nativeButton={false}
                      variant="outline"
                      render={<a href={provider.url} />}
                    >
                      Continuer avec {provider.name}
                    </Button>
                  ))}
                </div>
              </>
            )}
          </CardContent>
          <CardFooter className="flex-col justify-center gap-3 text-xs text-muted-foreground">
            {action === "login" && options?.registrationAvailable && (
              <Link href="/auth/create-workspace">Créer un espace client</Link>
            )}
            {action === "login" ? (
              "Votre administrateur gère l’accès à cet espace."
            ) : (
              <Link href="/auth/login">Retour à la connexion</Link>
            )}
            <span>
              Kevin Vavelin, <a href="https://codetics.fr">Codetics</a>
            </span>
          </CardFooter>
        </Card>
      </section>
    </main>
  );
}
