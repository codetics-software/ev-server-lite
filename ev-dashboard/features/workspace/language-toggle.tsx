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
import { localeNames, locales, t, type Locale } from "@/lib/i18n";
import { changeLocale, useLocale } from "@/lib/i18n/provider";
import { IconLanguage } from "@tabler/icons-react";

export function LanguageToggle() {
  const locale = useLocale();
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={<Button variant="ghost" size="icon" aria-label={t("Changer de langue")} />}
      >
        <IconLanguage />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-44">
        <DropdownMenuGroup>
          <DropdownMenuLabel>{t("Langue")}</DropdownMenuLabel>
          <DropdownMenuRadioGroup
            value={locale}
            onValueChange={(value) => {
              if (value !== locale) {
                changeLocale(value as Locale);
              }
            }}
          >
            {locales.map((item) => (
              <DropdownMenuRadioItem key={item} value={item} lang={item}>
                {localeNames[item]}
              </DropdownMenuRadioItem>
            ))}
          </DropdownMenuRadioGroup>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
