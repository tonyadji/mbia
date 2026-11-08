# terraform/staging

AWS resources of the staging / closed-beta environment (ADR-010, `mbia-specs/delivery/phase-7-staging.md` PR-67), region `eu-west-3`. Terraform is planned and applied by the human only; CI runs `fmt` and `validate`.

| File | Resources |
|---|---|
| `versions.tf`, `providers.tf`, `locals.tf` | Terraform `1.17.1`, AWS provider `6.69.1` (pinned, `.terraform.lock.hcl` committed), S3 state, staging names |
| `network.tf` | VPC, two public subnets (two zones, required by RDS), no NAT gateway; security groups of the plan §3.1 |
| `compute.tf`, `vm-init.sh` | EC2 `t4g.medium` (Amazon Linux 2023 arm64, Docker, SSM agent, no SSH key, IMDSv2, encrypted gp3), Elastic IP, instance role of §3.2 |
| `database.tf` | RDS PostgreSQL 18 `db.t4g.micro`, single-AZ, encrypted, not public, backups 7 days, deletion protection |
| `storage.tf` | media bucket: private, encrypted, versioning, CORS of the frontend origin |
| `ecr.tf` | `mbia-backend`, `mbia-keycloak`: immutable tags, scan on push, last 10 images kept |
| `ssm.tf` | names of the VM's settings under `/mbia/staging/` (values set by the human) |
| `logs.tf` | CloudWatch Logs group `/mbia/staging`, 30 days |
| `cdn.tf`, `cloudfront/spa-rewrite.js` | frontend bucket (private, read by the distribution only), CloudFront with Origin Access Control, ACM certificate in us-east-1, deep-link rewrite, cache and headers of `infrastructure/staging/README.md` §Frontend on CloudFront |
| `dns.tf` | zone `lehnade.com` read only; `mbia-staging.lehnade.com` (A, AAAA alias to CloudFront), `api.` and `auth.mbia-staging.lehnade.com` (A to the Elastic IP), ACM validation |
| `email.tf` | SES identity, MAIL FROM, DKIM and DMARC records, feedback topic and its Bounce and Complaint notifications (**imported**); MAIL FROM MX and SPF records (created); SES SMTP user of §3.2 |
| `deploy.tf` | GitHub OIDC provider and deployment role of §3.2 (`repo:tonyadji@19990474/mbia@1387134516:ref:refs/heads/main` only: the immutable subject of the repository, `locals.tf`) |

### Network and access (plan §3.1)

| Security group | Inbound | Outbound |
|---|---|---|
| `mbia-staging-vm` | TCP 80, TCP 443, UDP 443 (HTTP/3) from the Internet | TCP 443 (AWS APIs, Let's Encrypt, container images), TCP 587 (SES SMTP), TCP 5432 to `mbia-staging-rds` |
| `mbia-staging-rds` | TCP 5432 from `mbia-staging-vm` | none |

No port 22: the human reaches the VM with SSM Session Manager and port forwarding. The VPC's default security group is emptied.

### IAM (plan §3.2)

| Principal | Allowed |
|---|---|
| role `mbia-staging-vm` | S3 Get/Put/DeleteObject and ListBucket on the media bucket; ECR pull on the two repositories; read `/mbia/staging/*`; write to `/mbia/staging` logs; the SSM agent's own calls (hand-written: `AmazonSSMManagedInstanceCore` would grant every parameter) |
| role `mbia-staging-deploy` (GitHub OIDC, `main` of `tonyadji/mbia`) | ECR push on the two repositories; list, put and delete in the frontend bucket; create and read invalidations of the distribution; `ssm:SendCommand` of `AWS-RunShellScript` on the VM and `ssm:GetCommandInvocation` |
| user `mbia-staging-ses-smtp` | `ses:SendRawEmail` on the identity `mbia-staging.lehnade.com`, sender `*@mbia-staging.lehnade.com` only |

Actions without resource-level permission are granted on `*`: `ecr:GetAuthorizationToken`, the SSM agent's calls, `ssm:GetCommandInvocation`.

### Imported resources

Created by hand before Terraform (`delivery/phase-7-staging.md` §2.2), brought under Terraform by `import` blocks in `email.tf`, never recreated: the SES identity and its MAIL FROM, the three DKIM CNAME records, the DMARC record, the topic `mbia-staging-ses-feedback` and the identity's Bounce and Complaint notifications. Each carries `prevent_destroy`: a plan that would destroy or replace one of them fails. The `import` blocks stay in the code: once imported, they do nothing.

The MAIL FROM MX and SPF records were published by hand under a wrong name, `mail.mbia-staging.lehnade.com.mbia-staging.lehnade.com` (the full name typed in the console's subdomain field). The SES identity's MAIL FROM domain had the same wrong name: the first plan shows it changed in place to `mail.mbia-staging.lehnade.com`. Terraform creates the records under that name; the wrong ones are deleted by hand after the apply (below).

### What stays outside Terraform

- the state bucket (bootstrap, below);
- the RDS master password, an SSM SecureString outside `/mbia/staging/` that the VM cannot read; Terraform reads it as an ephemeral value and passes it as a write-only argument: it is in neither the state nor the plan;
- the values of the SSM parameters, the SES SMTP access key, the AWS Budgets alert and the email subscription of the SES feedback topic: they hold secrets or a real email address;
- the hosted zone `lehnade.com`, read only.

## Prerequisites

- Terraform `1.17.1` exactly (`brew install hashicorp/tap/terraform`, or `tfenv install 1.17.1`).
- AWS CLI v2, a profile of an administrator of the account (`export AWS_PROFILE=<profile>`), region `eu-west-3`.

## Bootstrap (once)

The state bucket, versioned, encrypted and private. Locking uses a lock file in the bucket (`use_lockfile`): no DynamoDB table.

```bash
aws s3api create-bucket --bucket lehnade-mbia-terraform-state --region eu-west-3 --create-bucket-configuration LocationConstraint=eu-west-3
aws s3api put-bucket-versioning --bucket lehnade-mbia-terraform-state --versioning-configuration Status=Enabled
aws s3api put-bucket-encryption --bucket lehnade-mbia-terraform-state --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
aws s3api put-public-access-block --bucket lehnade-mbia-terraform-state --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

The RDS master password (allowed characters only: no `/`, `@`, `"` nor space):

```bash
aws ssm put-parameter --region eu-west-3 --name /mbia-admin/staging/rds-master-password --type SecureString --value "$(openssl rand -base64 32 | tr -d '/+=')"
```

To rotate it later: change the parameter, then increase `password_wo_version` in `database.tf` and apply.

## Before the first plan: the records to import

The imports expect the records below. List those of the zone:

```bash
ZONE_ID=$(aws route53 list-hosted-zones-by-name --dns-name lehnade.com --query 'HostedZones[0].Id' --output text)
aws route53 list-resource-record-sets --hosted-zone-id "$ZONE_ID" --query "ResourceRecordSets[?contains(Name, 'mbia-staging')].[Name,Type,TTL,join(' ', ResourceRecords[].Value)]" --output text
```

| Name | Type | Value in `email.tf` |
|---|---|---|
| `<token>._domainkey.mbia-staging.lehnade.com` (×3) | CNAME | `<token>.dkim.amazonses.com`, TTL 1800 |
| `_dmarc.mbia-staging.lehnade.com` | TXT | `"v=DMARC1; p=none;"`, TTL 300 |

- A record missing from the list: remove its entry from the `import` block (`ses_dkim` or `ses` in `email.tf`); Terraform then creates it. Otherwise the plan fails with `Cannot import non-existent remote object`.
- A different TTL or value: the plan shows it as an in-place change (`~`). Align `email.tf` with the existing record if it must stay as it is.
- The MAIL FROM behavior on MX failure is `USE_DEFAULT_VALUE` (console default), the notifications without original headers: the same rule applies.

## Plan and apply

```bash
cd infrastructure/terraform/staging
terraform init
terraform plan -out staging.tfplan
terraform apply staging.tfplan
```

Before applying, read the plan:

- the last line reads `Plan: 9 to import, N to add, M to change, 0 to destroy` (9: identity, MAIL FROM, 3 DKIM, DMARC, topic, 2 notifications; the MAIL FROM MX and SPF records are among the additions); every `to change` is an imported resource receiving the project's tags (the identity, the topic) or a value to align (above); no `must be replaced`;
- RDS, the media bucket and every imported resource carry `prevent_destroy`: a plan that would destroy or replace them fails instead;
- no IAM statement other than the table above, no security group rule other than the table above.

Then: `terraform output` gives the instance id, the RDS endpoint, the media bucket and its origin (in the CSP), the frontend bucket, the distribution id, the deployment role ARN (a GitHub variable of PR-68), the SMTP user, the ECR URLs and the names of the SSM parameters. Their values are set by the runbook (PR-68); until then they hold `change-me`.

### After the first apply: the wrong MAIL FROM records

Once the right records exist, delete the two published under the wrong name, then check that SES sees the MAIL FROM domain (`MailFromDomainStatus` `SUCCESS`, within about an hour):

```bash
ZONE_ID=$(aws route53 list-hosted-zones-by-name --dns-name lehnade.com --query 'HostedZones[0].Id' --output text)
aws route53 change-resource-record-sets --hosted-zone-id "$ZONE_ID" --change-batch '{"Changes":[
  {"Action":"DELETE","ResourceRecordSet":{"Name":"mail.mbia-staging.lehnade.com.mbia-staging.lehnade.com.","Type":"MX","TTL":300,"ResourceRecords":[{"Value":"10 feedback-smtp.eu-west-3.amazonses.com"}]}},
  {"Action":"DELETE","ResourceRecordSet":{"Name":"mail.mbia-staging.lehnade.com.mbia-staging.lehnade.com.","Type":"TXT","TTL":300,"ResourceRecords":[{"Value":"\"v=spf1 include:amazonses.com ~all\""}]}}
]}'
aws sesv2 get-email-identity --region eu-west-3 --email-identity mbia-staging.lehnade.com --query MailFromAttributes
```

## Estimated monthly cost

Prices of `eu-west-3`, without VAT, 730 hours.

| Resource | € / month |
|---|---|
| EC2 `t4g.medium` | ~26.5 |
| EBS gp3 30 GB | ~2.6 |
| Public IPv4 (Elastic IP) | ~3.4 |
| RDS `db.t4g.micro` single-AZ | ~13 |
| RDS gp3 20 GB (backups up to the database size are free) | ~2.4 |
| ECR (about 6 GB) | ~0.6 |
| CloudWatch Logs (1–2 GB ingested) | ~0.5–1 |
| S3 media | < 0.5 |
| SSM Parameter Store (standard), KMS `aws/ssm` | 0 |
| CloudFront (always-free tier: 1 TB, 10 M requests), ACM, SES (< 1,000 emails), SNS, frontend bucket | ~0 |
| Route 53: zone `lehnade.com` already paid, queries | ~0 |
| **Total** | **≈ 50** (ADR-010: 40–55) |
