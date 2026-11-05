import { useOutletContext } from 'react-router-dom'

/** What the frame hands to the view inside it — today the name of this browser (R-WP-17). */
export interface AppContext {
  user: string | null
}

/** The frame's state as the view reads it. */
export function useAppContext(): AppContext {
  return useOutletContext<AppContext>()
}
