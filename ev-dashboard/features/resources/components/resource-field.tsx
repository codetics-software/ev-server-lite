"use client";

import { Checkbox } from "@/components/ui/checkbox";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { FieldIssue } from "@/lib/form";

import { valueLabel } from "@/lib/labels";
import type { ResourceValue } from "../hooks/use-resource-editor";
import type { FormField } from "../types";

export function ResourceField({
  field,
  value,
  invalid,
  errors,
  onBlur,
  onChange,
}: {
  field: FormField;
  value: ResourceValue;
  invalid: boolean;
  errors: Array<FieldIssue | undefined>;
  onBlur: () => void;
  onChange: (value: ResourceValue) => void;
}) {
  return (
    <Field
      orientation={field.type === "checkbox" ? "horizontal" : "vertical"}
      data-invalid={invalid}
    >
      <FieldLabel htmlFor={`edit-${field.key}`}>{field.label}</FieldLabel>
      {field.options ? (
        <Select
          name={field.key}
          value={String(value)}
          onValueChange={(selected) => {
            if (selected) {
              onChange(selected);
            }
          }}
        >
          <SelectTrigger id={`edit-${field.key}`} className="w-full" aria-invalid={invalid}>
            <SelectValue>
              {(selected: string | null) => (selected ? valueLabel(selected) : null)}
            </SelectValue>
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {field.options.map((option) => (
                <SelectItem key={option} value={option}>
                  {valueLabel(option)}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      ) : field.type === "checkbox" ? (
        <Checkbox
          id={`edit-${field.key}`}
          name={field.key}
          checked={value === true}
          onCheckedChange={(checked) => onChange(checked === true)}
        />
      ) : (
        <Input
          id={`edit-${field.key}`}
          name={field.key}
          type={field.type || "text"}
          step={
            field.type === "number" ? "any" : field.type === "datetime-local" ? "0.001" : undefined
          }
          required={field.required}
          value={String(value)}
          onBlur={onBlur}
          onChange={(event) => onChange(event.target.value)}
          aria-invalid={invalid}
          maxLength={field.maxLength ?? 320}
        />
      )}
      {invalid && <FieldError errors={errors} />}
    </Field>
  );
}
