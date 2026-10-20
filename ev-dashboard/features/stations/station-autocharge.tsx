"use client";

import { Alert, AlertAction, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldContent, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Switch } from "@/components/ui/switch";
import { useAction } from "@/features/shared/use-action";
import { api } from "@/lib/api";
import type { CommandReceipt } from "@/lib/api-types";
import { IconHistory } from "@tabler/icons-react";
import { useState } from "react";
import type { Station } from "./station-types";

export function StationAutoCharge({
  station,
  canEdit,
  onChanged,
  onShowCommands,
}: {
  station: Station;
  canEdit: boolean;
  onChanged: () => void;
  onShowCommands: () => void;
}) {
  const action = useAction();
  const [requested, setRequested] = useState<boolean | null>(null);
  const [queued, setQueued] = useState(false);
  const enabled = station.auto_charge === true;

  return (
    <Card className="max-w-3xl">
      <CardHeader>
        <CardTitle>Recharge automatique</CardTitle>
        <CardDescription>
          La recharge démarre dès le branchement d’un véhicule enregistré, sans badge : la borne
          transmet l’identifiant du véhicule (AutoCharge).
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <Field orientation="horizontal">
          <Switch
            id="station-autocharge"
            checked={enabled}
            disabled={!canEdit || action.busy}
            onCheckedChange={(checked) => setRequested(checked === true)}
          />
          <FieldContent>
            <FieldLabel htmlFor="station-autocharge">Recharge automatique activée</FieldLabel>
            <FieldDescription>
              L’identifiant du véhicule n’est pas un secret : n’activez la recharge automatique que
              sur des bornes dont l’accès est maîtrisé.
              {!canEdit && " Seul un administrateur peut modifier ce réglage."}
            </FieldDescription>
          </FieldContent>
        </Field>
        {queued && (
          <Alert>
            <IconHistory />
            <AlertTitle>Commande en file d’attente</AlertTitle>
            <AlertDescription>
              Le réglage AutoChargeEnabled est envoyé à la borne ; il s’applique dès qu’elle accepte
              la modification.
            </AlertDescription>
            <AlertAction>
              <Button size="sm" variant="outline" onClick={onShowCommands}>
                Voir les commandes
              </Button>
            </AlertAction>
          </Alert>
        )}
      </CardContent>
      <AlertDialog open={requested !== null} onOpenChange={(open) => !open && setRequested(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {requested
                ? "Activer la recharge automatique ?"
                : "Désactiver la recharge automatique ?"}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {requested
                ? "Tout véhicule enregistré pourra démarrer une recharge sur cette borne simplement en se branchant. L’identifiant d’un véhicule peut être imité : réservez ce réglage aux bornes dont l’accès est contrôlé."
                : "Les véhicules enregistrés devront de nouveau utiliser un badge ou l’application sur cette borne."}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={action.busy}>Annuler</AlertDialogCancel>
            <AlertDialogAction
              variant={requested ? "default" : "destructive"}
              disabled={action.busy}
              onClick={async (event) => {
                event.preventDefault();
                if (requested === null) {
                  return;
                }
                const receipt = await action.run(
                  "autocharge",
                  () =>
                    api<CommandReceipt>(`stations/${encodeURIComponent(station.id)}/autocharge`, {
                      method: "PUT",
                      body: JSON.stringify({ enabled: requested }),
                    }),
                  {
                    success: requested
                      ? "Recharge automatique activée."
                      : "Recharge automatique désactivée.",
                    failure: "Le réglage n’a pas pu être modifié",
                  },
                );
                setRequested(null);
                if (receipt) {
                  setQueued(true);
                  onChanged();
                }
              }}
            >
              {action.busy ? "Veuillez patienter…" : requested ? "Activer" : "Désactiver"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Card>
  );
}
