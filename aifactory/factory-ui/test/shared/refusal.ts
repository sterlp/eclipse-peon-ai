/**
 * A refusal written the way the server writes it, `fieldErrors` included (R-WP-55). The plain `Error` entries
 * of the stubs answer 400 with a message only, which is the older half of the contract — a field the answer
 * names has to arrive as the wire brings it, or a page would have to invent the field it marks.
 */
export class Refused extends Error {
  readonly status: number
  readonly body: unknown

  constructor(body: Record<string, unknown>) {
    super(String(body.message ?? 'refused'))
    this.status = Number(body.status ?? 400)
    this.body = body
  }
}

/** Shorthand for a stub entry: `{ PUT: refused({ status: 400, … }) }`. */
export function refused(body: Record<string, unknown>): Refused {
  return new Refused(body)
}
