<script setup lang="ts">
import { computed, defineAsyncComponent, ref, useId, useTemplateRef } from 'vue'

import type { Readout, SceneObject } from '@/api/scene'
import type { SimulationInfo } from '@/api/simulations'
import ClockControls from '@/components/ClockControls.vue'
import ParameterControls from '@/components/ParameterControls.vue'
import ReadoutList from '@/components/ReadoutList.vue'
import XrayControls from '@/components/XrayControls.vue'
import type { StreamStatus } from '@/composables/useSimulationStream'

// the panel loads when it first opens, with KaTeX and Shiki, so they burden no other page
const LessonPanel = defineAsyncComponent(() => import('@/components/LessonPanel.vue'))

const CONNECTION_LABELS: Record<StreamStatus, string> = {
  connecting: 'Connecting…',
  open: 'Live',
  closed: 'Disconnected',
  lost: 'Connection lost',
}

const {
  simulation,
  time,
  grabbable,
  selectable,
  sceneUnavailable,
  values,
  readouts = [],
  readoutValues = {},
  paused,
  speed = 1,
  xray = [],
  xrayShown,
  status,
  failure,
  restartPending,
} = defineProps<{
  /** the simulation with its parameters, `undefined` until the server described it */
  simulation: SimulationInfo | undefined
  /** simulated time like `t = 1.5 s`, shown in a corner of the scene */
  time: string
  /** the pointer can grab something in the scene and drag it around */
  grabbable?: boolean
  /** the pointer can select points of the scene, whose values the readouts report */
  selectable?: boolean
  sceneUnavailable?: boolean
  /** current value of every parameter, by name */
  values: Readonly<Record<string, number>>
  readouts?: readonly Readout[]
  /** latest value of every readout by name, NaN while it's unknown */
  readoutValues?: Readonly<Record<string, number>>
  paused?: boolean
  /** share of real time at which the room plays the simulation */
  speed?: number
  /** objects of the simulation's x-ray, none if it offers no x-ray */
  xray?: readonly SceneObject[]
  xrayShown?: boolean
  status: StreamStatus
  failure: string | undefined
  /** parameters that take effect on restart changed since the simulation started */
  restartPending?: boolean
}>()

const emit = defineEmits<{
  change: [name: string, value: number]
  reset: []
  restart: []
  /** an experiment of the lesson sets these values and the other parameters to their defaults */
  experiment: [values: Readonly<Record<string, number>>]
  pause: []
  play: []
  /** advance the paused simulation by one tick */
  step: []
  speed: [value: number]
  xray: [shown: boolean]
}>()

const title = computed(() => simulation?.name ?? 'Simulation')

const liveParameters = computed(
  () => simulation?.parameters.filter((parameter) => parameter.effect === 'LIVE') ?? [],
)
const restartParameters = computed(
  () => simulation?.parameters.filter((parameter) => parameter.effect === 'RESTART') ?? [],
)

const readoutsHeadingId = useId()
const restartHeadingId = useId()
const pendingNoteId = useId()

const hint = computed(() => `${pointerHint()} The mouse wheel zooms.`)

function pointerHint(): string {
  if (grabbable) {
    return 'Drag the surface to pull it, and drag anywhere else to turn the view.'
  }
  if (selectable) {
    return 'Click a point to read its values, and drag to turn the view.'
  }
  return 'Drag to turn the view.'
}

const canvasHost = useTemplateRef('canvasHost')

const lessonButton = useTemplateRef('lessonButton')
const lessonOpen = ref(false)
/** the panel stays in the page once it opened, with its lesson and scroll position */
const lessonOpenedOnce = ref(false)
/** parameter whose control and symbols light up while the pointer is at one of them */
const highlightedParameter = ref<string>()
/** readout that lights up with its symbols while the pointer is at one of them */
const highlightedReadout = ref<string>()

function toggleLesson(): void {
  lessonOpenedOnce.value = true
  lessonOpen.value = !lessonOpen.value
}

function closeLesson(): void {
  lessonOpen.value = false
  lessonButton.value?.focus()
}

/** lights up a parameter or a readout with the symbols that stand for it, and nothing else */
function highlight(parameter: string | undefined, readout: string | undefined): void {
  highlightedParameter.value = parameter
  highlightedReadout.value = readout
}

defineExpose({
  /** element the scene renders into */
  canvasHost,
})
</script>

<template>
  <article>
    <header class="intro">
      <h1>{{ title }}</h1>
      <template v-if="simulation">
        <p>{{ simulation.description }}</p>
        <button
          ref="lessonButton"
          type="button"
          class="lesson-button"
          :aria-expanded="lessonOpen"
          @click="toggleLesson"
        >
          How it works
        </button>
      </template>
    </header>

    <div class="workspace">
      <div class="viewport">
        <div
          ref="canvasHost"
          class="canvas-host"
          role="img"
          :aria-label="`${title}, live in 3D`"
        ></div>
        <p class="time">{{ time }}</p>
        <p v-if="sceneUnavailable" class="notice">This browser cannot display 3D graphics.</p>
      </div>

      <aside class="panel" aria-label="Simulation controls">
        <p class="connection" :data-status="status">{{ CONNECTION_LABELS[status] }}</p>
        <ClockControls
          :paused="paused"
          :speed="speed"
          :disabled="status !== 'open'"
          @pause="emit('pause')"
          @play="emit('play')"
          @step="emit('step')"
          @speed="(value) => emit('speed', value)"
        />
        <XrayControls
          v-if="xray.length > 0"
          :objects="xray"
          :shown="xrayShown"
          :disabled="status !== 'open'"
          @show="(shown) => emit('xray', shown)"
        />
        <ParameterControls
          v-if="liveParameters.length > 0"
          :parameters="liveParameters"
          :values="values"
          :disabled="status !== 'open'"
          :highlighted="highlightedParameter"
          @change="(name, value) => emit('change', name, value)"
          @highlight="(name) => highlight(name, undefined)"
        />
        <section v-if="readouts.length > 0" class="readouts" :aria-labelledby="readoutsHeadingId">
          <h2 :id="readoutsHeadingId">Readouts</h2>
          <ReadoutList
            :readouts="readouts"
            :values="readoutValues"
            :highlighted="highlightedReadout"
            @highlight="(name) => highlight(undefined, name)"
          />
        </section>
        <!-- the simulation that starts over reads these values, so they can change any time -->
        <section
          v-if="restartParameters.length > 0"
          class="restart-parameters"
          :aria-labelledby="restartHeadingId"
        >
          <h2 :id="restartHeadingId">Applies on restart</h2>
          <ParameterControls
            :parameters="restartParameters"
            :values="values"
            :highlighted="highlightedParameter"
            @change="(name, value) => emit('change', name, value)"
            @highlight="(name) => highlight(name, undefined)"
          />
        </section>
        <p class="hint">{{ hint }}</p>
        <div class="actions">
          <button type="button" :disabled="status !== 'open'" @click="emit('reset')">
            Reset parameters
          </button>
          <button
            type="button"
            :class="{ pending: restartPending }"
            :aria-describedby="restartPending ? pendingNoteId : undefined"
            @click="emit('restart')"
          >
            Restart
          </button>
        </div>
        <p v-if="restartPending" :id="pendingNoteId" class="pending-note">
          Restart to apply the changes.
        </p>
        <p v-if="failure" class="failure" role="alert">{{ failure }}</p>
      </aside>
    </div>

    <LessonPanel
      v-if="simulation && lessonOpenedOnce"
      :type="simulation.type"
      :open="lessonOpen"
      :parameters="simulation.parameters"
      :parameter-values="values"
      :readout-values="readoutValues"
      :highlighted-parameter="highlightedParameter"
      :highlighted-readout="highlightedReadout"
      :experiments-disabled="status !== 'open'"
      @close="closeLesson"
      @highlight="highlight"
      @experiment="(values) => emit('experiment', values)"
    />
  </article>
</template>

<style scoped>
/* the workspace lays out by the width of the page, which shrinks while the lesson is open */
article {
  container-type: inline-size;
}

.intro {
  max-width: 44rem;
  margin-bottom: 2rem;
}

.lesson-button {
  margin-top: 1rem;
  border-color: var(--color-link);
  color: var(--color-link);
}

.lesson-button[aria-expanded='true'] {
  background: var(--color-link);
  color: #ffffff;
}

h1 {
  margin: 0 0 0.75rem;
  font-size: clamp(1.75rem, 4vw, 2.5rem);
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.intro p {
  margin: 0;
  color: var(--color-text-muted);
}

.workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 17rem;
  gap: 1.5rem;
}

@container (width <= 45rem) {
  .workspace {
    grid-template-columns: minmax(0, 1fr);
  }
}

.viewport {
  position: relative;
  height: min(70vh, 40rem);
  overflow: hidden;
  border-radius: 0.75rem;
  background: #0d1117;
}

.canvas-host {
  position: absolute;
  inset: 0;
}

.canvas-host :deep(canvas) {
  display: block;
  width: 100%;
  height: 100%;
}

.time,
.notice {
  position: absolute;
  margin: 0;
  color: #9198a1;
  font-family: var(--font-mono);
  font-size: 0.875rem;
  pointer-events: none;
}

.time {
  top: 0.75rem;
  left: 1rem;
  font-variant-numeric: tabular-nums;
}

.notice {
  inset: 0;
  display: grid;
  place-items: center;
  padding: 1rem;
  text-align: center;
}

.panel {
  display: grid;
  align-content: start;
  gap: 1.5rem;
}

.connection {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
  color: var(--color-text-muted);
  font-size: 0.875rem;
}

.connection::before {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
  background: var(--color-neutral);
  content: '';
}

.connection[data-status='open']::before {
  background: var(--color-positive);
}

.connection[data-status='lost']::before {
  background: var(--color-negative);
}

.hint {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 0.875rem;
}

.readouts,
.restart-parameters {
  display: grid;
  gap: 1rem;
}

h2 {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.actions .pending {
  border-color: var(--color-link);
  background: var(--color-link);
  color: #ffffff;
}

.pending-note {
  margin: 0;
  color: var(--color-link);
  font-size: 0.875rem;
}

.failure {
  margin: 0;
  color: var(--color-negative);
}
</style>
