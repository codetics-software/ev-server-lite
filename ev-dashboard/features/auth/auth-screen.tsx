"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { useApi } from "@/hooks/use-api";
import { api } from "@/lib/api";
import { email, isInvalid, lengthBetween, maxLength, required, rules } from "@/lib/form";
import { revalidateLogic, useForm, useStore } from "@tanstack/react-form";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { LanguageToggle } from "@/features/workspace/language-toggle";
import { ThemeToggle } from "@/features/workspace/theme-toggle";
import { AuthIntro } from "./auth-intro";
import { t, translated } from "@/lib/i18n";

const titles: Record<string, string> = translated({
  login: "Se connecter",
  register: "Créer votre compte",
  "forgot-password": "Rétablir votre accès",
  "reset-password": "Définir votre mot de passe",
  "define-password": "Définir votre mot de passe",
  "verify-email": "Vérifier votre adresse électronique",
});

export function AuthScreen({ action }: { action: string }) {
  const router = useRouter();
  const { data: options } = useApi<{ registrationAvailable: boolean }>("public/workspaces/options");
  const { data: providers, loading: providersLoading } =
    useApi<{ id: string; name: string; url: string }[]>("auth/providers");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const hasTenant = ["login", "forgot-password"].includes(action);
  const hasName = action === "register";
  const hasEmail = ["login", "register", "forgot-password"].includes(action);
  const hasPassword = ["login", "register", "reset-password", "define-password"].includes(action);
  const [accepted, setAccepted] = useState(false);
  const [logoFailed, setLogoFailed] = useState("");
  const form = useForm({
    defaultValues: { tenant: "", name: "", email: "", password: "" },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      if (action === "register" && !accepted) {
        setError(t("Acceptez les conditions d’utilisation pour créer votre compte."));
        return;
      }

      const parameters = new URLSearchParams(window.location.search);
      const payload = {
        tenant: hasTenant ? value.tenant : null,
        email: hasEmail ? value.email : null,
        name: hasName ? value.name : null,
        password: hasPassword ? value.password : null,
        token: parameters.get("token"),
      };
      const paths: Record<string, string> = {
        login: "auth/signin",
        register: "auth/signon",
        "forgot-password": "auth/password/reset/request",
        "reset-password": "auth/password/reset",
        "define-password": "auth/password/reset",
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
              ? t(
                  "Si votre compte est éligible, vous recevrez un courriel avec les prochaines étapes.",
                )
              : t("Votre compte a été mis à jour. Vous pouvez vous connecter."),
          );
        }
      } catch (failure) {
        setError(
          failure instanceof Error ? failure.message : t("Impossible de traiter cette demande."),
        );
      }
    },
  });
  const pending = useStore(form.store, (state) => state.isSubmitting);
  const tenant = useStore(form.store, (state) => state.values.tenant.trim().toLowerCase());
  const [logoTenant, setLogoTenant] = useState("");

  useEffect(() => {
    const initial = new URLSearchParams(window.location.search).get("espace");
    if (initial) {
      form.setFieldValue("tenant", initial);
    }
  }, [form]);

  useEffect(() => {
    const timer = setTimeout(
      () => setLogoTenant(/^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$/.test(tenant) ? tenant : ""),
      400,
    );
    return () => clearTimeout(timer);
  }, [tenant]);

  return (
    <main className="grid min-h-svh lg:grid-cols-2" aria-busy={providersLoading}>
      <AuthIntro />
      <section className="relative flex items-center justify-center p-5 sm:p-12">
        <div className="absolute top-4 right-4 flex items-center gap-1">
          <LanguageToggle />
          <ThemeToggle />
        </div>
        <Card className="w-full max-w-sm">
          <CardHeader>
            {logoTenant && logoFailed !== logoTenant && (
              <Image
                src={`/api/backend/api/v1/public/workspaces/${encodeURIComponent(logoTenant)}/logo`}
                alt=""
                width={48}
                height={48}
                unoptimized
                className="mb-2 size-12 rounded-lg object-contain"
                onError={() => setLogoFailed(logoTenant)}
              />
            )}
            <CardDescription>Codetics Supervision</CardDescription>
            <CardTitle className="text-2xl">{titles[action]}</CardTitle>
            <CardDescription>
              {action === "define-password"
                ? t(
                    "Choisissez le mot de passe de votre nouveau compte. Il contient au moins 16 caractères.",
                  )
                : action === "reset-password"
                  ? t(
                      "Nouveau compte ou mot de passe oublié : choisissez un mot de passe d’au moins 16 caractères.",
                    )
                  : t("Renseignez votre espace client et vos identifiants.")}
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-5">
            {message ? (
              <Alert>
                <AlertTitle>{t("Demande traitée")}</AlertTitle>
                <AlertDescription>{message}</AlertDescription>
              </Alert>
            ) : (
              <form
                noValidate
                onSubmit={(event) => {
                  event.preventDefault();
                  void form.handleSubmit();
                }}
              >
                <FieldGroup>
                  {hasTenant && (
                    <form.Field
                      name="tenant"
                      validators={{
                        onDynamic: rules(required(t("Renseignez l’identifiant de votre espace."))),
                      }}
                    >
                      {(field) => {
                        const invalid = isInvalid(field);
                        return (
                          <Field data-invalid={invalid}>
                            <FieldLabel htmlFor="tenant">{t("Espace client")}</FieldLabel>
                            <Input
                              id="tenant"
                              name={field.name}
                              value={field.state.value}
                              onBlur={field.handleBlur}
                              onChange={(event) => field.handleChange(event.target.value)}
                              aria-invalid={invalid}
                              autoComplete="organization"
                              placeholder={t("Identifiant de votre espace")}
                              required
                            />
                            {invalid && <FieldError errors={field.state.meta.errors} />}
                          </Field>
                        );
                      }}
                    </form.Field>
                  )}
                  {hasName && (
                    <form.Field
                      name="name"
                      validators={{
                        onDynamic: rules(
                          required(t("Renseignez votre nom.")),
                          maxLength(200, t("Le nom contient au plus 200 caractères.")),
                        ),
                      }}
                    >
                      {(field) => {
                        const invalid = isInvalid(field);
                        return (
                          <Field data-invalid={invalid}>
                            <FieldLabel htmlFor="name">{t("Nom complet")}</FieldLabel>
                            <Input
                              id="name"
                              name={field.name}
                              value={field.state.value}
                              onBlur={field.handleBlur}
                              onChange={(event) => field.handleChange(event.target.value)}
                              aria-invalid={invalid}
                              autoComplete="name"
                              required
                            />
                            {invalid && <FieldError errors={field.state.meta.errors} />}
                          </Field>
                        );
                      }}
                    </form.Field>
                  )}
                  {hasEmail && (
                    <form.Field
                      name="email"
                      validators={{
                        onDynamic: rules(
                          required(t("Renseignez votre adresse électronique.")),
                          email(),
                        ),
                      }}
                    >
                      {(field) => {
                        const invalid = isInvalid(field);
                        return (
                          <Field data-invalid={invalid}>
                            <FieldLabel htmlFor="email">{t("Adresse électronique")}</FieldLabel>
                            <Input
                              id="email"
                              name={field.name}
                              type="email"
                              value={field.state.value}
                              onBlur={field.handleBlur}
                              onChange={(event) => field.handleChange(event.target.value)}
                              aria-invalid={invalid}
                              autoComplete="username"
                              placeholder="vous@entreprise.fr"
                              required
                              maxLength={320}
                            />
                            {invalid && <FieldError errors={field.state.meta.errors} />}
                          </Field>
                        );
                      }}
                    </form.Field>
                  )}
                  {hasPassword && (
                    <form.Field
                      name="password"
                      validators={{
                        onDynamic: rules(
                          action === "login"
                            ? required(t("Renseignez votre mot de passe."))
                            : lengthBetween(
                                16,
                                128,
                                t("Le mot de passe doit contenir entre 16 et 128 caractères."),
                              ),
                        ),
                      }}
                    >
                      {(field) => {
                        const invalid = isInvalid(field);
                        return (
                          <Field data-invalid={invalid}>
                            <FieldLabel htmlFor="password">{t("Mot de passe")}</FieldLabel>
                            <Input
                              id="password"
                              name={field.name}
                              type="password"
                              value={field.state.value}
                              onBlur={field.handleBlur}
                              onChange={(event) => field.handleChange(event.target.value)}
                              aria-invalid={invalid}
                              autoComplete={
                                action === "login" ? "current-password" : "new-password"
                              }
                              minLength={action === "login" ? undefined : 16}
                              maxLength={128}
                              required
                            />
                            {invalid && <FieldError errors={field.state.meta.errors} />}
                          </Field>
                        );
                      }}
                    </form.Field>
                  )}
                  {action === "register" && (
                    <Field orientation="horizontal">
                      <Checkbox
                        id="eula"
                        checked={accepted}
                        onCheckedChange={(checked) => setAccepted(checked === true)}
                      />
                      <FieldLabel htmlFor="eula" className="font-normal">
                        <span>
                          {t("J’accepte les")}{" "}
                          <Link
                            href="/auth/eula"
                            target="_blank"
                            className="underline underline-offset-4"
                          >
                            {t("conditions d’utilisation")}
                          </Link>
                        </span>
                      </FieldLabel>
                    </Field>
                  )}
                  {error && (
                    <Alert variant="destructive">
                      <AlertDescription>{error}</AlertDescription>
                    </Alert>
                  )}
                  <Button type="submit" className="w-full" disabled={pending}>
                    {pending
                      ? t("Veuillez patienter…")
                      : action === "login"
                        ? t("Se connecter")
                        : action === "verify-email"
                          ? t("Vérifier l’adresse")
                          : t("Continuer")}
                  </Button>
                </FieldGroup>
              </form>
            )}
            {action === "login" && (
              <Link
                href="/auth/forgot-password"
                className="text-center text-sm text-muted-foreground hover:text-foreground"
              >
                {t("Mot de passe oublié ?")}
              </Link>
            )}
            {action === "login" && Boolean(providers?.length) && (
              <>
                <Separator />
                <div className="flex flex-col gap-2">
                  {providers?.map((provider) => (
                    <a
                      key={provider.id}
                      href={provider.url}
                      data-slot="button"
                      className={buttonVariants({ variant: "outline" })}
                    >
                      {t("Continuer avec {provider}", { provider: provider.name })}
                    </a>
                  ))}
                </div>
              </>
            )}
          </CardContent>
          <CardFooter className="flex-col justify-center gap-3 text-xs text-muted-foreground">
            {action === "login" && options?.registrationAvailable && (
              <Link href="/auth/create-workspace">{t("Créer un espace client")}</Link>
            )}
            {action === "login" ? (
              t("Votre administrateur gère l’accès à cet espace.")
            ) : (
              <Link href="/auth/login">{t("Retour à la connexion")}</Link>
            )}
            <Link href="/auth/eula">{t("Conditions d’utilisation")}</Link>
            <span>
              Kevin Vavelin, <a href="https://codetics.fr">Codetics</a>
            </span>
          </CardFooter>
        </Card>
      </section>
    </main>
  );
}
