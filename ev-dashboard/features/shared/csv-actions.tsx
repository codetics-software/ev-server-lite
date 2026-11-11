"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { download, errorMessage, query, upload } from "@/lib/api";
import type { ImportResult } from "@/lib/api-types";
import { IconDownload, IconUpload } from "@tabler/icons-react";
import { useId, useState } from "react";
import { notify, notifyError } from "./use-action";
import { t, tp } from "@/lib/i18n";

type Parameters = Record<string, string | number | boolean | null | undefined>;

export function ExportButton({
  path,
  filename,
  parameters = {},
  label = "Exporter",
}: {
  path: string;
  filename: string;
  parameters?: Parameters;
  label?: string;
}) {
  const [pending, setPending] = useState(false);

  async function exportFile() {
    setPending(true);
    try {
      await download(`${path}${query(parameters)}`, `${filename}.csv`);
    } catch (error) {
      notifyError(t("L’export n’a pas abouti"), errorMessage(error, t("Réessayez plus tard.")));
    } finally {
      setPending(false);
    }
  }

  return (
    <Button variant="outline" onClick={exportFile} disabled={pending}>
      <IconDownload data-icon="inline-start" />
      {pending ? t("Export…") : label}
    </Button>
  );
}

export function ImportButton({
  path,
  title,
  columns,
  onImported,
}: {
  path: string;
  title: string;
  columns: string;
  onImported: () => void;
}) {
  const id = useId();
  const [open, setOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [pending, setPending] = useState(false);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [error, setError] = useState("");

  function reset(next: boolean) {
    setOpen(next);
    if (!next) {
      setFile(null);
      setResult(null);
      setError("");
    }
  }

  async function submit() {
    if (!file) {
      setError(t("Choisissez un fichier CSV."));
      return;
    }
    setPending(true);
    setError("");
    try {
      const imported = await upload<ImportResult>(path, await file.text(), "text/csv");
      setResult(imported);
      onImported();
      notify(
        t("Import terminé"),
        t("{created} créés, {updated} mis à jour.", {
          created: imported.created,
          updated: imported.updated,
        }),
      );
    } catch (failure) {
      setError(errorMessage(failure, t("L’import n’a pas abouti.")));
    } finally {
      setPending(false);
    }
  }

  return (
    <>
      <Button variant="outline" onClick={() => reset(true)}>
        <IconUpload data-icon="inline-start" />
        {t("Importer")}
      </Button>
      <Dialog open={open} onOpenChange={reset}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{title}</DialogTitle>
            <DialogDescription>
              {t(
                "Fichier CSV encodé en UTF-8, séparateur point-virgule, avec une ligne d’en-tête.",
              )}
            </DialogDescription>
          </DialogHeader>
          {result ? (
            <div className="flex flex-col gap-3 text-sm">
              <dl className="grid grid-cols-3 gap-3 text-center">
                {(
                  [
                    [t("Créés"), result.created],
                    [t("Mis à jour"), result.updated],
                    [t("Ignorés"), result.skipped],
                  ] as const
                ).map(([label, value]) => (
                  <div key={label} className="rounded-lg border p-3">
                    <dt className="text-xs text-muted-foreground">{label}</dt>
                    <dd className="text-xl font-medium tabular-nums">{value}</dd>
                  </div>
                ))}
              </dl>
              {result.errors.length > 0 && (
                <Alert variant="destructive">
                  <AlertTitle>
                    {tp(
                      result.errors.length,
                      "{count} ligne en erreur",
                      "{count} lignes en erreur",
                    )}
                  </AlertTitle>
                  <AlertDescription>
                    <ul className="max-h-40 list-disc overflow-auto pl-4">
                      {result.errors.map((item) => (
                        <li key={`${item.line}-${item.message}`}>
                          {t("Ligne {line} : {message}", {
                            line: item.line,
                            message: item.message,
                          })}
                        </li>
                      ))}
                    </ul>
                  </AlertDescription>
                </Alert>
              )}
            </div>
          ) : (
            <Field>
              <FieldLabel htmlFor={id}>{t("Fichier")}</FieldLabel>
              <Input
                id={id}
                type="file"
                accept=".csv,text/csv,text/plain"
                onChange={(event) => setFile(event.target.files?.[0] ?? null)}
              />
              <FieldDescription>
                {t("Colonnes attendues : {columns}", { columns })}
              </FieldDescription>
            </Field>
          )}
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <DialogFooter>
            {result ? (
              <Button onClick={() => reset(false)}>{t("Terminer")}</Button>
            ) : (
              <Button onClick={submit} disabled={pending || !file}>
                {pending ? t("Import…") : t("Importer le fichier")}
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
