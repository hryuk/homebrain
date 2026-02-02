import { createSignal, onMount, onCleanup, For, Show } from 'solid-js'
import { Radio, Pause, Play, Trash2, Search, Copy, Check } from 'lucide-solid'

interface MqttMessage {
  timestamp: string
  topic: string
  payload: string
  is_binary: boolean
  size: number
}

const MAX_PAYLOAD_LENGTH = 200

export default function MqttViewer() {
  const [messages, setMessages] = createSignal<MqttMessage[]>([])
  const [connected, setConnected] = createSignal(false)
  const [filter, setFilter] = createSignal('')
  const [expandedIndex, setExpandedIndex] = createSignal<number | null>(null)
  const [isPaused, setIsPaused] = createSignal(false)
  const [copiedIndex, setCopiedIndex] = createSignal<number | null>(null)
  let ws: WebSocket | null = null

  const connectWebSocket = () => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/ws/mqtt`

    ws = new WebSocket(wsUrl)

    ws.onopen = () => {
      setConnected(true)
      console.log('MQTT WebSocket connected')
    }

    ws.onclose = () => {
      setConnected(false)
      console.log('MQTT WebSocket disconnected')
      setTimeout(connectWebSocket, 3000)
    }

    ws.onmessage = (event) => {
      if (isPaused()) return
      try {
        const newMessages = JSON.parse(event.data)
        setMessages((prev) => [...newMessages, ...prev].slice(0, 5000))
      } catch (error) {
        console.error('Failed to parse MQTT message:', error)
      }
    }

    ws.onerror = (error) => {
      console.error('WebSocket error:', error)
    }
  }

  const fetchInitialMessages = async () => {
    try {
      const response = await fetch('/api/mqtt/messages')
      if (response.ok) {
        const data = await response.json()
        setMessages(data || [])
      }
    } catch (error) {
      console.error('Failed to fetch messages:', error)
    }
  }

  const clearMessages = () => {
    setMessages([])
    setExpandedIndex(null)
  }

  const formatTimestamp = (ts: string) => {
    const date = new Date(ts)
    return date.toLocaleTimeString()
  }

  const matchesFilter = (topic: string) => {
    const filterValue = filter().toLowerCase().trim()
    if (!filterValue) return true
    return topic.toLowerCase().includes(filterValue)
  }

  const filteredMessages = () => messages().filter((m) => matchesFilter(m.topic))

  const formatPayload = (payload: string, isBinary: boolean, size: number, index: number) => {
    if (isBinary) {
      return `[binary: ${size} bytes]`
    }

    try {
      const parsed = JSON.parse(payload)
      const pretty = JSON.stringify(parsed, null, 2)

      if (expandedIndex() === index) {
        return pretty
      }

      if (pretty.length > MAX_PAYLOAD_LENGTH) {
        return JSON.stringify(parsed)
      }
      return JSON.stringify(parsed)
    } catch {
      if (expandedIndex() === index) {
        return payload
      }
      if (payload.length > MAX_PAYLOAD_LENGTH) {
        return payload.substring(0, MAX_PAYLOAD_LENGTH) + '...'
      }
      return payload
    }
  }

  const isExpandable = (payload: string, isBinary: boolean) => {
    if (isBinary) return false
    try {
      const parsed = JSON.parse(payload)
      const pretty = JSON.stringify(parsed, null, 2)
      return pretty.length > MAX_PAYLOAD_LENGTH || pretty.includes('\n')
    } catch {
      return payload.length > MAX_PAYLOAD_LENGTH
    }
  }

  const toggleExpand = (index: number) => {
    setExpandedIndex((prev) => (prev === index ? null : index))
  }

  const copyPayload = async (payload: string, isBinary: boolean, index: number) => {
    if (isBinary) return
    try {
      const parsed = JSON.parse(payload)
      await navigator.clipboard.writeText(JSON.stringify(parsed, null, 2))
    } catch {
      await navigator.clipboard.writeText(payload)
    }
    setCopiedIndex(index)
    setTimeout(() => setCopiedIndex(null), 2000)
  }

  onMount(() => {
    fetchInitialMessages()
    connectWebSocket()
  })

  onCleanup(() => {
    ws?.close()
  })

  return (
    <div class="flex flex-col h-full">
      {/* Header */}
      <div class="flex items-center justify-between px-4 py-2 border-b border-border bg-card">
        <div class="flex items-center gap-2">
          <Radio class="h-3 w-3 text-muted-foreground" />
          <span class="text-xs font-mono text-muted-foreground">mqtt</span>
          <span
            class={`text-[9px] font-mono px-1 ${
              connected() && !isPaused() ? 'text-success' : 'text-muted-foreground'
            }`}
          >
            {connected() ? (isPaused() ? '○ paused' : '● live') : '○ disconnected'}
          </span>
          <span class="text-[9px] font-mono text-muted-foreground">
            ({filteredMessages().length})
          </span>
        </div>

        <div class="flex items-center gap-2">
          <div class="relative">
            <Search class="absolute left-2 top-1/2 -translate-y-1/2 h-3 w-3 text-muted-foreground" />
            <input
              type="text"
              value={filter()}
              onInput={(e) => setFilter(e.currentTarget.value)}
              placeholder="filter topic..."
              class="w-48 bg-background border border-border pl-7 pr-2 py-1 text-[10px] font-mono text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors"
            />
          </div>
          <button
            onClick={() => setIsPaused(!isPaused())}
            class={`flex items-center gap-1 px-2 py-1 text-[10px] font-mono border transition-colors ${
              !isPaused()
                ? 'border-primary text-primary'
                : 'border-border text-muted-foreground hover:text-foreground'
            }`}
          >
            {isPaused() ? <Play class="h-2.5 w-2.5" /> : <Pause class="h-2.5 w-2.5" />}
            {isPaused() ? 'resume' : 'pause'}
          </button>
          <button
            onClick={clearMessages}
            class="flex items-center gap-1 px-2 py-1 text-[10px] font-mono text-muted-foreground border border-border hover:text-foreground hover:border-muted-foreground transition-colors"
          >
            <Trash2 class="h-2.5 w-2.5" />
            clear
          </button>
        </div>
      </div>

      {/* Messages */}
      <div class="flex-1 overflow-auto">
        <For each={filteredMessages()}>
          {(msg, index) => (
            <div
              class={`border-b border-border/50 hover:bg-muted/20 transition-colors ${
                expandedIndex() === index() ? 'bg-muted/10' : ''
              }`}
            >
              {/* Message header */}
              <div class="flex items-center gap-3 px-4 py-1.5">
                <span class="text-[10px] text-muted-foreground whitespace-nowrap font-mono w-20 shrink-0">
                  {formatTimestamp(msg.timestamp)}
                </span>
                <span class="text-[10px] text-primary font-mono flex-1 break-all">
                  {msg.topic}
                </span>
                <div class="flex items-center gap-1 shrink-0">
                  <Show when={isExpandable(msg.payload, msg.is_binary)}>
                    <button
                      onClick={() => toggleExpand(index())}
                      class="text-[9px] font-mono text-muted-foreground hover:text-foreground px-1"
                    >
                      {expandedIndex() === index() ? '[-]' : '[+]'}
                    </button>
                  </Show>
                  <Show when={!msg.is_binary}>
                    <button
                      onClick={() => copyPayload(msg.payload, msg.is_binary, index())}
                      class="flex items-center gap-1 text-[9px] font-mono text-muted-foreground hover:text-foreground px-1"
                    >
                      {copiedIndex() === index() ? (
                        <Check class="h-2.5 w-2.5 text-success" />
                      ) : (
                        <Copy class="h-2.5 w-2.5" />
                      )}
                    </button>
                  </Show>
                </div>
              </div>

              {/* Payload */}
              <div class="px-4 pb-2">
                <pre
                  class={`text-[10px] font-mono p-2 bg-background border border-border overflow-auto max-h-48 ${
                    msg.is_binary ? 'text-muted-foreground italic' : 'text-foreground/70'
                  }`}
                >
                  {formatPayload(msg.payload, msg.is_binary, msg.size, index())}
                </pre>
              </div>
            </div>
          )}
        </For>

        <Show when={messages().length === 0}>
          <div class="flex items-center justify-center h-32 text-xs font-mono text-muted-foreground">
            No MQTT messages yet. Messages will appear here as they arrive.
          </div>
        </Show>

        <Show when={messages().length > 0 && filteredMessages().length === 0}>
          <div class="flex items-center justify-center h-32 text-xs font-mono text-muted-foreground">
            No messages match the filter "{filter()}"
          </div>
        </Show>
      </div>
    </div>
  )
}
