"use client";

import { Checkbox } from "@/components/ui/checkbox";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { type Row } from "@/lib/api";

import { inputValue } from "../resource-format";
import type { FormField } from "../types";

export function ResourceField({
  field,
  existing,
  isRecord,
}: {
  field: FormField;
  existing: Row;
  isRecord: boolean;
}) {
  const aliases: Record<string, string> = {
    userId: "user_id",
    expiresAt: "expires_at",
  };
  const alias = aliases[field.key];
  const fallback =
    field.key === "name"
      ? existing.name || existing.display_name
      : (existing[field.key] ?? (alias ? existing[alias] : undefined));
  const value = (isRecord ? (existing.data as Row)?.[field.key] : undefined) ?? fallback;

  return (
    <Field key={field.key} orientation={field.type === "checkbox" ? "horizontal" : "vertical"}>
      <FieldLabel htmlFor={`edit-${field.key}`}>{field.label}</FieldLabel>
      {field.options ? (
        <Select name={field.key} defaultValue={String(value || field.options[0])}>
          <SelectTrigger id={`edit-${field.key}`} className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {field.options.map((option) => (
                <SelectItem key={option} value={option}>
                  {option}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      ) : field.type === "checkbox" ? (
        <Checkbox id={`edit-${field.key}`} name={field.key} defaultChecked={value !== false} />
      ) : (
        <Input
          id={`edit-${field.key}`}
          name={field.key}
          type={field.type || "text"}
          step={
            field.type === "number" ? "any" : field.type === "datetime-local" ? "0.001" : undefined
          }
          required={field.required}
          defaultValue={inputValue(value, field.type)}
          maxLength={field.key === "uid" ? 20 : 320}
        />
      )}
    </Field>
  );
}
