import { Button } from "@/components/ui/button";
import { PAGE_SIZE } from "../hooks/use-resource-list";

type Props = {
  offset: number;
  count: number;
  total: number;
  loading: boolean;
  paginated: boolean;
  onChange: (offset: number) => void;
};

export function ResourcePagination({ offset, count, total, loading, paginated, onChange }: Props) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t pt-4">
      <span className="text-xs text-muted-foreground tabular-nums">
        {Math.min(offset + 1, total)}–{Math.min(offset + count, total)} sur {total}
      </span>
      {paginated && total > PAGE_SIZE && (
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={offset === 0 || loading}
            onClick={() => onChange(Math.max(0, offset - PAGE_SIZE))}
          >
            Précédent
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={offset + PAGE_SIZE >= total || loading}
            onClick={() => onChange(offset + PAGE_SIZE)}
          >
            Suivant
          </Button>
        </div>
      )}
    </div>
  );
}
