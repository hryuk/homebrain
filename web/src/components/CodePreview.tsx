import { createSignal, createEffect } from 'solid-js'
import hljs from 'highlight.js/lib/core'
import python from 'highlight.js/lib/languages/python'
import './CodePreview.css'

// Register Python language (Starlark is Python-like)
hljs.registerLanguage('python', python)

interface CodePreviewProps {
  code: string
  filename: string
  editable?: boolean
  onCodeChange?: (code: string) => void
}

export default function CodePreview(props: CodePreviewProps) {
  const [copied, setCopied] = createSignal(false)
  const [code, setCode] = createSignal(props.code)
  let codeRef: HTMLElement | undefined

  // Update code when props change
  createEffect(() => {
    setCode(props.code)
  })

  // Apply syntax highlighting when code changes (read-only mode)
  createEffect(() => {
    if (codeRef && !props.editable) {
      const currentCode = code()
      codeRef.textContent = currentCode
      hljs.highlightElement(codeRef)
    }
  })

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
          <code ref={codeRef} class="language-python">{code()}</code>
        </pre>
      )}
    </div>
  )
}
