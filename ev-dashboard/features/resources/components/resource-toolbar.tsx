import { Button } from "@/components/ui/button";
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group";
import { IconDownload, IconRefresh, IconSearch } from "@tabler/icons-react";
import { t } from "@/lib/i18n";

type Props = {
  search: string;
  onSearch: (value: string) => void;
  onRefresh: () => void;
  onExport: () => void;
  loading: boolean;
  exportDisabled: boolean;
};

export function ResourceToolbar({
  search,
  onSearch,
  onRefresh,
  onExport,
  loading,
  exportDisabled,
}: Props) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <InputGroup className="w-full sm:w-64">
        <InputGroupInput
          aria-label={t("Rechercher dans cette page")}
          placeholder={t("Rechercher dans cette page…")}
          value={search}
          onChange={(event) => onSearch(event.target.value)}
        />
        <InputGroupAddon>
          <IconSearch />
        </InputGroupAddon>
      </InputGroup>
      <Button
        variant="outline"
        size="icon"
        aria-label={t("Actualiser")}
        onClick={onRefresh}
        disabled={loading}
      >
        <IconRefresh />
      </Button>
      <Button variant="outline" onClick={onExport} disabled={exportDisabled}>
        <IconDownload data-icon="inline-start" />
        {t("Exporter cette page")}
      </Button>
    </div>
  );
}
