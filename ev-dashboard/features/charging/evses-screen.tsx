"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useWorkspace } from "@/features/workspace/workspace-context";
import { useApi } from "@/hooks/use-api";
import { useState } from "react";
import { EvseEditor } from "./evse-editor";
import type { Evse } from "./portal-types";

export function EvsesScreen() {
  const user = useWorkspace();
  const [revision, setRevision] = useState(0);
  const { data, error } = useApi<Evse[]>("evses", revision);
  const [editing, setEditing] = useState<Evse | null | undefined>();
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Points de charge publics</h1>
          <p>Un portail de paiement et un QR code pour chaque connecteur.</p>
        </div>
        {user.role === "ADMIN" && (
          <Button onClick={() => setEditing(null)}>Ajouter un point de charge</Button>
        )}
      </div>
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
      )}
      {data?.length ? (
        <Table>
          <TableHeader>
            <TableRow>
              {["Point de charge", "Connecteur", "Publication", "Accès"].map((label) => (
                <TableHead key={label}>{label}</TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map((evse) => (
              <TableRow key={evse.id}>
                <TableCell className="font-medium">{evse.name}</TableCell>
                <TableCell>
                  {evse.stationId} · {evse.connectorId}
                </TableCell>
                <TableCell>
                  <Badge variant="outline">{evse.published ? "Publié" : "Brouillon"}</Badge>
                </TableCell>
                <TableCell>
                  <div className="flex flex-wrap gap-2">
                    <Button
                      nativeButton={false}
                      size="sm"
                      variant="outline"
                      render={<a href={evse.portalUrl} target="_blank" rel="noreferrer" />}
                    >
                      Ouvrir le portail
                    </Button>
                    <Button
                      nativeButton={false}
                      size="sm"
                      variant="outline"
                      render={
                        <a
                          href={`/api/backend/api/v1/evses/${evse.id}/qr`}
                          download={`recharge-${evse.stationId}-${evse.connectorId}.svg`}
                        />
                      }
                    >
                      Télécharger le QR code
                    </Button>
                    {user.role === "ADMIN" && (
                      <Button size="sm" variant="ghost" onClick={() => setEditing(evse)}>
                        Modifier
                      </Button>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      ) : (
        data && (
          <Empty className="border">
            <EmptyHeader>
              <EmptyTitle>Aucun point de charge public</EmptyTitle>
              <EmptyDescription>
                Ajoutez un connecteur et son tarif, puis connectez Stripe dans la page Paiements.
              </EmptyDescription>
            </EmptyHeader>
          </Empty>
        )
      )}
      {editing !== undefined && (
        <EvseEditor
          evse={editing}
          onClose={() => setEditing(undefined)}
          onSaved={() => setRevision((value) => value + 1)}
        />
      )}
    </>
  );
}
