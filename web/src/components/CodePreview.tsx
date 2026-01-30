import { createSignal, createEffect, onMount, onCleanup, Show } from 'solid-js'
import { EditorView, basicSetup } from 'codemirror'
import { EditorState } from '@codemirror/state'
import { python } from '@codemirror/lang-python'
import { oneDark } from '@codemirror/theme-one-dark'
import './CodePreview.css'

interface CodePreviewProps {
  code: string
  filename: string
  type?: 'automation' | 'library'
  editable?: boolean
  onCodeChange?: (code: string) => void
}

export default function CodePreview(props: CodePreviewProps) {
  const [copied, setCopied] = createSignal(false)
  let containerRef: HTMLDivElement | undefined
  let editorView: EditorView | undefined

  onMount(() => {
    if (!containerRef) return

    const extensions = [
      basicSetup,
      python(),
      oneDark,
      EditorState.readOnly.of(!props.editable),
      EditorView.updateListener.of((update) => {
        if (update.docChanged && props.editable && props.onCodeChange) {
          props.onCodeChange(update.state.doc.toString())
        }
      }),
      // Custom styling to integrate with our component
      EditorView.theme({
        '&': {
          fontSize: '0.9rem',
        },
        '.cm-scroller': {
          fontFamily: "'Monaco', 'Menlo', 'Consolas', monospace",
        },
        '.cm-content': {
          padding: '0.5rem 0',
        },
        '.cm-gutters': {
          backgroundColor: '#1a1a1a',
          borderRight: '1px solid #333',
        },
        '&.cm-focused': {
          outline: 'none',
        },
      }),
    ]

    editorView = new EditorView({
      state: EditorState.create({
        doc: props.code,
        extensions,
      }),
      parent: containerRef,
    })
  })

  // Sync code changes from props (when code prop changes externally)
  createEffect(() => {
    const newCode = props.code
    if (editorView && newCode !== editorView.state.doc.toString()) {
      editorView.dispatch({
        changes: {
          from: 0,
          to: editorView.state.doc.length,
          insert: newCode,
        },
      })
    }
  })

  onCleanup(() => {
    editorView?.destroy()
  })

  const copyCode = async () => {
    const code = editorView?.state.doc.toString() || props.code
    await navigator.clipboard.writeText(code)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const getTypeLabel = () => {
    if (props.type === 'library') return 'Library'
    if (props.type === 'automation') return 'Automation'
    return null
  }

  return (
    <div class={`code-preview ${props.type === 'library' ? 'library' : ''}`}>
      <div class="code-header">
        <div class="code-header-left">
          <Show when={getTypeLabel()}>
            <span class={`file-type-badge ${props.type}`}>{getTypeLabel()}</span>
          </Show>
          <span class="filename">{props.filename}</span>
        </div>
        <button class="copy-btn" onClick={copyCode}>
          {copied() ? 'Copied!' : 'Copy'}
        </button>
      </div>
      <div class="code-editor-container" ref={containerRef} />
    </div>
  )
}
