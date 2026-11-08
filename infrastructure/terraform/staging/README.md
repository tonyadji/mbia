# terraform/staging

AWS resources of the staging / closed-beta environment (ADR-010, `mbia-specs/delivery/phase-7-staging.md` PR-67), region `eu-west-3`. Terraform is planned and applied by the human only; CI runs `fmt` and `validate`.

| File | Resources |
|---|---|
| `versions.tf`, `providers.tf`, `locals.tf` | Terraform `1.16.7`, AWS provider `6.69.1` (pinned, `.terraform.lock.hcl` committed), S3 state, staging names |
| `network.tf` | VPC, two public subnets (two zones, required by RDS), no NAT gateway; security groups of the plan §3.1 |
| `compute.tf`, `vm-init.sh` | EC2 `t4g.medium` (Amazon Linux 2023 arm64, Docker, SSM agent, no SSH key, IMDSv2, encrypted gp3), Elastic IP, instance role of §3.2 |
| `database.tf` | RDS PostgreSQL 18 `db.t4g.micro`, single-AZ, encrypted, not public, backups 7 days, deletion protection |
| `storage.tf` | media bucket: private, encrypted, versioning, CORS of the frontend origin |
| `ecr.tf` | `mbia-backend`, `mbia-keycloak`: immutable tags, scan on push, last 10 images kept |
| `ssm.tf` | names of the VM's settings under `/mbia/staging/` (values set by the human) |
| `logs.tf` | CloudWatch Logs group `/mbia/staging`, 30 days |

### Network and access (plan §3.1)

| Security group | Inbound | Outbound |
|---|---|---|
| `mbia-staging-vm` | TCP 80, TCP 443, UDP 443 (HTTP/3) from the Internet | TCP 443 (AWS APIs, Let's Encrypt, container images), TCP 587 (SES SMTP), TCP 5432 to `mbia-staging-rds` |
| `mbia-staging-rds` | TCP 5432 from `mbia-staging-vm` | none |

No port 22: the human reaches the VM with SSM Session Manager and port forwarding. The VPC's default security group is emptied.

### What stays outside Terraform

- the state bucket (bootstrap, below);
- the RDS master password, an SSM SecureString outside `/mbia/staging/` that the VM cannot read; Terraform reads it as an ephemeral value and passes it as a write-only argument: it is in neither the state nor the plan;
- the values of the SSM parameters, the SES SMTP access key, the AWS Budgets alert and the email subscription of the SES feedback topic: they hold secrets or a real email address.

## Prerequisites

- Terraform `1.16.7` exactly (`brew install hashicorp/tap/terraform`, or `tfenv install 1.16.7`).
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

## Plan and apply

```bash
cd infrastructure/terraform/staging
terraform init
terraform plan -out staging.tfplan
terraform apply staging.tfplan
```

Before applying, read the plan:

- the last line reads `Plan: N to add, 0 to change, 0 to destroy` on the first apply; later, `0 to destroy` and no `must be replaced`, unless the change asks for it;
- RDS and the media bucket carry `prevent_destroy`: a plan that would destroy or replace them fails instead;
- no IAM statement other than those of the plan §3.2, no security group rule other than the table above.

Then: `terraform output` gives the instance id, the RDS endpoint, the media bucket and its origin for the CSP, the ECR URLs and the names of the SSM parameters. Their values are set by the runbook (PR-68); until then they hold `change-me`.

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
