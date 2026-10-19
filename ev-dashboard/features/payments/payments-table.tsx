import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { date } from "@/lib/format";
import { money, paymentLabels, type OperatorPayment } from "./payment-types";

export function PaymentsTable({ payments }: { payments: OperatorPayment[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          {["Date", "Borne", "État", "Crédit", "Facturé", "Remboursé"].map((label, index) => (
            <TableHead key={label} className={index > 2 ? "text-right" : undefined}>
              {label}
            </TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {payments.map((payment) => (
          <TableRow key={payment.id}>
            <TableCell className="text-muted-foreground">{date(payment.createdAt)}</TableCell>
            <TableCell>
              <span className="block font-medium">{payment.stationId}</span>
              <span className="text-xs text-muted-foreground">
                Connecteur {payment.connectorId}
              </span>
            </TableCell>
            <TableCell>
              <Badge variant={payment.state === "FAILED" ? "destructive" : "outline"}>
                {paymentLabels[payment.state]}
              </Badge>
              {payment.message && (
                <p className="mt-2 max-w-64 whitespace-normal text-xs text-muted-foreground">
                  {payment.message}
                </p>
              )}
            </TableCell>
            {[payment.prepaidMinor, payment.billedMinor, payment.refundedMinor].map(
              (amount, index) => (
                <TableCell key={index} className="text-right tabular-nums">
                  {money(amount, payment.currency)}
                </TableCell>
              ),
            )}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
