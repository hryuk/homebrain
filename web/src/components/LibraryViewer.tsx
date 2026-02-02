import { createSignal, onMount, For, Show } from 'solid-js'
import { RefreshCw, ChevronRight, FolderOpen, File, Trash2 } from 'lucide-solid'
import CodePreview from './CodePreview'

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
  const [deleting, setDeleting] = createSignal(false)
  const [expandedModules, setExpandedModules] = createSignal<string[]>([])

  const fetchModules = async () => {
    try {
      const response = await fetch('/api/libraries')
      if (response.ok) {
        const data = await response.json()
        setModules(data || [])
        // Auto-expand all modules
        setExpandedModules((data || []).map((m: LibraryModule) => m.name))
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

  const deleteModule = async (name: string) => {
    if (!confirm(`Are you sure you want to delete the library "${name}"?`)) return

    setDeleting(true)
    try {
      const response = await fetch(`/api/libraries/${name}`, {
        method: 'DELETE',
      })

      if (response.ok) {
        setSelectedModule(null)
        setSelectedCode('')
        fetchModules()
      }
    } catch (error) {
      console.error('Failed to delete library:', error)
    } finally {
      setDeleting(false)
    }
  }

  const toggleModule = (name: string) => {
    setExpandedModules((prev) =>
      prev.includes(name) ? prev.filter((m) => m !== name) : [...prev, name]
    )
  }

  onMount(fetchModules)

  return (
    <div class="flex h-full">
      {/* Sidebar */}
      <aside class="w-56 border-r border-border p-3 overflow-auto bg-card">
        <div class="flex items-center justify-between mb-3">
          <span class="text-[10px] font-mono text-muted-foreground">libraries</span>
          <button
            onClick={fetchModules}
            class="p-1 text-muted-foreground hover:text-foreground transition-colors"
          >
            <RefreshCw class="h-3 w-3" />
          </button>
        </div>

        <Show when={loading()}>
          <div class="text-xs font-mono text-muted-foreground p-2">Loading...</div>
        </Show>

        <Show when={!loading() && modules().length === 0}>
          <div class="text-xs font-mono text-muted-foreground p-2">
            No library modules found.
            <span class="block text-[10px] mt-1">Create .lib.star files in automations/lib/</span>
          </div>
        </Show>

        <div class="space-y-px">
          <For each={modules()}>
            {(module) => (
              <div>
                <button
                  onClick={() => toggleModule(module.name)}
                  class="flex items-center gap-1.5 w-full text-left py-1.5 px-2 hover:bg-muted/30 transition-colors"
                >
                  <ChevronRight
                    class={`h-3 w-3 text-muted-foreground transition-transform ${
                      expandedModules().includes(module.name) ? 'rotate-90' : ''
                    }`}
                  />
                  <FolderOpen class="h-3 w-3 text-muted-foreground" />
                  <span class="font-mono text-xs text-foreground">{module.name}</span>
                </button>

                <Show when={expandedModules().includes(module.name)}>
                  <div class="ml-3 pl-3 border-l border-border">
                    <For each={module.functions}>
                      {(fn) => (
                        <button
                          onClick={() => fetchModuleCode(module.name)}
                          class={`flex items-center gap-1.5 w-full text-left py-1 px-2 text-[10px] font-mono transition-colors ${
                            selectedModule() === module.name
                              ? 'text-primary bg-primary/10'
                              : 'text-muted-foreground hover:text-foreground'
                          }`}
                        >
                          <File class="h-2.5 w-2.5" />
                          {fn}()
                        </button>
                      )}
                    </For>
                  </div>
                </Show>
              </div>
            )}
          </For>
        </div>
      </aside>

      {/* Main content */}
      <div class="flex-1 p-4 overflow-auto">
        <Show when={selectedModule()}>
          <Show when={loadingCode()}>
            <div class="text-xs font-mono text-muted-foreground">Loading code...</div>
          </Show>
          <Show when={!loadingCode()}>
            <div class="flex flex-col h-full">
              {/* Header */}
              <div class="flex items-center justify-between mb-2">
                <span class="text-xs font-mono text-muted-foreground">
                  {selectedModule()}.lib.star
                </span>
                <button
                  onClick={() => deleteModule(selectedModule()!)}
                  disabled={deleting()}
                  class="flex items-center gap-1 px-2 py-1 text-[10px] font-mono text-destructive border border-border hover:border-destructive transition-colors disabled:opacity-50"
                >
                  <Trash2 class="h-2.5 w-2.5" />
                  {deleting() ? 'deleting...' : 'delete'}
                </button>
              </div>

              {/* Code container */}
              <div class="flex-1 border border-border bg-card overflow-hidden">
                <div class="flex items-center justify-between px-3 py-1.5 border-b border-border bg-secondary/30">
                  <span class="text-[10px] font-mono text-muted-foreground">
                    {selectedModule()}.lib.star
                  </span>
                </div>
                <pre class="p-3 overflow-auto max-h-[calc(100vh-12rem)] text-xs leading-relaxed font-mono text-foreground">
                  {selectedCode()}
                </pre>
              </div>
            </div>
          </Show>
        </Show>

        <Show when={!selectedModule()}>
          <div class="flex items-center justify-center h-full text-xs font-mono text-muted-foreground">
            Select a library module to view its source code
          </div>
        </Show>
      </div>
    </div>
  )
}
