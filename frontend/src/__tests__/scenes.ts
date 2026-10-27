import type { Scene } from '@/api/scene'

/** two points with trails that a camera circles, up along z. Frames hold 6 floats */
export const pointsScene: Scene = {
  camera: {
    position: { x: 0, y: -10, z: 5 },
    target: { x: 0, y: 0, z: 0 },
    up: { x: 0, y: 0, z: 1 },
    orbiting: true,
  },
  objects: [
    {
      kind: 'PointCloud',
      count: 2,
      firstColor: { red: 139, green: 223, blue: 248 },
      lastColor: { red: 248, green: 139, blue: 214 },
      trailLength: 4,
      glowing: false,
      selectable: false,
    },
  ],
  xray: [],
}

/**
 * Two clouds of two glowing stars each, which a camera circles, up along z. Frames hold 12 floats,
 * the x-ray shows up to two cubes, which add 9 floats to the frames that show it
 */
export const starsScene: Scene = {
  camera: {
    position: { x: 0, y: -110, z: 70 },
    target: { x: 0, y: 0, z: 0 },
    up: { x: 0, y: 0, z: 1 },
    orbiting: true,
  },
  objects: [
    {
      kind: 'PointCloud',
      count: 2,
      firstColor: { red: 227, green: 235, blue: 255 },
      lastColor: { red: 74, green: 120, blue: 255 },
      trailLength: 0,
      glowing: true,
      selectable: false,
    },
    {
      kind: 'PointCloud',
      count: 2,
      firstColor: { red: 255, green: 232, blue: 184 },
      lastColor: { red: 255, green: 183, blue: 102 },
      trailLength: 0,
      glowing: true,
      selectable: false,
    },
  ],
  xray: [{ kind: 'Cubes', capacity: 2, label: 'Cells', color: { red: 63, green: 185, blue: 80 } }],
}

/** x-ray of the stars scene: one cube around the origin, of half the edge 2 */
export const STARS_XRAY = [1, 0, 0, 0, 2, 0, 0, 0, 0]

/**
 * Two points that can be selected, then the readouts x and ẋ of the selected one. Frames hold 8
 * floats. The camera looks along +y at the origin, up along z
 */
export const selectableScene: Scene = {
  camera: {
    position: { x: 0, y: -10, z: 0 },
    target: { x: 0, y: 0, z: 0 },
    up: { x: 0, y: 0, z: 1 },
    orbiting: false,
  },
  objects: [
    {
      kind: 'PointCloud',
      count: 2,
      firstColor: { red: 139, green: 223, blue: 248 },
      lastColor: { red: 248, green: 139, blue: 214 },
      trailLength: 0,
      glowing: false,
      selectable: true,
    },
    {
      kind: 'Readouts',
      readouts: [
        { name: 'x', label: 'x', unit: '' },
        { name: 'xDot', label: 'ẋ', unit: '' },
      ],
    },
  ],
  xray: [],
}

/**
 * A frame of the selectable scene: a point at the origin and one at (1, 0, 1), x = 1.5, ẋ = -21.4
 */
export const SELECTABLE_FRAME = [0, 0, 0, 1, 0, 1, 1.5, -21.4]

/**
 * A grabbable square of four vertices and a sphere above a floor, up along y. Frames hold 16
 * floats. The square lies in the plane z = 0 in front of the camera, which looks down on it. Its
 * x-ray shows up to two lines colored by strain, which add 15 floats to the frames that show it
 */
export const clothScene: Scene = {
  camera: {
    position: { x: 0, y: 2, z: 4 },
    target: { x: 0, y: 1, z: 0 },
    up: { x: 0, y: 1, z: 0 },
    orbiting: false,
  },
  objects: [
    {
      kind: 'Mesh',
      vertexCount: 4,
      triangles: [0, 1, 3, 0, 3, 2],
      material: { color: { red: 217, green: 81, blue: 60 }, finish: 'FABRIC' },
      grabbable: true,
    },
    { kind: 'Sphere', material: { color: { red: 201, green: 209, blue: 217 }, finish: 'GLOSSY' } },
    { kind: 'Floor', radius: 4 },
  ],
  xray: [
    {
      kind: 'Lines',
      capacity: 2,
      label: 'Strain of the edges',
      scale: {
        label: 'ε',
        unit: '%',
        min: -2,
        max: 2,
        colors: [
          { red: 0, green: 0, blue: 255 },
          { red: 255, green: 255, blue: 255 },
          { red: 255, green: 0, blue: 0 },
        ],
      },
      directed: false,
    },
  ],
}

/** frame of the cloth scene: the square from (-1, 0) to (1, 2), then a sphere of radius 0.5 */
export const CLOTH_FRAME = [-1, 0, 0, 1, 0, 0, -1, 2, 0, 1, 2, 0, 0, 1, -1, 0.5]

/**
 * The x-ray of the cloth scene: the lower edge of the square stretched by 2 %, and its left edge,
 * which keeps its length
 */
export const CLOTH_XRAY = [2, -1, 0, 0, 1, 0, 0, 2, -1, 0, 0, -1, 2, 0, 0]
