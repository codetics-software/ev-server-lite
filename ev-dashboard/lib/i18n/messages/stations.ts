import type { Messages } from "../types";

const messages: Messages = {
  "(actuel)": { en: "(current)", es: "(actual)" },
  "-----BEGIN CERTIFICATE-----": {
    en: "-----BEGIN CERTIFICATE-----",
    es: "-----BEGIN CERTIFICATE-----",
  },
  "64 caractères hexadécimaux, avec ou sans « : », en majuscules ou minuscules.": {
    en: "64 hexadecimal characters, with or without “:”, in upper or lower case.",
    es: "64 caracteres hexadecimales, con o sin «:», en mayúsculas o minúsculas.",
  },
  "AB:CD:…": { en: "AB:CD:…", es: "AB:CD:…" },
  Abaisser: { en: "Lower", es: "Reducir" },
  "Abaisser la sécurité de la borne ?": {
    en: "Lower the charging station’s security?",
    es: "¿Reducir la seguridad de la estación?",
  },
  "Activer la recharge automatique ?": {
    en: "Enable automatic charging?",
    es: "¿Activar la carga automática?",
  },
  "Adaptez le nom d’hôte si le serveur OCPP est publié à une autre adresse que ce tableau de bord.":
    {
      en: "Adjust the host name if the OCPP server is published at a different address than this dashboard.",
      es: "Ajuste el nombre de host si el servidor OCPP se publica en una dirección distinta a la de este panel.",
    },
  "Adresse de dépôt": { en: "Upload URL", es: "Dirección de depósito" },
  "Adresse du fichier": { en: "File URL", es: "Dirección del archivo" },
  "Adresse HTTPS ou FTP accessible par la borne.": {
    en: "HTTPS or FTP URL reachable by the charging station.",
    es: "Dirección HTTPS o FTP accesible para la estación.",
  },
  "Adresse OCPP": { en: "OCPP URL", es: "Dirección OCPP" },
  Ajouter: { en: "Add", es: "Añadir" },
  "Ajouter la borne": { en: "Add charging station", es: "Añadir la estación" },
  "Ajouter une borne": { en: "Add a charging station", es: "Añadir una estación" },
  "Ajouter une période": { en: "Add a period", es: "Añadir un periodo" },
  "Ajoutez une borne pour obtenir ses identifiants de connexion OCPP.": {
    en: "Add a charging station to get its OCPP connection credentials.",
    es: "Añada una estación para obtener sus credenciales de conexión OCPP.",
  },
  "Ajout…": { en: "Adding…", es: "Añadiendo…" },
  "Ancien mot de passe révoqué.": {
    en: "Previous password revoked.",
    es: "Contraseña anterior revocada.",
  },
  "Annule une réservation en cours à partir de son numéro.": {
    en: "Cancels an active reservation using its number.",
    es: "Cancela una reserva en curso a partir de su número.",
  },
  "Annuler la réservation": { en: "Cancel reservation", es: "Cancelar la reserva" },
  "Annuler le renouvellement": { en: "Cancel renewal", es: "Cancelar la renovación" },
  "Annuler le renouvellement ?": { en: "Cancel the renewal?", es: "¿Cancelar la renovación?" },
  "Applique immédiatement un plafond de puissance à la borne ou à un connecteur.": {
    en: "Immediately applies a power cap to the charging station or a connector.",
    es: "Aplica inmediatamente un límite de potencia a la estación o a un conector.",
  },
  Appliquer: { en: "Apply", es: "Aplicar" },
  "Appliquer le modèle correspondant": {
    en: "Apply matching template",
    es: "Aplicar la plantilla correspondiente",
  },
  "Appliqué au prochain démarrage de la borne.": {
    en: "Applied at the charging station’s next boot.",
    es: "Se aplica en el próximo arranque de la estación.",
  },
  "Appliqué dès que la borne se reconnecte avec ce profil ; l’ancien reste accepté d’ici là.": {
    en: "Applied as soon as the charging station reconnects with this profile; the previous one remains accepted until then.",
    es: "Se aplica en cuanto la estación se vuelve a conectar con este perfil; el anterior sigue aceptado hasta entonces.",
  },
  "Aucun certificat client": { en: "No client certificate", es: "Ningún certificado de cliente" },
  "Aucun certificat client connu pour cette borne.": {
    en: "No known client certificate for this charging station.",
    es: "No hay ningún certificado de cliente conocido para esta estación.",
  },
  "Aucun certificat de ce type.": {
    en: "No certificate of this type.",
    es: "Ningún certificado de este tipo.",
  },
  "Aucun connecteur": { en: "No connectors", es: "Ningún conector" },
  "Aucun jeton d’enregistrement": { en: "No registration tokens", es: "Ningún token de registro" },
  "Aucun modèle ne correspond à cette borne.": {
    en: "No template matches this charging station.",
    es: "Ninguna plantilla corresponde a esta estación.",
  },
  "Aucun paramètre correspondant": {
    en: "No matching parameters",
    es: "Ningún parámetro coincidente",
  },
  "Aucun profil de recharge": { en: "No charging profiles", es: "Ningún perfil de carga" },
  "Aucun renouvellement en attente : seul le mot de passe actuel est accepté.": {
    en: "No pending renewal: only the current password is accepted.",
    es: "Ninguna renovación pendiente: solo se acepta la contraseña actual.",
  },
  "Aucun événement": { en: "No events", es: "Ningún evento" },
  "Aucune autorité de certification cliente n’est configurée sur le serveur.": {
    en: "No client certificate authority is configured on the server.",
    es: "No hay ninguna autoridad de certificación de cliente configurada en el servidor.",
  },
  "Aucune autorité de signature n’est configurée sur le serveur : la borne ne peut pas obtenir de certificat signé.":
    {
      en: "No signing authority is configured on the server: the charging station cannot obtain a signed certificate.",
      es: "No hay ninguna autoridad de firma configurada en el servidor: la estación no puede obtener un certificado firmado.",
    },
  "Aucune borne": { en: "No charging stations", es: "Ninguna estación" },
  "Aucune borne correspondante": {
    en: "No matching charging stations",
    es: "Ninguna estación coincidente",
  },
  "Aucune borne en erreur": {
    en: "No charging stations in error",
    es: "Ninguna estación con error",
  },
  "Aucune borne n’est rattachée ici.": {
    en: "No charging stations are attached here.",
    es: "No hay ninguna estación vinculada aquí.",
  },
  "Aucune commande envoyée": { en: "No commands sent", es: "Ningún comando enviado" },
  "Aucune photo envoyée par les conducteurs pour cette borne.": {
    en: "No photos sent by drivers for this charging station.",
    es: "Ninguna foto enviada por los conductores para esta estación.",
  },
  "Aucune période définie.": { en: "No period defined.", es: "Ningún periodo definido." },
  "Aucune session en cours sur cette borne.": {
    en: "No session in progress on this charging station.",
    es: "Ninguna sesión en curso en esta estación.",
  },
  "Aucune session en cours.": { en: "No session in progress.", es: "Ninguna sesión en curso." },
  "Autorités de confiance installées sur la borne (GetInstalledCertificateIds).": {
    en: "Trusted authorities installed on the charging station (GetInstalledCertificateIds).",
    es: "Autoridades de confianza instaladas en la estación (GetInstalledCertificateIds).",
  },
  Autre: { en: "Other", es: "Otro" },
  Avancé: { en: "Advanced", es: "Avanzado" },
  "Borne activée": { en: "Charging station enabled", es: "Estación activada" },
  "Borne ajoutée": { en: "Charging station added", es: "Estación añadida" },
  "Borne publique": { en: "Public charging station", es: "Estación pública" },
  "Borne supprimée.": { en: "Charging station deleted.", es: "Estación eliminada." },
  "Caractéristiques non renseignées": {
    en: "Specifications not provided",
    es: "Características no indicadas",
  },
  "Ce paramètre de sécurité ne peut pas figurer dans un modèle.": {
    en: "This security parameter cannot be included in a template.",
    es: "Este parámetro de seguridad no puede figurar en una plantilla.",
  },
  "Ce paramètre de sécurité se modifie depuis la section Sécurité de la borne.": {
    en: "This security parameter is changed from the charging station’s Security section.",
    es: "Este parámetro de seguridad se modifica desde la sección Seguridad de la estación.",
  },
  "Certificat (PEM)": { en: "Certificate (PEM)", es: "Certificado (PEM)" },
  "Certificat client": { en: "Client certificate", es: "Certificado de cliente" },
  "Certificat de signature (PEM)": {
    en: "Signing certificate (PEM)",
    es: "Certificado de firma (PEM)",
  },
  "Certificat révoqué.": { en: "Certificate revoked.", es: "Certificado revocado." },
  "Certificats installés": { en: "Installed certificates", es: "Certificados instalados" },
  "Certificats non lus": { en: "Certificates not read", es: "Certificados no leídos" },
  "Changement en attente": { en: "Pending change", es: "Cambio pendiente" },
  "Chaque jour": { en: "Every day", es: "Cada día" },
  "Collez le certificat au format PEM.": {
    en: "Paste the certificate in PEM format.",
    es: "Pegue el certificado en formato PEM.",
  },
  "Commande en file d’attente": { en: "Command queued", es: "Comando en cola" },
  "Commande enregistrée : {id}. Consultez sa réponse dans le suivi des commandes.": {
    en: "Command saved: {id}. Check its response in the command log.",
    es: "Comando registrado: {id}. Consulte su respuesta en el seguimiento de comandos.",
  },
  "Commande OCPP 1.6": { en: "OCPP 1.6 command", es: "Comando OCPP 1.6" },
  Commandes: { en: "Commands", es: "Comandos" },
  "Commandes de la borne": { en: "Charging station commands", es: "Comandos de la estación" },
  "Configuration non lue": { en: "Configuration not read", es: "Configuración no leída" },
  "Connecteur {id}": { en: "Connector {id}", es: "Conector {id}" },
  "Connecteur {id} · session {session}": {
    en: "Connector {id} · session {session}",
    es: "Conector {id} · sesión {session}",
  },
  connectée: { en: "connected", es: "conectada" },
  Connectées: { en: "Connected", es: "Conectadas" },
  "Connexion des bornes, état des connecteurs en temps réel et commandes à distance.": {
    en: "Charging station connections, real-time connector status and remote commands.",
    es: "Conexión de las estaciones, estado de los conectores en tiempo real y comandos a distancia.",
  },
  Continuer: { en: "Continue", es: "Continuar" },
  "Coupe et relance la borne. Les sessions en cours sont interrompues immédiatement.": {
    en: "Powers the charging station off and on. Sessions in progress are interrupted immediately.",
    es: "Apaga y vuelve a encender la estación. Las sesiones en curso se interrumpen inmediatamente.",
  },
  "Création…": { en: "Creating…", es: "Creando…" },
  "Créer le jeton": { en: "Create token", es: "Crear el token" },
  "Créez un jeton pour laisser vos bornes s’enregistrer elles-mêmes.": {
    en: "Create a token to let your charging stations register themselves.",
    es: "Cree un token para que sus estaciones se registren por sí mismas.",
  },
  "Câble attaché": { en: "Tethered cable", es: "Cable fijo" },
  "Date de téléchargement": { en: "Download date", es: "Fecha de descarga" },
  "Demande de certificat envoyée à la borne.": {
    en: "Certificate request sent to the charging station.",
    es: "Solicitud de certificado enviada a la estación.",
  },
  "Demande à la borne de démarrer une session pour un badge sur un connecteur.": {
    en: "Asks the charging station to start a session for a badge on a connector.",
    es: "Solicita a la estación que inicie una sesión para una tarjeta RFID en un conector.",
  },
  "Demande à la borne d’arrêter la session en cours sur le connecteur.": {
    en: "Asks the charging station to stop the session in progress on the connector.",
    es: "Solicita a la estación que detenga la sesión en curso en el conector.",
  },
  "Demande à la borne d’envoyer immédiatement un message OCPP (ExtendedTriggerMessage, profil de sécurité).":
    {
      en: "Asks the charging station to send an OCPP message immediately (ExtendedTriggerMessage, security profile).",
      es: "Solicita a la estación que envíe inmediatamente un mensaje OCPP (ExtendedTriggerMessage, perfil de seguridad).",
    },
  "Demande à la borne d’envoyer immédiatement un message OCPP.": {
    en: "Asks the charging station to send an OCPP message immediately.",
    es: "Solicita a la estación que envíe inmediatamente un mensaje OCPP.",
  },
  Demander: { en: "Request", es: "Solicitar" },
  "Demander le diagnostic": { en: "Request diagnostics", es: "Solicitar el diagnóstico" },
  "Demander un certificat client": {
    en: "Request a client certificate",
    es: "Solicitar un certificado de cliente",
  },
  "Demander un certificat client ?": {
    en: "Request a client certificate?",
    es: "¿Solicitar un certificado de cliente?",
  },
  "Demandez la lecture : la liste apparaît dès la réponse de la borne.": {
    en: "Request a read: the list appears as soon as the charging station responds.",
    es: "Solicite la lectura: la lista aparece en cuanto responde la estación.",
  },
  "Demandez la lecture de la configuration : les valeurs apparaissent dès la réponse de la borne.":
    {
      en: "Request a configuration read: values appear as soon as the charging station responds.",
      es: "Solicite la lectura de la configuración: los valores aparecen en cuanto responde la estación.",
    },
  "Depuis (facultatif)": { en: "From (optional)", es: "Desde (opcional)" },
  "Dernier certificat présenté par la borne ou signé par le serveur.": {
    en: "Last certificate presented by the charging station or signed by the server.",
    es: "Último certificado presentado por la estación o firmado por el servidor.",
  },
  "Dernier démarrage": { en: "Last boot", es: "Último arranque" },
  "Dernier signal {time}": { en: "Last seen {time}", es: "Última señal {time}" },
  "Dernière lecture du {date}": { en: "Last read on {date}", es: "Última lectura del {date}" },
  Disponibilité: { en: "Availability", es: "Disponibilidad" },
  "Décrivez l’usage du jeton.": {
    en: "Describe what the token is for.",
    es: "Describa el uso del token.",
  },
  Démarrer: { en: "Start", es: "Iniciar" },
  "Désactiver la recharge automatique ?": {
    en: "Disable automatic charging?",
    es: "¿Desactivar la carga automática?",
  },
  "Désactiver l’authentification": {
    en: "Disable authentication",
    es: "Desactivar la autenticación",
  },
  "Désactiver l’authentification de la borne ?": {
    en: "Disable the charging station’s authentication?",
    es: "¿Desactivar la autenticación de la estación?",
  },
  "Désactivez pour appliquer le profil seulement côté serveur (mise en service, borne hors ligne) : la borne devra être configurée sur place. Un abaissement n’est jamais envoyé à la borne.":
    {
      en: "Turn off to apply the profile on the server side only (commissioning, offline charging station): the charging station will have to be configured on site. A downgrade is never sent to the charging station.",
      es: "Desactive para aplicar el perfil solo en el servidor (puesta en servicio, estación sin conexión): la estación deberá configurarse in situ. Una reducción nunca se envía a la estación.",
    },
  Désactivée: { en: "Disabled", es: "Desactivada" },
  Détail: { en: "Details", es: "Detalle" },
  Déverrouiller: { en: "Unlock", es: "Desbloquear" },
  "Efface les badges mémorisés localement par la borne.": {
    en: "Clears the badges stored locally by the charging station.",
    es: "Borra las tarjetas RFID almacenadas localmente por la estación.",
  },
  "Empreinte retirée.": { en: "Fingerprint removed.", es: "Huella eliminada." },
  "Empreinte SHA-256": { en: "SHA-256 fingerprint", es: "Huella SHA-256" },
  "Empreinte SHA-256 invalide (64 caractères hexadécimaux).": {
    en: "Invalid SHA-256 fingerprint (64 hexadecimal characters).",
    es: "Huella SHA-256 no válida (64 caracteres hexadecimales).",
  },
  "Empreinte épinglée": { en: "Pinned fingerprint", es: "Huella fijada" },
  "Empreinte épinglée.": { en: "Fingerprint pinned.", es: "Huella fijada." },
  "En attente d’envoi": { en: "Waiting to be sent", es: "Pendiente de envío" },
  "Enregistrement…": { en: "Saving…", es: "Guardando…" },
  "Enregistrer les propriétés": { en: "Save properties", es: "Guardar las propiedades" },
  "Enregistrer les échanges OCPP": {
    en: "Save OCPP settings",
    es: "Guardar los intercambios OCPP",
  },
  "Envoi d’une commande OCPP 1.6 brute. Réservé aux administrateurs : préférez les actions dédiées.":
    {
      en: "Sends a raw OCPP 1.6 command. Administrators only: prefer the dedicated actions.",
      es: "Envío de un comando OCPP 1.6 sin procesar. Reservado a los administradores: utilice preferentemente las acciones dedicadas.",
    },
  "Envoi…": { en: "Sending…", es: "Enviando…" },
  "Envoyer la commande": { en: "Send command", es: "Enviar el comando" },
  "Envoyer la modification à la borne": {
    en: "Send the change to the charging station",
    es: "Enviar la modificación a la estación",
  },
  "Envoyer le profil": { en: "Send profile", es: "Enviar el perfil" },
  "Erreur : {error}": { en: "Error: {error}", es: "Error: {error}" },
  "Expire dans {count} jour": { en: "Expires in {count} day", es: "Caduca en {count} día" },
  "Expire dans {count} jours": { en: "Expires in {count} days", es: "Caduca en {count} días" },
  "Expire le {date}": { en: "Expires on {date}", es: "Caduca el {date}" },
  Expiré: { en: "Expired", es: "Caducado" },
  "Fiche de mise en service": { en: "Commissioning sheet", es: "Ficha de puesta en servicio" },
  "Fiche de mise en service de la borne {id} : configurez-la avec ces informations.": {
    en: "Commissioning sheet for charging station {id}: configure it with this information.",
    es: "Ficha de puesta en servicio de la estación {id}: configúrela con esta información.",
  },
  "Filtrer les paramètres": { en: "Filter parameters", es: "Filtrar los parámetros" },
  "Filtrer par état": { en: "Filter by status", es: "Filtrar por estado" },
  "Fin de la réservation": { en: "Reservation end", es: "Fin de la reserva" },
  "Fournisseurs acceptés": { en: "Accepted vendors", es: "Proveedores aceptados" },
  "Fournisseurs DataTransfer acceptés": {
    en: "Accepted DataTransfer vendors",
    es: "Proveedores DataTransfer aceptados",
  },
  "Heure invalide.": { en: "Invalid time.", es: "Hora no válida." },
  "Historique des événements": { en: "Event history", es: "Historial de eventos" },
  "hors ligne": { en: "offline", es: "sin conexión" },
  "Identifiant annoncé par la borne dans l’adresse OCPP. Lettres, chiffres et . _ : * -": {
    en: "ID announced by the charging station in the OCPP URL. Letters, digits and . _ : * -",
    es: "Identificador anunciado por la estación en la dirección OCPP. Letras, cifras y . _ : * -",
  },
  Identification: { en: "Identification", es: "Identificación" },
  "Impossible de créer le jeton.": {
    en: "Unable to create the token.",
    es: "No se puede crear el token.",
  },
  "Impossible d’ajouter la borne.": {
    en: "Unable to add the charging station.",
    es: "No se puede añadir la estación.",
  },
  "Impossible d’enregistrer la borne.": {
    en: "Unable to save the charging station.",
    es: "No se puede guardar la estación.",
  },
  "Impossible d’envoyer la commande.": {
    en: "Unable to send the command.",
    es: "No se puede enviar el comando.",
  },
  "inactif {duration}": { en: "idle {duration}", es: "inactivo {duration}" },
  "Indiquez une puissance.": { en: "Enter a power value.", es: "Indique una potencia." },
  "Informations à transmettre à l’installateur. Le mot de passe n’est affiché qu’à sa création.": {
    en: "Information to give to the installer. The password is only shown when it is created.",
    es: "Información que se debe transmitir al instalador. La contraseña solo se muestra al crearla.",
  },
  "Installation du certificat demandée à la borne.": {
    en: "Certificate installation requested from the charging station.",
    es: "Instalación del certificado solicitada a la estación.",
  },
  Installer: { en: "Install", es: "Instalar" },
  "Installer un certificat racine": {
    en: "Install a root certificate",
    es: "Instalar un certificado raíz",
  },
  "Intensité (A)": { en: "Current (A)", es: "Intensidad (A)" },
  "Intervalle du signal de vie (secondes)": {
    en: "Heartbeat interval (seconds)",
    es: "Intervalo de la señal de vida (segundos)",
  },
  "Intervalle entre 10 et 300 secondes.": {
    en: "Interval between 10 and 300 seconds.",
    es: "Intervalo entre 10 y 300 segundos.",
  },
  "Jeton créé": { en: "Token created", es: "Token creado" },
  "Jeton d’enregistrement": { en: "Registration token", es: "Token de registro" },
  "Jeton révoqué.": { en: "Token revoked.", es: "Token revocado." },
  "Jeton supprimé.": { en: "Token deleted.", es: "Token eliminado." },
  Jetons: { en: "Tokens", es: "Tokens" },
  "Jetons d’enregistrement": { en: "Registration tokens", es: "Tokens de registro" },
  "Jusqu’à (facultatif)": { en: "Until (optional)", es: "Hasta (opcional)" },
  "La borne ajoute ce certificat à ses autorités de confiance (InstallCertificate).": {
    en: "The charging station adds this certificate to its trusted authorities (InstallCertificate).",
    es: "La estación añade este certificado a sus autoridades de confianza (InstallCertificate).",
  },
  "La borne disparaît des listes et ne peut plus se connecter. L’historique des sessions est conservé. Impossible tant qu’une session est en cours.":
    {
      en: "The charging station disappears from lists and can no longer connect. Session history is kept. Not possible while a session is in progress.",
      es: "La estación desaparece de las listas y ya no puede conectarse. Se conserva el historial de sesiones. No es posible mientras haya una sesión en curso.",
    },
  "La borne doit d’abord se connecter en wss:// (TLS).": {
    en: "The charging station must first connect over wss:// (TLS).",
    es: "La estación debe conectarse primero mediante wss:// (TLS).",
  },
  "La borne doit être connectée pour changer de profil.": {
    en: "The charging station must be connected to change profile.",
    es: "La estación debe estar conectada para cambiar de perfil.",
  },
  "La borne envoie ses journaux de diagnostic à l’adresse indiquée.": {
    en: "The charging station uploads its diagnostics logs to the given URL.",
    es: "La estación envía sus registros de diagnóstico a la dirección indicada.",
  },
  "La borne envoie son journal de diagnostic ou de sécurité à l’adresse indiquée.": {
    en: "The charging station uploads its diagnostics or security log to the given URL.",
    es: "La estación envía su registro de diagnóstico o de seguridad a la dirección indicada.",
  },
  "La borne est hors ligne : la demande sera envoyée dès sa reconnexion ou expirera.": {
    en: "The charging station is offline: the request will be sent as soon as it reconnects, or will expire.",
    es: "La estación está sin conexión: la solicitud se enviará en cuanto se vuelva a conectar o caducará.",
  },
  "La borne est hors ligne : le renouvellement à distance n’est possible que lorsqu’elle est connectée. Utilisez le renouvellement sur place.":
    {
      en: "The charging station is offline: remote renewal is only possible while it is connected. Use on-site renewal.",
      es: "La estación está sin conexión: la renovación a distancia solo es posible cuando está conectada. Utilice la renovación in situ.",
    },
  "La borne est hors ligne : saisissez ce mot de passe sur la borne. L’ancien reste accepté jusqu’à la révocation.":
    {
      en: "The charging station is offline: enter this password on the charging station. The previous one remains accepted until it is revoked.",
      es: "La estación está sin conexión: introduzca esta contraseña en la estación. La anterior sigue aceptada hasta su revocación.",
    },
  "La borne génère une demande de signature (SignCertificate) que le serveur signe avec son autorité, puis lui renvoie le certificat.":
    {
      en: "The charging station generates a signing request (SignCertificate) that the server signs with its authority, then sends the certificate back.",
      es: "La estación genera una solicitud de firma (SignCertificate) que el servidor firma con su autoridad y luego le devuelve el certificado.",
    },
  "La borne n’a pas été jointe.": {
    en: "The charging station could not be reached.",
    es: "No se ha podido contactar con la estación.",
  },
  "La borne n’est pas prise en compte dans la répartition de puissance.": {
    en: "The charging station is not included in power allocation.",
    es: "La estación no se tiene en cuenta en el reparto de potencia.",
  },
  "La borne peut se connecter avec un profil plus fort que celui configuré, jamais plus faible.": {
    en: "The charging station can connect with a stronger profile than the configured one, never a weaker one.",
    es: "La estación puede conectarse con un perfil más fuerte que el configurado, nunca más débil.",
  },
  "La borne reçoit une demande de suppression du profil.": {
    en: "The charging station receives a request to delete the profile.",
    es: "La estación recibe una solicitud de eliminación del perfil.",
  },
  "La borne se connecte ensuite en OCPP 1.6 avec son identifiant et son mot de passe.": {
    en: "The charging station then connects over OCPP 1.6 with its ID and password.",
    es: "Después, la estación se conecta en OCPP 1.6 con su identificador y su contraseña.",
  },
  "La borne télécharge le micrologiciel, vérifie sa signature puis l’installe (SignedUpdateFirmware).":
    {
      en: "The charging station downloads the firmware, verifies its signature, then installs it (SignedUpdateFirmware).",
      es: "La estación descarga el firmware, verifica su firma y lo instala (SignedUpdateFirmware).",
    },
  "La borne télécharge puis installe le micrologiciel à la date indiquée.": {
    en: "The charging station downloads and installs the firmware on the given date.",
    es: "La estación descarga e instala el firmware en la fecha indicada.",
  },
  "La borne va envoyer une demande de signature ; le certificat signé lui est ensuite transmis.": {
    en: "The charging station will send a signing request; the signed certificate is then sent to it.",
    es: "La estación enviará una solicitud de firma; después se le transmitirá el certificado firmado.",
  },
  "La borne va redémarrer sa connexion. L’ancien niveau reste accepté jusqu’à sa reconnexion.": {
    en: "The charging station will restart its connection. The previous level remains accepted until it reconnects.",
    es: "La estación reiniciará su conexión. El nivel anterior sigue aceptado hasta que se vuelva a conectar.",
  },
  "La demande de certificat n’a pas été envoyée": {
    en: "The certificate request was not sent",
    es: "No se ha enviado la solicitud de certificado",
  },
  "La lecture n’a pas été envoyée": {
    en: "The read request was not sent",
    es: "No se ha enviado la lectura",
  },
  "La modification n’a pas pu être envoyée": {
    en: "The change could not be sent",
    es: "No se ha podido enviar la modificación",
  },
  "La recharge démarre dès le branchement d’un véhicule enregistré, sans badge : la borne transmet l’identifiant du véhicule (AutoCharge).":
    {
      en: "Charging starts as soon as a registered vehicle is plugged in, without a badge: the charging station sends the vehicle ID (AutoCharge).",
      es: "La carga se inicia en cuanto se conecta un vehículo registrado, sin tarjeta RFID: la estación transmite el identificador del vehículo (AutoCharge).",
    },
  "La suppression n’a pas été envoyée": {
    en: "The deletion was not sent",
    es: "No se ha enviado la eliminación",
  },
  "La valeur est enregistrée lorsque la borne accepte la modification (ChangeConfiguration).": {
    en: "The value is saved when the charging station accepts the change (ChangeConfiguration).",
    es: "El valor se guarda cuando la estación acepta la modificación (ChangeConfiguration).",
  },
  "Laissez vide pour utiliser la valeur du serveur.": {
    en: "Leave empty to use the server value.",
    es: "Déjelo vacío para usar el valor del servidor.",
  },
  "Le certificat client dont l’empreinte SHA-256 correspond est accepté pour {station}, même si son nom commun diffère de l’identifiant de la borne.":
    {
      en: "The client certificate with the matching SHA-256 fingerprint is accepted for {station}, even if its common name differs from the charging station ID.",
      es: "El certificado de cliente cuya huella SHA-256 coincide se acepta para {station}, aunque su nombre común difiera del identificador de la estación.",
    },
  "Le certificat contient 5 500 caractères au plus.": {
    en: "The certificate contains at most 5,500 characters.",
    es: "El certificado contiene 5500 caracteres como máximo.",
  },
  "Le certificat doit être au format PEM (-----BEGIN CERTIFICATE-----).": {
    en: "The certificate must be in PEM format (-----BEGIN CERTIFICATE-----).",
    es: "El certificado debe estar en formato PEM (-----BEGIN CERTIFICATE-----).",
  },
  "Le certificat n’a pas été envoyé": {
    en: "The certificate was not sent",
    es: "No se ha enviado el certificado",
  },
  "Le certificat n’a pas été révoqué": {
    en: "The certificate was not revoked",
    es: "No se ha revocado el certificado",
  },
  "Le certificat signé par le serveur et l’empreinte épinglée ne sont plus acceptés, et la borne est déconnectée. Une borne au profil 3 ne pourra plus se reconnecter sans nouveau certificat.":
    {
      en: "The certificate signed by the server and the pinned fingerprint are no longer accepted, and the charging station is disconnected. A charging station on profile 3 will not be able to reconnect without a new certificate.",
      es: "El certificado firmado por el servidor y la huella fijada dejan de aceptarse, y la estación se desconecta. Una estación con perfil 3 no podrá volver a conectarse sin un nuevo certificado.",
    },
  "Le format JSON des paramètres est invalide.": {
    en: "The parameters JSON is invalid.",
    es: "El formato JSON de los parámetros no es válido.",
  },
  "Le jeton est supprimé définitivement.": {
    en: "The token is permanently deleted.",
    es: "El token se elimina definitivamente.",
  },
  "Le mot de passe déjà en attente est remplacé.": {
    en: "The password already pending is replaced.",
    es: "La contraseña ya pendiente se sustituye.",
  },
  "Le mot de passe n’a pas été renouvelé": {
    en: "The password was not renewed",
    es: "No se ha renovado la contraseña",
  },
  "Le nouveau mot de passe en attente ne sera plus accepté ; seul le mot de passe actuel restera valide. Si la borne a déjà enregistré le nouveau mot de passe, elle ne pourra plus se connecter : le serveur refuse donc l’annulation dès que la commande a été transmise. Utilisez alors « Révoquer l’ancien mot de passe ».":
    {
      en: "The pending new password will no longer be accepted; only the current password will remain valid. If the charging station has already saved the new password, it will no longer be able to connect, so the server rejects the cancellation once the command has been sent. In that case, use “Revoke previous password”.",
      es: "La nueva contraseña pendiente dejará de aceptarse; solo seguirá siendo válida la contraseña actual. Si la estación ya ha guardado la nueva contraseña, ya no podrá conectarse: por eso el servidor rechaza la cancelación en cuanto se ha transmitido el comando. En ese caso, utilice «Revocar la contraseña anterior».",
    },
  "Le nouveau mot de passe est envoyé à la borne. L’ancien reste accepté jusqu’à sa reconnexion avec le nouveau.":
    {
      en: "The new password is sent to the charging station. The previous one remains accepted until it reconnects with the new one.",
      es: "La nueva contraseña se envía a la estación. La anterior sigue aceptada hasta que se vuelva a conectar con la nueva.",
    },
  "Le nouveau mot de passe est envoyé à la borne. L’ancien reste accepté jusqu’à sa reconnexion avec le nouveau. Si la borne refuse la modification, saisissez ce mot de passe sur place.":
    {
      en: "The new password is sent to the charging station. The previous one remains accepted until it reconnects with the new one. If the charging station rejects the change, enter this password on site.",
      es: "La nueva contraseña se envía a la estación. La anterior sigue aceptada hasta que se vuelva a conectar con la nueva. Si la estación rechaza la modificación, introduzca esta contraseña in situ.",
    },
  "Le nouvel intervalle du signal de vie est envoyé à la borne.": {
    en: "The new heartbeat interval is sent to the charging station.",
    es: "El nuevo intervalo de la señal de vida se envía a la estación.",
  },
  "Le nouvel intervalle est envoyé à la reconnexion de la borne et appliqué au prochain démarrage de la borne.":
    {
      en: "The new interval is sent when the charging station reconnects and applied at its next boot.",
      es: "El nuevo intervalo se envía cuando la estación se vuelve a conectar y se aplica en su próximo arranque.",
    },
  "Le profil 0 est désactivé sur ce serveur.": {
    en: "Profile 0 is disabled on this server.",
    es: "El perfil 0 está desactivado en este servidor.",
  },
  "Le profil de sécurité n’a pas été modifié": {
    en: "The security profile was not changed",
    es: "No se ha modificado el perfil de seguridad",
  },
  "Le profil n’a pas pu être envoyé.": {
    en: "The profile could not be sent.",
    es: "No se ha podido enviar el perfil.",
  },
  "Le profil {profile} est appliqué immédiatement côté serveur, sans rien envoyer à la borne : configurez-la sur place pour qu’elle se reconnecte avec ce profil.":
    {
      en: "Profile {profile} is applied immediately on the server side, without sending anything to the charging station: configure it on site so it reconnects with this profile.",
      es: "El perfil {profile} se aplica inmediatamente en el servidor, sin enviar nada a la estación: configúrela in situ para que se vuelva a conectar con este perfil.",
    },
  "Le renouvellement n’a pas été annulé": {
    en: "The renewal was not cancelled",
    es: "No se ha cancelado la renovación",
  },
  "Le réglage AutoChargeEnabled est envoyé à la borne ; il s’applique dès qu’elle accepte la modification.":
    {
      en: "The AutoChargeEnabled setting is sent to the charging station; it applies as soon as the station accepts the change.",
      es: "El ajuste AutoChargeEnabled se envía a la estación; se aplica en cuanto acepta la modificación.",
    },
  "Le réglage n’a pas pu être modifié": {
    en: "The setting could not be changed",
    es: "No se ha podido modificar el ajuste",
  },
  "Le serveur accepte immédiatement le profil {profile}, plus faible. Rien n’est envoyé à la borne : une borne ne peut pas abaisser son propre profil, elle doit être reconfigurée sur place.":
    {
      en: "The server immediately accepts the weaker profile {profile}. Nothing is sent to the charging station: a charging station cannot lower its own profile, it must be reconfigured on site.",
      es: "El servidor acepta inmediatamente el perfil {profile}, más débil. No se envía nada a la estación: una estación no puede reducir su propio perfil, debe reconfigurarse in situ.",
    },
  "Le serveur répond Accepted sans traiter le contenu : n’ajoutez un fournisseur que si la borne l’exige.":
    {
      en: "The server replies Accepted without processing the content: only add a vendor if the charging station requires it.",
      es: "El servidor responde Accepted sin procesar el contenido: añada un proveedor solo si la estación lo exige.",
    },
  "Le site est déduit de la zone choisie.": {
    en: "The site is derived from the selected area.",
    es: "El sitio se deduce de la zona elegida.",
  },
  "Lecture de la configuration demandée à la borne.": {
    en: "Configuration read requested from the charging station.",
    es: "Lectura de la configuración solicitada a la estación.",
  },
  "Lecture des certificats demandée à la borne.": {
    en: "Certificate read requested from the charging station.",
    es: "Lectura de los certificados solicitada a la estación.",
  },
  "Lecture seule": { en: "Read-only", es: "Solo lectura" },
  "Les bornes ne pourront plus s’enregistrer avec ce jeton.": {
    en: "Charging stations will no longer be able to register with this token.",
    es: "Las estaciones ya no podrán registrarse con este token.",
  },
  "Les commandes envoyées à cette borne et leurs réponses apparaîtront ici.": {
    en: "Commands sent to this charging station and their responses will appear here.",
    es: "Los comandos enviados a esta estación y sus respuestas aparecerán aquí.",
  },
  "Les connecteurs apparaissent lorsque la borne envoie leur état.": {
    en: "Connectors appear when the charging station sends their status.",
    es: "Los conectores aparecen cuando la estación envía su estado.",
  },
  "Les démarrages, changements d’état et déconnexions de la borne apparaîtront ici.": {
    en: "The charging station’s boots, status changes and disconnections will appear here.",
    es: "Los arranques, cambios de estado y desconexiones de la estación aparecerán aquí.",
  },
  "Les identifiants « . » et « .. » sont réservés.": {
    en: "The IDs “.” and “..” are reserved.",
    es: "Los identificadores «.» y «..» están reservados.",
  },
  "Les paramètres OCPP n’ont pas été enregistrés": {
    en: "The OCPP settings were not saved",
    es: "No se han guardado los parámetros OCPP",
  },
  "Les véhicules enregistrés devront de nouveau utiliser un badge ou l’application sur cette borne.":
    {
      en: "Registered vehicles will again need to use a badge or the app on this charging station.",
      es: "Los vehículos registrados deberán volver a usar una tarjeta RFID o la aplicación en esta estación.",
    },
  "Lettres, chiffres et . _ : * - uniquement.": {
    en: "Letters, digits and . _ : * - only.",
    es: "Solo letras, cifras y . _ : * -.",
  },
  "Libère le câble bloqué dans le connecteur.": {
    en: "Releases the cable locked in the connector.",
    es: "Libera el cable bloqueado en el conector.",
  },
  "Limitation de puissance envoyée à la borne.": {
    en: "Power limit sent to the charging station.",
    es: "Limitación de potencia enviada a la estación.",
  },
  "Limitation et profils": { en: "Limits and profiles", es: "Limitación y perfiles" },
  "Limitation refusée": { en: "Limit rejected", es: "Limitación rechazada" },
  Limite: { en: "Limit", es: "Límite" },
  "Limiter la puissance": { en: "Limit power", es: "Limitar la potencia" },
  "Lire les certificats": { en: "Read certificates", es: "Leer los certificados" },
  "Liste des bornes": { en: "Charging station list", es: "Lista de estaciones" },
  "Lu le": { en: "Read on", es: "Leído el" },
  "L’ancien et le nouveau mot de passe sont acceptés jusqu’à la reconnexion de la borne avec le nouveau. Si la borne a refusé la modification, saisissez le nouveau mot de passe sur place, puis révoquez l’ancien.":
    {
      en: "Both the previous and new passwords are accepted until the charging station reconnects with the new one. If the charging station rejected the change, enter the new password on site, then revoke the previous one.",
      es: "La contraseña anterior y la nueva se aceptan hasta que la estación se vuelva a conectar con la nueva. Si la estación ha rechazado la modificación, introduzca la nueva contraseña in situ y luego revoque la anterior.",
    },
  "L’ancien mot de passe n’a pas été révoqué": {
    en: "The previous password was not revoked",
    es: "No se ha revocado la contraseña anterior",
  },
  "L’empreinte n’a pas été retirée": {
    en: "The fingerprint was not removed",
    es: "No se ha eliminado la huella",
  },
  "L’empreinte n’a pas été épinglée": {
    en: "The fingerprint was not pinned",
    es: "No se ha fijado la huella",
  },
  "L’identifiant du véhicule n’est pas un secret : n’activez la recharge automatique que sur des bornes dont l’accès est maîtrisé.":
    {
      en: "The vehicle ID is not a secret: only enable automatic charging on charging stations with controlled access.",
      es: "El identificador del vehículo no es secreto: active la carga automática solo en estaciones con acceso controlado.",
    },
  Maintenance: { en: "Maintenance", es: "Mantenimiento" },
  Matériel: { en: "Hardware", es: "Hardware" },
  "Matériel inconnu": { en: "Unknown hardware", es: "Hardware desconocido" },
  "Met la borne ou un connecteur en service ou hors service.": {
    en: "Sets the charging station or a connector operative or inoperative.",
    es: "Pone la estación o un conector en servicio o fuera de servicio.",
  },
  "Mettre hors service": { en: "Set inoperative", es: "Poner fuera de servicio" },
  Modifiable: { en: "Editable", es: "Modificable" },
  "Modification envoyée à la borne.": {
    en: "Change sent to the charging station.",
    es: "Modificación enviada a la estación.",
  },
  "Modifier le profil": { en: "Change profile", es: "Modificar el perfil" },
  "Modifier le profil de sécurité": {
    en: "Change security profile",
    es: "Modificar el perfil de seguridad",
  },
  "Modifier l’empreinte": { en: "Change fingerprint", es: "Modificar la huella" },
  "Modèle de configuration appliqué.": {
    en: "Configuration template applied.",
    es: "Plantilla de configuración aplicada.",
  },
  "Mot de passe de la borne": { en: "Charging station password", es: "Contraseña de la estación" },
  "Mot de passe d’authentification Basic de la borne (AuthorizationKey).": {
    en: "The charging station’s Basic authentication password (AuthorizationKey).",
    es: "Contraseña de autenticación Basic de la estación (AuthorizationKey).",
  },
  "Mot de passe en cours de renouvellement": {
    en: "Password renewal in progress",
    es: "Renovación de contraseña en curso",
  },
  "Mot de passe OCPP": { en: "OCPP password", es: "Contraseña OCPP" },
  "Niveau de batterie du véhicule": {
    en: "Vehicle battery level",
    es: "Nivel de batería del vehículo",
  },
  "Niveau de priorité": { en: "Priority level", es: "Nivel de prioridad" },
  "Niveau entre 0 et 99.": { en: "Level between 0 and 99.", es: "Nivel entre 0 y 99." },
  "Nom affiché": { en: "Display name", es: "Nombre mostrado" },
  "Nom ou identifiant…": { en: "Name or ID…", es: "Nombre o identificador…" },
  "Nombre de tentatives": { en: "Number of retries", es: "Número de intentos" },
  "Nouveau jeton": { en: "New token", es: "Nuevo token" },
  "Nouveau jeton d’enregistrement": { en: "New registration token", es: "Nuevo token de registro" },
  "Nouveau mot de passe OCPP": { en: "New OCPP password", es: "Nueva contraseña OCPP" },
  "Nouveau profil": { en: "New profile", es: "Nuevo perfil" },
  "Nouveau profil de recharge": { en: "New charging profile", es: "Nuevo perfil de carga" },
  "Nouvelle valeur": { en: "New value", es: "Nuevo valor" },
  "Numéro de réservation": { en: "Reservation number", es: "Número de reserva" },
  "Numéro de série": { en: "Serial number", es: "Número de serie" },
  "Numéro du profil": { en: "Profile number", es: "Número del perfil" },
  "Numéro invalide.": { en: "Invalid number.", es: "Número no válido." },
  "N’importe quel appareil connaissant l’identifiant pourra se faire passer pour cette borne. Le serveur accepte immédiatement les connexions sans authentification.":
    {
      en: "Any device that knows the ID will be able to impersonate this charging station. The server immediately accepts unauthenticated connections.",
      es: "Cualquier dispositivo que conozca el identificador podrá hacerse pasar por esta estación. El servidor acepta inmediatamente las conexiones sin autenticación.",
    },
  "OCPP 1.6 JSON, sous-protocole ocpp1.6.": {
    en: "OCPP 1.6 JSON, ocpp1.6 subprotocol.",
    es: "OCPP 1.6 JSON, subprotocolo ocpp1.6.",
  },
  "Paramètres de la commande": { en: "Command parameters", es: "Parámetros del comando" },
  "Paramètres OCPP": { en: "OCPP parameters", es: "Parámetros OCPP" },
  "Passer au profil {profile}": {
    en: "Switch to profile {profile}",
    es: "Pasar al perfil {profile}",
  },
  "Passer la borne au profil {profile} ?": {
    en: "Switch the charging station to profile {profile}?",
    es: "¿Pasar la estación al perfil {profile}?",
  },
  "Photo de la borne, envoyée le {date}": {
    en: "Charging station photo, sent on {date}",
    es: "Foto de la estación, enviada el {date}",
  },
  "Photo supprimée.": { en: "Photo deleted.", es: "Foto eliminada." },
  Photos: { en: "Photos", es: "Fotos" },
  "Plus d’options": { en: "More options", es: "Más opciones" },
  "Position et puissance": { en: "Location and power", es: "Posición y potencia" },
  "Profil 0 · sans authentification": {
    en: "Profile 0 · no authentication",
    es: "Perfil 0 · sin autenticación",
  },
  "Profil 1 · mot de passe": { en: "Profile 1 · password", es: "Perfil 1 · contraseña" },
  "Profil 2 · TLS et mot de passe": {
    en: "Profile 2 · TLS and password",
    es: "Perfil 2 · TLS y contraseña",
  },
  "Profil 3 · certificat client (TLS mutuel)": {
    en: "Profile 3 · client certificate (mutual TLS)",
    es: "Perfil 3 · certificado de cliente (TLS mutuo)",
  },
  "Profil attendu": { en: "Expected profile", es: "Perfil esperado" },
  "Profil configuré": { en: "Configured profile", es: "Perfil configurado" },
  "Profil de sécurité": { en: "Security profile", es: "Perfil de seguridad" },
  "Profil supprimé.": { en: "Profile deleted.", es: "Perfil eliminado." },
  "Profil {current} → {requested} en attente": {
    en: "Profile {current} → {requested} pending",
    es: "Perfil {current} → {requested} pendiente",
  },
  "Profil {id} · connecteur {connector}": {
    en: "Profile {id} · connector {connector}",
    es: "Perfil {id} · conector {connector}",
  },
  "Profil {id} · toute la borne": {
    en: "Profile {id} · whole charging station",
    es: "Perfil {id} · toda la estación",
  },
  "Profils de recharge": { en: "Charging profiles", es: "Perfiles de carga" },
  "Programmer la mise à jour": { en: "Schedule update", es: "Programar la actualización" },
  "Programmes de puissance envoyés à la borne (SetChargingProfile).": {
    en: "Power schedules sent to the charging station (SetChargingProfile).",
    es: "Programas de potencia enviados a la estación (SetChargingProfile).",
  },
  "Propriétés de la borne enregistrées.": {
    en: "Charging station properties saved.",
    es: "Propiedades de la estación guardadas.",
  },
  Publique: { en: "Public", es: "Pública" },
  "Publiée aux partenaires d’itinérance lorsque son site est public.": {
    en: "Published to roaming partners when its site is public.",
    es: "Publicada a los socios de itinerancia cuando su sitio es público.",
  },
  Périodes: { en: "Periods", es: "Periodos" },
  "Racine du fabricant": { en: "Manufacturer root", es: "Raíz del fabricante" },
  "Racine du serveur": { en: "Server root", es: "Raíz del servidor" },
  "Racines du fabricant": { en: "Manufacturer roots", es: "Raíces del fabricante" },
  "Racines du serveur": { en: "Server roots", es: "Raíces del servidor" },
  Recharge: { en: "Charging", es: "Carga" },
  "Recharge automatique activée": {
    en: "Automatic charging enabled",
    es: "Carga automática activada",
  },
  "Recharge automatique activée.": {
    en: "Automatic charging enabled.",
    es: "Carga automática activada.",
  },
  "Recharge automatique désactivée.": {
    en: "Automatic charging disabled.",
    es: "Carga automática desactivada.",
  },
  "Rechercher une borne": { en: "Search for a charging station", es: "Buscar una estación" },
  "Redémarre le logiciel de la borne. Les sessions en cours sont arrêtées proprement.": {
    en: "Restarts the charging station software. Sessions in progress are stopped cleanly.",
    es: "Reinicia el software de la estación. Las sesiones en curso se detienen correctamente.",
  },
  Redémarrer: { en: "Restart", es: "Reiniciar" },
  "Relire la configuration": {
    en: "Read configuration again",
    es: "Volver a leer la configuración",
  },
  "Remettre en service": { en: "Set operative", es: "Volver a poner en servicio" },
  Renouveler: { en: "Renew", es: "Renovar" },
  "Renouveler le certificat": { en: "Renew certificate", es: "Renovar el certificado" },
  "Renouveler le certificat client ?": {
    en: "Renew the client certificate?",
    es: "¿Renovar el certificado de cliente?",
  },
  "Renouveler le mot de passe OCPP": {
    en: "Renew OCPP password",
    es: "Renovar la contraseña OCPP",
  },
  "Renouveler le mot de passe OCPP ?": {
    en: "Renew the OCPP password?",
    es: "¿Renovar la contraseña OCPP?",
  },
  "Renouveler le mot de passe sur place ?": {
    en: "Renew the password on site?",
    es: "¿Renovar la contraseña in situ?",
  },
  "Renouveler sur place": { en: "Renew on site", es: "Renovar in situ" },
  "Renouvellement annulé.": { en: "Renewal cancelled.", es: "Renovación cancelada." },
  "Renouvellement sur place : l’ancien mot de passe ne fonctionne plus et la borne a été déconnectée. Configurez la borne avec celui-ci pour qu’elle se reconnecte.":
    {
      en: "On-site renewal: the previous password no longer works and the charging station has been disconnected. Configure the charging station with this one so it reconnects.",
      es: "Renovación in situ: la contraseña anterior ya no funciona y la estación se ha desconectado. Configure la estación con esta para que se vuelva a conectar.",
    },
  "Renseignez l’identifiant OCPP de la borne.": {
    en: "Enter the charging station’s OCPP ID.",
    es: "Indique el identificador OCPP de la estación.",
  },
  "Renseignez un objet JSON pour les paramètres de la commande.": {
    en: "Enter a JSON object for the command parameters.",
    es: "Indique un objeto JSON para los parámetros del comando.",
  },
  "Retirer la période {index}": { en: "Remove period {index}", es: "Quitar el periodo {index}" },
  "Retirer le connecteur {index}": {
    en: "Remove connector {index}",
    es: "Quitar el conector {index}",
  },
  "Retirer l’empreinte": { en: "Remove fingerprint", es: "Quitar la huella" },
  "Retirer l’empreinte épinglée ?": {
    en: "Remove the pinned fingerprint?",
    es: "¿Quitar la huella fijada?",
  },
  Réponse: { en: "Response", es: "Respuesta" },
  Répétition: { en: "Recurrence", es: "Repetición" },
  "Réserve le connecteur pour un badge jusqu’à l’échéance choisie.": {
    en: "Reserves the connector for a badge until the chosen deadline.",
    es: "Reserva el conector para una tarjeta RFID hasta el vencimiento elegido.",
  },
  Réserver: { en: "Reserve", es: "Reservar" },
  "Révoquer ce jeton ?": { en: "Revoke this token?", es: "¿Revocar este token?" },
  "Révoquer le certificat": { en: "Revoke certificate", es: "Revocar el certificado" },
  "Révoquer le certificat de la borne ?": {
    en: "Revoke the charging station’s certificate?",
    es: "¿Revocar el certificado de la estación?",
  },
  "Révoquer l’ancien mot de passe": {
    en: "Revoke previous password",
    es: "Revocar la contraseña anterior",
  },
  "Révoquer l’ancien mot de passe ?": {
    en: "Revoke the previous password?",
    es: "¿Revocar la contraseña anterior?",
  },
  Révoqué: { en: "Revoked", es: "Revocado" },
  "Saisissez l’identifiant {id} pour confirmer": {
    en: "Type the ID {id} to confirm",
    es: "Introduzca el identificador {id} para confirmar",
  },
  "Sans authentification": { en: "No authentication", es: "Sin autenticación" },
  "Sans profil, la borne délivre sa puissance maximale.": {
    en: "Without a profile, the charging station delivers its maximum power.",
    es: "Sin perfil, la estación suministra su potencia máxima.",
  },
  "Section Sécurité": { en: "Security section", es: "Sección Seguridad" },
  "Session en cours": { en: "Session in progress", es: "Sesión en curso" },
  "Seul le nouveau mot de passe reste accepté et la connexion en cours est fermée. Si la borne n’a pas enregistré le nouveau mot de passe, elle ne pourra plus se connecter tant qu’il n’aura pas été saisi sur place.":
    {
      en: "Only the new password remains accepted and the current connection is closed. If the charging station has not saved the new password, it will not be able to connect until it has been entered on site.",
      es: "Solo se sigue aceptando la nueva contraseña y se cierra la conexión en curso. Si la estación no ha guardado la nueva contraseña, no podrá conectarse hasta que se introduzca in situ.",
    },
  "Seul un administrateur peut modifier ce réglage.": {
    en: "Only an administrator can change this setting.",
    es: "Solo un administrador puede modificar este ajuste.",
  },
  "Seul un administrateur peut modifier la sécurité de la borne.": {
    en: "Only an administrator can change the charging station’s security.",
    es: "Solo un administrador puede modificar la seguridad de la estación.",
  },
  "Signal de vie et messages DataTransfer propres au fabricant.": {
    en: "Heartbeat and manufacturer-specific DataTransfer messages.",
    es: "Señal de vida y mensajes DataTransfer propios del fabricante.",
  },
  "Signal {time}": { en: "Seen {time}", es: "Señal {time}" },
  Signaler: { en: "Report", es: "Notificar" },
  "Signature (base64)": { en: "Signature (base64)", es: "Firma (base64)" },
  "Site · zone": { en: "Site · area", es: "Sitio · zona" },
  "Suivez la réponse de la borne dans le suivi des commandes.": {
    en: "Follow the charging station’s response in the command log.",
    es: "Siga la respuesta de la estación en el seguimiento de comandos.",
  },
  Sujet: { en: "Subject", es: "Sujeto" },
  "Suppression du certificat demandée à la borne.": {
    en: "Certificate deletion requested from the charging station.",
    es: "Eliminación del certificado solicitada a la estación.",
  },
  "Supprimer ce certificat ?": {
    en: "Delete this certificate?",
    es: "¿Eliminar este certificado?",
  },
  "Supprimer ce jeton ?": { en: "Delete this token?", es: "¿Eliminar este token?" },
  "Supprimer ce profil ?": { en: "Delete this profile?", es: "¿Eliminar este perfil?" },
  "Supprimer la borne": { en: "Delete charging station", es: "Eliminar la estación" },
  "Supprimer la borne {name} ?": {
    en: "Delete charging station {name}?",
    es: "¿Eliminar la estación {name}?",
  },
  "Supprimer le certificat racine du serveur peut empêcher la borne de se reconnecter en TLS.": {
    en: "Deleting the server root certificate may prevent the charging station from reconnecting over TLS.",
    es: "Eliminar el certificado raíz del servidor puede impedir que la estación se vuelva a conectar mediante TLS.",
  },
  "Supprimer le profil": { en: "Delete profile", es: "Eliminar el perfil" },
  Tesla: { en: "Tesla", es: "Tesla" },
  "Tous les événements": { en: "All events", es: "Todos los eventos" },
  "Tout véhicule enregistré pourra démarrer une recharge sur cette borne simplement en se branchant. L’identifiant d’un véhicule peut être imité : réservez ce réglage aux bornes dont l’accès est contrôlé.":
    {
      en: "Any registered vehicle will be able to start charging on this charging station simply by plugging in. A vehicle ID can be spoofed: reserve this setting for charging stations with controlled access.",
      es: "Cualquier vehículo registrado podrá iniciar una carga en esta estación simplemente al conectarse. El identificador de un vehículo puede imitarse: reserve este ajuste a las estaciones con acceso controlado.",
    },
  "Toute la borne": { en: "Whole charging station", es: "Toda la estación" },
  "Toutes les bornes sont connectées, rattachées à une zone et sans défaut signalé.": {
    en: "All charging stations are connected, attached to an area and have no reported fault.",
    es: "Todas las estaciones están conectadas, vinculadas a una zona y sin averías notificadas.",
  },
  "Type de certificat": { en: "Certificate type", es: "Tipo de certificado" },
  "Type d’événement": { en: "Event type", es: "Tipo de evento" },
  "Un certificat client dont le nom commun diffère de l’identifiant de la borne ne sera plus accepté.":
    {
      en: "A client certificate whose common name differs from the charging station ID will no longer be accepted.",
      es: "Ya no se aceptará un certificado de cliente cuyo nombre común difiera del identificador de la estación.",
    },
  "Un identifiant de fournisseur contient 255 caractères au plus.": {
    en: "A vendor ID contains at most 255 characters.",
    es: "Un identificador de proveedor contiene 255 caracteres como máximo.",
  },
  "Un identifiant de fournisseur ne peut pas contenir de virgule.": {
    en: "A vendor ID cannot contain a comma.",
    es: "Un identificador de proveedor no puede contener comas.",
  },
  "Une borne désactivée refuse les nouvelles connexions OCPP.": {
    en: "A disabled charging station rejects new OCPP connections.",
    es: "Una estación desactivada rechaza las nuevas conexiones OCPP.",
  },
  "Une borne qui se connecte avec un jeton valide comme mot de passe est créée automatiquement dans la zone choisie.":
    {
      en: "A charging station that connects with a valid token as its password is created automatically in the selected area.",
      es: "Una estación que se conecta con un token válido como contraseña se crea automáticamente en la zona elegida.",
    },
  "Une seule fois, à partir de maintenant": {
    en: "Once, starting now",
    es: "Una sola vez, a partir de ahora",
  },
  Unité: { en: "Unit", es: "Unidad" },
  Usage: { en: "Purpose", es: "Uso" },
  "Utilisé comme mot de passe OCPP lors de la première connexion de la borne.": {
    en: "Used as the OCPP password when the charging station first connects.",
    es: "Se utiliza como contraseña OCPP en la primera conexión de la estación.",
  },
  "Valeur du serveur ({value} s)": {
    en: "Server value ({value} s)",
    es: "Valor del servidor ({value} s)",
  },
  "Version inconnue": { en: "Unknown version", es: "Versión desconocida" },
  "Vider le cache des autorisations": {
    en: "Clear authorization cache",
    es: "Vaciar la caché de autorizaciones",
  },
  "Voir les commandes": { en: "View commands", es: "Ver los comandos" },
  "Vus récemment :": { en: "Recently seen:", es: "Vistos recientemente:" },
  "Vérifiez le connecteur et les paramètres avant l’envoi. La réponse confirme la réception de la commande ; le suivi de session confirme le démarrage de la recharge.":
    {
      en: "Check the connector and parameters before sending. The response confirms the command was received; session tracking confirms charging has started.",
      es: "Compruebe el conector y los parámetros antes del envío. La respuesta confirma la recepción del comando; el seguimiento de la sesión confirma el inicio de la carga.",
    },
  "Zone de recharge (facultatif)": {
    en: "Charging area (optional)",
    es: "Zona de carga (opcional)",
  },
  "Zone des nouvelles bornes": {
    en: "Area for new charging stations",
    es: "Zona de las nuevas estaciones",
  },
  "{action} : demande envoyée": { en: "{action}: request sent", es: "{action}: solicitud enviada" },
  "{action} : échec": { en: "{action}: failed", es: "{action}: error" },
  "{count} fournisseurs au plus.": {
    en: "{count} vendors at most.",
    es: "{count} proveedores como máximo.",
  },
  "{count} phase": { en: "{count} phase", es: "{count} fase" },
  "{count} phases": { en: "{count} phases", es: "{count} fases" },
  "{profile} : OCPP 1.6 JSON, sous-protocole ocpp1.6, authentification Basic avec l’identifiant et ce mot de passe.":
    {
      en: "{profile}: OCPP 1.6 JSON, ocpp1.6 subprotocol, Basic authentication with the ID and this password.",
      es: "{profile}: OCPP 1.6 JSON, subprotocolo ocpp1.6, autenticación Basic con el identificador y esta contraseña.",
    },
  "{purpose} · niveau {level} · {date}": {
    en: "{purpose} · level {level} · {date}",
    es: "{purpose} · nivel {level} · {date}",
  },
  "{station} · actuellement {profile}": {
    en: "{station} · currently {profile}",
    es: "{station} · actualmente {profile}",
  },
  "{station} · programme de puissance OCPP 1.6": {
    en: "{station} · OCPP 1.6 power schedule",
    es: "{station} · programa de potencia OCPP 1.6",
  },
  "À utiliser lorsque vous êtes devant la borne : l’ancien mot de passe ne fonctionne plus immédiatement et la borne est déconnectée jusqu’à ce que le nouveau y soit saisi.":
    {
      en: "Use this when you are at the charging station: the previous password stops working immediately and the charging station is disconnected until the new one is entered on it.",
      es: "Utilícelo cuando esté delante de la estación: la contraseña anterior deja de funcionar inmediatamente y la estación se desconecta hasta que se introduzca la nueva.",
    },
  "Échanges OCPP": { en: "OCPP exchanges", es: "Intercambios OCPP" },
  "Émetteur (empreinte du nom)": { en: "Issuer (name hash)", es: "Emisor (huella del nombre)" },
  Épingler: { en: "Pin", es: "Fijar" },
  "Épingler l’empreinte présentée": {
    en: "Pin the presented fingerprint",
    es: "Fijar la huella presentada",
  },
  "Épingler une empreinte": { en: "Pin a fingerprint", es: "Fijar una huella" },
  "État reçu {time}": { en: "Status received {time}", es: "Estado recibido {time}" },
  Événement: { en: "Event", es: "Evento" },
  Événements: { en: "Events", es: "Eventos" },
};

export default messages;
