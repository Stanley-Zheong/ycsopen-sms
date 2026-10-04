#!/bin/sh
set -u

BASE_URL="${BASE_URL:-http://core:8080}"
INTERVAL_SECONDS="${INTERVAL_SECONDS:-900}"
BATCH_SIZE="${BATCH_SIZE:-3}"
USERNAME="${USERNAME:-tenant_admin}"
PASSWORD="${PASSWORD:-Admin@123456}"
TEMPLATE_IDS="${TEMPLATE_IDS:-2 1}"
SIGN_IDS="${SIGN_IDS:-2 1}"

log() {
  printf '%s ycsopen-sms-simulator %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*"
}

json_value() {
  key="$1"
  sed -n "s/.*\"$key\":\"\\([^\"]*\\)\".*/\\1/p"
}

login() {
  curl -sS \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" \
    "$BASE_URL/api/v1/console/auth/login" |
    json_value accessToken
}

send_attempt() {
  token="$1"
  submit_id="$2"
  phone="$3"
  code="$4"
  template_id="$5"
  sign_id="$6"
  payload="{\"submitId\":\"$submit_id\",\"phoneNumber\":\"$phone\",\"templateId\":\"$template_id\",\"signId\":\"$sign_id\",\"templateParams\":{\"code\":\"$code\"},\"callbackUrl\":null}"
  curl -sS \
    -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' \
    -d "$payload" \
    "$BASE_URL/api/v1/console/tenant/send"
}

send_one() {
  token="$1"
  suffix="$2"
  phone="$3"
  code="$4"
  submit_id="sim-$(date -u '+%Y%m%dT%H%M%SZ')-${suffix}"
  for template_id in $TEMPLATE_IDS; do
    for sign_id in $SIGN_IDS; do
      response="$(send_attempt "$token" "$submit_id" "$phone" "$code" "$template_id" "$sign_id" || true)"
      log "sent submitId=$submit_id phone=$phone templateId=$template_id signId=$sign_id response=$response"
      echo "$response" | grep -q '"code":200' && return 0
    done
  done
  return 1
}

send_cycle() {
  token="$(login || true)"
  if [ -z "$token" ]; then
    log "login-error empty-token"
    return 1
  fi

  log "cycle-start batchSize=$BATCH_SIZE"
  send_one "$token" "1" "13800138000" "246810" || return 1
  [ "$BATCH_SIZE" -lt 2 ] || send_one "$token" "2" "13800138001" "135790" || return 1
  [ "$BATCH_SIZE" -lt 3 ] || send_one "$token" "3" "13800138002" "864200" || return 1
  log "cycle-complete"
}

log "simulator-start interval=${INTERVAL_SECONDS}s base=${BASE_URL}"
while true; do
  send_cycle || log "cycle-error"
  sleep "$INTERVAL_SECONDS"
done
