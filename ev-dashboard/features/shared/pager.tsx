import { Button } from "@/components/ui/button";
import { IconChevronLeft, IconChevronRight } from "@tabler/icons-react";
import { t } from "@/lib/i18n";

export function Pager({
  offset,
  count,
  total,
  pageSize,
  loading,
  onChange,
}: {
  offset: number;
  count: number;
  total: number;
  pageSize: number;
  loading?: boolean;
  onChange: (offset: number) => void;
}) {
  const page = Math.floor(offset / pageSize) + 1;
  const pages = Math.max(1, Math.ceil(total / pageSize));
  return (
    <nav
      aria-label={t("Pagination")}
      className="flex flex-wrap items-center justify-between gap-3 border-t pt-4"
    >
      <span className="text-xs text-muted-foreground tabular-nums" aria-live="polite">
        {total
          ? t("{start}–{end} sur {total}", {
              start: offset + 1,
              end: Math.min(offset + count, total),
              total,
            })
          : t("0 élément")}
      </span>
      {pages > 1 && (
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={offset === 0 || loading}
            onClick={() => onChange(Math.max(0, offset - pageSize))}
          >
            <IconChevronLeft data-icon="inline-start" />
            {t("Précédent")}
          </Button>
          <span className="text-xs tabular-nums text-muted-foreground">
            {t("Page {page} sur {pages}", { page, pages })}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={offset + pageSize >= total || loading}
            onClick={() => onChange(offset + pageSize)}
          >
            {t("Suivant")}
            <IconChevronRight data-icon="inline-end" />
          </Button>
        </div>
      )}
    </nav>
  );
}
