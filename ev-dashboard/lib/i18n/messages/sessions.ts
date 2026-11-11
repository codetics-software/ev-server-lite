import type { Messages } from "../types";

const messages: Messages = {
  "Anomalies :": { en: "Anomalies:", es: "Anomalías:" },
  "Arrêter la recharge ?": { en: "Stop charging?", es: "¿Detener la carga?" },
  Arrêter: { en: "Stop", es: "Detener" },
  "Aucune mesure": { en: "No measurements", es: "Sin mediciones" },
  "Aucune session en cours": { en: "No sessions in progress", es: "Ninguna sesión en curso" },
  "Aucune session en erreur": { en: "No sessions with errors", es: "Ninguna sesión con errores" },
  "Aucune session": { en: "No sessions", es: "Ninguna sesión" },
  Automatique: { en: "Automatic", es: "Automática" },
  "Badge, borne, session…": {
    en: "Badge, station, session…",
    es: "Tarjeta RFID, estación, sesión…",
  },
  "Batterie (%)": { en: "Battery (%)", es: "Batería (%)" },
  "Puissance (kW)": { en: "Power (kW)", es: "Potencia (kW)" },
  "Clôturer la session localement ?": {
    en: "Close the session locally?",
    es: "¿Cerrar la sesión localmente?",
  },
  "Clôturer localement": { en: "Close locally", es: "Cerrar localmente" },
  Clôturer: { en: "Close", es: "Cerrar" },
  "Connecteur {id}": { en: "Connector {id}", es: "Conector {id}" },
  "Demande d’arrêt envoyée à la borne.": {
    en: "Stop request sent to the charging station.",
    es: "Solicitud de parada enviada a la estación.",
  },
  "Données signées (Eichrecht)": {
    en: "Signed data (Eichrecht)",
    es: "Datos firmados (Eichrecht)",
  },
  Durée: { en: "Duration", es: "Duración" },
  Détails: { en: "Details", es: "Detalles" },
  "En cours": { en: "In progress", es: "En curso" },
  Facture: { en: "Invoice", es: "Factura" },
  Format: { en: "Format", es: "Formato" },
  Historique: { en: "History", es: "Historial" },
  "La borne n’a pas encore envoyé de relevé pour cette session.": {
    en: "The charging station has not sent any readings for this session yet.",
    es: "La estación aún no ha enviado lecturas para esta sesión.",
  },
  "La borne reçoit une demande d’arrêt de la session.": {
    en: "The charging station receives a request to stop the session.",
    es: "La estación recibe una solicitud de parada de la sesión.",
  },
  "La session est terminée dans la supervision à partir du dernier relevé, sans attendre la borne. À utiliser si la borne ne répond plus.":
    {
      en: "The session is ended in the supervision from the last reading, without waiting for the charging station. Use this if the charging station no longer responds.",
      es: "La sesión se cierra en la supervisión a partir de la última lectura, sin esperar a la estación. Úselo si la estación ya no responde.",
    },
  "La session et ses mesures sont supprimées définitivement.": {
    en: "The session and its measurements are permanently deleted.",
    es: "La sesión y sus mediciones se eliminan definitivamente.",
  },
  "Les recharges en cours apparaissent ici en temps réel.": {
    en: "Ongoing charging sessions appear here in real time.",
    es: "Las cargas en curso aparecen aquí en tiempo real.",
  },
  "Les sessions sans consommation, sans prix ou avec un badge invalide apparaîtront ici.": {
    en: "Sessions without consumption, without a price or with an invalid badge will appear here.",
    es: "Las sesiones sin consumo, sin precio o con una tarjeta RFID no válida aparecerán aquí.",
  },
  "Modifiez la période ou les filtres pour retrouver une session.": {
    en: "Change the period or filters to find a session.",
    es: "Modifique el periodo o los filtros para encontrar una sesión.",
  },
  "Paiement public par QR code": { en: "Public QR code payment", es: "Pago público con código QR" },
  "Paiement public": { en: "Public payment", es: "Pago público" },
  Paiement: { en: "Payment", es: "Pago" },
  "Partenaire d’itinérance": { en: "Roaming partner", es: "Socio de itinerancia" },
  "Puissance actuelle": { en: "Current power", es: "Potencia actual" },
  "Puissance, énergie cumulée et niveau de batterie relevés.": {
    en: "Measured power, cumulative energy and battery level.",
    es: "Potencia, energía acumulada y nivel de batería medidos.",
  },
  "Recharges en cours, historique, consommation et anomalies sur votre réseau.": {
    en: "Ongoing charging, history, consumption and anomalies on your network.",
    es: "Cargas en curso, historial, consumo y anomalías en su red.",
  },
  "Relevé de début signé non reçu": {
    en: "Signed start reading not received",
    es: "Lectura inicial firmada no recibida",
  },
  "Relevé de début": { en: "Start reading", es: "Lectura inicial" },
  "Relevé de fin signé non reçu": {
    en: "Signed end reading not received",
    es: "Lectura final firmada no recibida",
  },
  "Relevé de fin": { en: "End reading", es: "Lectura final" },
  "Relevés signés par le compteur, à vérifier avec un logiciel de transparence (S.A.F.E.).": {
    en: "Readings signed by the meter, to be verified with transparency software (S.A.F.E.).",
    es: "Lecturas firmadas por el contador, que se verifican con un software de transparencia (S.A.F.E.).",
  },
  "Session clôturée.": { en: "Session closed.", es: "Sesión cerrada." },
  "Session supprimée.": { en: "Session deleted.", es: "Sesión eliminada." },
  "Sessions en erreur": { en: "Sessions with errors", es: "Sesiones con errores" },
  "Supprimer cette session ?": { en: "Delete this session?", es: "¿Eliminar esta sesión?" },
  "Télécharger la facture": { en: "Download invoice", es: "Descargar la factura" },
  "Télécharger les relevés signés": {
    en: "Download signed readings",
    es: "Descargar las lecturas firmadas",
  },
  "Vos recharges en cours et votre historique de consommation.": {
    en: "Your ongoing charging and your consumption history.",
    es: "Sus cargas en curso y su historial de consumo.",
  },
  "Véhicule identifié": { en: "Identified vehicle", es: "Vehículo identificado" },
  "{station} · connecteur {connector} · {date}": {
    en: "{station} · connector {connector} · {date}",
    es: "{station} · conector {connector} · {date}",
  },
  "· connecteur {id}": { en: "· connector {id}", es: "· conector {id}" },
  Énergie: { en: "Energy", es: "Energía" },
};

export default messages;
