"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useApi } from "@/hooks/use-api";
import { api, type User } from "@/lib/api";
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
      setError(failure instanceof Error ? failure.message : "Unable to link account");
    }
  }

  async function revoke() {
    await api("auth/signout/all", { method: "POST" });
    router.replace("/auth/login");
  }

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <div className="flex flex-col gap-2">
        <h1 className="text-3xl font-semibold tracking-tight">My account</h1>
        <p className="text-sm text-muted-foreground">Your identity and sign-in options.</p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>{user.name}</CardTitle>
          <CardDescription>{user.email}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <Badge variant="secondary">{user.role}</Badge>
          <p className="break-all text-xs text-muted-foreground">Workspace ID: {user.tenantId}</p>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Connected identities</CardTitle>
          <CardDescription>Link a provider while signed in to this account.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          {providers?.length ? (
            providers.map((provider) => (
              <Button key={provider.id} variant="outline" onClick={() => link(provider.id)}>
                Connect {provider.name}
              </Button>
            ))
          ) : (
            <p className="text-sm text-muted-foreground">
              Your administrator has not enabled additional sign-in providers.
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
        Sign out on all devices
      </Button>
    </div>
  );
}
