"use client";

import {
  Field,
  FieldContent,
  FieldDescription,
  FieldError,
  FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { isInvalid } from "@/lib/form";
import type { AnyFieldApi } from "@tanstack/react-form";
import { useId, type ComponentProps, type ReactNode } from "react";
import { EntityPicker, type PickerSource } from "./entity-picker";

type Base = {
  field: AnyFieldApi;
  label: ReactNode;
  description?: ReactNode;
};

function useFieldState(field: AnyFieldApi) {
  const id = useId();
  const invalid = isInvalid(field);
  return { id: `${field.name}-${id}`, invalid, errors: field.state.meta.errors };
}

export function FormText({
  field,
  label,
  description,
  ...input
}: Base & Omit<ComponentProps<typeof Input>, "value" | "onChange" | "onBlur" | "name" | "id">) {
  const { id, invalid, errors } = useFieldState(field);
  return (
    <Field data-invalid={invalid}>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <Input
        id={id}
        name={field.name}
        value={String(field.state.value ?? "")}
        onBlur={field.handleBlur}
        onChange={(event) => field.handleChange(event.target.value)}
        aria-invalid={invalid}
        step={input.type === "number" ? "any" : undefined}
        {...input}
      />
      {description && <FieldDescription>{description}</FieldDescription>}
      {invalid && <FieldError errors={errors} />}
    </Field>
  );
}

export function FormTextarea({
  field,
  label,
  description,
  ...input
}: Base & Omit<ComponentProps<typeof Textarea>, "value" | "onChange" | "onBlur" | "name" | "id">) {
  const { id, invalid, errors } = useFieldState(field);
  return (
    <Field data-invalid={invalid}>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <Textarea
        id={id}
        name={field.name}
        value={String(field.state.value ?? "")}
        onBlur={field.handleBlur}
        onChange={(event) => field.handleChange(event.target.value)}
        aria-invalid={invalid}
        {...input}
      />
      {description && <FieldDescription>{description}</FieldDescription>}
      {invalid && <FieldError errors={errors} />}
    </Field>
  );
}

export function FormSwitch({ field, label, description, disabled }: Base & { disabled?: boolean }) {
  const { id } = useFieldState(field);
  return (
    <Field orientation="horizontal">
      <Switch
        id={id}
        name={field.name}
        checked={field.state.value === true}
        disabled={disabled}
        onCheckedChange={(checked) => field.handleChange(checked === true)}
      />
      <FieldContent>
        <FieldLabel htmlFor={id}>{label}</FieldLabel>
        {description && <FieldDescription>{description}</FieldDescription>}
      </FieldContent>
    </Field>
  );
}

export type Option = { value: string; label: string };

export function OptionSelect({
  id,
  value,
  onChange,
  options,
  placeholder = "Choisir…",
  invalid,
  disabled,
  className = "w-full",
  label,
}: {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  options: readonly Option[];
  placeholder?: string;
  invalid?: boolean;
  disabled?: boolean;
  className?: string;
  label?: string;
}) {
  return (
    <Select
      value={value}
      disabled={disabled}
      onValueChange={(selected) => onChange(selected === null ? "" : String(selected))}
    >
      <SelectTrigger id={id} className={className} aria-invalid={invalid} aria-label={label}>
        <SelectValue placeholder={placeholder}>
          {(selected: string | null) =>
            options.find((option) => option.value === selected)?.label ?? placeholder
          }
        </SelectValue>
      </SelectTrigger>
      <SelectContent>
        <SelectGroup>
          {options.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectGroup>
      </SelectContent>
    </Select>
  );
}

export function FormSelect({
  field,
  label,
  description,
  options,
  placeholder,
}: Base & { options: readonly Option[]; placeholder?: string }) {
  const { id, invalid, errors } = useFieldState(field);
  return (
    <Field data-invalid={invalid}>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <OptionSelect
        id={id}
        value={String(field.state.value ?? "")}
        onChange={(value) => field.handleChange(value)}
        options={options}
        placeholder={placeholder}
        invalid={invalid}
      />
      {description && <FieldDescription>{description}</FieldDescription>}
      {invalid && <FieldError errors={errors} />}
    </Field>
  );
}

export function FormPicker({
  field,
  label,
  description,
  source,
  filters,
  placeholder,
  fallbackLabel,
}: Base & {
  source: PickerSource;
  filters?: Record<string, string | undefined>;
  placeholder?: string;
  fallbackLabel?: string;
}) {
  const { id, invalid, errors } = useFieldState(field);
  return (
    <Field data-invalid={invalid}>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <EntityPicker
        id={id}
        source={source}
        value={String(field.state.value ?? "")}
        onChange={(value) => field.handleChange(value)}
        onBlur={field.handleBlur}
        filters={filters}
        invalid={invalid}
        placeholder={placeholder}
        fallbackLabel={fallbackLabel}
      />
      {description && <FieldDescription>{description}</FieldDescription>}
      {invalid && <FieldError errors={errors} />}
    </Field>
  );
}
