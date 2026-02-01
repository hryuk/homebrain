import { createSignal, onMount, onCleanup, For, Show } from 'solid-js'
import './MqttViewer.css'

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
      // Reconnect after delay
      setTimeout(connectWebSocket, 3000)
    }

    ws.onmessage = (event) => {
      try {
        const newMessages = JSON.parse(event.data)
        // Prepend new messages (newest-first order)
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

    // Try to parse and pretty-print JSON
    try {
      const parsed = JSON.parse(payload)
      const pretty = JSON.stringify(parsed, null, 2)
      
      if (expandedIndex() === index) {
        return pretty
      }
      
      // Truncate if needed
      if (pretty.length > MAX_PAYLOAD_LENGTH) {
        return JSON.stringify(parsed)
      }
      return JSON.stringify(parsed)
    } catch {
      // Not JSON, show as-is
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

  const copyPayload = async (payload: string, isBinary: boolean) => {
    if (isBinary) return
    try {
      // Try to pretty-print JSON
      const parsed = JSON.parse(payload)
      await navigator.clipboard.writeText(JSON.stringify(parsed, null, 2))
    } catch {
      await navigator.clipboard.writeText(payload)
    }
  }

  onMount(() => {
    fetchInitialMessages()
    connectWebSocket()
  })

  onCleanup(() => {
    ws?.close()
  })

  return (
    <div class="mqtt-viewer">
      <div class="mqtt-header">
        <div class="mqtt-title">
          <h2>MQTT Messages</h2>
          <span class={`status ${connected() ? 'connected' : 'disconnected'}`}>
            {connected() ? 'Live' : 'Disconnected'}
          </span>
          <span class="message-count">{filteredMessages().length} messages</span>
        </div>
        <div class="mqtt-controls">
          <input
            type="text"
            placeholder="Filter by topic..."
            value={filter()}
            onInput={(e) => setFilter(e.currentTarget.value)}
            class="filter-input"
          />
          <button onClick={clearMessages} class="clear-btn">
            Clear
          </button>
        </div>
      </div>

      <div class="mqtt-content">
        <For each={filteredMessages()}>
          {(msg, index) => (
            <div class={`mqtt-entry ${expandedIndex() === index() ? 'expanded' : ''}`}>
              <div class="mqtt-entry-header">
                <span class="mqtt-time">{formatTimestamp(msg.timestamp)}</span>
                <span class="mqtt-topic">{msg.topic}</span>
                <div class="mqtt-actions">
                  <Show when={isExpandable(msg.payload, msg.is_binary)}>
                    <button
                      class="expand-btn"
                      onClick={() => toggleExpand(index())}
                      title={expandedIndex() === index() ? 'Collapse' : 'Expand'}
                    >
                      {expandedIndex() === index() ? '[-]' : '[+]'}
                    </button>
                  </Show>
                  <Show when={!msg.is_binary}>
                    <button
                      class="copy-btn"
                      onClick={() => copyPayload(msg.payload, msg.is_binary)}
                      title="Copy payload"
                    >
                      Copy
                    </button>
                  </Show>
                </div>
              </div>
              <div class={`mqtt-payload ${msg.is_binary ? 'binary' : ''}`}>
                <pre>{formatPayload(msg.payload, msg.is_binary, msg.size, index())}</pre>
              </div>
            </div>
          )}
        </For>

        {messages().length === 0 && (
          <div class="no-messages">No MQTT messages yet. Messages will appear here as they arrive.</div>
        )}

        {messages().length > 0 && filteredMessages().length === 0 && (
          <div class="no-messages">No messages match the filter "{filter()}"</div>
        )}
      </div>
    </div>
  )
}
