import { createSignal, onMount, onCleanup, For } from 'solid-js'
import './LogViewer.css'

interface LogEntry {
  timestamp: string
  automation_id: string
  message: string
}

export default function LogViewer() {
  const [logs, setLogs] = createSignal<LogEntry[]>([])
  const [connected, setConnected] = createSignal(false)
  let ws: WebSocket | null = null

  const connectWebSocket = () => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/ws/logs`

    ws = new WebSocket(wsUrl)

    ws.onopen = () => {
      setConnected(true)
      console.log('WebSocket connected')
    }

    ws.onclose = () => {
      setConnected(false)
      console.log('WebSocket disconnected')
      // Reconnect after delay
      setTimeout(connectWebSocket, 3000)
    }

    ws.onmessage = (event) => {
      try {
        const newLogs = JSON.parse(event.data)
        setLogs((prev) => [...prev, ...newLogs].slice(-500))
      } catch (error) {
        console.error('Failed to parse log message:', error)
      }
    }

    ws.onerror = (error) => {
      console.error('WebSocket error:', error)
    }
  }

  const fetchInitialLogs = async () => {
    try {
      const response = await fetch('/api/logs')
      if (response.ok) {
        const data = await response.json()
        setLogs(data || [])
      }
    } catch (error) {
      console.error('Failed to fetch logs:', error)
    }
  }

  const clearLogs = () => {
    setLogs([])
  }

  const formatTimestamp = (ts: string) => {
    const date = new Date(ts)
    return date.toLocaleTimeString()
  }

  onMount(() => {
    fetchInitialLogs()
    connectWebSocket()
  })

  onCleanup(() => {
    ws?.close()
  })

  return (
    <div class="log-viewer">
      <div class="log-header">
        <div class="log-title">
          <h2>Logs</h2>
          <span class={`status ${connected() ? 'connected' : 'disconnected'}`}>
            {connected() ? 'Live' : 'Disconnected'}
          </span>
        </div>
        <button onClick={clearLogs} class="clear-btn">
          Clear
        </button>
      </div>

      <div class="log-content">
        <For each={logs()}>
          {(log) => (
            <div class="log-entry">
              <span class="log-time">{formatTimestamp(log.timestamp)}</span>
              <span class="log-automation">[{log.automation_id}]</span>
              <span class="log-message">{log.message}</span>
            </div>
          )}
        </For>

        {logs().length === 0 && (
          <div class="no-logs">No logs yet. Automation logs will appear here.</div>
        )}
      </div>
    </div>
  )
}
