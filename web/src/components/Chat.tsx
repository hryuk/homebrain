import { createSignal, Show, For } from 'solid-js'
import CodePreview from './CodePreview'
import './Chat.css'

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

export default function Chat() {
  const [messages, setMessages] = createSignal<Message[]>([])
  const [input, setInput] = createSignal('')
  const [loading, setLoading] = createSignal(false)
  const [pendingProposal, setPendingProposal] = createSignal<CodeProposal | null>(null)

  const sendMessage = async () => {
    const text = input().trim()
    if (!text || loading()) return

    // Add user message
    const userMessage: Message = { role: 'user', content: text }
    setMessages([...messages(), userMessage])
    setInput('')
    setLoading(true)
    setPendingProposal(null)

    try {
      // Build conversation history for context
      const history = messages().map((m) => ({
        role: m.role,
        content: m.content,
      }))

      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: text,
          conversation_history: history,
        }),
      })

      if (!response.ok) {
        throw new Error('Failed to get response')
      }

      const data: ChatResponse = await response.json()

      // Add assistant message
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

      // Set pending proposal if there's code to deploy
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
      // Use multi-deploy endpoint for all proposals (supports both single and multi-file)
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

  // Helper to get deploy bar text
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
    <div class="chat">
      <div class="chat-messages">
        <Show when={messages().length === 0}>
          <div class="chat-welcome">
            <h2>Your Smart Home Assistant</h2>
            <p>Ask me anything about your smart home or describe automations you'd like to create.</p>
            <div class="examples">
              <p>Examples:</p>
              <ul>
                <li>"What lights are available?"</li>
                <li>"What automations are running?"</li>
                <li>"When motion is detected in the hallway, turn on the light for 2 minutes"</li>
                <li>"Blink the kitchen light 3 times"</li>
              </ul>
            </div>
          </div>
        </Show>

        <For each={messages()}>
          {(msg) => (
            <div class={`message ${msg.role}`}>
              <div class="message-content">{msg.content}</div>
              <Show when={msg.codeProposal}>
                <For each={msg.codeProposal!.files}>
                  {(file) => <CodePreview code={file.code} filename={file.filename} type={file.type} />}
                </For>
              </Show>
            </div>
          )}
        </For>

        <Show when={loading()}>
          <div class="message assistant">
            <div class="message-content loading">Thinking...</div>
          </div>
        </Show>
      </div>

      <Show when={pendingProposal()}>
        <div class="deploy-bar">
          <span>{getDeployBarText()}</span>
          <div class="deploy-actions">
            <button class="cancel-btn" onClick={cancelProposal} disabled={loading()}>
              Cancel
            </button>
            <button class="deploy-btn" onClick={deployCode} disabled={loading()}>
              Deploy
            </button>
          </div>
        </div>
      </Show>

      <div class="chat-input">
        <textarea
          value={input()}
          onInput={(e) => setInput(e.currentTarget.value)}
          onKeyDown={handleKeyDown}
          placeholder="Ask about your smart home or describe an automation..."
          disabled={loading()}
          rows={2}
        />
        <button onClick={sendMessage} disabled={loading() || !input().trim()}>
          Send
        </button>
      </div>
    </div>
  )
}
