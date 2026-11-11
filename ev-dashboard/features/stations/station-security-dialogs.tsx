"use client";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Field,
  FieldContent,
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { OptionSelect } from "@/features/shared/form-fields";
import { SecretReveal } from "@/features/shared/secret-reveal";
import { useAction } from "@/features/shared/use-action";
import { api } from "@/lib/api";
import type { CommandReceipt } from "@/lib/api-types";
import { IconCertificate, IconShieldLock } from "@tabler/icons-react";
import { useId, useState } from "react";
import {
  fingerprintMessage,
  isFingerprint,
  normalizeFingerprint,
  securityProfiles,
  type SecurityCapabilities,
  type SecurityChange,
  type SecurityProfile,
} from "./station-security";
import type { ProvisionedStation, Station } from "./station-types";
import { t, translated } from "@/lib/i18n";

function securityPath(station: Station) {
  return `stations/${encodeURIComponent(station.id)}/security`;
}

export function profileIssue(
  station: Station,
  capabilities: SecurityCapabilities | null,
  target: number,
  send: boolean,
) {
  const current = station.security_profile ?? 1;
  if (target === 0 && current !== 0 && !capabilities?.allow_unauthenticated) {
    return t("Le profil 0 est désactivé sur ce serveur.");
  }
  if (
    target === 3 &&
    target > current &&
    capabilities &&
    !capabilities.client_certificate_authority &&
    !station.pinned_certificate_fingerprint
  ) {
    return t("Aucune autorité de certification cliente n’est configurée sur le serveur.");
  }
  if (target > current && target > 0 && send) {
    if (!station.connected) {
      return t("La borne doit être connectée pour changer de profil.");
    }
    if (target >= 2 && (station.connection_security_profile ?? 0) < 2) {
      return t("La borne doit d’abord se connecter en wss:// (TLS).");
    }
    if (
      target === 3 &&
      !station.pinned_certificate_fingerprint &&
      !station.certificate_fingerprint &&
      station.connection_security_profile !== 3
    ) {
      return t("Aucun certificat client connu pour cette borne.");
    }
  }
  return null;
}

export function ProfileChange({
  station,
  capabilities,
  onChanged,
}: {
  station: Station;
  capabilities: SecurityCapabilities | null;
  onChanged: (change: SecurityChange) => void;
}) {
  const current = station.security_profile ?? 1;
  const [open, setOpen] = useState(false);
  const [target, setTarget] = useState(String(current));
  const [send, setSend] = useState(true);
  const [confirming, setConfirming] = useState(false);
  const [typed, setTyped] = useState("");
  const action = useAction();
  const sendId = useId();
  const confirmId = useId();
  const chosen = Number(target);
  const profiles = ([0, 1, 2, 3] as SecurityProfile[]).filter(
    (profile) => profile !== 0 || current === 0 || capabilities?.allow_unauthenticated,
  );
  const issue = profileIssue(station, capabilities, chosen, send);
  const lowering = chosen < current;
  const disabling = chosen === 0 && current !== 0;
  const [confirmBefore, confirmAfter] = t("Saisissez l’identifiant {id} pour confirmer").split(
    "{id}",
  );

  function reset() {
    setTarget(String(current));
    setSend(true);
    setTyped("");
  }

  async function submit() {
    await action.run(
      "profile",
      () =>
        api<SecurityChange>(securityPath(station), {
          method: "PUT",
          body: JSON.stringify({
            securityProfile: chosen,
            pinnedCertificateFingerprint: station.pinned_certificate_fingerprint ?? null,
            sendToStation: send,
          }),
        }),
      {
        success: (result) => result.message,
        failure: t("Le profil de sécurité n’a pas été modifié"),
        onDone: onChanged,
      },
    );
    setConfirming(false);
    reset();
  }

  return (
    <>
      <Button
        variant="outline"
        onClick={() => {
          reset();
          setOpen(true);
        }}
      >
        <IconShieldLock data-icon="inline-start" />
        {t("Modifier le profil")}
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("Modifier le profil de sécurité")}</DialogTitle>
            <DialogDescription>
              {t("{station} · actuellement {profile}", {
                station: station.name,
                profile: securityProfiles[current as SecurityProfile],
              })}
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <RadioGroup value={target} onValueChange={(value) => setTarget(String(value))}>
              {profiles.map((profile) => {
                const reason = profileIssue(station, capabilities, profile, send);
                const id = `${confirmId}-profile-${profile}`;
                return (
                  <Field key={profile} orientation="horizontal" data-disabled={Boolean(reason)}>
                    <RadioGroupItem id={id} value={String(profile)} disabled={Boolean(reason)} />
                    <FieldContent>
                      <FieldLabel htmlFor={id}>
                        {securityProfiles[profile]}
                        {profile === current && ` ${t("(actuel)")}`}
                      </FieldLabel>
                      {reason && <FieldDescription>{reason}</FieldDescription>}
                    </FieldContent>
                  </Field>
                );
              })}
            </RadioGroup>
            <Field orientation="horizontal">
              <Switch
                id={sendId}
                checked={send}
                onCheckedChange={(checked) => setSend(checked === true)}
              />
              <FieldContent>
                <FieldLabel htmlFor={sendId}>{t("Envoyer la modification à la borne")}</FieldLabel>
                <FieldDescription>
                  {t(
                    "Désactivez pour appliquer le profil seulement côté serveur (mise en service, borne hors ligne) : la borne devra être configurée sur place. Un abaissement n’est jamais envoyé à la borne.",
                  )}
                </FieldDescription>
              </FieldContent>
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              {t("Annuler")}
            </Button>
            <Button
              variant={lowering ? "destructive" : "default"}
              disabled={chosen === current || Boolean(issue)}
              onClick={() => {
                setOpen(false);
                setConfirming(true);
              }}
            >
              {t("Continuer")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <AlertDialog
        open={confirming}
        onOpenChange={(next) => {
          if (!next && !action.busy) {
            setConfirming(false);
            reset();
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {disabling
                ? t("Désactiver l’authentification de la borne ?")
                : lowering
                  ? t("Abaisser la sécurité de la borne ?")
                  : t("Passer la borne au profil {profile} ?", { profile: chosen })}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {disabling
                ? t(
                    "N’importe quel appareil connaissant l’identifiant pourra se faire passer pour cette borne. Le serveur accepte immédiatement les connexions sans authentification.",
                  )
                : lowering
                  ? t(
                      "Le serveur accepte immédiatement le profil {profile}, plus faible. Rien n’est envoyé à la borne : une borne ne peut pas abaisser son propre profil, elle doit être reconfigurée sur place.",
                      { profile: chosen },
                    )
                  : send
                    ? t(
                        "La borne va redémarrer sa connexion. L’ancien niveau reste accepté jusqu’à sa reconnexion.",
                      )
                    : t(
                        "Le profil {profile} est appliqué immédiatement côté serveur, sans rien envoyer à la borne : configurez-la sur place pour qu’elle se reconnecte avec ce profil.",
                        { profile: chosen },
                      )}
            </AlertDialogDescription>
          </AlertDialogHeader>
          {disabling && (
            <Field>
              <FieldLabel htmlFor={confirmId}>
                {confirmBefore}
                <code className="font-mono">{station.id}</code>
                {confirmAfter}
              </FieldLabel>
              <Input
                id={confirmId}
                value={typed}
                autoComplete="off"
                onChange={(event) => setTyped(event.target.value)}
              />
            </Field>
          )}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={action.busy}>{t("Annuler")}</AlertDialogCancel>
            <AlertDialogAction
              variant={lowering ? "destructive" : "default"}
              disabled={action.busy || (disabling && typed.trim() !== station.id)}
              onClick={(event) => {
                event.preventDefault();
                void submit();
              }}
            >
              {action.busy
                ? t("Veuillez patienter…")
                : disabling
                  ? t("Désactiver l’authentification")
                  : lowering
                    ? t("Abaisser")
                    : t("Passer au profil {profile}", { profile: chosen })}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

export function PinDialog({
  station,
  initial,
  onClose,
  onChanged,
}: {
  station: Station;
  initial: string | null;
  onClose: () => void;
  onChanged: (change: SecurityChange) => void;
}) {
  return (
    <Dialog open={initial !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        {initial !== null && (
          <PinForm
            key={initial}
            station={station}
            initial={initial}
            onDone={(change) => {
              onChanged(change);
              onClose();
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function PinForm({
  station,
  initial,
  onDone,
}: {
  station: Station;
  initial: string;
  onDone: (change: SecurityChange) => void;
}) {
  const [value, setValue] = useState(initial);
  const [touched, setTouched] = useState(Boolean(initial));
  const action = useAction();
  const id = useId();
  const normalized = normalizeFingerprint(value);
  const invalid = touched && !isFingerprint(value);
  return (
    <form
      noValidate
      className="flex flex-col gap-5"
      onSubmit={(event) => {
        event.preventDefault();
        setTouched(true);
        if (!isFingerprint(value)) {
          return;
        }
        void action.run(
          "pin",
          () =>
            api<SecurityChange>(securityPath(station), {
              method: "PUT",
              body: JSON.stringify({
                securityProfile: station.security_profile ?? 1,
                pinnedCertificateFingerprint: normalized,
                sendToStation: false,
              }),
            }),
          {
            success: t("Empreinte épinglée."),
            failure: t("L’empreinte n’a pas été épinglée"),
            onDone,
          },
        );
      }}
    >
      <DialogHeader>
        <DialogTitle>{t("Épingler une empreinte")}</DialogTitle>
        <DialogDescription>
          {t(
            "Le certificat client dont l’empreinte SHA-256 correspond est accepté pour {station}, même si son nom commun diffère de l’identifiant de la borne.",
            { station: station.name },
          )}
        </DialogDescription>
      </DialogHeader>
      <Field data-invalid={invalid}>
        <FieldLabel htmlFor={id}>{t("Empreinte SHA-256")}</FieldLabel>
        <Input
          id={id}
          value={value}
          className="font-mono text-xs"
          autoComplete="off"
          placeholder={t("AB:CD:…")}
          aria-invalid={invalid}
          onBlur={() => setTouched(true)}
          onChange={(event) => setValue(event.target.value)}
        />
        <FieldDescription>
          {t("64 caractères hexadécimaux, avec ou sans « : », en majuscules ou minuscules.")}
          {isFingerprint(value) && (
            <span className="mt-1 block break-all font-mono">{normalized}</span>
          )}
        </FieldDescription>
        {invalid && <FieldError>{fingerprintMessage()}</FieldError>}
      </Field>
      <DialogFooter>
        <Button type="submit" disabled={action.busy}>
          {action.busy ? t("Veuillez patienter…") : t("Épingler")}
        </Button>
      </DialogFooter>
    </form>
  );
}

export function unpinStation(station: Station) {
  return api<SecurityChange>(securityPath(station), {
    method: "PUT",
    body: JSON.stringify({
      securityProfile: station.security_profile ?? 1,
      pinnedCertificateFingerprint: null,
      sendToStation: false,
    }),
  });
}

export function certificateCommand(
  station: Station,
  action: "InstallCertificate" | "DeleteCertificate" | "ExtendedTriggerMessage",
  payload: Record<string, unknown>,
) {
  return api<CommandReceipt>(`stations/${encodeURIComponent(station.id)}/certificate/commands`, {
    method: "POST",
    body: JSON.stringify({ action, payload }),
  });
}

const certificateTypes = translated([
  { value: "CentralSystemRootCertificate", label: "Racine du serveur" },
  { value: "ManufacturerRootCertificate", label: "Racine du fabricant" },
]);

const PEM = /^-----BEGIN CERTIFICATE-----\s+[A-Za-z0-9+/=\s]+-----END CERTIFICATE-----\s*$/;

export function InstallCertificate({
  station,
  onQueued,
}: {
  station: Station;
  onQueued: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [type, setType] = useState(certificateTypes[0].value);
  const [pem, setPem] = useState("");
  const [touched, setTouched] = useState(false);
  const action = useAction();
  const id = useId();
  const error = !pem.trim()
    ? t("Collez le certificat au format PEM.")
    : pem.trim().length > 5500
      ? t("Le certificat contient 5 500 caractères au plus.")
      : !PEM.test(pem.trim())
        ? t("Le certificat doit être au format PEM (-----BEGIN CERTIFICATE-----).")
        : null;
  return (
    <>
      <Button
        variant="outline"
        onClick={() => {
          setPem("");
          setTouched(false);
          setOpen(true);
        }}
      >
        <IconCertificate data-icon="inline-start" />
        {t("Installer un certificat")}
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <form
            noValidate
            className="flex flex-col gap-5"
            onSubmit={(event) => {
              event.preventDefault();
              setTouched(true);
              if (error) {
                return;
              }
              void action.run(
                "install",
                () =>
                  certificateCommand(station, "InstallCertificate", {
                    certificateType: type,
                    certificate: pem.trim(),
                  }),
                {
                  success: t("Installation du certificat demandée à la borne."),
                  failure: t("Le certificat n’a pas été envoyé"),
                  onDone: () => {
                    setOpen(false);
                    onQueued();
                  },
                },
              );
            }}
          >
            <DialogHeader>
              <DialogTitle>{t("Installer un certificat racine")}</DialogTitle>
              <DialogDescription>
                {t(
                  "La borne ajoute ce certificat à ses autorités de confiance (InstallCertificate).",
                )}
              </DialogDescription>
            </DialogHeader>
            {!station.connected && (
              <p className="rounded-lg border border-dashed p-3 text-sm text-muted-foreground">
                {t(
                  "La borne est hors ligne : la demande sera envoyée dès sa reconnexion ou expirera.",
                )}
              </p>
            )}
            <FieldGroup>
              <Field>
                <FieldLabel>{t("Type de certificat")}</FieldLabel>
                <OptionSelect
                  value={type}
                  onChange={setType}
                  options={certificateTypes}
                  label={t("Type de certificat")}
                />
              </Field>
              <Field data-invalid={touched && Boolean(error)}>
                <FieldLabel htmlFor={id}>{t("Certificat (PEM)")}</FieldLabel>
                <Textarea
                  id={id}
                  value={pem}
                  className="min-h-40 font-mono text-xs"
                  placeholder={t("-----BEGIN CERTIFICATE-----")}
                  aria-invalid={touched && Boolean(error)}
                  onBlur={() => setTouched(true)}
                  onChange={(event) => setPem(event.target.value)}
                />
                {touched && error && <FieldError>{error}</FieldError>}
              </Field>
            </FieldGroup>
            <DialogFooter>
              <Button type="submit" disabled={action.busy}>
                {action.busy ? t("Veuillez patienter…") : t("Installer")}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}

export type RevealedSecret = { secret: ProvisionedStation; remote: boolean };

export function SecretDialog({
  value,
  online,
  onClose,
}: {
  value: RevealedSecret | null;
  online: boolean;
  onClose: () => void;
}) {
  return (
    <Dialog open={value !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("Nouveau mot de passe OCPP")}</DialogTitle>
          <DialogDescription>
            {value?.remote
              ? online
                ? t(
                    "Le nouveau mot de passe est envoyé à la borne. L’ancien reste accepté jusqu’à sa reconnexion avec le nouveau. Si la borne refuse la modification, saisissez ce mot de passe sur place.",
                  )
                : t(
                    "La borne est hors ligne : saisissez ce mot de passe sur la borne. L’ancien reste accepté jusqu’à la révocation.",
                  )
              : t(
                  "Renouvellement sur place : l’ancien mot de passe ne fonctionne plus et la borne a été déconnectée. Configurez la borne avec celui-ci pour qu’elle se reconnecte.",
                )}
          </DialogDescription>
        </DialogHeader>
        {value && (
          <SecretReveal
            title={t("Mot de passe de la borne")}
            message={value.secret.message}
            secret={value.secret.secret}
          />
        )}
        <DialogFooter>
          <Button onClick={onClose}>{t("Terminer")}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
