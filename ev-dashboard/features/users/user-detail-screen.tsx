"use client";

import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { BadgesList } from "@/features/badges/badges-list";
import { InvoicesList } from "@/features/billing/invoices-list";
import { PaymentMethodsList } from "@/features/billing/payment-methods";
import { CarsList } from "@/features/cars/cars-list";
import { NotificationPreferences } from "@/features/notifications/notification-preferences";
import { SessionsList } from "@/features/sessions/sessions-list";
import { ConfirmButton } from "@/features/shared/confirm-button";
import { PageHeader } from "@/features/shared/page-header";
import { ErrorState, LoadingState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { useAction } from "@/features/shared/use-action";
import { useWorkspaceSession } from "@/features/workspace/workspace-context";
import { useApi, useRevision } from "@/hooks/use-api";
import { api } from "@/lib/api";
import { date } from "@/lib/format";
import { roleLabels } from "@/lib/labels";
import { IconMailForward, IconRefresh } from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { UserForm } from "./user-form";
import { UserSites } from "./user-sites";
import { UserState } from "./users-screen";
import type { ManagedUser } from "./user-types";

export function UserDetailScreen({ id }: { id: string }) {
  const { user: me, workspace, components } = useWorkspaceSession();
  const router = useRouter();
  const [revision, refresh] = useRevision();
  const { data: user, error, loading } = useApi<ManagedUser>(`users/${id}`, revision);
  const action = useAction();

  if (error && !user) {
    return (
      <>
        <PageHeader title="Utilisateur" back={{ href: "/users", label: "Utilisateurs" }} />
        <ErrorState error={error} onRetry={refresh} />
      </>
    );
  }
  if (loading && !user) {
    return <LoadingState />;
  }
  if (!user) {
    return null;
  }
  const self = user.id === me.id;

  return (
    <>
      <PageHeader
        back={{ href: "/users", label: "Utilisateurs" }}
        title={user.display_name}
        description={`${user.email} · ${roleLabels[user.role]} · créé le ${date(user.created_at)}`}
        meta={
          <>
            <UserState user={user} />
            {user.billing_customer && <ToneBadge tone="progress">Client facturé</ToneBadge>}
            <span className="text-xs text-muted-foreground">
              Dernière connexion : {user.last_login_at ? date(user.last_login_at) : "jamais"}
            </span>
          </>
        }
        actions={
          <>
            {workspace && (
              <Button
                variant="outline"
                disabled={action.busy}
                onClick={() =>
                  action.run(
                    "invite",
                    () =>
                      api("auth/password/reset/request", {
                        method: "POST",
                        body: JSON.stringify({ tenant: workspace.slug, email: user.email }),
                      }),
                    { success: "Lien de définition du mot de passe envoyé." },
                  )
                }
              >
                <IconMailForward data-icon="inline-start" />
                {user.email_verified ? "Réinitialiser le mot de passe" : "Renvoyer l’invitation"}
              </Button>
            )}
            {components.billing && (
              <Button
                variant="outline"
                disabled={action.busy}
                onClick={() =>
                  action.run(
                    "sync",
                    () => api(`billing/users/${user.id}/synchronize`, { method: "POST" }),
                    {
                      success: "Client de facturation synchronisé.",
                      onDone: refresh,
                    },
                  )
                }
              >
                <IconRefresh data-icon="inline-start" />
                Synchroniser la facturation
              </Button>
            )}
            {!self && (
              <ConfirmButton
                title={`Supprimer ${user.display_name} ?`}
                description="Le compte est anonymisé, ses badges sont libérés et l’historique des sessions est conservé. Cette action est irréversible."
                confirmLabel="Supprimer le compte"
                onConfirm={() =>
                  action.run("delete", () => api(`users/${user.id}`, { method: "DELETE" }), {
                    success: "Compte supprimé.",
                    onDone: () => router.replace("/users"),
                  })
                }
              >
                Supprimer
              </ConfirmButton>
            )}
          </>
        }
      />
      <Tabs defaultValue="profile">
        <div className="overflow-x-auto">
          <TabsList variant="line">
            <TabsTrigger value="profile">Profil</TabsTrigger>
            <TabsTrigger value="badges">Badges</TabsTrigger>
            {components.organization && <TabsTrigger value="sites">Sites</TabsTrigger>}
            <TabsTrigger value="sessions">Sessions</TabsTrigger>
            <TabsTrigger value="notifications">Notifications</TabsTrigger>
            {components.billing && <TabsTrigger value="payment">Moyens de paiement</TabsTrigger>}
            {components.billing && <TabsTrigger value="invoices">Factures</TabsTrigger>}
            {components.cars && <TabsTrigger value="cars">Véhicules</TabsTrigger>}
          </TabsList>
        </div>
        <TabsContent value="profile" className="max-w-3xl pt-4">
          <UserForm
            key={`${user.role}:${user.enabled}:${user.display_name}`}
            user={user}
            onSaved={refresh}
          />
        </TabsContent>
        <TabsContent value="badges" className="pt-4">
          <BadgesList userId={user.id} />
        </TabsContent>
        {components.organization && (
          <TabsContent value="sites" className="pt-4">
            <UserSites userId={user.id} />
          </TabsContent>
        )}
        <TabsContent value="sessions" className="pt-4">
          <SessionsList status={null} fixed={{ userId: user.id }} />
        </TabsContent>
        <TabsContent value="notifications" className="pt-4">
          <NotificationPreferences
            path={`users/${user.id}/notifications`}
            staff={user.role !== "DRIVER"}
          />
        </TabsContent>
        {components.billing && (
          <TabsContent value="payment" className="pt-4">
            <PaymentMethodsList path={`users/${user.id}/payment-methods`} manage={false} />
          </TabsContent>
        )}
        {components.billing && (
          <TabsContent value="invoices" className="pt-4">
            <InvoicesList userId={user.id} />
          </TabsContent>
        )}
        {components.cars && (
          <TabsContent value="cars" className="pt-4">
            <CarsList userId={user.id} />
          </TabsContent>
        )}
      </Tabs>
    </>
  );
}
