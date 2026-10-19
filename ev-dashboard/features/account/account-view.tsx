"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useApi } from "@/hooks/use-api";
import { api, type User } from "@/lib/api";
import { display } from "@/lib/format";
import { useRouter } from "next/navigation";
import { useState } from "react";

type Provider = { id: string; name: string; url: string };

export function AccountView({ user }: { user: User }) {
  const router = useRouter();
  const { data: providers } = useApi<Provider[]>("auth/providers");
  const [error, setError] = useState("");

  async function link(provider: string) {
    setError("");

    try {
      const result = await api<{ url: string }>(`auth/link/${provider}`, {
        method: "POST",
      });
      window.location.assign(result.url);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Impossible d’associer ce compte.");
    }
  }

  async function revoke() {
    await api("auth/signout/all", { method: "POST" });
    router.replace("/auth/login");
  }

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <div className="flex flex-col gap-2">
        <h1 className="text-3xl font-semibold tracking-tight">Mon compte</h1>
        <p className="text-sm text-muted-foreground">Votre identité et vos moyens de connexion.</p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>{user.name}</CardTitle>
          <CardDescription>{user.email}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <Badge variant="secondary">{display(user.role)}</Badge>
          <p className="break-all text-xs text-muted-foreground">
            Identifiant de l’espace : {user.tenantId}
          </p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Comptes associés</CardTitle>
          <CardDescription>Associez un compte externe à votre compte actuel.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          {providers?.length ? (
            providers.map((provider) => (
              <Button key={provider.id} variant="outline" onClick={() => link(provider.id)}>
                Associer {provider.name}
              </Button>
            ))
          ) : (
            <p className="text-sm text-muted-foreground">
              Votre administrateur n’a pas activé de fournisseur de connexion supplémentaire.
            </p>
          )}
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
        </CardContent>
      </Card>
      <Button variant="destructive" onClick={revoke}>
        Déconnecter tous les appareils
      </Button>
    </div>
  );
}
