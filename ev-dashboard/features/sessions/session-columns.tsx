import type { Column } from "@/features/shared/data-table";
import { ToneBadge } from "@/features/shared/status-badge";
import { date, display, duration, kw, kwh, money, percent } from "@/lib/format";
import Link from "next/link";
import { sessionDuration, type ChargingSession } from "./session-types";

export function SessionState({ session }: { session: ChargingSession }) {
  if (!session.stopped_at) {
    return <ToneBadge tone="active">En cours</ToneBadge>;
  }
  if (session.authorization_status && session.authorization_status !== "Accepted") {
    return <ToneBadge tone="danger">{display(session.authorization_status)}</ToneBadge>;
  }
  return <ToneBadge tone="neutral">Terminée</ToneBadge>;
}

export function sessionColumns({
  showUser,
  showStation = true,
  active,
}: {
  showUser: boolean;
  showStation?: boolean;
  active: boolean;
}): Column<ChargingSession>[] {
  return [
    {
      id: "id",
      header: "Session",
      sort: (session) => session.id,
      cell: (session) => (
        <Link
          href={`/transactions/${session.id}`}
          className="flex flex-col hover:text-primary dark:hover:text-sidebar-primary"
          onClick={(event) => event.stopPropagation()}
        >
          <span className="font-medium tabular-nums">n° {session.id}</span>
          <SessionState session={session} />
        </Link>
      ),
    },
    ...(showStation
      ? [
          {
            id: "station",
            header: "Borne",
            sort: (session: ChargingSession) => session.station_id,
            cell: (session: ChargingSession) => (
              <span className="flex flex-col">
                <span>{session.station_id}</span>
                <span className="text-xs text-muted-foreground">
                  Connecteur {session.connector_id}
                </span>
              </span>
            ),
          },
        ]
      : []),
    ...(showUser
      ? [
          {
            id: "user",
            header: "Utilisateur",
            sort: (session: ChargingSession) => session.user_name ?? "",
            cell: (session: ChargingSession) => (
              <span className="flex flex-col">
                <span>{session.user_name ?? (session.roaming_partner ? "Itinérance" : "—")}</span>
                <span className="text-xs text-muted-foreground">
                  {session.token_uid ?? (session.public_payment ? "Paiement public" : "")}
                </span>
              </span>
            ),
          },
        ]
      : []),
    {
      id: "start",
      header: "Début",
      sort: (session) => session.started_at,
      cell: (session) => <span className="whitespace-nowrap">{date(session.started_at)}</span>,
    },
    {
      id: "duration",
      header: "Durée",
      align: "right",
      sort: (session) => sessionDuration(session),
      cell: (session) => duration(sessionDuration(session)),
    },
    {
      id: "inactivity",
      header: "Inactivité",
      align: "right",
      sort: (session) => session.inactivity_seconds ?? null,
      cell: (session) => duration(session.inactivity_seconds),
    },
    {
      id: "energy",
      header: "Énergie",
      align: "right",
      sort: (session) => session.energy_kwh ?? null,
      cell: (session) => kwh(session.energy_kwh),
    },
    ...(active
      ? [
          {
            id: "power",
            header: "Puissance",
            align: "right" as const,
            sort: (session: ChargingSession) => session.current_power_kw ?? null,
            cell: (session: ChargingSession) => kw(session.current_power_kw),
          },
        ]
      : []),
    {
      id: "soc",
      header: "Batterie",
      align: "right",
      sort: (session) => session.state_of_charge ?? null,
      cell: (session) =>
        session.state_of_charge != null
          ? `${session.state_of_charge_start != null ? `${percent(session.state_of_charge_start)} → ` : ""}${percent(session.state_of_charge)}`
          : "—",
    },
    {
      id: "price",
      header: "Prix",
      align: "right",
      sort: (session) => session.price ?? null,
      cell: (session) => money(session.price, session.currency),
    },
  ];
}
