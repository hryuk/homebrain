import { createSignal, createEffect, onMount, onCleanup, Show } from 'solid-js'
import { EditorView, basicSetup } from 'codemirror'
import { EditorState } from '@codemirror/state'
import { python } from '@codemirror/lang-python'
import { oneDark } from '@codemirror/theme-one-dark'
import { Copy, Edit2, Trash2, Check, X } from 'lucide-solid'

interface CodePreviewProps {
  code: string
  filename: string
  type?: 'automation' | 'library'
  showActions?: boolean
  editable?: boolean
  isEditing?: boolean
  onCodeChange?: (code: string) => void
  onEdit?: () => void
  onSave?: () => void
  onCancel?: () => void
  onDelete?: () => void
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
      EditorView.theme({
        '&': {
          fontSize: '12px',
          backgroundColor: 'hsl(0 0% 5%)',
        },
        '.cm-scroller': {
          fontFamily: "'JetBrains Mono', monospace",
        },
        '.cm-content': {
          padding: '12px 0',
        },
        '.cm-gutters': {
          backgroundColor: 'hsl(0 0% 5%)',
          borderRight: '1px solid hsl(0 0% 12%)',
          color: 'hsl(0 0% 35%)',
        },
        '.cm-lineNumbers .cm-gutterElement': {
          padding: '0 12px 0 8px',
          minWidth: '32px',
        },
        '&.cm-focused': {
          outline: 'none',
        },
        '.cm-activeLine': {
          backgroundColor: 'hsl(0 0% 8%)',
        },
        '.cm-selectionBackground': {
          backgroundColor: 'hsl(0 70% 50% / 0.2) !important',
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

  const handleCopy = async () => {
    const code = editorView?.state.doc.toString() || props.code
    await navigator.clipboard.writeText(code)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div class="flex flex-col h-full">
      {/* Header */}
      <div class="flex items-center justify-between mb-2">
        <span class="text-xs font-mono text-muted-foreground">{props.filename}</span>
        <Show when={props.showActions}>
          <div class="flex items-center gap-1">
            <Show when={props.isEditing}>
              <button
                onClick={props.onSave}
                class="flex items-center gap-1 px-2 py-1 text-[10px] font-mono text-success border border-success/30 hover:bg-success/10 transition-colors"
              >
                <Check class="h-2.5 w-2.5" />
                save
              </button>
              <button
                onClick={props.onCancel}
                class="flex items-center gap-1 px-2 py-1 text-[10px] font-mono text-muted-foreground border border-border hover:text-foreground hover:border-muted-foreground transition-colors"
              >
                <X class="h-2.5 w-2.5" />
                cancel
              </button>
            </Show>
            <Show when={!props.isEditing}>
              <button
                onClick={props.onEdit}
                class="flex items-center gap-1 px-2 py-1 text-[10px] font-mono text-muted-foreground border border-border hover:text-foreground hover:border-primary transition-colors"
              >
                <Edit2 class="h-2.5 w-2.5" />
                edit
              </button>
              <button
                onClick={props.onDelete}
                class="flex items-center gap-1 px-2 py-1 text-[10px] font-mono text-destructive border border-border hover:border-destructive transition-colors"
              >
                <Trash2 class="h-2.5 w-2.5" />
                delete
              </button>
            </Show>
          </div>
        </Show>
      </div>

      {/* Code container */}
      <div class="flex-1 border border-border bg-card overflow-hidden">
        {/* File tab */}
        <div class="flex items-center justify-between px-3 py-1.5 border-b border-border bg-secondary/30">
          <span class="text-[10px] font-mono text-muted-foreground">{props.filename}</span>
          <button
            onClick={handleCopy}
            class="flex items-center gap-1 text-[9px] font-mono text-muted-foreground hover:text-foreground transition-colors"
          >
            <Show when={copied()} fallback={<Copy class="h-2.5 w-2.5" />}>
              <Check class="h-2.5 w-2.5 text-success" />
            </Show>
            {copied() ? 'copied!' : 'copy'}
          </button>
        </div>

        {/* Code editor */}
        <div
          ref={containerRef}
          class="overflow-auto max-h-[calc(100vh-12rem)] [&_.cm-editor]:!bg-card"
        />
      </div>
    </div>
  )
}
