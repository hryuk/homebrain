import { createSignal, onMount, onCleanup, For, Show } from 'solid-js'
import { Terminal, Pause, Play, Trash2, AlertCircle } from 'lucide-solid'

interface LogEntry {
  timestamp: string
  automation_id: string
  message: string
}

export default function LogViewer() {
  const [logs, setLogs] = createSignal<LogEntry[]>([])
  const [connected, setConnected] = createSignal(false)
  const [isPaused, setIsPaused] = createSignal(false)
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
      setTimeout(connectWebSocket, 3000)
    }

    ws.onmessage = (event) => {
      if (isPaused()) return
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

  const isError = (message: string) => {
    return message.toLowerCase().includes('error')
  }

  const errorCount = () => logs().filter((l) => isError(l.message)).length

  onMount(() => {
    fetchInitialLogs()
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
          <Terminal class="h-3 w-3 text-muted-foreground" />
          <span class="text-xs font-mono text-muted-foreground">logs</span>
          <span
            class={`text-[9px] font-mono px-1 ${
              connected() && !isPaused() ? 'text-success' : 'text-muted-foreground'
            }`}
          >
            {connected() ? (isPaused() ? '○ paused' : '● live') : '○ disconnected'}
          </span>
          <Show when={errorCount() > 0}>
            <span class="flex items-center gap-1 text-[9px] font-mono text-destructive">
              <AlertCircle class="h-2.5 w-2.5" />
              {errorCount()}
            </span>
          </Show>
        </div>

        <div class="flex items-center gap-2">
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
            onClick={clearLogs}
            class="flex items-center gap-1 px-2 py-1 text-[10px] font-mono text-muted-foreground border border-border hover:text-foreground hover:border-muted-foreground transition-colors"
          >
            <Trash2 class="h-2.5 w-2.5" />
            clear
          </button>
        </div>
      </div>

      {/* Logs */}
      <div class="flex-1 overflow-auto">
        <For each={logs()}>
          {(log) => (
            <div
              class={`flex items-start gap-3 py-1.5 px-4 border-b border-border/50 hover:bg-muted/20 transition-colors ${
                isError(log.message) ? 'bg-destructive/5' : ''
              }`}
            >
              <span class="text-[10px] text-muted-foreground whitespace-nowrap font-mono w-20 shrink-0">
                {formatTimestamp(log.timestamp)}
              </span>
              <span class="text-[10px] text-primary whitespace-nowrap font-mono shrink-0">
                [{log.automation_id}]
              </span>
              <span
                class={`text-[10px] font-mono ${
                  isError(log.message) ? 'text-destructive' : 'text-foreground/70'
                }`}
              >
                {log.message}
              </span>
            </div>
          )}
        </For>

        <Show when={logs().length === 0}>
          <div class="flex items-center justify-center h-32 text-xs font-mono text-muted-foreground">
            No logs yet. Automation logs will appear here.
          </div>
        </Show>
      </div>
    </div>
  )
}
