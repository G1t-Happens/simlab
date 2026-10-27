import {
  computed,
  readonly,
  ref,
  shallowReadonly,
  shallowRef,
  watch,
  type ComputedRef,
  type Ref,
  type ShallowRef,
} from 'vue'

import { ApiError, openRoom, type SimulationInfo } from '@/api/simulations'
import { useSimulationsStore } from '@/stores/simulations'

import { injectBackendHealth } from './useBackendHealth'
import { useSimulationStream, type SimulationStream } from './useSimulationStream'

export interface SimulationRoom extends SimulationStream {
  /** the simulation with its parameters, `undefined` until the server described it */
  readonly simulation: Readonly<ShallowRef<SimulationInfo | undefined>>
  /** current value of every parameter, by name */
  readonly parameters: Readonly<Ref<Readonly<Record<string, number>>>>
  /** changes whenever the simulation starts over in a new room */
  readonly roomId: Readonly<Ref<string | undefined>>
  /** why the simulation couldn't be started, `undefined` unless it failed to */
  readonly failure: Readonly<Ref<string | undefined>>
  /**
   * Whether parameters that take effect on restart changed since the simulation in the current room
   * started
   */
  readonly restartPending: ComputedRef<boolean>
  /** opens a new room with the current parameters and joins it, the simulation starts over */
  readonly start: () => Promise<void>
  /**
   * Changes a parameter of the running simulation if the parameter changes live, otherwise of the
   * simulation the next start opens a room for
   */
  readonly changeParameter: (name: string, value: number) => void
  readonly resetParameters: () => void
  /**
   * Sets the parameters an experiment names to its values and every other one to its default, so it
   * runs the same for everyone. If that changes a parameter that takes effect on restart, the
   * simulation starts over
   */
  readonly runExperiment: (values: Readonly<Record<string, number>>) => void
  /** the room's clock stands still. Every new room starts playing */
  readonly paused: Readonly<Ref<boolean>>
  /** share of real time at which the room plays the simulation, 1 in every new room */
  readonly speed: Readonly<Ref<number>>
  /** stops the room's clock: the simulation stands still but inputs still apply */
  readonly pause: () => void
  readonly play: () => void
  /** advances the paused simulation by one tick of the room */
  readonly step: () => void
  /** plays at a share of real time, above 0 and at most 1, for slow motion */
  readonly changeSpeed: (speed: number) => void
  /** the room's frames show the x-ray, those of no new room do */
  readonly xrayShown: Readonly<Ref<boolean>>
  readonly showXray: (shown: boolean) => void
}

/**
 * Runs a simulation in a room of its own and streams its state until the current effect scope is
 * disposed. Uses the simulations store and the backend health the app provides.
 *
 * Rooms live in the memory of the backend. If the backend goes away the simulation starts over in
 * a new room as soon as the backend is up again
 *
 * @param type the simulation, as the HTTP API identifies it
 */
export function useSimulationRoom(type: string): SimulationRoom {
  const backend = injectBackendHealth()
  const simulations = useSimulationsStore()
  const simulation = shallowRef<SimulationInfo>()
  const parameters = ref<Record<string, number>>({})
  const roomId = ref<string>()
  /** parameter values the simulation in the current room started with */
  const startedWith = shallowRef<Readonly<Record<string, number>>>({})
  const failure = ref<string>()
  const paused = ref(false)
  const speed = ref(1)
  const xrayShown = ref(false)
  const stream = useSimulationStream(roomId)
  let startWhenBackendIsBack = false

  const restartPending = computed(
    () =>
      roomId.value !== undefined &&
      (simulation.value?.parameters ?? []).some(
        ({ name, effect }) =>
          effect === 'RESTART' && parameters.value[name] !== startedWith.value[name],
      ),
  )

  async function start(): Promise<void> {
    startWhenBackendIsBack = false
    failure.value = undefined
    try {
      if (simulation.value === undefined) {
        const offered = (await simulations.load()).find((candidate) => candidate.type === type)
        if (offered === undefined) {
          throw new Error(`The server does not offer the ${type} simulation.`)
        }
        simulation.value = offered
        parameters.value = Object.fromEntries(
          offered.parameters.map((parameter) => [parameter.name, parameter.defaultValue]),
        )
      }
      const values = { ...parameters.value }
      roomId.value = (await openRoom(type, values)).id
      startedWith.value = values
    } catch (error) {
      failure.value =
        error instanceof Error ? error.message : 'The simulation could not be started.'
      startWhenBackendIsBack = isBackendUnavailable(error)
    }
  }

  function changeParameter(name: string, value: number): void {
    parameters.value[name] = value
    const parameter = simulation.value?.parameters.find((candidate) => candidate.name === name)
    if (parameter?.effect === 'LIVE') {
      stream.send({ type: 'param', name, value })
    }
  }

  /** sets the given values and every other parameter to its default */
  function setParameters(values: Readonly<Record<string, number>>): void {
    for (const parameter of simulation.value?.parameters ?? []) {
      changeParameter(parameter.name, values[parameter.name] ?? parameter.defaultValue)
    }
  }

  function resetParameters(): void {
    setParameters({})
  }

  function runExperiment(values: Readonly<Record<string, number>>): void {
    setParameters(values)
    if (restartPending.value) {
      void start()
    }
  }

  function pause(): void {
    if (stream.send({ type: 'pause' })) {
      paused.value = true
    }
  }

  function play(): void {
    if (stream.send({ type: 'play' })) {
      paused.value = false
    }
  }

  function step(): void {
    stream.send({ type: 'step' })
  }

  function changeSpeed(value: number): void {
    if (stream.send({ type: 'speed', value })) {
      speed.value = value
    }
  }

  function showXray(shown: boolean): void {
    if (stream.send({ type: 'xray', shown })) {
      xrayShown.value = shown
    }
  }

  // a new room plays its simulation in real time, without its x-ray
  watch(roomId, () => {
    paused.value = false
    speed.value = 1
    xrayShown.value = false
  })
  watch(stream.status, (status) => {
    if (status === 'lost') {
      startWhenBackendIsBack = true
      backend.refresh()
    }
  })
  // only a backend that comes back starts the simulation over, so a failing room can't loop
  watch(backend.health, (health, previous) => {
    if (startWhenBackendIsBack && health === 'UP' && previous !== 'UP') {
      void start()
    }
  })

  return {
    ...stream,
    simulation: shallowReadonly(simulation),
    parameters: readonly(parameters),
    roomId: readonly(roomId),
    failure: readonly(failure),
    restartPending,
    start,
    changeParameter,
    resetParameters,
    runExperiment,
    paused: readonly(paused),
    speed: readonly(speed),
    pause,
    play,
    step,
    changeSpeed,
    xrayShown: readonly(xrayShown),
    showXray,
  }
}

/** neither the backend nor a proxy in front of it could serve the request */
function isBackendUnavailable(error: unknown): boolean {
  return error instanceof TypeError || (error instanceof ApiError && error.status >= 500)
}
