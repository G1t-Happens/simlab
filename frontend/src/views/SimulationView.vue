<script setup lang="ts">
import { computed, onMounted, useTemplateRef, watch } from 'vue'

import { readoutValues, sceneReadouts } from '@/api/scene'
import SimulationPage from '@/components/SimulationPage.vue'
import { useRenderer } from '@/composables/useRenderer'
import { useSimulationRoom } from '@/composables/useSimulationRoom'
import { SceneRenderer } from '@/rendering/SceneRenderer'
import { withUnit } from '@/units'

const { type } = defineProps<{
  /** the simulation as the HTTP API identifies it, another one starts the view over */
  type: string
}>()

const {
  simulation,
  parameters,
  status,
  scene,
  frame,
  failure,
  restartPending,
  paused,
  speed,
  send,
  start,
  changeParameter,
  resetParameters,
  runExperiment,
  pause,
  play,
  step,
  changeSpeed,
  xrayShown,
  showXray,
} = useSimulationRoom(type)

// typed explicitly: inferred from the template, the type of the page would depend on itself
// through the renderer that renders into it
const page = useTemplateRef<InstanceType<typeof SimulationPage>>('page')
const { renderer, unavailable } = useRenderer(
  () => page.value?.canvasHost,
  (container) =>
    new SceneRenderer(container, {
      grab: (vertex, { x, y, z }) => send({ type: 'grab', vertex, x, y, z }),
      drag: (vertex, { x, y, z }) => send({ type: 'drag', vertex, x, y, z }),
      release: (vertex) => send({ type: 'release', vertex }),
      select: (point) => send({ type: 'select', point }),
      deselect: () => send({ type: 'deselect' }),
    }),
)

const time = computed(
  () => `t = ${withUnit(frame.value?.time.toFixed(1) ?? '–', simulation.value?.timeUnit ?? '')}`,
)

const grabbable = computed(
  () => scene.value?.objects.some((object) => object.kind === 'Mesh' && object.grabbable) ?? false,
)
const selectable = computed(
  () =>
    scene.value?.objects.some((object) => object.kind === 'PointCloud' && object.selectable) ??
    false,
)

const readouts = computed(() => (scene.value ? sceneReadouts(scene.value) : []))
/** readout values in the latest frame, by name */
const readings = computed(() =>
  scene.value && frame.value ? readoutValues(scene.value, frame.value.payload) : {},
)

watch([renderer, scene], ([currentRenderer, currentScene]) => {
  if (currentRenderer && currentScene) {
    currentRenderer.show(currentScene)
  }
})
watch(frame, (current) => {
  if (current) {
    renderer.value?.update(current.payload)
  }
})

onMounted(() => {
  void start()
})
</script>

<template>
  <SimulationPage
    ref="page"
    :simulation="simulation"
    :time="time"
    :grabbable="grabbable"
    :selectable="selectable"
    :scene-unavailable="unavailable"
    :values="parameters"
    :readouts="readouts"
    :readout-values="readings"
    :paused="paused"
    :speed="speed"
    :xray="scene?.xray"
    :xray-shown="xrayShown"
    :status="status"
    :failure="failure"
    :restart-pending="restartPending"
    @change="changeParameter"
    @reset="resetParameters"
    @experiment="runExperiment"
    @restart="start"
    @pause="pause"
    @play="play"
    @step="step"
    @speed="changeSpeed"
    @xray="showXray"
  />
</template>
