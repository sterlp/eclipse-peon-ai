import { useState } from 'react'

import { Agents } from '../../src/config/config.agents'
import { SLOT_ERROR_FIELDS } from '../../src/config/config.error.fields'
import { ApiErrorPanel } from '../../src/shared/api.error.panel'
import type { ThinkInput } from '../../src/config/config.client'

/**
 * The agent section inside a page (R-WP-62): one page, one panel. The section marks the fields of its own
 * cards and hands up whatever no card owns — the same shape `config.page.tsx` holds — so a test that reads a
 * refusal off the screen reads it here, above the slots. A refusal that named only a card's own field keeps
 * the panel closed, because then the card says it (R-WP-56, UC-WP-99 GIVEN 3).
 */
export function AgentsHost({
  think,
  connection,
  extraBodySupported,
  loadModels,
}: {
  think: ThinkInput
  /** the base connection the slots inherit from, as the page names it (R-WP-50) */
  connection: string
  extraBodySupported: boolean
  loadModels: (slotId: string, slotUrl: string, force: boolean) => Promise<string[]>
}) {
  const [pageError, setPageError] = useState<unknown>(null)
  return (
    <>
      <ApiErrorPanel
        error={pageError}
        fields={SLOT_ERROR_FIELDS}
        onClose={() => setPageError(null)}
      />
      <Agents
        think={think}
        connection={connection}
        extraBodySupported={extraBodySupported}
        reloadToken={0}
        onError={setPageError}
        loadModels={loadModels}
      />
    </>
  )
}
