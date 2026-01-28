import { createSignal, onMount, For, Show } from 'solid-js'
import CodePreview from './CodePreview'
import './AutomationList.css'

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
    <div class="automation-list">
      <div class="list-panel">
        <div class="panel-header">
          <h2>Automations</h2>
          <button onClick={fetchAutomations} class="refresh-btn">
            Refresh
          </button>
        </div>

        <Show when={loading()}>
          <div class="loading">Loading...</div>
        </Show>

        <Show when={!loading() && automations().length === 0}>
          <div class="empty">
            No automations yet. Use the Chat tab to create one!
          </div>
        </Show>

        <div class="list">
          <For each={automations()}>
            {(automation) => (
              <div
                class={`automation-item ${selectedId() === automation.id ? 'selected' : ''}`}
                onClick={() => fetchAutomationCode(automation.id)}
              >
                <div class="automation-name">{automation.config.name}</div>
                <div class="automation-desc">{automation.config.description}</div>
                <div class="automation-meta">
                  <Show when={automation.config.subscribe?.length > 0}>
                    <span class="topics">
                      {automation.config.subscribe.length} topic(s)
                    </span>
                  </Show>
                  <Show when={automation.config.schedule}>
                    <span class="schedule">Scheduled</span>
                  </Show>
                  <span class={`status ${automation.config.enabled ? 'enabled' : 'disabled'}`}>
                    {automation.config.enabled ? 'Enabled' : 'Disabled'}
                  </span>
                </div>
              </div>
            )}
          </For>
        </div>
      </div>

      <div class="detail-panel">
        <Show when={selectedId()}>
          <div class="panel-header">
            <h2>{selectedId()}.star</h2>
            <div class="actions">
              <Show when={editMode()}>
                <button onClick={saveAutomation} class="save-btn">Save</button>
                <button onClick={() => setEditMode(false)} class="cancel-btn">Cancel</button>
              </Show>
              <Show when={!editMode()}>
                <button onClick={() => setEditMode(true)} class="edit-btn">Edit</button>
                <button onClick={() => deleteAutomation(selectedId()!)} class="delete-btn">Delete</button>
              </Show>
            </div>
          </div>
          <CodePreview
            code={selectedCode()}
            filename={`${selectedId()}.star`}
            editable={editMode()}
            onCodeChange={setSelectedCode}
          />
        </Show>

        <Show when={!selectedId()}>
          <div class="no-selection">
            Select an automation to view its code
          </div>
        </Show>
      </div>
    </div>
  )
}
