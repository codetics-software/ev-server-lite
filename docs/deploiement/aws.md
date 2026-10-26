# AWS

Lisez d’abord [l’architecture de référence](README.md). Les commandes utilisent l’AWS CLI v2 et la région `eu-west-3` (Paris) ; remplacez `COMPTE` et les identifiants entre chevrons.

## Topologie recommandée

| Besoin | Choix |
|---|---|
| Serveur et tableau de bord | Une instance EC2 `t3.medium` (ou `t4g.medium`, Arm) avec `deploy/docker-compose.yml` et une adresse Elastic IP |
| Base | Amazon RDS for PostgreSQL, sous-réseaux privés |
| Secrets | AWS Secrets Manager |
| Images | Amazon ECR (si ECS ou EKS) |
| Profil 3 | Caddy sur l’instance, ou NLB TCP devant Caddy |
| Variante gérée | ECS Fargate derrière un Application Load Balancer, profils 1 et 2 |
| Variante cluster | EKS avec `deploy/kubernetes/` et un NLB |

## Étapes : EC2 et RDS

```sh
aws ec2 create-security-group --group-name ev-supervision-web \
  --description "Supervision : HTTP et HTTPS" --vpc-id <vpc>
aws ec2 authorize-security-group-ingress --group-id <sg-web> \
  --ip-permissions IpProtocol=tcp,FromPort=443,ToPort=443,IpRanges='[{CidrIp=0.0.0.0/0}]' \
                   IpProtocol=tcp,FromPort=80,ToPort=80,IpRanges='[{CidrIp=0.0.0.0/0}]'
aws ec2 create-security-group --group-name ev-supervision-db \
  --description "Supervision : PostgreSQL" --vpc-id <vpc>
aws ec2 authorize-security-group-ingress --group-id <sg-db> \
  --protocol tcp --port 5432 --source-group <sg-web>

aws rds create-db-subnet-group --db-subnet-group-name ev-supervision \
  --db-subnet-group-description "Supervision" --subnet-ids <privé-a> <privé-b>
aws rds create-db-instance --db-instance-identifier ev-supervision \
  --engine postgres --engine-version 17 --db-instance-class db.t4g.small \
  --allocated-storage 20 --storage-type gp3 --db-name ev_supervision \
  --master-username ev_supervision --manage-master-user-password \
  --db-subnet-group-name ev-supervision --vpc-security-group-ids <sg-db> \
  --no-publicly-accessible --backup-retention-period 7 --storage-encrypted \
  --deletion-protection
aws rds describe-db-instances --db-instance-identifier ev-supervision \
  --query 'DBInstances[0].[Endpoint.Address,MasterUserSecret.SecretArn]'

aws secretsmanager create-secret --name ev-supervision/app-encryption-key \
  --secret-string "$(openssl rand -base64 32)"

aws ec2 run-instances --image-id resolve:ssm:/aws/service/debian/release/12/latest/amd64 \
  --instance-type t3.medium --subnet-id <public-a> --security-group-ids <sg-web> \
  --block-device-mappings 'DeviceName=/dev/xvda,Ebs={VolumeSize=30,VolumeType=gp3,Encrypted=true}' \
  --iam-instance-profile Name=<profil-lecture-secrets> \
  --metadata-options HttpTokens=required \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=ev-supervision}]'
aws ec2 allocate-address --domain vpc
aws ec2 associate-address --instance-id <instance> --allocation-id <eipalloc>
```

`--manage-master-user-password` place le mot de passe dans Secrets Manager. Le profil de l’instance a besoin de `secretsmanager:GetSecretValue` sur ces deux secrets. Sur l’instance, installez Docker Engine et suivez [Machine virtuelle unique](README.md#machine-virtuelle-unique-docker-compose) avec :

```text
COMPOSE_PROFILES=
DATABASE_URL=jdbc:postgresql://<point de terminaison RDS>:5432/ev_supervision?sslmode=require
DATABASE_USER=ev_supervision
DATABASE_PASSWORD=<aws secretsmanager get-secret-value --secret-id <arn> --query SecretString>
```

Le secret RDS est un document JSON : extrayez le champ `password`. Ajoutez `--multi-az` à RDS pour une base hautement disponible.

## Variante ECS Fargate et ALB (profils 1 et 2)

- Une tâche par service. Pour le serveur, `desiredCount=1` et `--deployment-configuration minimumHealthyPercent=0,maximumPercent=100`, sinon ECS démarre une seconde instance pendant le déploiement.
- Groupe cible du serveur : contrôle de santé `/actuator/health/readiness`, port 8080 ; règle d’écouteur `/ocpp/*` et `/ocpi/*` vers ce groupe, le reste vers le tableau de bord.
- **Délai d’inactivité de l’ALB : 60 s par défaut**, de 1 à 4 000 s ([attributs de l’ALB](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/edit-load-balancer-attributes.html)). Portez-le à 3 600 s :

```sh
aws elbv2 modify-load-balancer-attributes --load-balancer-arn <alb> \
  --attributes Key=idle_timeout.timeout_seconds,Value=3600
```

- L’ALB ajoute `X-Forwarded-Proto` et l’adresse du client à `X-Forwarded-For` (mode `append` par défaut). `OCPP_TRUSTED_PROXIES` reçoit les plages des sous-réseaux de l’ALB ; `APP_TRUSTED_PROXIES` les mêmes plages et celles des tâches du tableau de bord.
- Le tableau de bord joint le serveur par Service Connect ou par un ALB interne : `EV_SERVER_URL=http://server.ev-supervision:8080`.
- Les variables secrètes passent par `secrets` dans la définition de tâche (Secrets Manager ou Parameter Store).

## Profil 3

**Recommandé : NLB TCP devant Caddy.** Le NLB transmet TLS sans le déchiffrer ; Caddy (`require_and_verify`) vérifie le certificat et écrit `X-Client-Cert`. Le délai d’inactivité TCP d’un écouteur NLB est de 350 s par défaut, réglable de 60 à 6 000 s ([NLB](https://docs.aws.amazon.com/elasticloadbalancing/latest/network/update-idle-timeout.html)) :

```sh
aws elbv2 modify-listener-attributes --listener-arn <écouteur-tcp-443> \
  --attributes Key=tcp.idle_timeout.seconds,Value=3600
```

Avec des cibles de type `instance`, l’adresse du client est conservée. Avec des cibles `ip` (Fargate, EKS), activez le protocole PROXY v2 sur le groupe cible et dans Caddy.

**ALB en mode `verify`.** L’ALB vérifie la chaîne contre un magasin de confiance et transmet le certificat final dans `X-Amzn-Mtls-Clientcert-Leaf`, en PEM encodé URL ([mTLS de l’ALB](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/mutual-authentication.html)). Le mode `passthrough` est interdit : l’ALB n’y vérifie rien.

```sh
aws s3 cp ocpp-client-ca.pem s3://<compartiment>/ocpp-client-ca.pem
aws elbv2 create-trust-store --name ocpp-bornes \
  --ca-certificates-bundle-s3-bucket <compartiment> \
  --ca-certificates-bundle-s3-key ocpp-client-ca.pem
aws elbv2 create-listener --load-balancer-arn <alb-ocpp> --protocol HTTPS --port 443 \
  --certificates CertificateArn=<certificat ACM de OCPP_HOST> \
  --ssl-policy ELBSecurityPolicy-TLS13-1-2-2021-06 \
  --mutual-authentication Mode=verify,TrustStoreArn=<trust-store>,IgnoreClientCertificateExpiry=false \
  --default-actions Type=forward,TargetGroupArn=<tg-server>
aws elbv2 modify-load-balancer-attributes --load-balancer-arn <alb-ocpp> \
  --attributes Key=idle_timeout.timeout_seconds,Value=3600
```

```text
OCPP_CLIENT_CERT_HEADER=X-Amzn-Mtls-Clientcert-Leaf
OCPP_CLIENT_CA=<racine et intermédiaires des bornes>
```

Le fichier du magasin de confiance est en PEM, sans ligne vide ; ajoutez-y `OCPP_SIGNING_CA_CERTIFICATE` si le serveur signe les CSR des bornes. La reprise de session TLS n’est pas prise en charge en mTLS.

La documentation AWS ne précise pas si l’ALB supprime un en-tête `X-Amzn-Mtls-*` envoyé par le client sur un écouteur sans mTLS. Placez donc l’ALB de `OCPP_HOST` dans des sous-réseaux qui lui sont propres, sans écouteur sans mTLS, et limitez la lecture de l’en-tête à ces sous-réseaux :

```text
OCPP_TRUSTED_PROXIES=<sous-réseaux de l’ALB principal>,<sous-réseaux de l’ALB mTLS>
OCPP_CLIENT_CERT_PROXIES=<sous-réseaux de l’ALB mTLS>
```

Un en-tête forgé arrivant par l’ALB principal est alors ignoré. Contrôlez-le avec la [vérification 4](README.md#vérifications-après-mise-en-service).

## Variante EKS

```sh
aws ecr create-repository --repository-name ev-server
aws ecr create-repository --repository-name ev-dashboard
aws ecr get-login-password | docker login --username AWS --password-stdin COMPTE.dkr.ecr.eu-west-3.amazonaws.com
```

Avec l’AWS Load Balancer Controller, annotez le Service `edge` :

```yaml
service.beta.kubernetes.io/aws-load-balancer-type: external
service.beta.kubernetes.io/aws-load-balancer-nlb-target-type: ip
service.beta.kubernetes.io/aws-load-balancer-scheme: internet-facing
service.beta.kubernetes.io/aws-load-balancer-proxy-protocol: "*"
```

puis activez `proxy_protocol` dans le Caddyfile et réglez le délai TCP de l’écouteur comme ci-dessus.

## Dimensionnement et coûts

| Parc | Calcul | Base |
|---|---|---|
| Essai | `t3.small`, base locale | — |
| Quelques centaines de bornes | `t3.medium` ou `t4g.medium` | `db.t4g.small`, mono-AZ |
| Production exigeante | `m7i.large` ou `m7g.large` | `db.m7g.large`, Multi-AZ |

Les instances `t3`/`t4g` fonctionnent par crédits de processeur : surveillez `CPUCreditBalance` ou passez en mode `unlimited`. Un ALB ou un NLB ajoute un coût horaire et à l’usage ; une Elastic IP associée reste facturée comme adresse IPv4 publique. Estimez avec le [simulateur de prix](https://calculator.aws/).

## Pièges connus

- Délai d’inactivité de l’ALB à 60 s par défaut : à relever.
- ECS avec `minimumHealthyPercent=100` lance deux serveurs pendant un déploiement.
- `X-Forwarded-For` traité en mode `preserve` ou `remove` : l’adresse réelle des bornes est perdue.
- Le port 25 sortant d’EC2 est limité : utilisez Amazon SES en SMTP sur 587.
- RDS impose TLS par défaut sur PostgreSQL récent : gardez `sslmode=require`.
