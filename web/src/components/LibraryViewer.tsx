import { createSignal, onMount, For, Show } from 'solid-js'
import CodePreview from './CodePreview'
import './LibraryViewer.css'

interface LibraryModule {
  name: string
  description: string
  functions: string[]
}

export default function LibraryViewer() {
  const [modules, setModules] = createSignal<LibraryModule[]>([])
  const [loading, setLoading] = createSignal(true)
  const [selectedModule, setSelectedModule] = createSignal<string | null>(null)
  const [selectedCode, setSelectedCode] = createSignal<string>('')
  const [loadingCode, setLoadingCode] = createSignal(false)

  const fetchModules = async () => {
    try {
      const response = await fetch('/api/libraries')
      if (response.ok) {
        const data = await response.json()
        setModules(data || [])
      }
    } catch (error) {
      console.error('Failed to fetch library modules:', error)
    } finally {
      setLoading(false)
    }
  }

  const fetchModuleCode = async (name: string) => {
    setLoadingCode(true)
    try {
      const response = await fetch(`/api/libraries/${name}`)
      if (response.ok) {
        const data = await response.json()
        setSelectedCode(data.code)
        setSelectedModule(name)
      }
    } catch (error) {
      console.error('Failed to fetch module code:', error)
    } finally {
      setLoadingCode(false)
    }
  }

  onMount(fetchModules)

  return (
    <div class="library-viewer">
      <div class="list-panel">
        <div class="panel-header">
          <h2>Library Modules</h2>
          <button onClick={fetchModules} class="refresh-btn">
            Refresh
          </button>
        </div>

        <Show when={loading()}>
          <div class="loading">Loading...</div>
        </Show>

        <Show when={!loading() && modules().length === 0}>
          <div class="empty">
            No library modules found.
            <br />
            <span class="hint">Create .lib.star files in automations/lib/</span>
          </div>
        </Show>

        <div class="list">
          <For each={modules()}>
            {(module) => (
              <div
                class={`module-item ${selectedModule() === module.name ? 'selected' : ''}`}
                onClick={() => fetchModuleCode(module.name)}
              >
                <div class="module-name">{module.name}</div>
                <Show when={module.description}>
                  <div class="module-desc">{module.description}</div>
                </Show>
                <div class="module-functions">
                  <For each={module.functions}>
                    {(func) => (
                      <span class="function-tag">{func}()</span>
                    )}
                  </For>
                </div>
              </div>
            )}
          </For>
        </div>
      </div>

      <div class="detail-panel">
        <Show when={selectedModule()}>
          <Show when={loadingCode()}>
            <div class="loading">Loading code...</div>
          </Show>
          <Show when={!loadingCode()}>
            <CodePreview
              code={selectedCode()}
              filename={`${selectedModule()}.lib.star`}
            />
          </Show>
        </Show>

        <Show when={!selectedModule()}>
          <div class="no-selection">
            Select a library module to view its source code
          </div>
        </Show>
      </div>
    </div>
  )
}
