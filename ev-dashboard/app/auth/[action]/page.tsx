import { AuthScreen } from "@/features/auth/auth-screen";
import { notFound } from "next/navigation";

const actions = [
  "login",
  "register",
  "forgot-password",
  "reset-password",
  "define-password",
  "verify-email",
];

export function generateStaticParams() {
  return actions.map((action) => ({ action }));
}

export default async function AuthPage({ params }: { params: Promise<{ action: string }> }) {
  const { action } = await params;

  if (!actions.includes(action)) {
    notFound();
  }

  return <AuthScreen action={action} />;
}
