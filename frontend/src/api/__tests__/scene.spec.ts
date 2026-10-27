import { describe, expect, it } from 'vitest'

import {
  clothScene,
  pointsScene,
  SELECTABLE_FRAME,
  selectableScene,
  starsScene,
} from '@/__tests__/scenes'

import {
  frameFloats,
  isScene,
  readoutValues,
  sceneFrameFloats,
  sceneReadouts,
  sceneXrayFloats,
  type ColorScale,
  type Material,
  type SceneObject,
} from '../scene'

const gray: Material = { color: { red: 128, green: 128, blue: 128 }, finish: 'MATTE' }

const strain: ColorScale = {
  label: 'ε',
  unit: '%',
  min: -2,
  max: 2,
  colors: [gray.color, gray.color],
}

describe('frameFloats', () => {
  it.each<[string, SceneObject, number]>([
    [
      'three floats per point',
      {
        kind: 'PointCloud',
        count: 5,
        firstColor: gray.color,
        lastColor: gray.color,
        trailLength: 0,
        glowing: false,
        selectable: false,
      },
      15,
    ],
    [
      'three floats per vertex',
      { kind: 'Mesh', vertexCount: 4, triangles: [0, 1, 2], material: gray, grabbable: false },
      12,
    ],
    ['the center and the radius of a sphere', { kind: 'Sphere', material: gray }, 4],
    ['no floats for a floor', { kind: 'Floor', radius: 1 }, 0],
    [
      'a float per readout',
      { kind: 'Readouts', readouts: [{ name: 'speed', label: '|w|', unit: 'm/s' }] },
      1,
    ],
    [
      'the number of lines and seven floats per line',
      { kind: 'Lines', capacity: 3, label: 'Strain', scale: strain, directed: false },
      22,
    ],
    [
      'the number of cubes and four floats per cube',
      { kind: 'Cubes', capacity: 3, label: 'Cells', color: gray.color },
      13,
    ],
  ])('counts %s', (_, object, floats) => {
    expect(frameFloats(object)).toBe(floats)
  })

  it('adds up the floats of the objects of a scene', () => {
    expect(sceneFrameFloats(pointsScene)).toBe(6)
    expect(sceneFrameFloats(clothScene)).toBe(16)
    expect(sceneFrameFloats(selectableScene)).toBe(8)
  })

  it('adds up the floats of the objects of the x-ray of a scene, none without x-ray', () => {
    expect(sceneXrayFloats(clothScene)).toBe(15)
    expect(sceneXrayFloats(starsScene)).toBe(9)
    expect(sceneXrayFloats(pointsScene)).toBe(0)
  })
})

describe('sceneReadouts', () => {
  it('lists the readouts of a scene, or none', () => {
    expect(sceneReadouts(selectableScene).map(({ name }) => name)).toEqual(['x', 'xDot'])
    expect(sceneReadouts(pointsScene)).toEqual([])
  })
})

describe('readoutValues', () => {
  it('reads the values of the readouts from a frame, by their names', () => {
    expect(readoutValues(selectableScene, Float32Array.from(SELECTABLE_FRAME))).toEqual({
      x: 1.5,
      xDot: Math.fround(-21.4),
    })
  })

  it('keeps the values that are unknown', () => {
    const frame = Float32Array.of(0, 0, 0, 1, 0, 1, Number.NaN, Number.NaN)

    expect(readoutValues(selectableScene, frame)).toEqual({ x: Number.NaN, xDot: Number.NaN })
  })

  it('reads no values from the frame of a scene without readouts', () => {
    expect(readoutValues(pointsScene, Float32Array.of(1, 2, 3, 4, 5, 6))).toEqual({})
  })
})

describe('isScene', () => {
  it('accepts scenes of every kind of object', () => {
    expect(isScene(pointsScene)).toBe(true)
    expect(isScene(starsScene)).toBe(true)
    expect(isScene(clothScene)).toBe(true)
    expect(isScene(selectableScene)).toBe(true)
  })

  it('accepts an x-ray of objects that the pointer neither grabs nor selects', () => {
    expect(isScene({ ...clothScene, xray: [...clothScene.xray, pointsScene.objects[0]] })).toBe(
      true,
    )
  })

  function withReadouts(readouts: unknown): unknown {
    const [points] = selectableScene.objects
    return { ...selectableScene, objects: [points, { kind: 'Readouts', readouts }] }
  }

  function withMesh(changes: Record<string, unknown>): unknown {
    const [mesh, ...others] = clothScene.objects
    return { ...clothScene, objects: [{ ...mesh, ...changes }, ...others] }
  }

  function withPoints(changes: Record<string, unknown>): unknown {
    return { ...pointsScene, objects: [{ ...pointsScene.objects[0], ...changes }] }
  }

  function withLines(changes: Record<string, unknown>): unknown {
    return { ...clothScene, xray: [{ ...clothScene.xray[0], ...changes }] }
  }

  function withScale(changes: Record<string, unknown>): unknown {
    return withLines({ scale: { ...strain, ...changes } })
  }

  function withCubes(changes: Record<string, unknown>): unknown {
    return { ...starsScene, xray: [{ ...starsScene.xray[0], ...changes }] }
  }

  it.each([
    ['without camera', { objects: [] }],
    [
      'with a camera without up',
      { ...clothScene, camera: { ...clothScene.camera, up: undefined } },
    ],
    ['with a number among its objects', { ...clothScene, objects: [42] }],
    ['with an object of an unknown kind', { ...clothScene, objects: [{ kind: 'Torus' }] }],
    ['with a mesh without triangles', withMesh({ triangles: [] })],
    ['with a mesh without whole triangles', withMesh({ triangles: [0, 1] })],
    ['with a triangle of a vertex that the mesh lacks', withMesh({ triangles: [0, 1, 4] })],
    [
      'with a color channel above 255',
      withMesh({ material: { ...gray, color: { ...gray.color, red: 256 } } }),
    ],
    [
      'with a color channel of a fraction',
      withMesh({ material: { ...gray, color: { ...gray.color, red: 0.5 } } }),
    ],
    ['with an unknown finish', withMesh({ material: { ...gray, finish: 'METAL' } })],
    ['with a mesh that is neither grabbable nor not', withMesh({ grabbable: 'yes' })],
    ['with a point cloud without points', withPoints({ count: 0 })],
    ['with a trail that connects a single frame', withPoints({ trailLength: 1 })],
    ['with points that neither glow nor not', withPoints({ glowing: undefined })],
    ['with points that can neither be selected nor not', withPoints({ selectable: 'yes' })],
    ['with a floor without radius', { ...clothScene, objects: [{ kind: 'Floor', radius: 0 }] }],
    ['with readouts without readouts', withReadouts([])],
    ['with a readout without name', withReadouts([{ name: '', label: 'x', unit: '' }])],
    ['with a readout without label', withReadouts([{ name: 'x', label: '', unit: '' }])],
    ['with a readout without unit', withReadouts([{ name: 'x', label: 'x' }])],
    [
      'with readouts of the same name',
      withReadouts([
        { name: 'x', label: 'x', unit: '' },
        { name: 'x', label: 'ẋ', unit: '' },
      ]),
    ],
    [
      'with two objects of readouts',
      { ...selectableScene, objects: [...selectableScene.objects, selectableScene.objects[1]] },
    ],
    ['without x-ray', { ...pointsScene, xray: undefined }],
    ['with lines without capacity', withLines({ capacity: 0 })],
    ['with lines without label', withLines({ label: '' })],
    ['with lines that neither point nor not', withLines({ directed: undefined })],
    ['with a color scale without label', withScale({ label: '' })],
    ['with a color scale without unit', withScale({ unit: undefined })],
    ['with a color scale that ends where it starts', withScale({ max: -2 })],
    ['with a color scale of a single color', withScale({ colors: [gray.color] })],
    ['with a color scale of a color that is none', withScale({ colors: [gray.color, 'red'] })],
    ['with cubes without capacity', withCubes({ capacity: 0 })],
    ['with cubes without label', withCubes({ label: '' })],
    ['with cubes without color', withCubes({ color: undefined })],
    ['with readouts in its x-ray', { ...pointsScene, xray: [selectableScene.objects[1]] }],
    ['with a grabbable mesh in its x-ray', { ...clothScene, xray: [clothScene.objects[0]] }],
    [
      'with a selectable point cloud in its x-ray',
      { ...pointsScene, xray: [selectableScene.objects[0]] },
    ],
  ])('rejects a scene %s', (_, scene) => {
    expect(isScene(scene)).toBe(false)
  })
})
