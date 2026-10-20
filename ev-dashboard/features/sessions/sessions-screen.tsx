"use client";

import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { PageHeader } from "@/features/shared/page-header";
import { isStaff, useWorkspace } from "@/features/workspace/workspace-context";
import { useRevision } from "@/hooks/use-api";
import { IconRefresh } from "@tabler/icons-react";
import { SessionsInError } from "./sessions-in-error";
import { SessionsList } from "./sessions-list";

export function SessionsScreen() {
  const user = useWorkspace();
  const [revision, refresh] = useRevision();
  const staff = isStaff(user);
  return (
    <>
      <PageHeader
        title="Sessions de recharge"
        description={
          staff
            ? "Recharges en cours, historique, consommation et anomalies sur votre réseau."
            : "Vos recharges en cours et votre historique de consommation."
        }
        actions={
          <Button variant="outline" onClick={refresh}>
            <IconRefresh data-icon="inline-start" />
            Actualiser
          </Button>
        }
      />
      <Tabs defaultValue="active">
        <TabsList variant="line">
          <TabsTrigger value="active">En cours</TabsTrigger>
          <TabsTrigger value="history">Historique</TabsTrigger>
          {staff && <TabsTrigger value="errors">En erreur</TabsTrigger>}
        </TabsList>
        <TabsContent value="active" className="pt-4">
          <SessionsList status="active" revision={revision} />
        </TabsContent>
        <TabsContent value="history" className="pt-4">
          <SessionsList status="completed" revision={revision} />
        </TabsContent>
        {staff && (
          <TabsContent value="errors" className="pt-4">
            <SessionsInError revision={revision} />
          </TabsContent>
        )}
      </Tabs>
    </>
  );
}
