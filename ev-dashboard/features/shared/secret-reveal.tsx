"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
  InputGroup,
  InputGroupAddon,
  InputGroupButton,
  InputGroupInput,
} from "@/components/ui/input-group";
import { IconCheck, IconCopy, IconKey } from "@tabler/icons-react";
import { useState } from "react";

export function CopyField({ value, label }: { value: string; label: string }) {
  const [copied, setCopied] = useState(false);
  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }
  return (
    <InputGroup>
      <InputGroupInput aria-label={label} value={value} readOnly className="font-mono text-xs" />
      <InputGroupAddon align="inline-end">
        <InputGroupButton size="icon-xs" aria-label={copied ? "Copié" : "Copier"} onClick={copy}>
          {copied ? <IconCheck /> : <IconCopy />}
        </InputGroupButton>
      </InputGroupAddon>
    </InputGroup>
  );
}

export function SecretReveal({
  title,
  message,
  secret,
  extra,
}: {
  title: string;
  message?: string;
  secret: string;
  extra?: { label: string; value: string }[];
}) {
  return (
    <div className="flex flex-col gap-4">
      <Alert>
        <IconKey />
        <AlertTitle>{title}</AlertTitle>
        <AlertDescription>
          {message ?? "Cette valeur n’est affichée qu’une seule fois. Conservez-la en lieu sûr."}
        </AlertDescription>
      </Alert>
      <CopyField value={secret} label="Valeur secrète" />
      {extra?.map((item) => (
        <div key={item.label} className="flex flex-col gap-1.5 text-sm">
          <span className="text-muted-foreground">{item.label}</span>
          <CopyField value={item.value} label={item.label} />
        </div>
      ))}
    </div>
  );
}
