"use client";

import {
  Combobox,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
} from "@/components/ui/combobox";
import { useApi } from "@/hooks/use-api";
import { query } from "@/lib/api";
import type { Row } from "@/lib/api-types";
import { useEffect, useMemo, useState } from "react";

export type PickerOption = { value: string; label: string; description?: string };

type Source = {
  path: string;
  toOption: (row: Row) => PickerOption;
};

const text = (value: unknown) => (value === null || value === undefined ? "" : String(value));
const record = (row: Row) => ({ value: text(row.id), label: text(row.name) || text(row.id) });

export const pickerSources = {
  companies: { path: "records/companies", toOption: record },
  sites: { path: "records/sites", toOption: record },
  "site-areas": { path: "records/site-areas", toOption: record },
  assets: { path: "records/assets", toOption: record },
  "pricing-definitions": { path: "records/pricing-definitions", toOption: record },
  "charging-station-templates": { path: "records/charging-station-templates", toOption: record },
  "car-catalog": {
    path: "records/car-catalog",
    toOption: (row: Row) => {
      const data = (row.data ?? {}) as Row;
      return {
        value: text(row.id),
        label: [data.make, data.model, data.version].filter(Boolean).join(" ") || text(row.name),
      };
    },
  },
  stations: {
    path: "stations",
    toOption: (row: Row) => ({
      value: text(row.id),
      label: text(row.name) || text(row.id),
      description: text(row.id),
    }),
  },
  users: {
    path: "users",
    toOption: (row: Row) => ({
      value: text(row.id),
      label: text(row.display_name ?? row.name) || text(row.email),
      description: text(row.email),
    }),
  },
  tokens: {
    path: "tokens",
    toOption: (row: Row) => ({
      value: text(row.uid),
      label: text(row.visual_id) || text(row.uid),
      description: text(row.user_name) || text(row.uid),
    }),
  },
} satisfies Record<string, Source>;

export type PickerSource = keyof typeof pickerSources;

function useDebounced(value: string, delay = 250) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

export function useOptions(
  source: PickerSource,
  filters: Record<string, string | undefined> = {},
  search = "",
) {
  const config: Source = pickerSources[source];
  const { data, loading, error } = useApi<{ items: Row[] } | Row[]>(
    `${config.path}${query({ ...filters, search, offset: 0, limit: 100 })}`,
  );
  const rows = Array.isArray(data) ? data : (data?.items ?? []);
  return { options: rows.map(config.toOption), rows, loading, error };
}

export function EntityPicker({
  source,
  value,
  onChange,
  id,
  placeholder = "Rechercher…",
  filters,
  disabled,
  invalid,
  fallbackLabel,
  onBlur,
}: {
  source: PickerSource;
  value: string;
  onChange: (value: string, option: PickerOption | null) => void;
  id?: string;
  placeholder?: string;
  filters?: Record<string, string | undefined>;
  disabled?: boolean;
  invalid?: boolean;
  fallbackLabel?: string;
  onBlur?: () => void;
}) {
  const [input, setInput] = useState("");
  const [chosen, setChosen] = useState<PickerOption | null>(null);
  const search = useDebounced(input);
  const { options, loading } = useOptions(source, filters, search);
  const selected = useMemo(() => {
    if (!value) {
      return null;
    }
    return (
      options.find((option) => option.value === value) ??
      (chosen?.value === value ? chosen : { value, label: fallbackLabel || value })
    );
  }, [options, value, chosen, fallbackLabel]);
  const items =
    selected && !options.some((option) => option.value === selected.value)
      ? [selected, ...options]
      : options;

  return (
    <Combobox
      items={items}
      value={selected}
      disabled={disabled}
      filter={null}
      itemToStringLabel={(option: PickerOption) => option.label}
      itemToStringValue={(option: PickerOption) => option.value}
      isItemEqualToValue={(left: PickerOption, right: PickerOption) => left.value === right.value}
      onInputValueChange={(next, details) => {
        if (details.reason === "input-change" || details.reason === "input-clear") {
          setInput(next);
        }
      }}
      onValueChange={(option: PickerOption | null) => {
        setChosen(option);
        setInput("");
        onChange(option?.value ?? "", option);
      }}
    >
      <ComboboxInput
        id={id}
        placeholder={placeholder}
        showClear={Boolean(value)}
        aria-invalid={invalid || undefined}
        onBlur={onBlur}
        className="w-full"
      />
      <ComboboxContent>
        <ComboboxEmpty>{loading ? "Recherche…" : "Aucun résultat"}</ComboboxEmpty>
        <ComboboxList>
          {(option: PickerOption) => (
            <ComboboxItem key={option.value} value={option}>
              <span className="flex min-w-0 flex-col">
                <span className="truncate">{option.label}</span>
                {option.description && option.description !== option.label && (
                  <span className="truncate text-xs text-muted-foreground">
                    {option.description}
                  </span>
                )}
              </span>
            </ComboboxItem>
          )}
        </ComboboxList>
      </ComboboxContent>
    </Combobox>
  );
}

export function useLabels(source: PickerSource) {
  const { rows } = useOptions(source);
  const config: Source = pickerSources[source];
  return useMemo(() => {
    const map = new Map<string, string>();
    for (const row of rows) {
      const option = config.toOption(row);
      map.set(option.value, option.label);
    }
    return map;
  }, [rows, config]);
}
