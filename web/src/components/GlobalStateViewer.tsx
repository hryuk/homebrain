import { createSignal, onMount, onCleanup, For, Show } from 'solid-js'
import { Database, RefreshCw, ToggleLeft, ToggleRight } from 'lucide-solid'

interface GlobalStateEntry {
  key: string
  value: any
  owners: string[]
}

interface GlobalStateResponse {
  entries: GlobalStateEntry[]
  timestamp: number
}

export default function GlobalStateViewer() {
  const [state, setState] = createSignal<GlobalStateEntry[]>([])
  const [loading, setLoading] = createSignal(true)
  const [lastUpdate, setLastUpdate] = createSignal<number>(0)
  const [autoRefresh, setAutoRefresh] = createSignal(true)
  const [expandedKeys, setExpandedKeys] = createSignal<Set<string>>(new Set())

  let refreshInterval: number | undefined

  const fetchGlobalState = async () => {
    try {
      const response = await fetch('/api/global-state')
      if (response.ok) {
        const data: GlobalStateResponse = await response.json()
        setState(data.entries || [])
        setLastUpdate(data.timestamp)
      }
    } catch (error) {
      console.error('Failed to fetch global state:', error)
    } finally {
      setLoading(false)
    }
  }

  const toggleExpand = (key: string) => {
    setExpandedKeys((prev) => {
      const newSet = new Set(prev)
      if (newSet.has(key)) {
        newSet.delete(key)
      } else {
        newSet.add(key)
      }
      return newSet
    })
  }

  const formatValue = (value: any): string => {
    if (value === null) return 'null'
    if (value === undefined) return 'undefined'
    if (typeof value === 'object') {
      return JSON.stringify(value, null, 2)
    }
    return String(value)
  }

  const isComplexValue = (value: any): boolean => {
    return typeof value === 'object' && value !== null
  }

  const formatTimestamp = (ts: number): string => {
    if (!ts) return 'Never'
    return new Date(ts).toLocaleTimeString()
  }

  const toggleAutoRefresh = () => {
    const newValue = !autoRefresh()
    setAutoRefresh(newValue)

    if (newValue) {
      refreshInterval = setInterval(fetchGlobalState, 5000) as unknown as number
    } else if (refreshInterval) {
      clearInterval(refreshInterval)
      refreshInterval = undefined
    }
  }

  const groupedEntries = () => {
    const groups: Record<string, GlobalStateEntry[]> = {}
    for (const entry of state()) {
      const prefix = entry.key.split('.')[0]
      if (!groups[prefix]) {
        groups[prefix] = []
      }
      groups[prefix].push(entry)
    }
    return groups
  }

  onMount(() => {
    fetchGlobalState()
    refreshInterval = setInterval(fetchGlobalState, 5000) as unknown as number
  })

  onCleanup(() => {
    if (refreshInterval) {
      clearInterval(refreshInterval)
    }
  })

  return (
    <div class="flex flex-col h-full">
      {/* Header */}
      <div class="flex items-center justify-between px-4 py-2 border-b border-border bg-card">
        <div class="flex items-center gap-2">
          <Database class="h-3 w-3 text-muted-foreground" />
          <span class="text-xs font-mono text-muted-foreground">
            state ({state().length} entries)
          </span>
        </div>

        <div class="flex items-center gap-2">
          <span class="text-[9px] text-muted-foreground font-mono">
            {formatTimestamp(lastUpdate())}
          </span>
          <button
            onClick={toggleAutoRefresh}
            class={`flex items-center gap-1 px-2 py-1 text-[10px] font-mono border transition-colors ${
              autoRefresh()
                ? 'border-primary text-primary'
                : 'border-border text-muted-foreground hover:text-foreground'
            }`}
          >
            {autoRefresh() ? (
              <ToggleRight class="h-3 w-3" />
            ) : (
              <ToggleLeft class="h-3 w-3" />
            )}
            auto
          </button>
          <button
            onClick={fetchGlobalState}
            class="flex items-center gap-1 px-2 py-1 text-[10px] font-mono text-muted-foreground border border-border hover:text-foreground hover:border-muted-foreground transition-colors"
          >
            <RefreshCw class="h-2.5 w-2.5" />
            refresh
          </button>
        </div>
      </div>

      <Show when={loading()}>
        <div class="flex items-center justify-center h-32 text-xs font-mono text-muted-foreground">
          Loading...
        </div>
      </Show>

      <Show when={!loading() && state().length === 0}>
        <div class="flex flex-col items-center justify-center h-32 text-xs font-mono text-muted-foreground">
          <p>No global state entries yet.</p>
          <p class="text-[10px] mt-1">
            Automations can use ctx.set_global() to store shared state.
          </p>
        </div>
      </Show>

      <Show when={!loading() && state().length > 0}>
        <div class="flex-1 overflow-auto">
          {/* Header row */}
          <div class="grid grid-cols-[180px_1fr_80px] gap-4 px-4 py-2 border-b border-border bg-secondary/30 text-[9px] font-mono text-muted-foreground">
            <span>key</span>
            <span>value</span>
            <span>owner</span>
          </div>

          <For each={Object.entries(groupedEntries())}>
            {([prefix, entries]) => (
              <>
                {/* Section header */}
                <div class="px-4 py-1.5 bg-muted/30 border-b border-border">
                  <span class="text-[9px] font-mono text-primary flex items-center gap-1.5">
                    <span class="w-1 h-1 bg-primary" />
                    {prefix}
                  </span>
                </div>

                {/* Rows */}
                <For each={entries}>
                  {(entry) => (
                    <div class="grid grid-cols-[180px_1fr_80px] gap-4 px-4 py-2 border-b border-border/50 hover:bg-muted/20 transition-colors">
                      <code class="text-[10px] text-primary font-mono break-all self-start">
                        {entry.key}
                      </code>
                      <div class="overflow-auto max-h-32">
                        <Show when={isComplexValue(entry.value)}>
                          <button
                            onClick={() => toggleExpand(entry.key)}
                            class="text-[9px] font-mono text-muted-foreground hover:text-foreground mr-2"
                          >
                            {expandedKeys().has(entry.key) ? '[-]' : '[+]'}
                          </button>
                        </Show>
                        <Show
                          when={expandedKeys().has(entry.key) || !isComplexValue(entry.value)}
                        >
                          <pre class="text-[10px] font-mono text-foreground whitespace-pre-wrap break-all p-2 bg-background border border-border">
                            {formatValue(entry.value)}
                          </pre>
                        </Show>
                        <Show
                          when={!expandedKeys().has(entry.key) && isComplexValue(entry.value)}
                        >
                          <span class="text-[10px] font-mono text-muted-foreground">
                            {Array.isArray(entry.value)
                              ? `Array(${entry.value.length})`
                              : `Object(${Object.keys(entry.value).length})`}
                          </span>
                        </Show>
                      </div>
                      <div class="self-start">
                        <Show when={entry.owners.length > 0}>
                          <For each={entry.owners}>
                            {(owner) => (
                              <span class="inline-block text-[9px] font-mono text-success bg-success/10 border border-success/30 px-1 mr-1 mb-1">
                                {owner}
                              </span>
                            )}
                          </For>
                        </Show>
                        <Show when={entry.owners.length === 0}>
                          <span class="text-[10px] text-muted-foreground font-mono">—</span>
                        </Show>
                      </div>
                    </div>
                  )}
                </For>
              </>
            )}
          </For>
        </div>
      </Show>
    </div>
  )
}
