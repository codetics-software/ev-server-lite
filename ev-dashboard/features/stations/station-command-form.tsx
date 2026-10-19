"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { api, type Row } from "@/lib/api";
import { useState, type FormEvent } from "react";

import { commands } from "./station-commands";

export function StationCommandForm({ station }: { station: string }) {
  const [action, setAction] = useState("GetConfiguration");
  const [payload, setPayload] = useState("{}");
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState("");
  const [failure, setFailure] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    setPending(true);
    setFailure("");
    setMessage("");

    try {
      const parsed = JSON.parse(payload);

      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        throw new Error("Enter a JSON object for the command payload.");
      }

      const result = await api<Row>(`stations/${encodeURIComponent(station)}/commands`, {
        method: "POST",
        body: JSON.stringify({ action, payload: parsed }),
      });
      setMessage(`Command queued: ${result.id}. Follow its response in Command activity.`);
    } catch (error) {
      setFailure(error instanceof Error ? error.message : "Unable to queue command");
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={submit}>
      <FieldGroup>
        <Field>
          <FieldLabel>OCPP 1.6 command</FieldLabel>
          <Select
            value={action}
            onValueChange={(value) => {
              if (value) {
                setAction(value);
                setPayload(JSON.stringify(commands[value], null, 2));
              }
            }}
          >
            <SelectTrigger className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {Object.keys(commands).map((name) => (
                  <SelectItem key={name} value={name}>
                    {name}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        </Field>
        <Field>
          <FieldLabel htmlFor="command-payload">Command payload</FieldLabel>
          <Textarea
            id="command-payload"
            className="min-h-32 font-mono"
            value={payload}
            onChange={(event) => setPayload(event.target.value)}
            required
          />
          <FieldDescription>
            Review the target connector and parameters before sending. A response confirms the
            station received the command; session activity confirms charging.
          </FieldDescription>
        </Field>
        {message && (
          <Alert>
            <AlertTitle>Queued</AlertTitle>
            <AlertDescription>{message}</AlertDescription>
          </Alert>
        )}
        {failure && (
          <Alert variant="destructive">
            <AlertDescription>{failure}</AlertDescription>
          </Alert>
        )}
        <Button type="submit" disabled={pending}>
          {pending ? "Queueing…" : "Send command"}
        </Button>
      </FieldGroup>
    </form>
  );
}
