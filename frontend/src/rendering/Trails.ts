/**
 * Positions of every particle in the latest frames, laid out for drawing trails as line segments.
 *
 * {@link positions} holds one block of positions per frame, newest frame first. {@link indices}
 * holds a pair of vertex indices per segment, which connects a particle to its position one frame
 * earlier. Only the positions change from frame to frame
 */
export class Trails {
  readonly positions: Float32Array
  readonly indices: Uint32Array
  private readonly frameSize: number
  private frames = 0

  constructor(
    readonly particleCount: number,
    readonly length: number,
  ) {
    if (length < 2) {
      throw new RangeError('A trail needs at least two frames')
    }
    this.frameSize = particleCount * 3
    this.positions = new Float32Array(length * this.frameSize)
    this.indices = new Uint32Array((length - 1) * particleCount * 2)
    for (let age = 0; age < length - 1; age++) {
      for (let particle = 0; particle < particleCount; particle++) {
        const vertex = age * particleCount + particle
        this.indices[2 * vertex] = vertex
        this.indices[2 * vertex + 1] = vertex + particleCount
      }
    }
  }

  /** indices that connect frames, to be drawn from the start of {@link indices} */
  get indexCount(): number {
    return Math.max(this.frames - 1, 0) * this.particleCount * 2
  }

  /** adds the positions of a new frame, once the trails are full the oldest frame drops out */
  push(frame: Float32Array): void {
    if (frame.length !== this.frameSize) {
      throw new RangeError(
        `Expected ${String(this.frameSize)} coordinates, but got ${String(frame.length)}`,
      )
    }
    this.positions.copyWithin(this.frameSize, 0, (this.length - 1) * this.frameSize)
    this.positions.set(frame)
    this.frames = Math.min(this.frames + 1, this.length)
  }

  /** forgets every frame, e.g. because a new simulation starts */
  clear(): void {
    this.frames = 0
  }
}
