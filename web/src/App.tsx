import { createSignal, Show, For } from 'solid-js'
import { 
  MessageSquare, 
  Zap, 
  Library, 
  Database, 
  Radio, 
  Terminal,
  Brain
} from 'lucide-solid'
import Chat from './components/Chat'
import AutomationList from './components/AutomationList'
import LibraryViewer from './components/LibraryViewer'
import GlobalStateViewer from './components/GlobalStateViewer'
import LogViewer from './components/LogViewer'
import MqttViewer from './components/MqttViewer'

type Tab = 'chat' | 'automations' | 'libraries' | 'state' | 'mqtt' | 'logs'

const navItems: { id: Tab; label: string; icon: any }[] = [
  { id: 'chat', label: 'chat', icon: MessageSquare },
  { id: 'automations', label: 'automations', icon: Zap },
  { id: 'libraries', label: 'libraries', icon: Library },
  { id: 'state', label: 'state', icon: Database },
  { id: 'mqtt', label: 'mqtt', icon: Radio },
  { id: 'logs', label: 'logs', icon: Terminal },
]

export default function App() {
  const [activeTab, setActiveTab] = createSignal<Tab>('chat')

  return (
    <div class="min-h-screen bg-background">
      {/* Header */}
      <header class="h-10 border-b border-border bg-card">
        <div class="flex h-full items-center px-4">
          <div class="flex items-center gap-1.5 mr-6">
            <Brain class="h-4 w-4 text-primary" />
            <span class="text-xs font-mono text-foreground">hb</span>
          </div>

          <nav class="flex items-center h-full">
            <For each={navItems}>
              {(item) => {
                const Icon = item.icon
                return (
                  <button
                    onClick={() => setActiveTab(item.id)}
                    class={`flex items-center gap-1.5 h-full px-3 text-xs font-mono border-b transition-colors ${
                      activeTab() === item.id
                        ? 'text-primary border-primary'
                        : 'text-muted-foreground border-transparent hover:text-foreground'
                    }`}
                  >
                    <Icon class="h-3 w-3" />
                    <span class="hidden md:inline">{item.label}</span>
                  </button>
                )
              }}
            </For>
          </nav>
        </div>
      </header>

      {/* Main content */}
      <main class="h-[calc(100vh-2.5rem)]">
        <Show when={activeTab() === 'chat'}>
          <Chat />
        </Show>
        <Show when={activeTab() === 'automations'}>
          <AutomationList />
        </Show>
        <Show when={activeTab() === 'libraries'}>
          <LibraryViewer />
        </Show>
        <Show when={activeTab() === 'state'}>
          <GlobalStateViewer />
        </Show>
        <Show when={activeTab() === 'mqtt'}>
          <MqttViewer />
        </Show>
        <Show when={activeTab() === 'logs'}>
          <LogViewer />
        </Show>
      </main>
    </div>
  )
}
