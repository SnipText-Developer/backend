#!/usr/bin/env bash
# 운영 서버에서 앱을 지정한 이미지 태그로 교체한다 (설계: docs/deploy-design.md 3절).
#
#   사용법: deploy.sh <이미지 태그(커밋 해시)>
#
# 1. 파라미터 저장소 /sniptext/prod/* → /opt/sniptext/.env (권한 600)
#    - 값이 CHANGE_ME인 비밀이 남아 있으면 아무것도 바꾸지 않고 멈춘다
#    - DB_MASTER_PASSWORD(관리 작업 전용)는 옮기지 않는다
# 2. 이 스크립트 옆의 compose.prod.yaml·Caddyfile을 /opt/sniptext로 복사하고 이미지를 받는다
# 3. 앱 컨테이너를 교체하고 127.0.0.1:8080/actuator/health를 최대 180초 확인한다
# 4. 실패하면 직전 설정(.env·compose·Caddyfile)과 이전 태그로 다시 띄우고 실패 코드로 끝낸다
#
# 종료 코드: 0 성공 / 1 실패, 이전 태그로 되돌림(또는 바꾼 것 없음) / 2 잘못된 입력·설정 / 3 되돌리기도 실패
#
# 출력은 SSM 명령 결과로 GitHub Actions 로그(공개 저장소)에 남는다.
# 비밀값·토큰·앱 로그를 출력하지 않는다. set -x와 `docker compose config`를 쓰지 않는다.
set -euo pipefail
umask 077

readonly APP_HOME="${SNIPTEXT_HOME:-/opt/sniptext}"
readonly PARAM_PATH="/sniptext/prod"
readonly HEALTH_URL="http://127.0.0.1:8080/actuator/health"
readonly HEALTH_TIMEOUT_SECONDS=180
readonly EXCLUDED_PARAMS=("DB_MASTER_PASSWORD")
readonly CONFIG_FILES=(".env" "compose.prod.yaml" "Caddyfile")

SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SRC_DIR

log() { printf '[deploy %s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
die() {
  local code="$1"
  shift
  log "실패: $*"
  exit "$code"
}

compose() {
  docker compose --project-directory "$APP_HOME" -f "$APP_HOME/compose.prod.yaml" "$@"
}

# ---------- 입력 확인 ----------
readonly TAG="${1:-}"
[[ -n "$TAG" ]] || die 2 "사용법: deploy.sh <이미지 태그>"
# 도커 태그 형식. latest는 어떤 커밋이 떠 있는지 알 수 없어 받지 않는다 (설계 3절)
[[ "$TAG" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]] || die 2 "태그 형식이 잘못됐습니다: $TAG"
[[ "$TAG" != "latest" ]] || die 2 "latest로는 배포하지 않습니다. 커밋 해시를 넘기세요"

for f in compose.prod.yaml Caddyfile; do
  [[ -f "$SRC_DIR/$f" ]] || die 2 "$SRC_DIR/$f 가 없습니다"
done
[[ -d "$APP_HOME" && -w "$APP_HOME" ]] || die 2 "$APP_HOME 에 쓸 수 없습니다 (root로 실행하세요)"

# 동시에 두 배포가 돌지 않게 한다
exec 9>"$APP_HOME/.deploy.lock"
flock -n 9 || die 2 "다른 배포가 진행 중입니다"

# ---------- 1. 파라미터 → .env.next ----------
# aws CLI는 리전이 없으면 실패한다. SSM 명령 실행 환경에는 AWS_REGION이 없을 수 있어 인스턴스 정보에서 읽는다 (IMDSv2)
if [[ -z "${AWS_REGION:-}" ]]; then
  imds_token="$(curl -fsS --max-time 2 -X PUT http://169.254.169.254/latest/api/token \
    -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')" || die 2 "인스턴스 정보(IMDS)를 읽지 못했습니다. AWS_REGION을 지정하세요"
  AWS_REGION="$(curl -fsS --max-time 2 -H "X-aws-ec2-metadata-token: $imds_token" \
    http://169.254.169.254/latest/meta-data/placement/region)" || die 2 "리전을 읽지 못했습니다"
  unset imds_token
  export AWS_REGION
fi

raw_params="$APP_HOME/.params.tmp"
env_next="$APP_HOME/.env.next"
trap 'rm -f "$raw_params" "$env_next"' EXIT

log "파라미터 읽기: $PARAM_PATH/*"
# 값은 화면에 내지 않고 권한 600 임시 파일로만 받는다 (umask 077)
aws ssm get-parameters-by-path --path "$PARAM_PATH" --with-decryption \
  --query 'Parameters[].[Name,Value]' --output text >"$raw_params" \
  || die 1 "파라미터를 읽지 못했습니다 (인스턴스 역할 권한·리전 확인)"

is_excluded() {
  local name="$1" excluded
  for excluded in "${EXCLUDED_PARAMS[@]}"; do
    [[ "$name" == "$excluded" ]] && return 0
  done
  return 1
}

placeholders=()
has_api_domain=false
: >"$env_next"
while IFS=$'\t' read -r full_name value; do
  [[ -n "$full_name" ]] || continue
  key="${full_name#"$PARAM_PATH"/}"
  # 하위 경로·이상한 이름, 또는 값에 줄바꿈이 있어 줄이 갈라진 경우
  [[ "$key" =~ ^[A-Z][A-Z0-9_]*$ ]] || die 2 "환경 변수로 쓸 수 없는 파라미터 이름이 있습니다: $full_name"
  is_excluded "$key" && continue
  if [[ "$value" == "CHANGE_ME" ]]; then
    placeholders+=("$key")
    continue
  fi
  # compose .env에서 작은따옴표 안은 그대로(변수 치환 없이) 읽힌다. 작은따옴표 자체는 넣을 수 없다
  [[ "$value" != *"'"* ]] || die 2 "$key 값에 작은따옴표(')가 있어 .env에 쓸 수 없습니다"
  [[ "$key" != "APP_TAG" ]] || die 2 "APP_TAG는 파라미터로 두지 않습니다 (deploy.sh가 씁니다)"
  [[ "$key" == "API_DOMAIN" ]] && has_api_domain=true
  printf "%s='%s'\n" "$key" "$value" >>"$env_next"
done <"$raw_params"
unset value
rm -f "$raw_params"

if ((${#placeholders[@]} > 0)); then
  die 2 "실제 값이 들어가지 않은 비밀(CHANGE_ME)이 있습니다: ${placeholders[*]}. aws ssm put-parameter --overwrite로 넣은 뒤 다시 실행하세요"
fi
[[ "$has_api_domain" == true ]] || die 2 "$PARAM_PATH/API_DOMAIN 파라미터가 없습니다"
printf "APP_TAG='%s'\n" "$TAG" >>"$env_next"

# ---------- 2. 설정 교체·이미지 받기 ----------
previous_tag=""
[[ -f "$APP_HOME/current_tag" ]] && previous_tag="$(<"$APP_HOME/current_tag")"
log "현재 태그: ${previous_tag:-(없음, 첫 배포)} → 새 태그: $TAG"

# 직전 설정을 .prev로 남긴다. 되돌릴 때 이전 태그와 함께 그대로 쓴다
for f in "${CONFIG_FILES[@]}"; do
  if [[ -f "$APP_HOME/$f" ]]; then
    cp -p "$APP_HOME/$f" "$APP_HOME/$f.prev"
  fi
done

# Caddyfile은 컨테이너에 파일 하나로 마운트돼 있다. mv로 바꾸면 컨테이너가 옛 파일(inode)을 계속 보므로 cp로 덮어쓴다
cp "$SRC_DIR/compose.prod.yaml" "$APP_HOME/compose.prod.yaml"
cp "$SRC_DIR/Caddyfile" "$APP_HOME/Caddyfile"
chmod 600 "$env_next"
mv "$env_next" "$APP_HOME/.env"

restore_previous_config() {
  local f
  for f in "${CONFIG_FILES[@]}"; do
    if [[ -f "$APP_HOME/$f.prev" ]]; then
      cp -p "$APP_HOME/$f.prev" "$APP_HOME/$f"
    fi
  done
}

log "이미지 받기"
if ! compose pull --quiet app caddy; then
  # 아직 컨테이너는 그대로다. 설정만 되돌린다
  if [[ -n "$previous_tag" ]]; then restore_previous_config; fi
  die 1 "이미지를 받지 못했습니다 (태그가 레지스트리에 있는지 확인). 실행 중인 앱은 바꾸지 않았습니다"
fi

# ---------- 3. 교체·상태 확인 ----------
wait_healthy() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
  while ((SECONDS < deadline)); do
    if curl -fsS --max-time 5 "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 3
  done
  return 1
}

start_services() {
  # 앱 교체 때 이전 컨테이너는 stop_grace_period(40초) 안에서 진행 중인 요청을 마치고 내려간다
  compose up -d --remove-orphans caddy app || return 1
  # Caddyfile이 바뀌었으면 반영한다 (바뀌지 않았으면 아무 일도 없다)
  compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile >/dev/null 2>&1 \
    || log "경고: Caddy 설정을 다시 읽지 못했습니다 (docker compose logs caddy 확인)"
}

log "앱 교체: $TAG"
if start_services && wait_healthy; then
  if [[ -n "$previous_tag" && "$previous_tag" != "$TAG" ]]; then
    printf '%s\n' "$previous_tag" >"$APP_HOME/previous_tag"
  fi
  printf '%s\n' "$TAG" >"$APP_HOME/current_tag"
  log "성공: $TAG 상태 확인 UP"

  # 디스크를 위해 현재·이전 태그 말고 앱 이미지를 지운다 (실패해도 배포는 성공)
  keep_previous="$(cat "$APP_HOME/previous_tag" 2>/dev/null || true)"
  app_image="$(docker inspect --format '{{.Config.Image}}' "$(compose ps -q app)" 2>/dev/null || true)"
  image_repo="${app_image%:*}"
  if [[ -n "$app_image" && "$image_repo" != "$app_image" ]]; then
    docker image ls "$image_repo" --format '{{.Tag}}' | while read -r old_tag; do
      if [[ "$old_tag" != "$TAG" && "$old_tag" != "$keep_previous" ]]; then
        docker image rm "$image_repo:$old_tag" >/dev/null 2>&1 || true
      fi
    done || true
  fi
  exit 0
fi

# ---------- 4. 실패 → 되돌리기 ----------
log "새 태그 $TAG 가 ${HEALTH_TIMEOUT_SECONDS}초 안에 UP이 되지 않았습니다"
compose ps app || true
log "원인은 서버에서 확인: cd $APP_HOME && docker compose -f compose.prod.yaml logs --tail 200 app"

if [[ -z "$previous_tag" ]]; then
  die 1 "되돌릴 이전 태그가 없습니다 (첫 배포)"
fi

log "되돌리기: $previous_tag (직전 .env·compose·Caddyfile)"
restore_previous_config
if start_services && wait_healthy; then
  die 1 "$TAG 배포 실패, $previous_tag 로 되돌렸습니다"
fi
die 3 "되돌리기도 실패했습니다. 서버에서 바로 확인하세요"
