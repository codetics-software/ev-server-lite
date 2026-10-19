import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { Row } from "@/lib/api-types";
import { formatCell, valueAt } from "../resource-format";
import type { ResourceColumn } from "../types";

type Props = {
  rows: Row[];
  columns: readonly ResourceColumn[];
  canEdit: boolean;
  onView: (row: Row) => void;
  onEdit: (row: Row) => void;
};

function isNumericColumn(key: string) {
  return ["energy_kwh", "connector_id"].includes(key) || /PowerKw$|Price$|Kwh$|Count$/.test(key);
}

export function ResourceTable({ rows, columns, canEdit, onView, onEdit }: Props) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          {columns.map(([key, label]) => (
            <TableHead key={key} className={isNumericColumn(key) ? "text-right" : undefined}>
              {label}
            </TableHead>
          ))}
          <TableHead className="text-right">
            <span className="sr-only">Actions</span>
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row, index) => (
          <TableRow key={String(row.id ?? row.uid ?? index)}>
            {columns.map(([key]) => (
              <TableCell
                key={key}
                className={isNumericColumn(key) ? "text-right tabular-nums" : "max-w-64 truncate"}
              >
                {typeof valueAt(row, key) === "boolean" || ["state", "role"].includes(key) ? (
                  <Badge variant="secondary">{formatCell(row, key)}</Badge>
                ) : (
                  formatCell(row, key)
                )}
              </TableCell>
            ))}
            <TableCell className="text-right">
              <Button variant="ghost" size="sm" onClick={() => onView(row)}>
                Consulter
              </Button>
              {canEdit && (
                <Button variant="ghost" size="sm" onClick={() => onEdit(row)}>
                  Modifier
                </Button>
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
