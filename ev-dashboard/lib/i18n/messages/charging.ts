import type { Messages } from "../types";

const messages: Messages = {
  "Actualiser la disponibilité": { en: "Refresh availability", es: "Actualizar la disponibilidad" },
  "Ajouter un point de charge": { en: "Add a connector", es: "Añadir un punto de carga" },
  "Ajoutez un connecteur et son tarif, puis connectez Stripe dans la page Paiements.": {
    en: "Add a connector and its tariff, then connect Stripe on the Payments page.",
    es: "Añada un conector y su tarifa y, después, conecte Stripe en la página Pagos.",
  },
  "Arrêter la recharge": { en: "Stop charging", es: "Detener la carga" },
  "Associez un connecteur à un tarif pour créer son portail public.": {
    en: "Link a connector to a tariff to create its public portal.",
    es: "Asocie un conector a una tarifa para crear su portal público.",
  },
  "Aucun point de charge public": {
    en: "No public connectors",
    es: "Ningún punto de carga público",
  },
  "Borne {station} · Connecteur {connector}": {
    en: "Charging station {station} · Connector {connector}",
    es: "Estación {station} · Conector {connector}",
  },
  "Branchez votre véhicule avant de payer. La borne recevra la demande de démarrage après confirmation du paiement. La facturation est plafonnée au crédit choisi ; l’arrêt dépend de la connexion et de la réponse de la borne.":
    {
      en: "Plug in your vehicle before paying. The charging station will receive the start request once the payment is confirmed. Billing is capped at the chosen credit; stopping depends on the charging station’s connection and response.",
      es: "Conecte su vehículo antes de pagar. La estación recibirá la solicitud de inicio tras confirmarse el pago. La facturación tiene como límite el crédito elegido; la parada depende de la conexión y de la respuesta de la estación.",
    },
  "Ce connecteur ne peut pas recevoir de nouvelle session pour le moment.": {
    en: "This connector cannot accept a new session at the moment.",
    es: "Este conector no puede recibir una nueva sesión por el momento.",
  },
  "Choisir un tarif": { en: "Choose a tariff", es: "Elegir una tarifa" },
  "Choisissez la borne.": { en: "Choose the charging station.", es: "Elija la estación." },
  "Crédit de recharge (€)": { en: "Charging credit (€)", es: "Crédito de carga (€)" },
  "Créez d’abord un tarif en euros dans la page Tarifs.": {
    en: "First create a tariff in euros on the Tariffs page.",
    es: "Cree primero una tarifa en euros en la página Tarifas.",
  },
  "De 5 € à 100 €. Le crédit est débité avant le démarrage. Le montant non utilisé est remboursé après la fin de la recharge.":
    {
      en: "From €5 to €100. The credit is charged before starting. Any unused amount is refunded after charging ends.",
      es: "De 5 € a 100 €. El crédito se cobra antes del inicio. El importe no utilizado se reembolsa al finalizar la carga.",
    },
  "Demande en cours…": { en: "Request in progress…", es: "Solicitud en curso…" },
  "Durée de recharge": { en: "Charging duration", es: "Duración de la carga" },
  "Frais de session": { en: "Session fee", es: "Tarifa por sesión" },
  "Gardez cette page ouverte pour suivre la session et demander l’arrêt.": {
    en: "Keep this page open to follow the session and request a stop.",
    es: "Mantenga esta página abierta para seguir la sesión y solicitar la parada.",
  },
  "Impossible de demander l’arrêt.": {
    en: "Unable to request a stop.",
    es: "No se puede solicitar la parada.",
  },
  "Impossible d’actualiser la session.": {
    en: "Unable to refresh the session.",
    es: "No se puede actualizar la sesión.",
  },
  "Impossible d’enregistrer le point de charge.": {
    en: "Unable to save the connector.",
    es: "No se puede guardar el punto de carga.",
  },
  "Information de l’exploitant": { en: "Operator information", es: "Información del operador" },
  "La demande d’arrêt a été transmise. En attente de la confirmation et du relevé final de la borne. Si nécessaire, arrêtez la recharge sur la borne ou le véhicule.":
    {
      en: "The stop request has been sent. Waiting for confirmation and the final reading from the charging station. If needed, stop charging at the charging station or in the vehicle.",
      es: "Se ha enviado la solicitud de parada. A la espera de la confirmación y de la lectura final de la estación. Si es necesario, detenga la carga en la estación o en el vehículo.",
    },
  "Le connecteur doit avoir été déclaré par la borne.": {
    en: "The connector must have been reported by the charging station.",
    es: "El conector debe haber sido declarado por la estación.",
  },
  "Le crédit doit être compris entre {minimum} € et {maximum} €.": {
    en: "The credit must be between €{minimum} and €{maximum}.",
    es: "El crédito debe estar entre {minimum} € y {maximum} €.",
  },
  "Le nom contient au plus 200 caractères.": {
    en: "The name has at most 200 characters.",
    es: "El nombre tiene como máximo 200 caracteres.",
  },
  "Le paiement n’a pas pu être ouvert.": {
    en: "The payment could not be opened.",
    es: "No se ha podido abrir el pago.",
  },
  "Le remboursement a été transmis à Stripe. Son apparition sur votre compte dépend de votre banque.":
    {
      en: "The refund has been sent to Stripe. When it appears on your account depends on your bank.",
      es: "El reembolso se ha transmitido a Stripe. Su aparición en su cuenta depende de su banco.",
    },
  "Logo de l’exploitant": { en: "Operator logo", es: "Logotipo del operador" },
  "L’accès à cette session n’est plus disponible dans ce navigateur. Contactez l’exploitant du point de charge.":
    {
      en: "Access to this session is no longer available in this browser. Contact the connector’s operator.",
      es: "El acceso a esta sesión ya no está disponible en este navegador. Póngase en contacto con el operador del punto de carga.",
    },
  "Modifier le point de charge": { en: "Edit connector", es: "Modificar el punto de carga" },
  "Montant de la recharge": { en: "Charging amount", es: "Importe de la carga" },
  "Montant remboursé": { en: "Amount refunded", es: "Importe reembolsado" },
  "Nom affiché aux conducteurs": {
    en: "Name shown to drivers",
    es: "Nombre mostrado a los conductores",
  },
  "Numéro du connecteur": { en: "Connector number", es: "Número del conector" },
  "Ouverture du paiement…": { en: "Opening payment…", es: "Abriendo el pago…" },
  "Ouvrir le portail": { en: "Open portal", es: "Abrir el portal" },
  "Paiement confirmé. En attente du démarrage annoncé par la borne. Si elle ne démarre pas sous cinq minutes, le crédit sera remboursé.":
    {
      en: "Payment confirmed. Waiting for the charging station to report the start. If it does not start within five minutes, the credit will be refunded.",
      es: "Pago confirmado. A la espera del inicio anunciado por la estación. Si no se inicia en cinco minutos, se reembolsará el crédito.",
    },
  "Payer et démarrer": { en: "Pay and start", es: "Pagar e iniciar" },
  "Point de charge indisponible": {
    en: "Connector unavailable",
    es: "Punto de carga no disponible",
  },
  "Point de charge": { en: "Connector", es: "Punto de carga" },
  "Publier le portail de paiement": {
    en: "Publish payment portal",
    es: "Publicar el portal de pago",
  },
  Publié: { en: "Published", es: "Publicado" },
  "Recharge momentanément indisponible": {
    en: "Charging temporarily unavailable",
    es: "Carga no disponible temporalmente",
  },
  "Recharge sans compte": { en: "Charging without an account", es: "Carga sin cuenta" },
  "Renseignez le nom du point de charge.": {
    en: "Enter the connector name.",
    es: "Introduzca el nombre del punto de carga.",
  },
  "Retour au point de charge": { en: "Back to the connector", es: "Volver al punto de carga" },
  "Retrouver ma session": { en: "Find my session", es: "Recuperar mi sesión" },
  "Référence : {id}": { en: "Reference: {id}", es: "Referencia: {id}" },
  "Supervision par Codetics · Paiement sécurisé par Stripe": {
    en: "Supervision by Codetics · Secure payment by Stripe",
    es: "Supervisión por Codetics · Pago seguro con Stripe",
  },
  "Sélectionnez un connecteur physique.": {
    en: "Select a physical connector.",
    es: "Seleccione un conector físico.",
  },
  "Tarifs appliqués au début de la session. Le montant final est calculé à partir des mesures de la borne.":
    {
      en: "Tariffs applied at the start of the session. The final amount is calculated from the charging station’s measurements.",
      es: "Tarifas aplicadas al inicio de la sesión. El importe final se calcula a partir de las mediciones de la estación.",
    },
  "Télécharger le QR code": { en: "Download QR code", es: "Descargar el código QR" },
  "Un portail de paiement et un QR code pour chaque connecteur.": {
    en: "A payment portal and a QR code for each connector.",
    es: "Un portal de pago y un código QR para cada conector.",
  },
  "Votre recharge": { en: "Your charging", es: "Su carga" },
  "Votre session de recharge": { en: "Your charging session", es: "Su sesión de carga" },
};

export default messages;
