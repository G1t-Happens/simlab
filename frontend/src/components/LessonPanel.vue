<script setup lang="ts">
import { computed, ref, shallowRef, useId, useTemplateRef, watch, watchPostEffect } from 'vue'

import type { CodeBlock, LessonSymbol } from '@/api/lessons'
import { fetchLesson, type ParameterInfo } from '@/api/simulations'
import CodeExcerpt from '@/components/CodeExcerpt.vue'
import LessonCalculation from '@/components/LessonCalculation.vue'
import LessonExperiment from '@/components/LessonExperiment.vue'
import { highlightJava } from '@/lesson/highlighter'
import { renderMarkdown } from '@/lesson/markdown'

/** a lesson block rendered as HTML, except calculations, which render as values change */
type RenderedBlock =
  | { readonly kind: 'Text'; readonly html: string }
  | { readonly kind: 'Code'; readonly block: CodeBlock; readonly html: string }
  | {
      readonly kind: 'Experiment'
      readonly question: string
      readonly explanation: string
      readonly values: Readonly<Record<string, number>>
    }
  | { readonly kind: 'Calculation'; readonly formula: string }

const {
  type,
  open,
  parameters,
  parameterValues,
  readoutValues,
  highlightedParameter,
  highlightedReadout,
  experimentsDisabled,
} = defineProps<{
  type: string
  /** whether the panel shows. It keeps its lesson and scroll position while it doesn't */
  open: boolean
  parameters: readonly ParameterInfo[]
  /** current value of every parameter by name, for the calculations */
  parameterValues: Readonly<Record<string, number>>
  /** latest value of every readout by name, for the calculations. NaN while unknown */
  readoutValues: Readonly<Record<string, number>>
  /** parameter the pointer is at among the controls, its symbols light up */
  highlightedParameter: string | undefined
  /** readout the pointer is at among the readouts, its symbols light up */
  highlightedReadout: string | undefined
  /** experiments can't set parameters, e.g. while the simulation isn't connected */
  experimentsDisabled?: boolean
}>()

const emit = defineEmits<{
  close: []
  /** pointer moved to a symbol (which can stand for a parameter or a readout) or away from it */
  highlight: [parameter: string | undefined, readout: string | undefined]
  /** an experiment sets the given parameter values, and the others to their defaults */
  experiment: [values: Readonly<Record<string, number>>]
}>()

const symbols = shallowRef<readonly LessonSymbol[]>([])
const blocks = shallowRef<readonly RenderedBlock[]>()
const failure = ref<string>()
const headingId = useId()
const panel = useTemplateRef('panel')
const lesson = useTemplateRef('lesson')

/** symbol the pointer is at in a formula or in the code */
const pointedSymbol = ref<string>()

/** the symbol the pointer is at, or the symbols of the parameter or readout it's at */
const highlightedSymbols = computed(() =>
  pointedSymbol.value === undefined
    ? symbols.value
        .filter(
          ({ parameter, readout }) =>
            (parameter !== null && parameter === highlightedParameter) ||
            (readout !== null && readout === highlightedReadout),
        )
        .map((symbol) => symbol.name)
    : [pointedSymbol.value],
)

/**
 * Values of the symbols the calculations show, by symbol name. None while the panel is closed, so
 * it renders no calculations nobody sees
 */
const symbolValues = computed(() => {
  const values: Record<string, number> = {}
  if (open) {
    for (const symbol of symbols.value) {
      const value = symbolValue(symbol)
      if (value !== undefined) {
        values[symbol.name] = value
      }
    }
  }
  return values
})

function symbolValue({ parameter, readout }: LessonSymbol): number | undefined {
  if (parameter !== null) {
    return parameterValues[parameter]
  }
  return readout === null ? undefined : readoutValues[readout]
}

const hint = computed(() =>
  symbols.value.some((symbol) => symbol.readout !== null)
    ? 'Point at a symbol in a formula to find it in the code, among the controls and the readouts.'
    : 'Point at a symbol in a formula to find it in the code and among the controls.',
)

async function load(): Promise<void> {
  try {
    const loaded = await fetchLesson(type)
    blocks.value = await Promise.all(
      loaded.blocks.map(async (block): Promise<RenderedBlock> => {
        switch (block.kind) {
          case 'Text':
            return { kind: 'Text', html: renderMarkdown(block.markdown) }
          case 'Code':
            return { kind: 'Code', block, html: await highlightJava(block) }
          case 'Experiment':
            return {
              kind: 'Experiment',
              question: renderMarkdown(block.question),
              explanation: renderMarkdown(block.explanation),
              values: block.values,
            }
          case 'Calculation':
            return block
        }
      }),
    )
    symbols.value = loaded.symbols
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error)
    failure.value = `The lesson could not be loaded: ${reason}`
  }
}

function pointAt(target: EventTarget | null): void {
  const symbol =
    target instanceof Element
      ? target.closest<HTMLElement>('[data-symbol]')?.dataset.symbol
      : undefined
  if (symbol !== pointedSymbol.value) {
    pointedSymbol.value = symbol
    const pointed = symbols.value.find((candidate) => candidate.name === symbol)
    emit('highlight', pointed?.parameter ?? undefined, pointed?.readout ?? undefined)
  }
}

// every time the panel opens the focus moves into it, so keyboards and screen readers follow
watchPostEffect(() => {
  if (open) {
    panel.value?.focus()
  }
})

// Markdown, Shiki and KaTeX render the marks of symbols, not Vue, so their classes are set here -
// also after calculations rendered their new values
watch(
  [highlightedSymbols, blocks, symbolValues],
  ([highlighted]) => {
    for (const mark of lesson.value?.querySelectorAll<HTMLElement>('[data-symbol]') ?? []) {
      mark.classList.toggle('highlighted', highlighted.includes(mark.dataset.symbol ?? ''))
    }
  },
  { flush: 'post' },
)

void load()
</script>

<template>
  <section
    ref="panel"
    class="lesson-panel"
    role="dialog"
    :aria-labelledby="headingId"
    tabindex="-1"
    :hidden="!open"
    @keydown.esc="emit('close')"
  >
    <header>
      <h2 :id="headingId">How it works</h2>
      <button type="button" class="close" aria-label="Close" @click="emit('close')">×</button>
    </header>
    <div
      ref="lesson"
      class="lesson"
      @mouseover="pointAt($event.target)"
      @mouseleave="pointAt(null)"
    >
      <p v-if="failure" class="failure" role="alert">{{ failure }}</p>
      <p v-else-if="blocks === undefined" class="loading">Loading…</p>
      <template v-else>
        <p v-if="symbols.length > 0" class="hint">{{ hint }}</p>
        <template v-for="(block, index) in blocks" :key="index">
          <!-- markdown-it keeps raw HTML as text, KaTeX adds no HTML but marks of symbols -->
          <!-- eslint-disable-next-line vue/no-v-html -->
          <div v-if="block.kind === 'Text'" class="text" v-html="block.html"></div>
          <CodeExcerpt v-else-if="block.kind === 'Code'" :block="block.block" :html="block.html" />
          <LessonCalculation
            v-else-if="block.kind === 'Calculation'"
            :formula="block.formula"
            :values="symbolValues"
          />
          <LessonExperiment
            v-else
            :question="block.question"
            :explanation="block.explanation"
            :values="block.values"
            :parameters="parameters"
            :disabled="experimentsDisabled"
            @run="emit('experiment', block.values)"
          />
        </template>
      </template>
    </div>
  </section>
</template>

<style scoped>
:global(:root) {
  --lesson-panel-width: clamp(24rem, 40vw, 44rem);
  --lesson-sheet-height: 70dvh;
}

.lesson-panel {
  position: fixed;
  z-index: 10;
  inset: 0 0 0 auto;
  display: flex;
  flex-direction: column;
  width: var(--lesson-panel-width);
  border-left: 1px solid var(--color-border);
  background: var(--color-background);
  box-shadow: -0.5rem 0 1.5rem rgb(0 0 0 / 12%);
}

.lesson-panel[hidden] {
  display: none;
}

.lesson-panel:focus {
  outline: none;
}

@media (width < 64rem) {
  .lesson-panel {
    inset: auto 0 0;
    width: auto;
    height: var(--lesson-sheet-height);
    border-top: 1px solid var(--color-border);
    border-left: none;
    border-radius: 0.75rem 0.75rem 0 0;
    box-shadow: 0 -0.5rem 1.5rem rgb(0 0 0 / 12%);
  }
}

/* the page makes room for the panel so the simulation and its controls stay in view */
@media (width >= 64rem) {
  :global(body:has(.lesson-panel:not([hidden]))) {
    padding-right: var(--lesson-panel-width);
  }
}

@media (width < 64rem) {
  :global(body:has(.lesson-panel:not([hidden]))) {
    padding-bottom: var(--lesson-sheet-height);
  }
}

header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem 1rem 0.75rem 1.5rem;
  border-bottom: 1px solid var(--color-border);
}

h2 {
  margin: 0;
  font-size: 1.125rem;
}

.close {
  padding: 0.125rem 0.625rem;
  font-size: 1.25rem;
  line-height: 1.4;
}

.lesson {
  flex: 1;
  padding: 0.5rem 1.5rem 2rem;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.loading,
.hint {
  color: var(--color-text-muted);
}

.hint {
  font-size: 0.875rem;
}

.failure {
  color: var(--color-negative);
}

/* a symbol lights up in the formulas and the code at once, like the controls light it up */
.lesson :deep([data-symbol].highlighted) {
  border-radius: 0.2em;
  background: var(--color-highlight);
  box-shadow: 0 0 0 0.15em var(--color-highlight);
}

.text :deep(h3) {
  margin: 2rem 0 0.75rem;
  font-size: 1.125rem;
  letter-spacing: -0.01em;
}

.text :deep(h4) {
  margin: 1.5rem 0 0.5rem;
  font-size: 1rem;
}

.text :deep(p),
.text :deep(ul),
.text :deep(ol) {
  margin: 0.75rem 0;
}

.text :deep(code) {
  padding: 0.1em 0.3em;
  border-radius: 0.25rem;
  background: rgb(127 127 127 / 15%);
  font-family: var(--font-mono);
  font-size: 0.875em;
}

/* tables too wide for the panel scroll sideways instead of overflowing it */
.text :deep(table) {
  display: block;
  width: max-content;
  max-width: 100%;
  margin: 1rem 0;
  overflow-x: auto;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.text :deep(th),
.text :deep(td) {
  padding: 0.375rem 0.5rem;
  border-bottom: 1px solid var(--color-border);
  text-align: left;
  vertical-align: top;
}

/* links (like those to the works a lesson cites) break anywhere rather than overflow */
.text :deep(a) {
  overflow-wrap: anywhere;
}

.text :deep(.katex) {
  font-size: 1.1em;
}

/* formulas too wide for the panel scroll sideways instead of overflowing it */
.text :deep(.katex-display) {
  padding: 0.25rem 0;
  overflow: auto hidden;
}
</style>
