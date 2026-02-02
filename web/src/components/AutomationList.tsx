import { createSignal, onMount, For, Show } from 'solid-js'
import { Plus, RefreshCw, Power, PowerOff } from 'lucide-solid'
import CodePreview from './CodePreview'

interface Automation {
  id: string
  file_path: string
  config: {
    name: string
    description: string
    subscribe: string[]
    schedule?: string
    enabled: boolean
  }
}

export default function AutomationList() {
  const [automations, setAutomations] = createSignal<Automation[]>([])
  const [loading, setLoading] = createSignal(true)
  const [selectedId, setSelectedId] = createSignal<string | null>(null)
  const [selectedCode, setSelectedCode] = createSignal<string>('')
  const [editMode, setEditMode] = createSignal(false)

  const fetchAutomations = async () => {
    try {
      const response = await fetch('/api/automations')
      if (response.ok) {
        const data = await response.json()
        setAutomations(data || [])
      }
    } catch (error) {
      console.error('Failed to fetch automations:', error)
    } finally {
      setLoading(false)
    }
  }

  const fetchAutomationCode = async (id: string) => {
    try {
      const response = await fetch(`/api/automations/${id}`)
      if (response.ok) {
        const data = await response.json()
        setSelectedCode(data.code)
        setSelectedId(id)
        setEditMode(false)
      }
    } catch (error) {
      console.error('Failed to fetch automation code:', error)
    }
  }

  const saveAutomation = async () => {
    const id = selectedId()
    if (!id) return

    try {
      const response = await fetch(`/api/automations/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code: selectedCode() }),
      })

      if (response.ok) {
        setEditMode(false)
        fetchAutomations()
      }
    } catch (error) {
      console.error('Failed to save automation:', error)
    }
  }

  const deleteAutomation = async (id: string) => {
    if (!confirm('Are you sure you want to delete this automation?')) return

    try {
      const response = await fetch(`/api/automations/${id}`, {
        method: 'DELETE',
      })

      if (response.ok) {
        setSelectedId(null)
        setSelectedCode('')
        fetchAutomations()
      }
    } catch (error) {
      console.error('Failed to delete automation:', error)
    }
  }

  onMount(fetchAutomations)

  return (
    <div class="flex h-full">
      {/* Sidebar */}
      <aside class="w-64 border-r border-border p-3 overflow-auto bg-card">
        <div class="flex items-center justify-between mb-3">
          <span class="text-[10px] font-mono text-muted-foreground">automations</span>
          <div class="flex gap-1">
            <button class="p-1 text-muted-foreground hover:text-foreground transition-colors">
              <Plus class="h-3 w-3" />
            </button>
            <button
              onClick={fetchAutomations}
              class="p-1 text-muted-foreground hover:text-foreground transition-colors"
            >
              <RefreshCw class="h-3 w-3" />
            </button>
          </div>
        </div>

        <Show when={loading()}>
          <div class="text-xs font-mono text-muted-foreground p-2">Loading...</div>
        </Show>

        <Show when={!loading() && automations().length === 0}>
          <div class="text-xs font-mono text-muted-foreground p-2">
            No automations yet. Use the Chat tab to create one!
          </div>
        </Show>

        <div class="space-y-1">
          <For each={automations()}>
            {(automation) => (
              <div
                onClick={() => fetchAutomationCode(automation.id)}
                class={`p-2 cursor-pointer border transition-colors ${
                  selectedId() === automation.id
                    ? 'border-primary/30 bg-primary/5'
                    : 'border-border hover:border-muted-foreground/30'
                }`}
              >
                <div class="flex items-center justify-between mb-1">
                  <span class="font-mono text-xs text-foreground truncate">
                    {automation.config.name}
                  </span>
                  {automation.config.enabled ? (
                    <Power class="h-3 w-3 text-success shrink-0" />
                  ) : (
                    <PowerOff class="h-3 w-3 text-muted-foreground shrink-0" />
                  )}
                </div>

                <p class="text-[10px] text-muted-foreground line-clamp-1">
                  {automation.config.description}
                </p>

                <div class="flex items-center gap-2 mt-2">
                  <Show when={automation.config.subscribe?.length > 0}>
                    <span class="text-[9px] font-mono text-muted-foreground border border-border px-1">
                      {automation.config.subscribe.length} topics
                    </span>
                  </Show>
                  <span
                    class={`text-[9px] font-mono px-1 ${
                      automation.config.enabled
                        ? 'text-success border border-success/30'
                        : 'text-muted-foreground border border-border'
                    }`}
                  >
                    {automation.config.enabled ? 'active' : 'inactive'}
                  </span>
                </div>
              </div>
            )}
          </For>
        </div>
      </aside>

      {/* Main content */}
      <div class="flex-1 p-4 overflow-auto">
        <Show when={selectedId()}>
          <CodePreview
            code={selectedCode()}
            filename={`${selectedId()}.star`}
            showActions
            editable={editMode()}
            onCodeChange={setSelectedCode}
            onEdit={() => setEditMode(true)}
            onSave={saveAutomation}
            onCancel={() => setEditMode(false)}
            onDelete={() => deleteAutomation(selectedId()!)}
            isEditing={editMode()}
          />
        </Show>

        <Show when={!selectedId()}>
          <div class="flex items-center justify-center h-full text-xs font-mono text-muted-foreground">
            Select an automation to view its code
          </div>
        </Show>
      </div>
    </div>
  )
}
