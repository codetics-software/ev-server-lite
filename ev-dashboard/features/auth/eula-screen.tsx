"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useApi } from "@/hooks/use-api";
import { api, errorMessage } from "@/lib/api";
import type { User } from "@/lib/api-types";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { AuthIntro } from "./auth-intro";

type Eula = { version: string; attribution: string; url: string };

export function EulaScreen() {
  const router = useRouter();
  const { data: eula, error } = useApi<Eula>("auth/eula");
  const { data: user, loading: userLoading } = useApi<User>("auth/me");
  const [failure, setFailure] = useState("");
  const [pending, setPending] = useState(false);

  async function accept() {
    setPending(true);
    setFailure("");
    try {
      await api("auth/eula/accept", { method: "POST" });
      router.replace(user?.role === "DRIVER" ? "/transactions" : "/");
    } catch (problem) {
      setFailure(errorMessage(problem, "Impossible d’enregistrer votre acceptation."));
      setPending(false);
    }
  }

  return (
    <main className="grid min-h-svh lg:grid-cols-2">
      <AuthIntro />
      <section className="flex items-center justify-center p-5 sm:p-12">
        <Card className="w-full max-w-lg">
          <CardHeader>
            <CardDescription>Codetics Supervision</CardDescription>
            <CardTitle className="text-2xl">Conditions d’utilisation</CardTitle>
            <CardDescription>{eula ? `Version ${eula.version}` : "Chargement…"}</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4 text-sm">
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error.message}</AlertDescription>
              </Alert>
            )}
            {!eula && !error ? (
              <Skeleton className="h-32" />
            ) : (
              eula && (
                <>
                  <p>
                    Cette supervision de recharge est fournie sous licence d’attribution :{" "}
                    <strong className="font-medium">{eula.attribution}</strong>.
                  </p>
                  <p className="text-muted-foreground">
                    En utilisant ce service, vous acceptez que vos données de recharge (sessions,
                    badges, consommation) soient traitées par l’exploitant du réseau pour la
                    supervision, la facturation et le support. Le texte complet est disponible sur{" "}
                    <a
                      href={eula.url}
                      target="_blank"
                      rel="noreferrer"
                      className="text-primary underline underline-offset-4 dark:text-sidebar-primary"
                    >
                      {eula.url.replace(/^https?:\/\//, "")}
                    </a>
                    .
                  </p>
                </>
              )
            )}
            {failure && (
              <Alert variant="destructive">
                <AlertDescription>{failure}</AlertDescription>
              </Alert>
            )}
          </CardContent>
          <CardFooter className="flex flex-wrap justify-between gap-3">
            {user ? (
              <>
                <span className="text-xs text-muted-foreground">
                  Connecté en tant que {user.email}
                </span>
                {user.eulaAccepted === false ? (
                  <Button onClick={accept} disabled={pending || !eula}>
                    {pending ? "Enregistrement…" : "J’accepte les conditions"}
                  </Button>
                ) : (
                  <Link
                    href={user.role === "DRIVER" ? "/transactions" : "/"}
                    className={buttonVariants()}
                  >
                    Retour à la supervision
                  </Link>
                )}
              </>
            ) : (
              !userLoading && (
                <Link href="/auth/login" className={buttonVariants({ variant: "outline" })}>
                  Retour à la connexion
                </Link>
              )
            )}
          </CardFooter>
        </Card>
      </section>
    </main>
  );
}
