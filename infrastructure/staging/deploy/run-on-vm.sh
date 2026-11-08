#!/bin/bash
# Runs a shell script on the staging VM with SSM Run Command and waits for its end (Phase 7 plan PR-68).
# Used by .github/workflows/deploy-staging.yml with the deployment role (ssm:SendCommand, GetCommandInvocation).
#   run-on-vm.sh <instance id> <script file> <output file>
# Prints the script's output; exits 0 only when the command succeeded. The output file keeps its stdout.
set -euo pipefail

instance=$1 script=$2 out=$3
deadline=$((SECONDS + ${MBIA_RUN_TIMEOUT:-1500}))

params=$(python3 -I -c '
import json, sys
lines = open(sys.argv[1]).read().splitlines()
print(json.dumps({"commands": lines, "executionTimeout": [sys.argv[2]]}))
' "$script" "${MBIA_RUN_TIMEOUT:-1500}")

command_id=$(aws ssm send-command --instance-ids "$instance" --document-name AWS-RunShellScript \
  --comment "mbia staging deployment, run ${GITHUB_RUN_ID:-manual}" --parameters "$params" \
  --query Command.CommandId --output text)
echo "SSM command $command_id on $instance"

status=Pending
while [ "$SECONDS" -lt "$deadline" ]; do
  sleep 10
  # Right after send-command, the invocation may not exist yet.
  status=$(aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance" \
    --query Status --output text 2>/dev/null || echo Pending)
  case $status in
    Pending | InProgress | Delayed) ;;
    *) break ;;
  esac
done

# At most the last 24,000 characters of each stream (SSM limit).
aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance" \
  --query StandardOutputContent --output text >"$out" || true
cat "$out"
aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance" \
  --query StandardErrorContent --output text >&2 || true

echo "SSM command status: $status"
[ "$status" = Success ]
