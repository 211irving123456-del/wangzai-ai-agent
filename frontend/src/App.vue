<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

type ChatMessage = {
  role: 'user' | 'assistant'
  content: string
}

type DeviceStatus = {
  device?: string
  online?: boolean
  expression?: string
  uptime_seconds?: number
  free_heap?: number
  boot_id?: string
  mode?: string
}

type TraceEvent = {
  trace_id: string
  timestamp?: string
  event: string
  round?: number
  tool_name?: string
  duration_ms?: number
  arguments?: Record<string, unknown>
  result?: Record<string, unknown>
}

const token = ref('')
const sessionId = ref(`console-${Date.now()}`)
const input = ref('')
const sending = ref(false)
const notice = ref('')

const messages = ref<ChatMessage[]>([
  {
    role: 'assistant',
    content: '你好，我是旺仔！你可以让我查询天气、读取设备状态或调整表情。',
  },
])

const device = ref<DeviceStatus | null>(null)
const deviceError = ref('')
const traces = ref<TraceEvent[]>([])
const selectedTraceId = ref('')
const traceInput = ref<HTMLInputElement | null>(null)

const traceIds = computed(() =>
  [...new Set(traces.value.map(x => x.trace_id))].reverse(),
)

const selectedEvents = computed(() =>
  traces.value.filter(x => x.trace_id === selectedTraceId.value),
)

async function refreshDevice() {
  try {
    const response = await fetch('/mock/api/device/status', {
      cache: 'no-store',
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    device.value = await response.json() as DeviceStatus
    deviceError.value = ''
  } catch {
    device.value = null
    deviceError.value = '无法连接模拟设备，请确认端口 18080 已启动'
  }
}

async function sendMessage() {
  const content = input.value.trim()

  if (!content || sending.value) return

  if (!token.value.trim()) {
    notice.value = '请先填写本地 API Token'
    return
  }

  input.value = ''
  notice.value = ''
  sending.value = true
  messages.value.push({ role: 'user', content })

  try {
    const response = await fetch('/api/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token.value.trim()}`,
      },
      body: JSON.stringify({
        sessionId: sessionId.value,
        message: content,
      }),
    })

    const body = await response.text()

    let result: {
      reply?: string
      status?: string
      message?: string
    }

    try {
      result = JSON.parse(body)
    } catch {
      throw new Error(`后端返回了非 JSON 内容，HTTP ${response.status}`)
    }

    if (!response.ok || result.status === 'error') {
      throw new Error(result.message || result.reply || `HTTP ${response.status}`)
    }

    messages.value.push({
      role: 'assistant',
      content: result.reply || 'Agent 没有返回回复内容',
    })
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    notice.value = `请求失败：${message}`
  } finally {
    sending.value = false
    await refreshDevice()
    await refreshTraces()
  }
}

function newSession() {
  sessionId.value = `console-${Date.now()}`
  messages.value = [{
    role: 'assistant',
    content: '新会话已创建。你可以开始新的 Agent 任务。',
  }]
  notice.value = ''
}

// LESSON37_AUTO_TRACE
const traceError = ref('')
let traceTimer: ReturnType<typeof setInterval> | undefined
let traceLoading = false

async function refreshTraces() {
  if (!token.value.trim() || traceLoading) return

  traceLoading = true

  try {
    const response = await fetch('/api/agent/traces/latest', {
      headers: {
        Authorization: `Bearer ${token.value.trim()}`,
      },
      cache: 'no-store',
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    const data = await response.json() as {
      traceId?: string
      events?: TraceEvent[]
    }

    if (Array.isArray(data.events) && data.events.length > 0) {
      traces.value = data.events
      selectedTraceId.value = data.traceId ||
        data.events[data.events.length - 1].trace_id
    }

    traceError.value = ''
  } catch (error) {
    traceError.value = error instanceof Error
      ? `Trace 查询失败：${error.message}`
      : 'Trace 查询失败'
  } finally {
    traceLoading = false
  }
}

function openTraceFile() {
  traceInput.value?.click()
}

async function importTraces(event: Event) {
  const element = event.target as HTMLInputElement
  const file = element.files?.[0]
  if (!file) return

  const content = await file.text()
  const parsed: TraceEvent[] = []

  for (const line of content.split(/\r?\n/)) {
    if (!line.trim()) continue
    try {
      const item: unknown = JSON.parse(line)
      if (item && typeof item === 'object' &&
          'trace_id' in item && 'event' in item &&
          typeof item.trace_id === 'string' &&
          typeof item.event === 'string') {
        parsed.push(item as TraceEvent)
      }
    } catch {
      // 忽略损坏的单行记录
    }
  }

  traces.value = parsed
  selectedTraceId.value = parsed.at(-1)?.trace_id || ''
  notice.value = `已导入 ${parsed.length} 条 Trace 事件`
  element.value = ''
}

function formatTime(value?: string) {
  if (!value) return '--'
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleTimeString('zh-CN', { hour12: false })
}

onMounted(() => {
  void refreshDevice()
  void refreshTraces()

  traceTimer = setInterval(() => {
    void refreshTraces()
  }, 5000)
})

onUnmounted(() => {
  if (traceTimer !== undefined) {
    clearInterval(traceTimer)
  }
})
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-symbol">W</div>
        <div>
          <strong>WANGZAI</strong>
          <span>AI Agent Console</span>
        </div>
      </div>

      <div class="section-title">工作空间</div>
      <div class="nav-item active">✦ Agent 对话</div>
      <div class="nav-item">⌁ 工具执行轨迹</div>
      <div class="nav-item">▣ 设备状态</div>

      <button class="new-session" @click="newSession">＋ 新建会话</button>

      <div class="sidebar-bottom">
        <div class="mini-label">运行环境</div>
        <strong>LOCAL DEVELOPMENT</strong>
        <p>Spring Boot · DeepSeek · ESP32 Mock</p>
      </div>
    </aside>

    <main class="workspace">
      <header class="topbar">
        <div>
          <div class="eyebrow">AGENT WORKSPACE</div>
          <h1>智能桌宠控制台</h1>
          <p>多轮工具调用 · 环境感知 · 设备控制</p>
        </div>
        <div class="status-pill">
          <span class="pulse"></span>
          开发环境
        </div>
      </header>

      <div class="main-grid">
        <section class="chat-panel">
          <div class="panel-heading">
            <div>
              <h2>Agent 对话</h2>
              <span>让模型自主选择工具并执行任务</span>
            </div>
            <span class="count">{{ messages.length }} messages</span>
          </div>

          <div class="chat-messages">
            <div
              v-for="(message, index) in messages"
              :key="index"
              class="message-row"
              :class="message.role"
            >
              <div class="avatar">
                {{ message.role === 'assistant' ? 'W' : 'U' }}
              </div>
              <div class="message-content">
                <div class="message-name">
                  {{ message.role === 'assistant' ? '旺仔 Agent' : '你' }}
                </div>
                <div class="bubble">{{ message.content }}</div>
              </div>
            </div>

            <div v-if="sending" class="thinking">
              <span class="spinner"></span>
              Agent 正在执行任务...
            </div>
          </div>

          <div class="composer">
            <label for="api-token">本地 API Token</label>
            <input
              id="api-token"
              v-model="token"
              type="password"
              autocomplete="off"
              placeholder="输入 ~/.config/wangzai/api-token 中的值"
            />

            <textarea
              v-model="input"
              rows="3"
              placeholder="例如：旺仔，请查询当前设备状态，然后把表情切换为 sad。"
              @keydown.ctrl.enter.prevent="sendMessage"
            ></textarea>

            <div class="composer-actions">
              <span>Ctrl + Enter 发送</span>
              <button :disabled="sending || !input.trim()" @click="sendMessage">
                {{ sending ? '执行中...' : '发送任务 →' }}
              </button>
            </div>

            <p v-if="notice" class="notice">{{ notice }}</p>
            <p class="privacy-note">Token 仅保存在当前页面内存中，不写入浏览器本地存储。</p>
          </div>
        </section>

        <aside class="right-panel">
          <section class="info-card">
            <div class="card-head">
              <div>
                <h2>设备状态</h2>
                <p>ESP32 MOCK</p>
              </div>
              <button class="icon-button" @click="refreshDevice">↻</button>
            </div>

            <template v-if="device">
              <div class="device-display">
                <div class="device-face">
                  {{ device.expression === 'happy' ? '☺' :
                     device.expression === 'sad' ? '☹' : '☾' }}
                </div>
                <strong>{{ device.expression || '--' }}</strong>
                <span>当前表情</span>
              </div>
              <div class="metric-row">
                <span>连接状态</span>
                <strong class="online">● Online</strong>
              </div>
              <div class="metric-row">
                <span>运行时间</span>
                <strong>{{ device.uptime_seconds ?? 0 }} s</strong>
              </div>
              <div class="metric-row">
                <span>可用内存</span>
                <strong>{{ device.free_heap ?? '--' }} B</strong>
              </div>
            </template>

            <div v-else class="empty-state">
              {{ deviceError || '正在查询设备...' }}
            </div>
          </section>

          <section class="info-card trace-card">
            <div class="card-head">
              <div>
                <h2>Agent 执行轨迹</h2>
                <p>多轮决策与工具执行</p>
              </div>
              <button class="small-button" @click="refreshTraces">
                刷新
              </button>
              <button class="small-button" @click="openTraceFile">
                导入 JSONL
              </button>
              <input
                ref="traceInput"
                type="file"
                accept=".jsonl,.txt"
                hidden
                @change="importTraces"
              />
            </div>

            <select v-if="traceIds.length" v-model="selectedTraceId">
              <option v-for="id in traceIds" :key="id" :value="id">
                {{ id.slice(0, 18) }}...
              </option>
            </select>

            <p v-if="traceError" class="notice">{{ traceError }}</p>

            <div v-if="!selectedEvents.length" class="empty-state">
              <strong>暂无执行记录</strong>
              <p>导入 data/agent-traces.jsonl 后即可查看模型与工具调用过程。</p>
            </div>

            <div v-else class="timeline">
              <div
                v-for="(event, index) in selectedEvents"
                :key="index"
                class="trace-event"
              >
                <div class="trace-dot"></div>
                <div class="trace-detail">
                  <div class="trace-top">
                    <strong>
                      {{ event.event === 'tool' ? event.tool_name :
                         event.event === 'llm_decision' ? 'LLM Decision' :
                         'Agent Start' }}
                    </strong>
                    <span>{{ event.duration_ms !== undefined ? `${event.duration_ms}ms` : formatTime(event.timestamp) }}</span>
                  </div>
                  <p>Round {{ event.round ?? 0 }} · {{ event.event }}</p>
                  <pre v-if="event.arguments">{{ JSON.stringify(event.arguments, null, 2) }}</pre>
                  <pre v-if="event.result">{{ JSON.stringify(event.result, null, 2) }}</pre>
                </div>
              </div>
            </div>
          </section>
        </aside>
      </div>
    </main>
  </div>
</template>
