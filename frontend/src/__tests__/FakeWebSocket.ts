/**
 * Stands in for the WebSocket of the browser. Tests play the server through
 * {@link FakeWebSocket.acceptConnection}, {@link FakeWebSocket.receive} and
 * {@link FakeWebSocket.closeFromServer}
 */
export class FakeWebSocket extends EventTarget {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSING = 2
  static readonly CLOSED = 3

  /** sockets created since the last {@link FakeWebSocket.reset}, oldest first */
  static instances: FakeWebSocket[] = []

  readonly url: string
  readonly sent: string[] = []
  binaryType: BinaryType = 'blob'
  readyState: number = FakeWebSocket.CONNECTING

  constructor(url: string) {
    super()
    this.url = url
    FakeWebSocket.instances.push(this)
  }

  static reset(): void {
    FakeWebSocket.instances = []
  }

  static latest(): FakeWebSocket {
    const socket = FakeWebSocket.instances[FakeWebSocket.instances.length - 1]
    if (socket === undefined) {
      throw new Error('No WebSocket has been created')
    }
    return socket
  }

  send(data: string): void {
    if (this.readyState !== FakeWebSocket.OPEN) {
      throw new DOMException('The WebSocket is not open', 'InvalidStateError')
    }
    this.sent.push(data)
  }

  close(): void {
    if (this.readyState !== FakeWebSocket.CLOSED) {
      this.readyState = FakeWebSocket.CLOSED
      this.dispatchEvent(new CloseEvent('close', { code: 1000 }))
    }
  }

  acceptConnection(): void {
    this.readyState = FakeWebSocket.OPEN
    this.dispatchEvent(new Event('open'))
  }

  receive(data: ArrayBuffer): void {
    this.dispatchEvent(new MessageEvent('message', { data }))
  }

  closeFromServer(code: number): void {
    this.readyState = FakeWebSocket.CLOSED
    this.dispatchEvent(new CloseEvent('close', { code }))
  }
}
