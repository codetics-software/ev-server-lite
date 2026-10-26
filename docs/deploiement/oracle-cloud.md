# Oracle Cloud (OCI)

Lisez d’abord [l’architecture de référence](README.md). Les commandes utilisent la CLI `oci` ; remplacez les OCID entre chevrons. Région conseillée : `eu-paris-1` ou `eu-marseille-1`.

## Topologie recommandée

| Besoin | Choix |
|---|---|
| Serveur et tableau de bord | Une instance Compute `VM.Standard.E5.Flex` (1 OCPU, 8 Go) ou `VM.Standard.A1.Flex` (Arm) avec `deploy/docker-compose.yml` |
| Base | OCI Database with PostgreSQL, sous-réseau privé |
| Secrets | OCI Vault |
| Images | OCI Container Registry (si OKE ou Container Instances) |
| Profil 3 | Caddy sur l’instance, ou écouteur dédié du Load Balancer avec vérification du pair |
| Variante cluster | OKE avec `deploy/kubernetes/` et un Network Load Balancer |

Sur une instance Arm, `docker compose up --build` construit directement les images `arm64`. Pour OKE ou Container Instances, construisez avec `docker buildx build --platform linux/arm64,linux/amd64`.

## Étapes : instance et PostgreSQL

1. Réseau : un VCN avec un sous-réseau public (instance ou Load Balancer) et un sous-réseau privé (base). Liste de sécurité : 80 et 443 depuis Internet vers l’instance, 5432 depuis le sous-réseau public vers le privé.
2. Base : **OCI Database with PostgreSQL**, version 16 ou ultérieure, forme 2 OCPU, sous-réseau privé, sauvegardes quotidiennes avec rétention de 7 jours au moins. Créez ensuite la base `ev_supervision` et un utilisateur propriétaire.
3. Secrets : un coffre OCI Vault, une clé maître, puis les secrets `database-password` et `app-encryption-key`.
4. Instance :

```sh
oci compute instance launch --compartment-id <compartiment> \
  --availability-domain <domaine> --shape VM.Standard.E5.Flex \
  --shape-config '{"ocpus":1,"memoryInGBs":8}' --image-id <image Ubuntu 24.04> \
  --subnet-id <sous-réseau-public> --assign-public-ip true \
  --display-name ev-supervision --ssh-authorized-keys-file ~/.ssh/id_ed25519.pub
```

Les images Oracle Linux et Ubuntu d’OCI filtrent les ports entrants dans le système, en plus de la liste de sécurité : ouvrez 80 et 443 avec `firewall-cmd` (Oracle Linux) ou dans `/etc/iptables/rules.v4` (Ubuntu). Pour lire les secrets depuis l’instance, placez-la dans un groupe dynamique autorisé à `read secret-bundles` dans le compartiment.

Installez Docker Engine et suivez [Machine virtuelle unique](README.md#machine-virtuelle-unique-docker-compose) avec :

```text
COMPOSE_PROFILES=
DATABASE_URL=jdbc:postgresql://<point de terminaison privé>:5432/ev_supervision?sslmode=require
DATABASE_USER=ev_supervision
DATABASE_PASSWORD=<oci secrets secret-bundle get --secret-id <ocid> --query 'data."secret-bundle-content".content' --raw-output | base64 -d>
```

## Variante Load Balancer

**Délai d’inactivité.** 60 s par défaut pour un écouteur HTTP, 300 s pour TCP, 7 200 s au plus. Pour WebSocket, un envoi ne réarme pas le délai de réception et inversement ([délais](https://docs.oracle.com/en-us/iaas/Content/Balance/Reference/connectionreuse.htm)). Le Load Balancer ferme les connexions inactives vers les backends après 300 s ; le serveur ne doit pas les fermer avant 310 s.

```sh
oci lb listener update --load-balancer-id <lb> --listener-name app \
  --default-backend-set-name ev-dashboard --port 443 --protocol HTTP \
  --ssl-certificate-name app-cert --connection-configuration-idle-timeout 3600 \
  --routing-policy-name routage --rule-set-names '["app"]'
```

**Profil 2.** Le Load Balancer ajoute `X-Forwarded-Proto` et ajoute l’adresse de son pair à la fin de `X-Forwarded-For` ; ces en-têtes ne peuvent pas être modifiés par une règle ([en-têtes](https://docs.oracle.com/iaas/Content/Balance/Reference/httpheaders.htm)). `OCPP_TRUSTED_PROXIES` reçoit la plage du sous-réseau du Load Balancer ; `APP_TRUSTED_PROXIES` la même plage et l’adresse de l’instance du tableau de bord. La politique de routage envoie `/ocpp/` et `/ocpi/` vers le jeu de backends du serveur.

**Profil 3.** Activez la vérification du certificat du pair sur un écouteur dédié à `OCPP_HOST` (autre port ou autre Load Balancer : un écouteur porte une seule configuration TLS), puis transmettez le certificat par un jeu de règles ([transfert du certificat client](https://docs.oracle.com/en-us/iaas/Content/Balance/Tasks/client-certificate-forwarding.htm)). Le comportement envers une borne sans certificat n’est pas documenté : n’y raccordez que des bornes en profil 3.

```sh
oci lb rule-set create --load-balancer-id <lb> --name ocpp_mtls \
  --items '[{"action":"REMOVE_HTTP_REQUEST_HEADER","header":"X-Client-Cert"},
            {"action":"ADD_HTTP_REQUEST_HEADER","header":"X-Client-Cert","value":"{oci_lb_client_cert_url_encoded}"}]'
oci lb rule-set create --load-balancer-id <lb> --name app \
  --items '[{"action":"REMOVE_HTTP_REQUEST_HEADER","header":"X-Client-Cert"}]'
oci lb listener update --load-balancer-id <lb> --listener-name ocpp \
  --default-backend-set-name ev-server --port 8443 --protocol HTTP \
  --ssl-certificate-name ocpp-cert --ssl-verify-peer-certificate true --ssl-verify-depth 3 \
  --connection-configuration-idle-timeout 3600 --rule-set-names '["ocpp_mtls"]'
```

```text
OCPP_CLIENT_CERT_HEADER=X-Client-Cert
OCPP_CLIENT_CERT_PROXIES=<sous-réseau du Load Balancer mTLS, s’il est distinct>
OCPP_CLIENT_CA=<racine et intermédiaires des bornes>
```

Avec un Load Balancer dédié au profil 3 dans son propre sous-réseau, `OCPP_CLIENT_CERT_PROXIES` fait ignorer un en-tête forgé passant par le Load Balancer principal. Avec un seul Load Balancer, la règle de suppression sur l’écouteur `app` reste obligatoire.

Le paquet de certificats `ocpp-cert` contient l’autorité des bornes dans son champ « certificat d’autorité » ; ajoutez-y `OCPP_SIGNING_CA_CERTIFICATE` si le serveur signe les CSR. Préférez `{oci_lb_client_cert_url_encoded}` aux variantes PEM brutes. Vérifiez avec un faux en-tête que la valeur du client est bien remplacée ([vérification 4](README.md#vérifications-après-mise-en-service)).

## Variante OKE

```sh
docker login <région>.ocir.io -u '<espace de noms>/<utilisateur>'
docker buildx build --platform linux/amd64,linux/arm64 \
  -t <région>.ocir.io/<espace de noms>/ev-server:0.0.1 --push ev-server
docker buildx build --platform linux/amd64,linux/arm64 \
  -t <région>.ocir.io/<espace de noms>/ev-dashboard:0.0.1 --push ev-dashboard
```

Annotez le Service `edge` pour un Network Load Balancer qui conserve l’adresse source ([équilibreurs OKE](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengcreatingnetworkloadbalancers.htm)) :

```yaml
oci.oraclecloud.com/load-balancer-type: "nlb"
oci-network-load-balancer.oraclecloud.com/is-preserve-source: "true"
```

Le délai d’inactivité TCP par défaut du Network Load Balancer est de 360 s ([NLB](https://docs.oracle.com/en-us/iaas/Content/NetworkLoadBalancer/NetworkLoadBalancers/configure-idle-timeout.htm)), au-delà du battement par défaut.

## Dimensionnement et coûts

| Parc | Instance | PostgreSQL |
|---|---|---|
| Essai | `VM.Standard.A1.Flex` 1 OCPU, 6 Go, base locale | — |
| Quelques centaines de bornes | `VM.Standard.E5.Flex` 1 OCPU, 8 Go | 2 OCPU, 32 Go de mémoire |
| Production exigeante | `VM.Standard.E5.Flex` 2 OCPU, 16 Go | 2 OCPU, deux nœuds |

Un OCPU correspond à deux processeurs virtuels sur x86 et à un cœur sur Ampere. Estimez avec le [simulateur de coûts](https://www.oracle.com/cloud/costestimator.html).

## Pièges connus

- Délai de 60 s d’un écouteur HTTP du Load Balancer.
- Pare-feu du système des images OCI : ports 80 et 443 fermés même si la liste de sécurité les autorise.
- Images `amd64` sur une instance Ampere : `exec format error`.
- Le port 25 sortant est bloqué par défaut : Email Delivery en SMTP sur 587.
