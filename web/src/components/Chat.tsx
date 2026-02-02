import { createSignal, Show, For } from 'solid-js'
import { Send, Lightbulb, Zap, Clock, Command } from 'lucide-solid'
import CodePreview from './CodePreview'

interface FileProposal {
  code: string
  filename: string
  type: 'automation' | 'library'
}

interface CodeProposal {
  summary: string
  files: FileProposal[]
}

interface Message {
  role: 'user' | 'assistant'
  content: string
  codeProposal?: CodeProposal
}

interface ChatResponse {
  message: string
  code_proposal?: {
    summary: string
    files: {
      code: string
      filename: string
      type: string
    }[]
  }
}

const examplePrompts = [
  { text: 'What lights are available?', icon: Lightbulb },
  { text: 'What automations are running?', icon: Zap },
  { text: 'When motion is detected in the hallway, turn on the light for 2 minutes', icon: Clock },
  { text: 'Blink the kitchen light 3 times', icon: Command },
]

export default function Chat() {
  const [messages, setMessages] = createSignal<Message[]>([])
  const [input, setInput] = createSignal('')
  const [loading, setLoading] = createSignal(false)
  const [pendingProposal, setPendingProposal] = createSignal<CodeProposal | null>(null)

  const sendMessage = async (text?: string) => {
    const messageText = text || input().trim()
    if (!messageText || loading()) return

    const userMessage: Message = { role: 'user', content: messageText }
    setMessages([...messages(), userMessage])
    setInput('')
    setLoading(true)
    setPendingProposal(null)

    try {
      const history = messages().map((m) => ({
        role: m.role,
        content: m.content,
      }))

      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: messageText,
          conversation_history: history,
        }),
      })

      if (!response.ok) {
        throw new Error('Failed to get response')
      }

      const data: ChatResponse = await response.json()

      const assistantMessage: Message = {
        role: 'assistant',
        content: data.message,
        codeProposal: data.code_proposal
          ? {
              summary: data.code_proposal.summary,
              files: data.code_proposal.files.map((f) => ({
                code: f.code,
                filename: f.filename,
                type: f.type as 'automation' | 'library',
              })),
            }
          : undefined,
      }

      setMessages([...messages(), assistantMessage])

      if (data.code_proposal) {
        setPendingProposal({
          summary: data.code_proposal.summary,
          files: data.code_proposal.files.map((f) => ({
            code: f.code,
            filename: f.filename,
            type: f.type as 'automation' | 'library',
          })),
        })
      }
    } catch (error) {
      setMessages([
        ...messages(),
        {
          role: 'assistant',
          content: `Error: ${error instanceof Error ? error.message : 'Unknown error'}`,
        },
      ])
    } finally {
      setLoading(false)
    }
  }

  const deployCode = async () => {
    const proposal = pendingProposal()
    if (!proposal) return

    setLoading(true)
    try {
      const response = await fetch('/api/automations/deploy', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          files: proposal.files.map((f) => ({
            code: f.code,
            filename: f.filename,
            type: f.type,
          })),
        }),
      })

      if (!response.ok) {
        throw new Error('Failed to deploy')
      }

      const result = await response.json()
      const fileNames = result.files.map((f: { filename: string }) => f.filename).join(', ')

      setMessages([
        ...messages(),
        {
          role: 'assistant',
          content: `Deployed successfully: ${fileNames}`,
        },
      ])
      setPendingProposal(null)
    } catch (error) {
      setMessages([
        ...messages(),
        {
          role: 'assistant',
          content: `Error deploying: ${error instanceof Error ? error.message : 'Unknown error'}`,
        },
      ])
    } finally {
      setLoading(false)
    }
  }

  const cancelProposal = () => {
    setPendingProposal(null)
    setMessages([
      ...messages(),
      {
        role: 'assistant',
        content: 'Proposal cancelled. Let me know if you want to try something different.',
      },
    ])
  }

  const handleKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  const handleExampleClick = (prompt: string) => {
    setInput(prompt)
  }

  const getDeployBarText = () => {
    const proposal = pendingProposal()
    if (!proposal) return ''

    if (proposal.files.length === 1) {
      return `Ready to deploy: ${proposal.files[0].filename}`
    }

    const libraries = proposal.files.filter((f) => f.type === 'library')
    const automations = proposal.files.filter((f) => f.type === 'automation')

    if (libraries.length > 0 && automations.length > 0) {
      return `Ready to deploy: ${libraries.length} library + ${automations.length} automation`
    }

    return `Ready to deploy: ${proposal.files.length} files`
  }

  return (
    <div class="flex flex-col h-full">
      <div class="flex-1 overflow-auto p-6">
        <Show when={messages().length === 0}>
          <div class="flex flex-col items-center justify-center h-full max-w-lg mx-auto">
            <div class="text-center mb-8">
              <h1 class="text-sm font-mono text-foreground mb-2">homebrain assistant</h1>
              <p class="text-xs font-mono text-muted-foreground">
                control your smart home or create automations
              </p>
            </div>

            <div class="w-full">
              <p class="text-[10px] font-mono text-muted-foreground mb-2">examples:</p>
              
              <div class="space-y-1">
                <For each={examplePrompts}>
                  {(prompt) => {
                    const Icon = prompt.icon
                    return (
                      <button
                        onClick={() => handleExampleClick(prompt.text)}
                        class="flex items-center gap-3 w-full text-left px-3 py-2 border border-border bg-card hover:border-primary/50 transition-colors"
                      >
                        <Icon class="w-3 h-3 text-muted-foreground" />
                        <span class="text-xs font-mono text-muted-foreground hover:text-foreground">
                          {prompt.text}
                        </span>
                      </button>
                    )
                  }}
                </For>
              </div>
            </div>
          </div>
        </Show>

        <Show when={messages().length > 0}>
          <div class="max-w-2xl mx-auto space-y-2">
            <For each={messages()}>
              {(msg) => (
                <div
                  class={`p-3 border text-xs font-mono ${
                    msg.role === 'user'
                      ? 'border-primary/30 bg-primary/5 ml-8'
                      : 'border-border bg-card mr-8'
                  }`}
                >
                  <span class="text-[9px] text-muted-foreground block mb-1">
                    {msg.role === 'user' ? '> you' : '< assistant'}
                  </span>
                  <p class="text-foreground leading-relaxed whitespace-pre-wrap">{msg.content}</p>
                  <Show when={msg.codeProposal}>
                    <div class="mt-3 space-y-2">
                      <For each={msg.codeProposal!.files}>
                        {(file) => (
                          <CodePreview
                            code={file.code}
                            filename={file.filename}
                            type={file.type}
                          />
                        )}
                      </For>
                    </div>
                  </Show>
                </div>
              )}
            </For>

            <Show when={loading()}>
              <div class="p-3 border border-border bg-card mr-8 text-xs font-mono">
                <span class="text-[9px] text-muted-foreground block mb-1">{'< assistant'}</span>
                <p class="text-muted-foreground animate-pulse">thinking...</p>
              </div>
            </Show>
          </div>
        </Show>
      </div>

      {/* Deploy bar */}
      <Show when={pendingProposal()}>
        <div class="border-t border-success/30 bg-success/5 p-3">
          <div class="max-w-2xl mx-auto flex items-center justify-between">
            <span class="text-xs font-mono text-success">{getDeployBarText()}</span>
            <div class="flex gap-2">
              <button
                onClick={cancelProposal}
                disabled={loading()}
                class="px-2 py-1 text-[10px] font-mono text-muted-foreground border border-border hover:text-foreground hover:border-muted-foreground transition-colors disabled:opacity-50"
              >
                cancel
              </button>
              <button
                onClick={deployCode}
                disabled={loading()}
                class="px-2 py-1 text-[10px] font-mono text-success border border-success/30 hover:bg-success/10 transition-colors disabled:opacity-50"
              >
                deploy
              </button>
            </div>
          </div>
        </div>
      </Show>

      {/* Input */}
      <div class="border-t border-border p-4 bg-card">
        <div class="max-w-2xl mx-auto flex gap-2">
          <input
            type="text"
            value={input()}
            onInput={(e) => setInput(e.currentTarget.value)}
            onKeyDown={handleKeyDown}
            placeholder="ask or describe an automation..."
            disabled={loading()}
            class="flex-1 bg-background border border-border px-3 py-2 text-xs font-mono text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors disabled:opacity-50"
          />
          <button
            onClick={() => sendMessage()}
            disabled={loading() || !input().trim()}
            class="px-3 py-2 bg-primary text-primary-foreground text-xs font-mono hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Send class="h-3 w-3" />
          </button>
        </div>
      </div>
    </div>
  )
}
