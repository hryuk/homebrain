import { createSignal, onMount, onCleanup, For, Show } from 'solid-js'
import './GlobalStateViewer.css'

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
    setExpandedKeys(prev => {
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

  // Group entries by key prefix for visual organization
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
    <div class="global-state-viewer">
      <div class="panel-header">
        <div class="header-left">
          <h2>Global State</h2>
          <span class="entry-count">{state().length} entries</span>
        </div>
        <div class="header-right">
          <span class="last-update">
            Updated: {formatTimestamp(lastUpdate())}
          </span>
          <button 
            class={`auto-refresh-btn ${autoRefresh() ? 'active' : ''}`}
            onClick={toggleAutoRefresh}
          >
            {autoRefresh() ? 'Auto-refresh ON' : 'Auto-refresh OFF'}
          </button>
          <button onClick={fetchGlobalState} class="refresh-btn">
            Refresh
          </button>
        </div>
      </div>

      <Show when={loading()}>
        <div class="loading">Loading...</div>
      </Show>

      <Show when={!loading() && state().length === 0}>
        <div class="empty">
          No global state entries yet.
          <br />
          <span class="hint">Automations can use ctx.set_global() to store shared state.</span>
        </div>
      </Show>

      <Show when={!loading() && state().length > 0}>
        <div class="state-table">
          <div class="table-header">
            <div class="col-key">Key</div>
            <div class="col-value">Value</div>
            <div class="col-owners">Owners</div>
          </div>
          
          <div class="table-body">
            <For each={Object.entries(groupedEntries())}>
              {([prefix, entries]) => (
                <>
                  <div class="group-header">{prefix}</div>
                  <For each={entries}>
                    {(entry) => (
                      <div class="state-row">
                        <div class="col-key">
                          <span class="key-name">{entry.key}</span>
                        </div>
                        <div class="col-value">
                          <Show when={isComplexValue(entry.value)}>
                            <button 
                              class="expand-btn"
                              onClick={() => toggleExpand(entry.key)}
                            >
                              {expandedKeys().has(entry.key) ? '[-]' : '[+]'}
                            </button>
                          </Show>
                          <Show when={expandedKeys().has(entry.key) || !isComplexValue(entry.value)}>
                            <pre class="value-content">{formatValue(entry.value)}</pre>
                          </Show>
                          <Show when={!expandedKeys().has(entry.key) && isComplexValue(entry.value)}>
                            <span class="value-preview">
                              {Array.isArray(entry.value) 
                                ? `Array(${entry.value.length})` 
                                : `Object(${Object.keys(entry.value).length})`}
                            </span>
                          </Show>
                        </div>
                        <div class="col-owners">
                          <Show when={entry.owners.length > 0}>
                            <For each={entry.owners}>
                              {(owner) => (
                                <span class="owner-tag">{owner}</span>
                              )}
                            </For>
                          </Show>
                          <Show when={entry.owners.length === 0}>
                            <span class="no-owner">No owner</span>
                          </Show>
                        </div>
                      </div>
                    )}
                  </For>
                </>
              )}
            </For>
          </div>
        </div>
      </Show>
    </div>
  )
}
