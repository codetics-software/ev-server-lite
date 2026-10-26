# Google Cloud

Lisez d’abord [l’architecture de référence](README.md). Les commandes utilisent `gcloud` et la région `europe-west9` (Paris) ; remplacez `PROJET`.

## Topologie recommandée

| Besoin | Choix |
|---|---|
| Serveur et tableau de bord | Une VM Compute Engine `e2-standard-2` avec `deploy/docker-compose.yml` (Caddy) |
| Base | Cloud SQL pour PostgreSQL, adresse IP privée |
| Secrets | Secret Manager |
| Images | Artifact Registry (si GKE ou Cloud Run) |
| Profil 3 | Caddy sur la VM, ou Application Load Balancer externe avec mTLS |
| Variante cluster | GKE avec `deploy/kubernetes/` |

**Cloud Run ne convient pas au serveur Java en production.** Le délai d’une requête WebSocket est au plus de 60 minutes : la borne est alors déconnectée et doit se reconnecter ([WebSockets sur Cloud Run](https://docs.cloud.google.com/run/docs/triggering/websockets)). L’affinité est approximative, alors que le serveur doit rester sur une seule instance, et il n’y a pas de mTLS natif. Une instance avec une connexion ouverte est facturée comme active en permanence. Cloud Run reste acceptable pour un essai en profils 1 et 2, avec une seule instance (`--min-instances=1 --max-instances=1 --no-cpu-throttling --timeout=3600`) et des bornes qui se reconnectent d’elles-mêmes. Le tableau de bord, lui, fonctionne bien sur Cloud Run.

L’option « déployer un conteneur à la création de la VM » (`gcloud compute instances create-with-container`) est abandonnée par Google ([annonce](https://docs.cloud.google.com/compute/docs/containers/prepare-for-container-agent-shutdown)) : installez Docker Engine sur une image Debian.

## Étapes : VM et Cloud SQL

```sh
gcloud config set project PROJET
gcloud services enable compute.googleapis.com sqladmin.googleapis.com \
  servicenetworking.googleapis.com secretmanager.googleapis.com

# Accès privé aux services pour Cloud SQL
gcloud compute addresses create google-managed-services-default \
  --global --purpose=VPC_PEERING --prefix-length=16 --network=default
gcloud services vpc-peerings connect --service=servicenetworking.googleapis.com \
  --ranges=google-managed-services-default --network=default

# PostgreSQL
gcloud sql instances create ev-supervision --database-version=POSTGRES_17 \
  --edition=ENTERPRISE --tier=db-custom-1-3840 --region=europe-west9 \
  --network=default --no-assign-ip --storage-auto-increase \
  --backup-start-time=02:00 --enable-point-in-time-recovery
gcloud sql databases create ev_supervision --instance=ev-supervision
DB_PASSWORD=$(openssl rand -base64 24)
gcloud sql users create ev_supervision --instance=ev-supervision --password="$DB_PASSWORD"
gcloud sql instances describe ev-supervision --format='value(ipAddresses[0].ipAddress)'

# Secrets
printf '%s' "$DB_PASSWORD" | gcloud secrets create ev-database-password --data-file=-
openssl rand -base64 32 | tr -d '\n' | gcloud secrets create ev-app-encryption-key --data-file=-

# VM, adresse fixe et pare-feu
gcloud compute addresses create ev-supervision --region=europe-west9
gcloud compute instances create ev-supervision --zone=europe-west9-a \
  --machine-type=e2-standard-2 --image-family=debian-12 --image-project=debian-cloud \
  --boot-disk-size=30GB --address=ev-supervision --tags=ev-supervision \
  --scopes=cloud-platform
gcloud compute firewall-rules create ev-supervision-web --network=default \
  --allow=tcp:80,tcp:443 --target-tags=ev-supervision
```

Donnez au compte de service de la VM le rôle `roles/secretmanager.secretAccessor` sur ces secrets. Pointez `APP_HOST` (et `OCPP_HOST`) vers l’adresse fixe, puis sur la VM : installez Docker Engine, copiez le dépôt et suivez [Machine virtuelle unique](README.md#machine-virtuelle-unique-docker-compose) avec, dans `deploy/.env` :

```text
COMPOSE_PROFILES=
DATABASE_URL=jdbc:postgresql://<IP privée Cloud SQL>:5432/ev_supervision?sslmode=require
DATABASE_USER=ev_supervision
DATABASE_PASSWORD=<gcloud secrets versions access latest --secret=ev-database-password>
```

Pour la haute disponibilité de la base, ajoutez `--availability-type=REGIONAL`. Une VM unique se protège par un instantané planifié du disque et une stratégie de redémarrage automatique (activée par défaut).

## Variante GKE

```sh
gcloud services enable container.googleapis.com artifactregistry.googleapis.com
gcloud artifacts repositories create ev-supervision --repository-format=docker --location=europe-west9
gcloud auth configure-docker europe-west9-docker.pkg.dev
docker build -t europe-west9-docker.pkg.dev/PROJET/ev-supervision/ev-server:0.0.1 ev-server
docker build -t europe-west9-docker.pkg.dev/PROJET/ev-supervision/ev-dashboard:0.0.1 ev-dashboard
docker push europe-west9-docker.pkg.dev/PROJET/ev-supervision/ev-server:0.0.1
docker push europe-west9-docker.pkg.dev/PROJET/ev-supervision/ev-dashboard:0.0.1
gcloud container clusters create-auto ev-supervision --region=europe-west9
```

Puis [Kubernetes](README.md#kubernetes). Le Service `edge` crée un équilibreur réseau à transfert direct : TLS et mTLS restent dans Caddy, et `externalTrafficPolicy: Local` conserve l’adresse des bornes. `OCPP_TRUSTED_PROXIES` et `APP_TRUSTED_PROXIES` visent la plage des pods du cluster (`gcloud container clusters describe ev-supervision --region=europe-west9 --format='value(clusterIpv4Cidr)'`). Autopilot impose des requêtes de ressources ; celles des manifestes conviennent.

## Variante Application Load Balancer

À utiliser si un équilibreur HTTP(S) géré est exigé (Cloud Armor, certificats gérés).

**Délai du service de backend.** Il vaut 30 s par défaut. Pour WebSocket, il fixe la **durée maximale de la connexion, active ou non** ([GKE, équilibreur externe](https://docs.cloud.google.com/kubernetes-engine/docs/concepts/ingress-xlb)) ; la plage autorisée va jusqu’à 2 147 483 647 s ([services de backend](https://docs.cloud.google.com/load-balancing/docs/backend-service)). Une valeur de 86 400 s provoque une reconnexion par jour :

```sh
gcloud compute backend-services update ev-server --global --timeout=86400
```

**Profil 2.** L’équilibreur ajoute `X-Forwarded-Proto` et ajoute à `X-Forwarded-For` l’adresse du client **puis celle de l’équilibreur** ([en-têtes ajoutés](https://docs.cloud.google.com/load-balancing/docs/https)). Le serveur garde la dernière adresse non approuvée : ajoutez donc l’adresse publique de l’équilibreur aux plages des frontaux Google.

```text
OCPP_TRUSTED_PROXIES=130.211.0.0/22,35.191.0.0/16,<adresse IP de l’équilibreur>/32
```

Pour l’équilibreur régional, remplacez les plages des frontaux par celle du sous-réseau proxy uniquement. Si le tableau de bord est aussi derrière l’équilibreur, `APP_TRUSTED_PROXIES` reçoit ces mêmes plages et les adresses du tableau de bord.

**Profil 3.** Le mTLS se règle sur le proxy HTTPS cible ([mTLS frontal](https://docs.cloud.google.com/load-balancing/docs/mtls)). Utilisez un proxy et une adresse dédiés à `OCPP_HOST`, en mode `REJECT_INVALID` : le mode `ALLOW_INVALID_OR_MISSING_CLIENT_CERT` laisse passer des certificats non vérifiés.

`trust_config.yaml` :

```yaml
name: ocpp-bornes
trustStores:
  - trustAnchors:
      - pemCertificate: |
          -----BEGIN CERTIFICATE-----
          …autorité racine des bornes…
          -----END CERTIFICATE-----
    intermediateCas:
      - pemCertificate: |
          -----BEGIN CERTIFICATE-----
          …autorité intermédiaire ou OCPP_SIGNING_CA_CERTIFICATE…
          -----END CERTIFICATE-----
```

`server_tls_policy.yaml` :

```yaml
name: ocpp-mtls
mtlsPolicy:
  clientValidationMode: REJECT_INVALID
  clientValidationTrustConfig: projects/NUMERO_PROJET/locations/global/trustConfigs/ocpp-bornes
```

```sh
gcloud certificate-manager trust-configs import ocpp-bornes \
  --source=trust_config.yaml --location=global
gcloud network-security server-tls-policies import ocpp-mtls \
  --source=server_tls_policy.yaml --location=global
gcloud compute target-https-proxies create ev-ocpp-mtls --global \
  --url-map=ev-ocpp --certificate-manager-certificates=ocpp-cert \
  --server-tls-policy=ocpp-mtls
gcloud compute backend-services update ev-server-mtls --global --timeout=86400 \
  --custom-request-header='X-Client-Cert:{client_cert_leaf},{client_cert_chain}'
```

```text
OCPP_CLIENT_CERT_HEADER=X-Client-Cert
```

Les en-têtes ajoutés par l’équilibreur **remplacent** ceux du client portant le même nom ([en-têtes personnalisés](https://docs.cloud.google.com/load-balancing/docs/https/custom-headers)). `client_cert_leaf` et `client_cert_chain` suivent la RFC 9440 et ne sont remplis que pour une chaîne validée ; ils sont vidés au-delà de 16 Ko. Limites du mTLS frontal : pas de contrôle de révocation, 10 certificats et 16 Ko par chaîne, 10 s pour présenter le certificat.

Sur le service de backend de `APP_HOST`, ajoutez `--custom-request-header='X-Client-Cert:'` pour écraser toute valeur fournie par un client. Les deux proxys partagent les plages des frontaux Google : `OCPP_CLIENT_CERT_PROXIES` ne les distingue pas, cet écrasement reste donc obligatoire.

## Dimensionnement et coûts

| Parc | VM | Cloud SQL |
|---|---|---|
| Essai | `e2-small` (2 Go), sans Cloud SQL (`local-db`) | — |
| Jusqu’à quelques centaines de bornes | `e2-standard-2` (8 Go) | `db-custom-1-3840`, zonal |
| Production exigeante | `e2-standard-4` | `db-custom-2-7680`, régional |

Les principaux postes sont la VM, l’instance Cloud SQL (facturée en continu), l’adresse IP fixe et, en variante, l’équilibreur et le cluster GKE. Estimez avec le [simulateur de prix](https://cloud.google.com/products/calculator).

## Pièges connus

- Délai de 30 s du service de backend : connexions WebSocket coupées toutes les 30 s.
- Sans l’adresse de l’équilibreur dans `OCPP_TRUSTED_PROXIES`, toutes les bornes partagent cette adresse pour la limite de connexions.
- Les sondes de santé de l’équilibreur viennent de `130.211.0.0/22` et `35.191.0.0/16` : autorisez-les dans le pare-feu vers le port des backends.
- Le port 25 sortant est bloqué : relais SMTP sur 587.
- Cloud SQL `db-f1-micro` et `db-g1-small` limitent les connexions et ne sont pas couverts par le SLA : évitez-les en production.
