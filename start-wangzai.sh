#!/usr/bin/env bash
set -euo pipefail
umask 077

PROJECT_DIR="$HOME/Project/ai-pet-agent/ai_pet_agent"
CONFIG_DIR="$HOME/.config/wangzai"
ESP32_IP="${ESP32_IP:-10.159.251.60}"

if [[ ! -f "$PROJECT_DIR/pom.xml" ]]; then
  echo "[ERROR] Cannot find $PROJECT_DIR/pom.xml" >&2
  exit 1
fi

mkdir -p "$CONFIG_DIR"
chmod 700 "$CONFIG_DIR"

ensure_token() {
  local file="$1"
  local description="$2"
  local value

  if [[ ! -s "$file" ]]; then
    echo "[INFO] Missing $description; enter it locally (hidden)."
    read -r -s -p "$description: " value
    printf '\n'
    if [[ -z "$value" ]]; then
      echo "[ERROR] Empty token, aborting." >&2
      exit 1
    fi
    printf '%s' "$value" > "$file"
    unset value
  fi
  chmod 600 "$file"
}

ensure_token "$CONFIG_DIR/device-token" "ESP32 device token"
ensure_token "$CONFIG_DIR/api-token" "Wangzai API token"

export ESP32_DEVICE_TOKEN="$(tr -d '\r\n' < "$CONFIG_DIR/device-token")"
export WANGZAI_AUTH_TOKEN="$(tr -d '\r\n' < "$CONFIG_DIR/api-token")"

if [[ -z "$ESP32_DEVICE_TOKEN" || -z "$WANGZAI_AUTH_TOKEN" ]]; then
  echo "[ERROR] One or both token files are empty." >&2
  exit 1
fi

export ESP32_DEVICE_URL="${ESP32_DEVICE_URL:-http://${ESP32_IP}/mock-esp32/expression}"
export ESP32_CONFIRM_BY_ID=true
unset ESP32_VERIFY_AFTER_COMMAND

echo "[INFO] Project: $PROJECT_DIR"
echo "[INFO] ESP32: $ESP32_DEVICE_URL"
echo "[INFO] Tokens loaded (values not printed)."
echo "[INFO] Confirmation by command ID enabled."
echo "[INFO] Launching Spring Boot on configured server port..."

cd "$PROJECT_DIR"

# WANGZAI_LLM_KEY_LOADER_V1
# 自动读取并加载大模型 API Key
LLM_KEY_FILE="$HOME/.config/wangzai/llm-api-key"

if [[ -z "${LLM_API_KEY:-}" ]]; then
    mkdir -p "$HOME/.config/wangzai"
    chmod 700 "$HOME/.config/wangzai"

    if [[ ! -s "$LLM_KEY_FILE" ]]; then
        echo "[INFO] 首次配置 LLM_API_KEY"
        read -r -s -p "请输入 DeepSeek API Key: " NEW_KEY
        printf '\n'

        if [[ -z "$NEW_KEY" ]]; then
            echo "[ERROR] API Key 不能为空" >&2
            exit 1
        fi

        umask 077
        printf '%s' "$NEW_KEY" > "$LLM_KEY_FILE"
        unset NEW_KEY
    fi

    chmod 600 "$LLM_KEY_FILE"
    export LLM_API_KEY="$(tr -d '\r\n' < "$LLM_KEY_FILE")"
fi

if [[ -z "$LLM_API_KEY" ]]; then
    echo "[ERROR] LLM_API_KEY 加载失败" >&2
    exit 1
fi

echo "[OK] LLM_API_KEY 已加载（密钥不显示）"

# WANGZAI_LLM_ENDPOINT_V1
# DeepSeek Chat Completions 配置
export LLM_API_URL="${LLM_API_URL:-https://api.deepseek.com/chat/completions}"
export LLM_MODEL="${LLM_MODEL:-deepseek-flash}"

echo "[OK] LLM_API_URL=$LLM_API_URL"
echo "[OK] LLM_MODEL=$LLM_MODEL"

# WANGZAI_WEATHER_CONFIG_V1
# 加载天气 API 专用备用地址
WEATHER_IP_FILE="$HOME/.config/wangzai/weather-fallback-ip"

if [[ -z "${WANGZAI_WEATHER_FALLBACK_IP:-}" ]] &&    [[ -s "$WEATHER_IP_FILE" ]]; then
    export WANGZAI_WEATHER_FALLBACK_IP="$(tr -d '[:space:]' < "$WEATHER_IP_FILE")"
fi

if [[ -n "${WANGZAI_WEATHER_FALLBACK_IP:-}" ]]; then
    echo "[OK] 天气备用节点已配置"
else
    echo "[INFO] 天气客户端使用系统 DNS"
fi

exec mvn spring-boot:run
