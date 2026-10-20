"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { FieldGroup } from "@/components/ui/field";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { Badge } from "@/features/badges/badge-types";
import { PaymentMethodsList } from "@/features/billing/payment-methods";
import { NotificationPreferences } from "@/features/notifications/notification-preferences";
import { DataTable, type Column } from "@/features/shared/data-table";
import { FormSelect, FormText } from "@/features/shared/form-fields";
import { FormFooter } from "@/features/shared/form-footer";
import { PageHeader } from "@/features/shared/page-header";
import { EmptyState, ErrorState, ListState, LoadingState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { notify } from "@/features/shared/use-action";
import { localeOptions } from "@/features/users/user-form";
import type { ManagedUser } from "@/features/users/user-types";
import { isStaff, useWorkspaceSession } from "@/features/workspace/workspace-context";
import { useApi, useRevision } from "@/hooks/use-api";
import { api, errorMessage } from "@/lib/api";
import { date, display, relative } from "@/lib/format";
import { maxLength, required, rules } from "@/lib/form";
import { roleLabels } from "@/lib/labels";
import { IconMapPin, IconTag } from "@tabler/icons-react";
import { revalidateLogic, useForm } from "@tanstack/react-form";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

type Provider = { id: string; name: string; url: string };
type AccountSite = { site_id: string; name: string; site_admin: boolean; site_owner: boolean };

function ProfileForm({ account, onSaved }: { account: ManagedUser; onSaved: () => void }) {
  const [error, setError] = useState("");
  const form = useForm({
    defaultValues: {
      name: account.display_name,
      phone: account.phone ?? "",
      locale: account.locale ?? "fr",
    },
    validationLogic: revalidateLogic(),
    onSubmit: async ({ value }) => {
      setError("");
      try {
        await api("account", {
          method: "PUT",
          body: JSON.stringify({
            name: value.name.trim(),
            phone: value.phone.trim() || undefined,
            locale: value.locale,
          }),
        });
        notify("Profil enregistré.");
        onSaved();
      } catch (failure) {
        setError(errorMessage(failure, "Impossible d’enregistrer le profil."));
      }
    },
  });
  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void form.handleSubmit();
      }}
    >
      <FieldGroup>
        <form.Field
          name="name"
          validators={{ onDynamic: rules(required("Renseignez votre nom."), maxLength(200)) }}
        >
          {(field) => <FormText field={field} label="Nom complet" autoComplete="name" required />}
        </form.Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <form.Field name="phone" validators={{ onDynamic: rules(maxLength(40)) }}>
            {(field) => <FormText field={field} label="Téléphone" type="tel" autoComplete="tel" />}
          </form.Field>
          <form.Field name="locale">
            {(field) => (
              <FormSelect field={field} label="Langue des courriels" options={localeOptions} />
            )}
          </form.Field>
        </div>
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => <FormFooter error={error} submitting={submitting} />}
        </form.Subscribe>
      </FieldGroup>
    </form>
  );
}

function MyBadges() {
  const { data, loading, error } = useApi<Badge[] | { items: Badge[] }>("account/tokens");
  const items = Array.isArray(data) ? data : (data?.items ?? []);
  const columns: Column<Badge>[] = [
    {
      id: "badge",
      header: "Badge",
      cell: (badge) =>
        badge.vehicle_id ? (
          <span className="flex flex-col items-start gap-1">
            <span className="font-mono text-xs font-medium">{badge.uid}</span>
            <span className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
              <ToneBadge tone="progress">Véhicule</ToneBadge>
              {badge.vehicle_name ?? badge.description}
            </span>
          </span>
        ) : (
          <span className="flex flex-col">
            <span className="font-medium">{badge.visual_id || badge.uid}</span>
            <span className="text-xs text-muted-foreground">{badge.description ?? badge.uid}</span>
          </span>
        ),
    },
    {
      id: "state",
      header: "État",
      cell: (badge) => (
        <ToneBadge tone={badge.enabled ? "success" : "neutral"}>
          {badge.enabled ? "Actif" : "Bloqué"}
        </ToneBadge>
      ),
    },
    {
      id: "used",
      header: "Dernière utilisation",
      cell: (badge) => (badge.last_used_at ? relative(badge.last_used_at) : "Jamais"),
    },
    { id: "expires", header: "Expiration", cell: (badge) => date(badge.expires_at) },
  ];
  return (
    <ListState
      data={data}
      items={items}
      loading={loading}
      error={error}
      empty={
        <EmptyState
          icon={<IconTag />}
          title="Aucun badge"
          description="Demandez un badge de recharge à l’exploitant du réseau."
        />
      }
    >
      <DataTable label="Mes badges" rows={items} columns={columns} rowKey={(badge) => badge.uid} />
    </ListState>
  );
}

function MySites() {
  const { data, loading, error } = useApi<AccountSite[]>("account/sites");
  const items = data ?? [];
  return (
    <ListState
      data={data}
      items={items}
      loading={loading}
      error={error}
      empty={
        <EmptyState
          icon={<IconMapPin />}
          title="Aucun site affecté"
          description="Vous pouvez recharger sur tous les sites en accès ouvert."
        />
      }
    >
      <DataTable
        label="Mes sites"
        rows={items}
        rowKey={(site) => site.site_id}
        columns={[
          {
            id: "name",
            header: "Site",
            cell: (site) => <span className="font-medium">{site.name}</span>,
          },
          {
            id: "role",
            header: "Rôle",
            cell: (site) =>
              site.site_owner
                ? "Propriétaire"
                : site.site_admin
                  ? "Administrateur du site"
                  : "Utilisateur",
          },
        ]}
      />
    </ListState>
  );
}

function Security() {
  const router = useRouter();
  const { data: providers } = useApi<Provider[]>("auth/providers");
  const [error, setError] = useState("");

  async function link(provider: string) {
    setError("");
    try {
      const result = await api<{ url: string }>(`auth/link/${provider}`, { method: "POST" });
      window.location.assign(result.url);
    } catch (failure) {
      setError(errorMessage(failure, "Impossible d’associer ce compte."));
    }
  }

  async function revoke() {
    setError("");
    try {
      await api("auth/signout/all", { method: "POST" });
      router.replace("/auth/login");
    } catch (failure) {
      setError(errorMessage(failure, "La déconnexion a échoué. Réessayez."));
    }
  }

  return (
    <div className="grid max-w-3xl gap-6">
      <Card>
        <CardHeader>
          <CardTitle>Comptes associés</CardTitle>
          <CardDescription>
            Associez un compte externe pour vous connecter sans mot de passe.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-3">
          {providers?.length ? (
            providers.map((provider) => (
              <Button key={provider.id} variant="outline" onClick={() => link(provider.id)}>
                Associer {provider.name}
              </Button>
            ))
          ) : (
            <p className="text-sm text-muted-foreground">
              Aucun fournisseur de connexion supplémentaire n’est activé.
            </p>
          )}
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Sessions et conditions</CardTitle>
          <CardDescription>
            Consultez les{" "}
            <Link
              href="/auth/eula"
              className="text-primary underline-offset-4 dark:text-sidebar-primary hover:underline"
            >
              conditions d’utilisation
            </Link>{" "}
            ou déconnectez tous vos appareils.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button variant="destructive" onClick={revoke}>
            Déconnecter tous les appareils
          </Button>
        </CardContent>
      </Card>
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
    </div>
  );
}

export function AccountScreen() {
  const { user, components } = useWorkspaceSession();
  const [revision, refresh] = useRevision();
  const { data: account, error, loading } = useApi<ManagedUser>("account", revision);
  return (
    <>
      <PageHeader
        title="Mon compte"
        description={`${user.email} · ${roleLabels[user.role]}`}
        meta={
          account && !account.email_verified ? (
            <ToneBadge tone="warning">Adresse à vérifier</ToneBadge>
          ) : undefined
        }
      />
      <Tabs defaultValue="profile">
        <div className="overflow-x-auto">
          <TabsList variant="line">
            <TabsTrigger value="profile">Profil</TabsTrigger>
            <TabsTrigger value="notifications">Notifications</TabsTrigger>
            {components.billing && <TabsTrigger value="payment">Moyens de paiement</TabsTrigger>}
            <TabsTrigger value="badges">Mes badges</TabsTrigger>
            {components.organization && <TabsTrigger value="sites">Mes sites</TabsTrigger>}
            <TabsTrigger value="security">Sécurité</TabsTrigger>
          </TabsList>
        </div>
        <TabsContent value="profile" className="max-w-2xl pt-4">
          {error && !account ? (
            <ErrorState error={error} onRetry={refresh} />
          ) : loading && !account ? (
            <LoadingState rows={3} />
          ) : account ? (
            <div className="flex flex-col gap-6">
              <ProfileForm
                key={`${account.display_name}:${account.phone}:${account.locale}`}
                account={account}
                onSaved={refresh}
              />
              <p className="text-xs text-muted-foreground">
                Compte créé le {date(account.created_at)} · dernière connexion{" "}
                {display(relative(account.last_login_at))}
              </p>
            </div>
          ) : null}
        </TabsContent>
        <TabsContent value="notifications" className="pt-4">
          <NotificationPreferences path="account/notifications" staff={isStaff(user)} />
        </TabsContent>
        {components.billing && (
          <TabsContent value="payment" className="max-w-3xl pt-4">
            <PaymentMethodsList path="account/payment-methods" manage />
          </TabsContent>
        )}
        <TabsContent value="badges" className="pt-4">
          <MyBadges />
        </TabsContent>
        {components.organization && (
          <TabsContent value="sites" className="pt-4">
            <MySites />
          </TabsContent>
        )}
        <TabsContent value="security" className="pt-4">
          <Security />
        </TabsContent>
      </Tabs>
    </>
  );
}
