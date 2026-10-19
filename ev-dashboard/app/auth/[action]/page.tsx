import { AuthScreen } from "@/features/auth/auth-screen";
import { notFound } from "next/navigation";

export function generateStaticParams() {
  return ["login", "register", "forgot-password", "reset-password", "verify-email"].map(
    (action) => ({
      action,
    }),
  );
}

export default async function AuthPage({ params }: { params: Promise<{ action: string }> }) {
  const { action } = await params;

  if (
    !["login", "register", "forgot-password", "reset-password", "verify-email"].includes(action)
  ) {
    notFound();
  }

  return <AuthScreen action={action} />;
}
