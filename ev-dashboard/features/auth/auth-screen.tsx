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
  login: "Sign in",
  register: "Create your account.",
  "forgot-password": "Reset your access.",
  "reset-password": "Choose a new password.",
  "verify-email": "Verify your email.",
};

export function AuthScreen({ action }: { action: string }) {
  const router = useRouter();
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
            ? "If your account is eligible, an email with the next steps will arrive shortly."
            : "Your account has been updated. You can now sign in.",
        );
      }
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Unable to complete this request");
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
            <CardDescription>Enter your workspace and account credentials.</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-5">
            {message ? (
              <Alert>
                <AlertTitle>Request completed</AlertTitle>
                <AlertDescription>{message}</AlertDescription>
              </Alert>
            ) : (
              <form onSubmit={submit}>
                <FieldGroup>
                  {["login", "forgot-password"].includes(action) && (
                    <Field>
                      <FieldLabel htmlFor="tenant">Workspace</FieldLabel>
                      <Input
                        id="tenant"
                        name="tenant"
                        autoComplete="organization"
                        placeholder="Your workspace identifier"
                        required
                      />
                    </Field>
                  )}
                  {action === "register" && (
                    <Field>
                      <FieldLabel htmlFor="name">Full name</FieldLabel>
                      <Input id="name" name="name" autoComplete="name" required />
                    </Field>
                  )}
                  {hasEmail && (
                    <Field>
                      <FieldLabel htmlFor="email">Email address</FieldLabel>
                      <Input
                        id="email"
                        name="email"
                        type="email"
                        autoComplete="username"
                        placeholder="you@company.com"
                        required
                        maxLength={320}
                      />
                    </Field>
                  )}
                  {hasPassword && (
                    <Field>
                      <FieldLabel htmlFor="password">Password</FieldLabel>
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
                      ? "Please wait…"
                      : action === "login"
                        ? "Sign in"
                        : action === "verify-email"
                          ? "Verify email"
                          : "Continue"}
                  </Button>
                </FieldGroup>
              </form>
            )}
            {action === "login" && (
              <Link
                href="/auth/forgot-password"
                className="text-center text-sm text-muted-foreground hover:text-foreground"
              >
                Forgot your password?
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
                      Continue with {provider.name}
                    </Button>
                  ))}
                </div>
              </>
            )}
          </CardContent>
          <CardFooter className="flex-col justify-center gap-3 text-xs text-muted-foreground">
            {action === "login" ? (
              "Access is managed by your workspace administrator."
            ) : (
              <Link href="/auth/login">Return to sign in</Link>
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
