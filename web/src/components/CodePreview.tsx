import { createSignal } from 'solid-js'
import './CodePreview.css'

interface CodePreviewProps {
  code: string
  filename: string
  editable?: boolean
  onCodeChange?: (code: string) => void
}

export default function CodePreview(props: CodePreviewProps) {
  const [copied, setCopied] = createSignal(false)
  const [code, setCode] = createSignal(props.code)

  const copyCode = async () => {
    await navigator.clipboard.writeText(code())
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const handleCodeChange = (e: Event) => {
    const newCode = (e.target as HTMLTextAreaElement).value
    setCode(newCode)
    props.onCodeChange?.(newCode)
  }

  return (
    <div class="code-preview">
      <div class="code-header">
        <span class="filename">{props.filename}</span>
        <button class="copy-btn" onClick={copyCode}>
          {copied() ? 'Copied!' : 'Copy'}
        </button>
      </div>
      {props.editable ? (
        <textarea
          class="code-editor"
          value={code()}
          onInput={handleCodeChange}
          spellcheck={false}
        />
      ) : (
        <pre class="code-content">
          <code>{code()}</code>
        </pre>
      )}
    </div>
  )
}
