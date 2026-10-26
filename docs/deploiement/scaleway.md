# Scaleway

Lisez d’abord [l’architecture de référence](README.md). Les commandes utilisent la CLI `scw` et la région `fr-par` ; remplacez les identifiants entre chevrons.

## Topologie recommandée

| Besoin | Choix |
|---|---|
| Serveur et tableau de bord | Une Instance `PRO2-XXS` (2 vCPU, 8 Go) ou `PLAY2-SMALL` avec `deploy/docker-compose.yml` et une IP flexible |
| Base | Managed Database for PostgreSQL, rattachée à un Private Network |
| Secrets | Secret Manager |
| Images | Container Registry (si Kapsule) |
| Profil 3 | Caddy sur l’Instance |
| Variante cluster | Kapsule avec `deploy/kubernetes/` |

Le Load Balancer Scaleway ne documente pas de vérification de certificat client en frontal : le profil 3 se termine dans Caddy, l’équilibreur restant en TCP. Pour une seule Instance, une IP flexible suffit et évite un saut supplémentaire.

**Serverless Containers ne convient pas au serveur Java.** Une requête dure au plus 60 minutes et une instance accepte au plus 80 requêtes simultanées ([limites](https://www.scaleway.com/en/docs/serverless-containers/reference-content/containers-limitations/)) : chaque borne occupant une requête permanente, une instance unique plafonnerait à 80 bornes. Le tableau de bord peut y être hébergé.

## Étapes : Instance et PostgreSQL

```sh
scw vpc private-network create name=ev-supervision region=fr-par

DB_PASSWORD="$(openssl rand -base64 24)A1!"
scw rdb instance create name=ev-supervision engine=PostgreSQL-16 node-type=DB-DEV-S \
  is-ha-cluster=false user-name=ev_supervision password="$DB_PASSWORD" \
  volume-type=sbs_5k volume-size=20GB disable-backup=false backup-same-region=false \
  init-endpoints.0.private-network.private-network-id=<private-network> \
  init-endpoints.0.private-network.enable-ipam=true region=fr-par
scw rdb database create instance-id=<rdb> name=ev_supervision region=fr-par

scw secret secret create name=ev-database-password region=fr-par
scw secret version create secret-id=<secret> data="$DB_PASSWORD" region=fr-par

scw instance server create type=PRO2-XXS image=debian_bookworm name=ev-supervision \
  ip=new zone=fr-par-1
scw instance private-nic create server-id=<instance> private-network-id=<private-network> zone=fr-par-1
```

Les noms de version de moteur, de type de nœud et de volume évoluent : vérifiez-les avec `scw rdb node-type list` et `scw rdb engine list`. Restreignez le groupe de sécurité de l’Instance aux ports 22 (depuis votre réseau), 80 et 443.

Sur l’Instance, installez Docker Engine et suivez [Machine virtuelle unique](README.md#machine-virtuelle-unique-docker-compose) avec :

```text
COMPOSE_PROFILES=
DATABASE_URL=jdbc:postgresql://<IP privée de la base>:5432/ev_supervision?sslmode=require
DATABASE_USER=ev_supervision
DATABASE_PASSWORD=<scw secret version access secret-id=<secret> revision=latest raw=true>
```

Pour la haute disponibilité de la base : `is-ha-cluster=true` et un type de nœud de production.

## Profils 2 et 3 derrière un Load Balancer

Si un Load Balancer est nécessaire (plusieurs adresses, IP stable indépendante de l’Instance), configurez :

- un frontal et un backend **TCP** sur 443 et 80, vers Caddy ;
- le protocole PROXY v2 sur le backend, et dans Caddy :

```caddyfile
{
	servers {
		listener_wrappers {
			proxy_protocol {
				allow <plage privée du Load Balancer>
			}
			tls
		}
	}
}
```

- le délai de tunnel au-delà du battement. Il vaut 900 000 ms par défaut et prime sur les délais client et serveur une fois le WebSocket établi ([backends](https://www.scaleway.com/en/docs/load-balancer/reference-content/configuring-backends/)).

Avec un backend HTTP, le Load Balancer transmet `X-Forwarded-For` ; vérifiez la présence de `X-Forwarded-Proto` avant de compter sur le profil 2, et réservez le profil 3 au montage TCP ci-dessus.

## Variante Kapsule

```sh
scw registry namespace create name=ev-supervision region=fr-par
docker login rg.fr-par.scw.cloud/ev-supervision -u nologin --password-stdin <<< "$SCW_SECRET_KEY"
docker build -t rg.fr-par.scw.cloud/ev-supervision/ev-server:0.0.1 ev-server
docker build -t rg.fr-par.scw.cloud/ev-supervision/ev-dashboard:0.0.1 ev-dashboard
docker push rg.fr-par.scw.cloud/ev-supervision/ev-server:0.0.1
docker push rg.fr-par.scw.cloud/ev-supervision/ev-dashboard:0.0.1
```

Annotez le Service `edge` ([annotations](https://github.com/scaleway/scaleway-cloud-controller-manager/blob/master/docs/loadbalancer-annotations.md)) et activez le `proxy_protocol` de Caddy :

```yaml
service.beta.kubernetes.io/scw-loadbalancer-proxy-protocol-v2: "true"
service.beta.kubernetes.io/scw-loadbalancer-timeout-tunnel: "1h"
service.beta.kubernetes.io/scw-loadbalancer-timeout-client: "1h"
```

Les réglages faits dans la console sur un Load Balancer créé par Kapsule sont écrasés par le contrôleur : passez toujours par les annotations.

## Dimensionnement et coûts

| Parc | Instance | Base |
|---|---|---|
| Essai | `PLAY2-SMALL`, base locale | — |
| Quelques centaines de bornes | `PRO2-XXS` | `DB-DEV-M` |
| Production exigeante | `PRO2-S` | `DB-PRO2-XXS` en haute disponibilité |

Les offres `DEV` et `PLAY2` partagent le processeur : réservez-les aux essais. Estimez avec le [calculateur de prix](https://www.scaleway.com/en/pricing/).

## Pièges connus

- Serverless Containers : 80 requêtes simultanées par instance et 60 minutes par requête.
- Délai de tunnel du Load Balancer trop court : reconnexions périodiques des bornes.
- Protocole PROXY activé d’un seul côté : toutes les connexions échouent.
- Le port 25 sortant est bloqué par défaut : Transactional Email en SMTP sur 587.
