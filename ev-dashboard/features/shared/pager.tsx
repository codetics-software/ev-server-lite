import { Button } from "@/components/ui/button";
import { IconChevronLeft, IconChevronRight } from "@tabler/icons-react";

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
      aria-label="Pagination"
      className="flex flex-wrap items-center justify-between gap-3 border-t pt-4"
    >
      <span className="text-xs text-muted-foreground tabular-nums" aria-live="polite">
        {total ? `${offset + 1}–${Math.min(offset + count, total)} sur ${total}` : "0 élément"}
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
            Précédent
          </Button>
          <span className="text-xs tabular-nums text-muted-foreground">
            Page {page} sur {pages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={offset + pageSize >= total || loading}
            onClick={() => onChange(offset + pageSize)}
          >
            Suivant
            <IconChevronRight data-icon="inline-end" />
          </Button>
        </div>
      )}
    </nav>
  );
}
