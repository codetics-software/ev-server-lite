"use client";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { ErrorState, LoadingState } from "@/features/shared/states";
import { ToneBadge } from "@/features/shared/status-badge";
import { useApi } from "@/hooks/use-api";
import { saveBlob } from "@/lib/api";
import { IconCheck, IconCopy, IconDownload } from "@tabler/icons-react";
import { useState } from "react";
import type { SignedMeterData } from "./session-types";
import { t } from "@/lib/i18n";

function escapeXml(value: string) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function detectedFormat(data: SignedMeterData) {
  if (data.format && data.format !== "OTHER") {
    return data.format;
  }
  return [data.start, data.stop].some((value) => value?.startsWith("OCMF|")) ? "OCMF" : null;
}

function transparencyFile(data: SignedMeterData, id: number) {
  const format = detectedFormat(data);
  const readings = [
    { context: "Transaction.Begin", value: data.start },
    { context: "Transaction.End", value: data.stop },
  ].filter((reading): reading is { context: string; value: string } => Boolean(reading.value));
  if (format === "OCMF" && readings.length === 1) {
    return {
      name: `releves-signes-session-${id}.txt`,
      type: "text/plain;charset=utf-8",
      content: `${readings[0].value.trim()}\n`,
    };
  }
  if (format === "XML" && readings.length === 1) {
    return {
      name: `releves-signes-session-${id}.xml`,
      type: "application/xml;charset=utf-8",
      content: readings[0].value,
    };
  }
  const values = readings
    .map(
      (reading) =>
        `  <value transactionId="${id}" context="${reading.context}">\n    <signedData${format ? ` format="${format}"` : ""} encoding="plain">${escapeXml(reading.value)}</signedData>\n  </value>`,
    )
    .join("\n");
  return {
    name: `releves-signes-session-${id}.xml`,
    type: "application/xml;charset=utf-8",
    content: `<?xml version="1.0" encoding="UTF-8"?>\n<values>\n${values}\n</values>\n`,
  };
}

function Reading({ label, value }: { label: string; value: string | null }) {
  const [copied, setCopied] = useState(false);
  async function copy() {
    if (!value) {
      return;
    }
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center justify-between gap-2">
        <span className="text-sm text-muted-foreground">{label}</span>
        {value && (
          <Button variant="ghost" size="sm" onClick={copy}>
            {copied ? (
              <IconCheck data-icon="inline-start" />
            ) : (
              <IconCopy data-icon="inline-start" />
            )}
            {copied ? t("Copié") : t("Copier")}
          </Button>
        )}
      </div>
      {value ? (
        <pre className="max-h-40 overflow-auto rounded-lg border bg-muted p-3 font-mono text-xs break-all whitespace-pre-wrap">
          {value}
        </pre>
      ) : (
        <p className="text-sm text-muted-foreground">{t("Relevé de fin signé non reçu")}</p>
      )}
    </div>
  );
}

export function SignedMeterDataCard({ sessionId }: { sessionId: number }) {
  const { data, error, loading } = useApi<SignedMeterData>(`transactions/${sessionId}/signed-data`);
  const format = data ? detectedFormat(data) : null;
  if (data && !data.start && !data.stop) {
    return null;
  }
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("Données signées (Eichrecht)")}</CardTitle>
        <CardDescription>
          {t(
            "Relevés signés par le compteur, à vérifier avec un logiciel de transparence (S.A.F.E.).",
          )}
        </CardDescription>
        {data && (
          <CardAction>
            <Button
              variant="outline"
              onClick={() => {
                const file = transparencyFile(data, sessionId);
                saveBlob(new Blob([file.content], { type: file.type }), file.name);
              }}
            >
              <IconDownload data-icon="inline-start" />
              {t("Télécharger les relevés signés")}
            </Button>
          </CardAction>
        )}
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {error && !data ? (
          <ErrorState error={error} />
        ) : loading && !data ? (
          <LoadingState rows={2} />
        ) : (
          data && (
            <>
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <span className="text-muted-foreground">{t("Format")}</span>
                <ToneBadge tone="neutral">{format ?? data.format ?? t("Inconnu")}</ToneBadge>
              </div>
              {data.start ? (
                <Reading label={t("Relevé de début")} value={data.start} />
              ) : (
                <p className="text-sm text-muted-foreground">
                  {t("Relevé de début signé non reçu")}
                </p>
              )}
              <Reading label={t("Relevé de fin")} value={data.stop} />
            </>
          )
        )}
      </CardContent>
    </Card>
  );
}
