import type { Messages } from "../types";

const messages: Messages = {
  Modèle: { en: "Model", es: "Modelo" },
  Batterie: { en: "Battery", es: "Batería" },
  Autonomie: { en: "Range", es: "Autonomía" },
  Catalogue: { en: "Catalog", es: "Catálogo" },
  "Catalogue vide": { en: "Empty catalog", es: "Catálogo vacío" },
  "Synchronisez le catalogue depuis la base de véhicules ou ajoutez des modèles.": {
    en: "Sync the catalog from the vehicle database or add models.",
    es: "Sincronice el catálogo desde la base de vehículos o añada modelos.",
  },
  "Catalogue synchronisé : {created} ajoutés, {updated} mis à jour, {skipped} ignorés.": {
    en: "Catalog synced: {created} added, {updated} updated, {skipped} skipped.",
    es: "Catálogo sincronizado: {created} añadidos, {updated} actualizados, {skipped} omitidos.",
  },
  "Catalogue synchronisé : {created} ajoutés, {updated} mis à jour, {skipped} ignorés, {errors} erreurs.":
    {
      en: "Catalog synced: {created} added, {updated} updated, {skipped} skipped, {errors} errors.",
      es: "Catálogo sincronizado: {created} añadidos, {updated} actualizados, {skipped} omitidos, {errors} errores.",
    },
  "La synchronisation a échoué": { en: "Sync failed", es: "Ha fallado la sincronización" },
  "Synchronisation…": { en: "Syncing…", es: "Sincronizando…" },
  Synchroniser: { en: "Sync", es: "Sincronizar" },
  "Ajouter un modèle de véhicule": {
    en: "Add a vehicle model",
    es: "Añadir un modelo de vehículo",
  },
  "Mes véhicules": { en: "My vehicles", es: "Mis vehículos" },
  "Vos véhicules électriques. Leur enregistrement est facultatif.": {
    en: "Your electric vehicles. Registering them is optional.",
    es: "Sus vehículos eléctricos. Registrarlos es opcional.",
  },
  Véhicules: { en: "Vehicles", es: "Vehículos" },
  "Véhicules des conducteurs et catalogue des modèles électriques.": {
    en: "Drivers’ vehicles and catalog of electric models.",
    es: "Vehículos de los conductores y catálogo de modelos eléctricos.",
  },
  "Recharge automatique disponible": {
    en: "Autocharge available",
    es: "Carga automática disponible",
  },
  "{count} véhicules se sont identifiés lors de vos dernières sessions.": {
    en: "{count} vehicles identified themselves during your recent sessions.",
    es: "{count} vehículos se identificaron durante sus últimas sesiones.",
  },
  "Votre véhicule s’est identifié auprès de la borne {station} le {date}.": {
    en: "Your vehicle identified itself to charging station {station} on {date}.",
    es: "Su vehículo se identificó ante la estación de carga {station} el {date}.",
  },
  "Associez-le à l’un de vos véhicules pour démarrer vos prochaines recharges sans badge.": {
    en: "Link it to one of your vehicles to start your next charging sessions without a badge.",
    es: "Asócielo a uno de sus vehículos para iniciar sus próximas cargas sin tarjeta RFID.",
  },
  Activer: { en: "Enable", es: "Activar" },
  "Activer la recharge automatique": { en: "Enable autocharge", es: "Activar la carga automática" },
  "Associez l’identifiant transmis par la borne à l’un de vos véhicules.": {
    en: "Link the identifier sent by the charging station to one of your vehicles.",
    es: "Asocie el identificador transmitido por la estación de carga a uno de sus vehículos.",
  },
  "Ajoutez d’abord votre véhicule, puis associez-lui cet identifiant depuis sa fiche.": {
    en: "Add your vehicle first, then link this identifier to it from its page.",
    es: "Añada primero su vehículo y después asóciele este identificador desde su ficha.",
  },
  "Recharge automatique activée pour ce véhicule.": {
    en: "Autocharge enabled for this vehicle.",
    es: "Carga automática activada para este vehículo.",
  },
  "Impossible d’activer la recharge automatique.": {
    en: "Unable to enable autocharge.",
    es: "No se pudo activar la carga automática.",
  },
  Session: { en: "Session", es: "Sesión" },
  "Choisissez un véhicule.": { en: "Choose a vehicle.", es: "Elija un vehículo." },
  Véhicule: { en: "Vehicle", es: "Vehículo" },
  "Les bornes dont la recharge automatique est activée démarreront la recharge dès le branchement de ce véhicule, sans badge, aux frais de son conducteur. L’identifiant du véhicule n’est pas un secret : révoquez-le si vous vendez ou prêtez le véhicule.":
    {
      en: "Charging stations with autocharge enabled will start charging as soon as this vehicle is plugged in, without a badge, at its driver’s expense. The vehicle identifier is not a secret: revoke it if you sell or lend the vehicle.",
      es: "Las estaciones de carga con la carga automática activada iniciarán la carga en cuanto se conecte este vehículo, sin tarjeta RFID, a cargo de su conductor. El identificador del vehículo no es secreto: revóquelo si vende o presta el vehículo.",
    },
  "{station} · connecteur {connector}": {
    en: "{station} · connector {connector}",
    es: "{station} · conector {connector}",
  },
  "Session n° {id}": { en: "Session no. {id}", es: "Sesión n.º {id}" },
  "Identifiant du véhicule": { en: "Vehicle ID", es: "Identificador del vehículo" },
  Bloqué: { en: "Blocked", es: "Bloqueado" },
  "Dernière utilisation": { en: "Last used", es: "Último uso" },
  Jamais: { en: "Never", es: "Nunca" },
  Actions: { en: "Actions", es: "Acciones" },
  "Révoquer cet identifiant ?": {
    en: "Revoke this identifier?",
    es: "¿Revocar este identificador?",
  },
  "Les bornes ne démarreront plus automatiquement la recharge de ce véhicule. Un badge ou l’application restent nécessaires.":
    {
      en: "Charging stations will no longer start charging this vehicle automatically. A badge or the app will be required.",
      es: "Las estaciones de carga ya no iniciarán automáticamente la carga de este vehículo. Se necesitará una tarjeta RFID o la aplicación.",
    },
  Révoquer: { en: "Revoke", es: "Revocar" },
  "Révoquer {uid}": { en: "Revoke {uid}", es: "Revocar {uid}" },
  "Identifiant du véhicule révoqué.": {
    en: "Vehicle identifier revoked.",
    es: "Identificador del vehículo revocado.",
  },
  "Recharge automatique": { en: "Automatic charging", es: "Carga automática" },
  "Sur les bornes compatibles, la recharge démarre dès le branchement du véhicule, sans badge. L’identifiant transmis par le véhicule n’est pas un secret : ne l’associez qu’à vos propres véhicules.":
    {
      en: "On compatible charging stations, charging starts as soon as the vehicle is plugged in, without a badge. The identifier sent by the vehicle is not a secret: only link it to your own vehicles.",
      es: "En las estaciones de carga compatibles, la carga se inicia en cuanto se conecta el vehículo, sin tarjeta RFID. El identificador transmitido por el vehículo no es secreto: asócielo solo a sus propios vehículos.",
    },
  "Aucun identifiant de véhicule n’est associé.": {
    en: "No vehicle identifier is linked.",
    es: "No hay ningún identificador de vehículo asociado.",
  },
  "Identifiants du véhicule": { en: "Vehicle identifiers", es: "Identificadores del vehículo" },
  "Conducteur requis": { en: "Driver required", es: "Conductor obligatorio" },
  "Attribuez ce véhicule à un conducteur pour activer la recharge automatique : les sessions démarrées ainsi sont rattachées à son compte.":
    {
      en: "Assign this vehicle to a driver to enable autocharge: sessions started this way are linked to their account.",
      es: "Asigne este vehículo a un conductor para activar la carga automática: las sesiones iniciadas así se vinculan a su cuenta.",
    },
  "Pour associer ce véhicule, branchez-le sur une borne où la recharge automatique est activée et démarrez la session avec votre badge ou l’application. La session apparaît ici pendant 24 heures.":
    {
      en: "To link this vehicle, plug it into a charging station with autocharge enabled and start the session with your badge or the app. The session appears here for 24 hours.",
      es: "Para asociar este vehículo, conéctelo a una estación de carga con la carga automática activada e inicie la sesión con su tarjeta RFID o la aplicación. La sesión aparece aquí durante 24 horas.",
    },
  "Sessions récentes ayant identifié un véhicule": {
    en: "Recent sessions that identified a vehicle",
    es: "Sesiones recientes que identificaron un vehículo",
  },
  "Associer cet identifiant au véhicule ?": {
    en: "Link this identifier to the vehicle?",
    es: "¿Asociar este identificador al vehículo?",
  },
  Associer: { en: "Link", es: "Asociar" },
  "Association impossible": { en: "Unable to link", es: "No se pudo asociar" },
  "Depuis une session de recharge": {
    en: "From a charging session",
    es: "Desde una sesión de carga",
  },
  "Saisie manuelle de l’identifiant": {
    en: "Enter the identifier manually",
    es: "Introducción manual del identificador",
  },
  "Impossible d’associer l’identifiant du véhicule.": {
    en: "Unable to link the vehicle identifier.",
    es: "No se pudo asociar el identificador del vehículo.",
  },
  "Associer un identifiant": { en: "Link an identifier", es: "Asociar un identificador" },
  "Indiquez le numéro de la session.": {
    en: "Enter the session number.",
    es: "Indique el número de la sesión.",
  },
  "Numéro de session invalide.": {
    en: "Invalid session number.",
    es: "Número de sesión no válido.",
  },
  "Indiquez l’identifiant du véhicule.": {
    en: "Enter the vehicle identifier.",
    es: "Indique el identificador del vehículo.",
  },
  "VID: suivi de 12 caractères hexadécimaux, par exemple VID:92D50A693F80.": {
    en: "VID: followed by 12 hexadecimal characters, for example VID:92D50A693F80.",
    es: "VID: seguido de 12 caracteres hexadecimales, por ejemplo VID:92D50A693F80.",
  },
  "Numéro de session": { en: "Session number", es: "Número de sesión" },
  "Session du conducteur de moins de 24 heures, pendant laquelle la borne a transmis l’identifiant du véhicule.":
    {
      en: "A driver session less than 24 hours old, during which the charging station sent the vehicle identifier.",
      es: "Sesión del conductor de menos de 24 horas, durante la cual la estación de carga transmitió el identificador del vehículo.",
    },
  "Préférez une session : l’identifiant est alors celui réellement transmis par la borne. Une saisie erronée peut autoriser un autre véhicule.":
    {
      en: "Prefer a session: the identifier is then the one actually sent by the charging station. A typo could authorize another vehicle.",
      es: "Es preferible usar una sesión: el identificador es entonces el que realmente transmitió la estación de carga. Un error de escritura puede autorizar otro vehículo.",
    },
  "Associer l’identifiant": { en: "Link identifier", es: "Asociar el identificador" },
  "Modèle non renseigné": { en: "Model not specified", es: "Modelo no indicado" },
  Conducteur: { en: "Driver", es: "Conductor" },
  Immatriculation: { en: "License plate", es: "Matrícula" },
  "Ajouter un véhicule": { en: "Add a vehicle", es: "Añadir un vehículo" },
  "Aucun véhicule": { en: "No vehicles", es: "Ningún vehículo" },
  "Facultatif : enregistrer votre véhicule améliore l’estimation de la recharge.": {
    en: "Optional: registering your vehicle improves the charging estimate.",
    es: "Opcional: registrar su vehículo mejora la estimación de la carga.",
  },
  "Modifier {name}": { en: "Edit {name}", es: "Modificar {name}" },
  "Supprimer ce véhicule ?": { en: "Delete this vehicle?", es: "¿Eliminar este vehículo?" },
  "Le véhicule est retiré de votre compte.": {
    en: "The vehicle is removed from your account.",
    es: "El vehículo se quita de su cuenta.",
  },
  Supprimer: { en: "Delete", es: "Eliminar" },
  "Véhicule supprimé.": { en: "Vehicle deleted.", es: "Vehículo eliminado." },
  "Supprimer le véhicule": { en: "Delete vehicle", es: "Eliminar el vehículo" },
  "Les véhicules sont facultatifs : leur enregistrement n’est pas nécessaire pour recharger.": {
    en: "Vehicles are optional: registering them is not required to charge.",
    es: "Los vehículos son opcionales: no es necesario registrarlos para cargar.",
  },
  "Le véhicule est supprimé définitivement.": {
    en: "The vehicle is permanently deleted.",
    es: "El vehículo se elimina definitivamente.",
  },
  "Valeur invalide.": { en: "Invalid value.", es: "Valor no válido." },
  "Véhicule enregistré.": { en: "Vehicle saved.", es: "Vehículo guardado." },
  "Véhicule ajouté.": { en: "Vehicle added.", es: "Vehículo añadido." },
  "Impossible d’enregistrer le véhicule.": {
    en: "Unable to save the vehicle.",
    es: "No se pudo guardar el vehículo.",
  },
  "Nom du véhicule (facultatif)": {
    en: "Vehicle name (optional)",
    es: "Nombre del vehículo (opcional)",
  },
  "Voiture de service": { en: "Company car", es: "Coche de empresa" },
  "Modèle du catalogue": { en: "Catalog model", es: "Modelo del catálogo" },
  "Remplit automatiquement les caractéristiques.": {
    en: "Fills in the specifications automatically.",
    es: "Rellena automáticamente las características.",
  },
  Fabricant: { en: "Manufacturer", es: "Fabricante" },
  "Le VIN contient 17 caractères (sans I, O ni Q).": {
    en: "The VIN has 17 characters (no I, O or Q).",
    es: "El VIN tiene 17 caracteres (sin I, O ni Q).",
  },
  "Numéro VIN": { en: "VIN", es: "Número VIN" },
  "Batterie (kWh)": { en: "Battery (kWh)", es: "Batería (kWh)" },
  "Puissance AC (kW)": { en: "AC power (kW)", es: "Potencia AC (kW)" },
  "Puissance DC (kW)": { en: "DC power (kW)", es: "Potencia DC (kW)" },
  "Véhicule par défaut": { en: "Default vehicle", es: "Vehículo predeterminado" },
  "Modèle de véhicule enregistré.": {
    en: "Vehicle model saved.",
    es: "Modelo de vehículo guardado.",
  },
  "Impossible d’enregistrer le modèle de véhicule.": {
    en: "Unable to save the vehicle model.",
    es: "No se puede guardar el modelo de vehículo.",
  },
  "Fabricant requis.": { en: "Manufacturer required.", es: "Fabricante obligatorio." },
  "Modèle requis.": { en: "Model required.", es: "Modelo obligatorio." },
  Version: { en: "Version", es: "Versión" },
  "Autonomie invalide.": { en: "Invalid range.", es: "Autonomía no válida." },
  "Autonomie (km)": { en: "Range (km)", es: "Autonomía (km)" },
  "Adresse HTTPS requise.": {
    en: "HTTPS address required.",
    es: "Se requiere una dirección HTTPS.",
  },
  "Image (adresse HTTPS)": { en: "Image (HTTPS address)", es: "Imagen (dirección HTTPS)" },
};

export default messages;
