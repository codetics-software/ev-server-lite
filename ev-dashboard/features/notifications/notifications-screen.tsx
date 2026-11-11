"use client";

import { Field, FieldLabel } from "@/components/ui/field";
import { DataTable, type Column } from "@/features/shared/data-table";
import { EntityPicker } from "@/features/shared/entity-picker";
import { OptionSelect } from "@/features/shared/form-fields";
import { PageHeader } from "@/features/shared/page-header";
import { Pager } from "@/features/shared/pager";
import { EmptyState, ErrorState, ListState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { usePaged } from "@/features/shared/use-paged";
import { useApi } from "@/hooks/use-api";
import { date, display } from "@/lib/format";
import { notificationLabels } from "@/lib/labels";
import { IconBell } from "@tabler/icons-react";
import { useState } from "react";
import { t } from "@/lib/i18n";

type NotificationItem = {
  id: string;
  type: string;
  user_id: string | null;
  recipient: string;
  subject: string;
  channel: string;
  created_at: string;
  sent_at: string | null;
};

type NotificationType = { type: string; label: string; audience: string };

export function NotificationsScreen() {
  const [type, setType] = useState("all");
  const [userId, setUserId] = useState("");
  const types = useApi<NotificationType[]>("notifications/types");
  const list = usePaged<NotificationItem>("notifications", {
    type: type === "all" ? null : type,
    userId,
  });
  const label = (value: string) =>
    types.data?.find((item) => item.type === value)?.label ?? notificationLabels[value] ?? value;
  const columns: Column<NotificationItem>[] = [
    {
      id: "date",
      header: t("Date"),
      sort: (item) => item.created_at,
      cell: (item) => <span className="whitespace-nowrap">{date(item.created_at)}</span>,
    },
    {
      id: "type",
      header: t("Notification"),
      sort: (item) => item.type,
      cell: (item) => label(item.type),
    },
    {
      id: "recipient",
      header: t("Destinataire"),
      sort: (item) => item.recipient,
      cell: (item) => (
        <span className="flex flex-col">
          <span>{item.recipient}</span>
          <span className="text-xs text-muted-foreground">{display(item.channel)}</span>
        </span>
      ),
    },
    {
      id: "subject",
      header: t("Objet"),
      cell: (item) => <span className="block max-w-96 truncate">{item.subject}</span>,
    },
    {
      id: "sent",
      header: t("Envoi"),
      sort: (item) => item.sent_at ?? "",
      cell: (item) =>
        item.sent_at ? (
          <ToneBadge tone="success">{t("Envoyée")}</ToneBadge>
        ) : (
          <ToneBadge tone="progress">{t("En attente")}</ToneBadge>
        ),
    },
  ];
  return (
    <>
      <PageHeader
        title={t("Notifications")}
        description={t("Courriels envoyés aux utilisateurs et aux administrateurs du réseau.")}
      />
      <div className="flex flex-wrap items-end gap-3">
        <OptionSelect
          value={type}
          onChange={setType}
          label={t("Type de notification")}
          className="w-full sm:w-72"
          options={[
            { value: "all", label: t("Toutes les notifications") },
            ...(
              types.data ??
              Object.keys(notificationLabels).map((key) => ({
                type: key,
                label: notificationLabels[key],
              }))
            ).map((item) => ({
              value: item.type,
              label: item.label,
            })),
          ]}
        />
        <Field className="w-full sm:w-64">
          <FieldLabel htmlFor="notifications-user" className="sr-only">
            {t("Utilisateur")}
          </FieldLabel>
          <EntityPicker
            id="notifications-user"
            source="users"
            value={userId}
            onChange={setUserId}
            placeholder={t("Tous les utilisateurs")}
          />
        </Field>
      </div>
      {list.error && list.data && <ErrorState error={list.error} onRetry={list.refresh} />}
      <ListState
        data={list.data}
        items={list.items}
        loading={list.loading}
        error={list.error}
        onRetry={list.refresh}
        empty={
          <EmptyState
            icon={<IconBell />}
            title={t("Aucune notification")}
            description={t("Les courriels envoyés par la supervision apparaîtront ici.")}
          />
        }
      >
        <DataTable
          label={t("Notifications")}
          rows={list.items}
          columns={columns}
          rowKey={(item) => item.id}
        />
      </ListState>
      <Pager
        offset={list.offset}
        count={list.items.length}
        total={list.total}
        pageSize={list.pageSize}
        loading={list.loading}
        onChange={list.setOffset}
      />
    </>
  );
}
