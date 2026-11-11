// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

"use client";

import { isStaff, useWorkspace } from "@/features/workspace/workspace-context";
import { PageHeader } from "@/features/shared/page-header";
import { CardOrdersManagement } from "./card-orders-management";
import { DriverCardScreen } from "./driver-card-screen";
import { t } from "@/lib/i18n";

export function CardsScreen() {
  const user = useWorkspace();
  const staff = isStaff(user);
  return (
    <>
      <PageHeader
        title={staff ? t("Cartes de recharge") : t("Ma carte de recharge")}
        description={
          staff
            ? t("Vente de cartes RFID aux conducteurs et préparation des commandes.")
            : t("Commandez une carte, puis activez-la dans l’application mobile.")
        }
      />
      {staff ? <CardOrdersManagement /> : <DriverCardScreen />}
    </>
  );
}
