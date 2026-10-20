"use client";

import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group";
import { IconSearch } from "@tabler/icons-react";
import { useEffect, useState } from "react";

export function SearchInput({
  value,
  onChange,
  placeholder = "Rechercher…",
  label = "Rechercher",
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  label?: string;
}) {
  const [draft, setDraft] = useState(value);

  useEffect(() => {
    if (draft === value) {
      return;
    }
    const timer = setTimeout(() => onChange(draft), 300);
    return () => clearTimeout(timer);
  }, [draft, value, onChange]);

  return (
    <InputGroup className="w-full sm:w-64">
      <InputGroupInput
        type="search"
        aria-label={label}
        placeholder={placeholder}
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
      />
      <InputGroupAddon>
        <IconSearch />
      </InputGroupAddon>
    </InputGroup>
  );
}
