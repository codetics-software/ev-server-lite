import React from "react";
import { AbsoluteFill, interpolate } from "remotion";
import { useAbsFrame } from "../components/AbsTime";
import { Caption } from "../components/Caption";
import { FloatCard } from "../components/FloatCard";
import { Link } from "../components/Link";
import { CarNode } from "../nodes/CarNode";
import { ChargerNode, IdChip } from "../nodes/ChargerNode";
import { DashboardNode } from "../nodes/DashboardNode";
import { RoamingNode, ZoneNode } from "../nodes/NetworkNodes";
import { PaymentNode, PhoneNode } from "../nodes/PayNodes";
import { ServerNode } from "../nodes/ServerNode";
import { CMD, OCPP_PACKETS, PAY, W } from "../story";
import { C, NNBSP } from "../theme";
import { BEAT, cue, EASE_IN_OUT, sectionFrom } from "../time";

const FOCUS = { x: 960, y: 400 };

const useCamera = () => {
  const frame = useAbsFrame();
  const keys = [
    { at: sectionFrom("plug"), x: 330, y: -30, z: 1.5 },
    { at: sectionFrom("plug") + 4, x: 360, y: -40, z: 1.18 },
    { at: cue("server.appear") - 6, x: 930, y: -20, z: 1.08 },
    { at: cue("dashboard.appear") - 6, x: 1745, y: 0, z: 1.0 },
    { at: cue("control.move") - 8, x: 1440, y: 0, z: 0.78 },
    { at: cue("phone.appear") - 8, x: 990, y: 320, z: 0.7 },
    { at: cue("zone.appear") - 8, x: 520, y: -300, z: 0.72 },
    { at: cue("roaming.appear") - 8, x: 1450, y: -300, z: 0.75 },
    { at: cue("eco") - 8, x: 1100, y: 20, z: 0.46 },
  ];
  let s = { x: keys[0].x, y: keys[0].y, z: keys[0].z };
  for (const k of keys.slice(1)) {
    const p = interpolate(frame, [k.at, k.at + 40], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: EASE_IN_OUT });
    s = {
      x: s.x + (k.x - s.x) * p,
      y: s.y + (k.y - s.y) * p,
      z: Math.exp(Math.log(s.z) + (Math.log(k.z) - Math.log(s.z)) * p),
    };
  }
  const drift = interpolate(frame, [cue("eco") + 40, sectionFrom("method")], [1, 1.04], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return { x: s.x + Math.sin(frame / 70) * 8, y: s.y + Math.cos(frame / 90) * 6, z: s.z * drift };
};

const edge = (n: keyof typeof W, side: "l" | "r" | "t" | "b", dx = 0, dy = 0): [number, number] => {
  const o = W[n];
  const p: [number, number] =
    side === "l" ? [o.x - o.w / 2, o.y] : side === "r" ? [o.x + o.w / 2, o.y] : side === "t" ? [o.x, o.y - o.h / 2] : [o.x, o.y + o.h / 2];
  return [p[0] + dx, p[1] + dy];
};

const WorldContent: React.FC = () => {
  const eco = cue("eco");
  const cam = useCamera();
  return (
    <div
      style={{
        position: "absolute",
        left: 0,
        top: 0,
        transformOrigin: "0 0",
        translate: `${FOCUS.x - cam.x * cam.z}px ${FOCUS.y - cam.y * cam.z}px`,
        scale: cam.z,
      }}
    >
      <div
        style={{
          position: "absolute",
          left: -4000,
          top: -3000,
          width: 10000,
          height: 7000,
          backgroundImage: "radial-gradient(circle, #c9d3cc 2px, transparent 2.4px)",
          backgroundSize: "48px 48px",
        }}
      />
      <svg style={{ position: "absolute", left: 0, top: 0, overflow: "visible" }} width={1} height={1}>
        <Link a={edge("car", "r", -4, 30)} b={edge("charger", "l", 4, 40)} drawAt={cue("cable")} drawDur={14} cable width={5} flow={{ from: cue("authorized"), color: C.energy, reverse: true }} />
        <Link
          a={edge("charger", "r")}
          b={edge("server", "l")}
          drawAt={cue("ocpp.link")}
          packets={[
            ...OCPP_PACKETS.map((p) => ({ at: p.at, label: p.label })),
            { at: CMD.toCharger, label: "Limiter à 7 kW", reverse: true, color: C.purple },
            { at: CMD.reply, label: "Acceptée", color: C.greenBright },
            { at: PAY.start, label: "Démarrer", reverse: true, color: C.purple },
          ]}
          flow={{ from: eco, color: C.greenBright }}
        />
        <Link
          a={edge("server", "r")}
          b={edge("dashboard", "l")}
          drawAt={cue("dash.link")}
          packets={[
            { at: cue("dash.link") + 10, label: "Temps réel" },
            { at: CMD.toServer, label: "Limiter à 7 kW", reverse: true, color: C.purple },
            { at: CMD.replyToDash, label: "Confirmée", color: C.greenBright },
          ]}
          flow={{ from: eco, color: C.greenBright }}
        />
        <Link a={edge("charger", "b")} b={edge("phone", "t")} drawAt={cue("phone.snap")} dashed width={5} flow={{ from: eco, color: C.cyan }} />
        <Link
          a={edge("phone", "r")}
          b={edge("payment", "l")}
          drawAt={cue("pay.snap")}
          packets={[{ at: PAY.toStripe, label: `20,00${NNBSP}€`, color: "#635bff" }]}
          flow={{ from: eco, color: "#8b85ff" }}
        />
        <Link
          a={edge("server", "b")}
          b={edge("payment", "t")}
          drawAt={cue("pay.snap") + 4}
          packets={[
            { at: PAY.confirmed, label: "Paiement confirmé", reverse: true, color: "#635bff" },
            { at: PAY.refund, label: `Rembourser 7,60${NNBSP}€`, color: C.purple },
          ]}
          flow={{ from: eco, color: "#8b85ff", reverse: true }}
        />
        <Link
          a={edge("zone", "b", 140)}
          b={edge("charger", "t")}
          drawAt={cue("zone.snap")}
          packets={[{ at: cue("zone.balance") - 4, label: `7,3${NNBSP}kW`, color: C.purple }]}
          flow={{ from: eco, color: C.energy, reverse: true }}
        />
        <Link
          a={edge("server", "t")}
          b={edge("roaming", "b", -100)}
          drawAt={cue("roaming.snap")}
          packets={[
            { at: cue("roaming.snap") + 8, label: "Badge partenaire", reverse: true, color: C.purple },
            { at: cue("roaming.snap") + 30, label: "Session partagée" },
          ]}
          flow={{ from: eco, color: C.purple }}
        />
      </svg>

      <FloatCard {...W.car} appear={cue("car.appear")} snap={cue("car.snap")} tag="Vehicule" from={{ dx: -260, dy: 60, rot: -10 }}>
        <CarNode />
      </FloatCard>
      <FloatCard {...W.charger} appear={cue("charger.appear")} snap={cue("charger.snap")} tag="Borne" from={{ dx: 120, dy: 220, rot: 8 }}>
        <ChargerNode />
      </FloatCard>
      <IdChip x={900} y={-250} at={cue("id.badge")} label="Badge RFID" icon="badge" />
      <IdChip x={900} y={-180} at={cue("id.qr")} label="QR code" icon="qr" />
      <IdChip x={900} y={-110} at={cue("id.auto")} label="AutoCharge" icon="car" winner />
      <FloatCard {...W.server} appear={cue("server.appear")} snap={cue("server.snap")} tag="Serveur" from={{ dx: 200, dy: -180, rot: 7 }}>
        <ServerNode />
      </FloatCard>
      <FloatCard {...W.dashboard} appear={cue("dashboard.appear")} snap={cue("dashboard.snap")} tag="TableauDeBord" from={{ dx: 260, dy: 160, rot: -6 }}>
        <DashboardNode />
      </FloatCard>
      <FloatCard {...W.phone} appear={cue("phone.appear")} snap={cue("phone.snap")} tag="PortailQR" bare from={{ dx: -200, dy: 240, rot: -12 }}>
        <PhoneNode />
      </FloatCard>
      <FloatCard {...W.payment} appear={cue("pay.appear")} snap={cue("pay.snap")} tag="Paiement" from={{ dx: 220, dy: 200, rot: 9 }}>
        <PaymentNode />
      </FloatCard>
      <FloatCard {...W.zone} appear={cue("zone.appear")} snap={cue("zone.snap")} tag="RechargeIntelligente" from={{ dx: -240, dy: -160, rot: -8 }}>
        <ZoneNode />
      </FloatCard>
      <FloatCard {...W.roaming} appear={cue("roaming.appear")} snap={cue("roaming.snap")} tag="Itinerance" from={{ dx: 240, dy: -180, rot: 8 }}>
        <RoamingNode />
      </FloatCard>
    </div>
  );
};

const Hud: React.FC = () => {
  const frame = useAbsFrame();
  const end = sectionFrom("method");
  const fade = interpolate(frame, [end - 12, end], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <AbsoluteFill style={{ opacity: fade }}>
      <AbsoluteFill style={{ background: `linear-gradient(0deg, ${C.bg} 0%, ${C.bg}f2 20%, ${C.bg}00 34%)` }} />
      <Caption
        from={sectionFrom("plug") + 6}
        to={sectionFrom("talk") - 2}
        kicker="1 · Le branchement"
        title="La voiture se branche à la borne."
        sub={`Le conducteur s’identifie${NNBSP}: badge, QR code… ou simplement sa voiture.`}
      />
      <Caption
        from={sectionFrom("talk")}
        to={sectionFrom("supervise") - 2}
        kicker="2 · La connexion"
        title="La borne dialogue avec notre serveur."
        sub={`En continu${NNBSP}: chaque démarrage, chaque mesure, chaque arrêt est transmis et vérifié.`}
      />
      <Caption
        from={sectionFrom("supervise")}
        to={sectionFrom("control") - 2}
        kicker="3 · La supervision"
        title="Tout s’affiche en temps réel."
        sub="État des bornes, sessions en cours, énergie délivrée et prix calculé automatiquement."
      />
      <Caption
        from={sectionFrom("control")}
        to={sectionFrom("pay") - 2}
        kicker="4 · Le pilotage"
        title="L’exploitant agit à distance."
        sub="Démarrer, arrêter, limiter la puissance… sans se déplacer."
      />
      <Caption
        from={sectionFrom("pay")}
        to={sectionFrom("smart") - 2}
        kicker="5 · Le paiement"
        title={`Pas de compte${NNBSP}? Un QR code suffit.`}
        sub="Le conducteur paie un crédit, recharge, et le solde non consommé lui est remboursé."
      />
      <Caption
        from={sectionFrom("smart")}
        to={sectionFrom("roaming") - 2}
        kicker="6 · La recharge intelligente"
        title="La puissance est partagée."
        sub={`Plusieurs voitures, une seule limite${NNBSP}: le site ne disjoncte pas.`}
      />
      <Caption
        from={sectionFrom("roaming")}
        to={sectionFrom("ecosystem") - 2}
        kicker="7 · L’itinérance"
        title="Vos bornes, ouvertes aux autres réseaux."
        sub="Les clients des opérateurs partenaires rechargent chez vous."
      />
      <Caption
        from={sectionFrom("ecosystem") + 10}
        to={sectionFrom("ecosystem") + 12 * BEAT}
        kicker="Codetics Supervision Lite"
        title="Une infrastructure complète…"
        sub={`Bornes, conducteurs, exploitants, paiements et partenaires${NNBSP}: tout est relié.`}
      />
      <Caption
        from={sectionFrom("ecosystem") + 12 * BEAT + 2}
        to={end}
        kicker="Codetics Supervision Lite"
        title="… et pourtant légère."
        sub="Un serveur, un tableau de bord, une base de données. Rien de superflu."
      />
    </AbsoluteFill>
  );
};

export const World: React.FC = () => {
  const frame = useAbsFrame();
  const end = sectionFrom("method");
  const fade = interpolate(frame, [end - 12, end], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <AbsoluteFill style={{ background: C.bg, overflow: "hidden" }}>
      <AbsoluteFill style={{ opacity: fade }}>
        <WorldContent />
      </AbsoluteFill>
      <Hud />
    </AbsoluteFill>
  );
};
