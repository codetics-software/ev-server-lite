"use client";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useTheme, type ThemePreference } from "@/hooks/use-theme";
import { IconDeviceDesktop, IconMoon, IconSun } from "@tabler/icons-react";

export function ThemeToggle() {
  const { preference, dark, setPreference } = useTheme();
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={<Button variant="ghost" size="icon" aria-label="Changer l’apparence" />}
      >
        {dark ? <IconMoon /> : <IconSun />}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-44">
        <DropdownMenuGroup>
          <DropdownMenuLabel>Apparence</DropdownMenuLabel>
          <DropdownMenuRadioGroup
            value={preference}
            onValueChange={(value) => setPreference(value as ThemePreference)}
          >
            <DropdownMenuRadioItem value="light">
              <IconSun />
              Clair
            </DropdownMenuRadioItem>
            <DropdownMenuRadioItem value="dark">
              <IconMoon />
              Sombre
            </DropdownMenuRadioItem>
            <DropdownMenuRadioItem value="system">
              <IconDeviceDesktop />
              Système
            </DropdownMenuRadioItem>
          </DropdownMenuRadioGroup>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
