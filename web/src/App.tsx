import { createSignal, Show } from 'solid-js'
import Chat from './components/Chat'
import AutomationList from './components/AutomationList'
import LogViewer from './components/LogViewer'

import './App.css'

type Tab = 'chat' | 'automations' | 'logs'

export default function App() {
  const [activeTab, setActiveTab] = createSignal<Tab>('chat')

  return (
    <div class="app">
      <header class="header">
        <h1>Homebrain</h1>
        <nav class="tabs">
          <button
            class={activeTab() === 'chat' ? 'active' : ''}
            onClick={() => setActiveTab('chat')}
          >
            Chat
          </button>
          <button
            class={activeTab() === 'automations' ? 'active' : ''}
            onClick={() => setActiveTab('automations')}
          >
            Automations
          </button>
          <button
            class={activeTab() === 'logs' ? 'active' : ''}
            onClick={() => setActiveTab('logs')}
          >
            Logs
          </button>
        </nav>
      </header>

      <main class="main">
        <Show when={activeTab() === 'chat'}>
          <Chat />
        </Show>
        <Show when={activeTab() === 'automations'}>
          <AutomationList />
        </Show>
        <Show when={activeTab() === 'logs'}>
          <LogViewer />
        </Show>
      </main>
    </div>
  )
}
