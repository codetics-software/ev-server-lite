# Azure

Lisez d’abord [l’architecture de référence](README.md). Les commandes utilisent Azure CLI et la région `francecentral` ; remplacez les noms entre chevrons.

## Topologie recommandée

| Besoin | Choix |
|---|---|
| Serveur et tableau de bord | Une VM `Standard_D2s_v5` (ou `Standard_B2s` en essai) avec `deploy/docker-compose.yml` |
| Base | Azure Database for PostgreSQL – serveur flexible, accès privé par réseau virtuel |
| Secrets | Azure Key Vault, identité managée de la VM |
| Images | Azure Container Registry (si AKS ou Container Apps) |
| Profil 3 | Caddy sur la VM, ou Application Gateway v2 en mTLS strict |
| Variante cluster | AKS avec `deploy/kubernetes/` |

**Azure Container Apps** convient au tableau de bord. Pour le serveur, deux limites : le délai de requête de l’entrée HTTP est de 240 s et la documentation ne précise pas son effet sur une connexion WebSocket établie ([entrée](https://learn.microsoft.com/azure/container-apps/ingress-overview)) ; le mode certificat client (`accept`, `require`) transmet `X-Forwarded-Client-Cert` sans vérifier la chaîne contre votre autorité ([certificats clients](https://learn.microsoft.com/azure/container-apps/client-certificate-authorization)). Le profil 3 y est donc exclu. Pour un essai en profils 1 et 2 : `--min-replicas 1 --max-replicas 1`, révision unique, `OCPP_TRUSTED_PROXIES=0.0.0.0/0,::/0` (l’application n’est joignable que par l’entrée), `APP_TRUSTED_PROXIES` limité au tableau de bord, et une borne de test connectée plusieurs heures avant toute mise en service.

## Étapes : VM et PostgreSQL

```sh
az group create -n ev-supervision -l francecentral
az network vnet create -g ev-supervision -n ev-vnet --address-prefixes 10.20.0.0/16 \
  --subnet-name vm --subnet-prefixes 10.20.1.0/24
az network vnet subnet create -g ev-supervision --vnet-name ev-vnet -n db \
  --address-prefixes 10.20.2.0/24 --delegations Microsoft.DBforPostgreSQL/flexibleServers

DB_PASSWORD=$(openssl rand -base64 24)
az postgres flexible-server create -g ev-supervision -n <serveur-pg> -l francecentral \
  --tier Burstable --sku-name Standard_B1ms --storage-size 32 --version 17 \
  --admin-user ev_supervision --admin-password "$DB_PASSWORD" \
  --vnet ev-vnet --subnet db --backup-retention 7
az postgres flexible-server db create -g ev-supervision -s <serveur-pg> -d ev_supervision

az keyvault create -g ev-supervision -n <coffre> --enable-rbac-authorization true
az keyvault secret set --vault-name <coffre> -n database-password --value "$DB_PASSWORD"
az keyvault secret set --vault-name <coffre> -n app-encryption-key --value "$(openssl rand -base64 32)"

az vm create -g ev-supervision -n ev-supervision --image Debian:debian-12:12-gen2:latest \
  --size Standard_D2s_v5 --vnet-name ev-vnet --subnet vm --public-ip-sku Standard \
  --admin-username exploitant --ssh-key-values ~/.ssh/id_ed25519.pub --assign-identity
az vm open-port -g ev-supervision -n ev-supervision --port 80,443 --priority 900
az network public-ip update -g ev-supervision -n ev-supervisionPublicIP --idle-timeout 30
```

Donnez à l’identité de la VM le rôle `Key Vault Secrets User` sur le coffre. Sur la VM, installez Docker Engine et suivez [Machine virtuelle unique](README.md#machine-virtuelle-unique-docker-compose) avec :

```text
COMPOSE_PROFILES=
DATABASE_URL=jdbc:postgresql://<serveur-pg>.postgres.database.azure.com:5432/ev_supervision?sslmode=require
DATABASE_USER=ev_supervision
DATABASE_PASSWORD=<az keyvault secret show --vault-name <coffre> -n database-password --query value -o tsv>
```

Le délai d’inactivité TCP d’une adresse IP publique et d’Azure Load Balancer vaut **4 minutes** par défaut, jusqu’à 100 minutes pour les règles d’équilibrage ([délai d’inactivité](https://learn.microsoft.com/azure/load-balancer/load-balancer-tcp-idle-timeout)). Le ping WebSocket envoyé par le serveur toutes les 30 s garde la connexion active ; la commande `public-ip update` ci-dessus porte tout de même le délai à 30 minutes, par marge. Pour une base hautement disponible : `--high-availability ZoneRedundant` (offre General Purpose).

## Variante Application Gateway v2

Application Gateway prend en charge WebSocket sans réglage. **Le délai de requête des paramètres de backend s’applique aussi à la session WebSocket** ([WebSocket](https://learn.microsoft.com/azure/application-gateway/application-gateway-websocket)) : 20 s par défaut, jusqu’à 86 400 s pour un backend privé ([paramètres de backend](https://learn.microsoft.com/azure/application-gateway/configuration-http-settings)).

```sh
az network application-gateway http-settings update -g ev-supervision \
  --gateway-name <agw> -n ev-server --timeout 3600 --connection-draining-timeout 60
```

**Profil 2.** `OCPP_TRUSTED_PROXIES` reçoit la plage du sous-réseau dédié à Application Gateway. La passerelle ajoute `adresse:port` à `X-Forwarded-For`, forme acceptée par le serveur. `APP_TRUSTED_PROXIES` reçoit ce même sous-réseau et les adresses du tableau de bord.

**Profil 3.** Utilisez le mode strict : une connexion sans certificat valide reçoit une erreur 400 avant d’atteindre le serveur. Le mode passthrough ne vérifie rien et est interdit ([authentification mutuelle](https://learn.microsoft.com/azure/application-gateway/mutual-authentication-overview)). Le profil SSL se place sur l’écouteur multisite de `OCPP_HOST` seulement.

```sh
az network application-gateway client-cert add -g ev-supervision --gateway-name <agw> \
  -n ocpp-bornes --data ocpp-client-ca.cer
az network application-gateway ssl-profile add -g ev-supervision --gateway-name <agw> \
  -n ocpp-mtls --trusted-client-certificates ocpp-bornes \
  --policy-type Predefined --policy-name AppGwSslPolicy20220101

az network application-gateway rewrite-rule set create -g ev-supervision --gateway-name <agw> -n ocpp-mtls
az network application-gateway rewrite-rule create -g ev-supervision --gateway-name <agw> \
  --rule-set-name ocpp-mtls -n en-tetes --sequence 100 \
  --request-headers "X-Client-Cert={var_client_certificate}"

az network application-gateway rewrite-rule set create -g ev-supervision --gateway-name <agw> -n app
az network application-gateway rewrite-rule create -g ev-supervision --gateway-name <agw> \
  --rule-set-name app -n en-tetes --sequence 100 \
  --request-headers "X-Client-Cert="
```

Associez `ocpp-mtls` (profil SSL et jeu de réécriture) à l’écouteur et à la règle de `OCPP_HOST`, `app` à la règle de `APP_HOST`. Une valeur vide supprime l’en-tête ([réécriture d’en-têtes](https://learn.microsoft.com/azure/application-gateway/rewrite-http-headers-url)). Les deux écouteurs partagent le sous-réseau de la passerelle : `OCPP_CLIENT_CERT_PROXIES` ne les distingue pas, la suppression sur `APP_HOST` reste donc obligatoire.

```text
OCPP_CLIENT_CERT_HEADER=X-Client-Cert
OCPP_CLIENT_CA=<racine et intermédiaires des bornes>
```

`client_certificate` est le certificat client en PEM. Le fichier de confiance doit contenir la racine (et les intermédiaires), 25 Ko au plus ; ajoutez `OCPP_SIGNING_CA_CERTIFICATE` si le serveur signe les CSR. Le mTLS demande la référence `Standard_v2` ou `WAF_v2`. Une fois la connexion passée en WebSocket, le pare-feu applicatif n’inspecte plus les trames.

## Variante AKS

```sh
az acr create -g ev-supervision -n <registre> --sku Basic
az aks create -g ev-supervision -n ev-supervision --node-count 2 \
  --node-vm-size Standard_D2s_v5 --attach-acr <registre> --network-policy azure \
  --generate-ssh-keys
az acr build -r <registre> -t ev-server:0.0.1 ev-server
az acr build -r <registre> -t ev-dashboard:0.0.1 ev-dashboard
```

Annotez le Service `edge` pour relever le délai d’inactivité de l’équilibreur (en minutes) :

```yaml
service.beta.kubernetes.io/azure-load-balancer-tcp-idle-timeout: "30"
```

`externalTrafficPolicy: Local` conserve l’adresse des bornes. `--network-policy azure` (ou Cilium) fait appliquer la NetworkPolicy fournie.

## Dimensionnement et coûts

| Parc | VM | PostgreSQL |
|---|---|---|
| Essai | `Standard_B2s`, base locale | — |
| Quelques centaines de bornes | `Standard_D2s_v5` | `Standard_B2s` ou `Standard_D2ds_v5` |
| Production exigeante | `Standard_D4s_v5` | General Purpose, haute disponibilité zonale |

Application Gateway v2 est facturé à l’heure et à l’unité de capacité : pour une seule VM, Caddy est nettement moins cher. Estimez avec le [simulateur de prix](https://azure.microsoft.com/pricing/calculator/).

## Pièges connus

- Délai de 20 s des paramètres de backend d’Application Gateway : il coupe les sessions WebSocket.
- Délai d’inactivité de 4 minutes sur IP publique et Load Balancer : à relever si un intermédiaire bloque les pings WebSocket.
- Le port 25 sortant est bloqué sur la plupart des abonnements : Azure Communication Services ou relais externe sur 587.
- Container Apps : ne pas activer plusieurs révisions actives ni plusieurs répliques pour le serveur.
