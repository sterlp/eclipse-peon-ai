import { writeUserName } from '../shared/user.name'

interface Props {
  /** The name of this browser, or null — null is the honest empty state, not a missing value (R-WP-17). */
  user: string | null
  /** Tells the shell what the browser now holds, so every send can carry it (R-WP-25). */
  onChange: (name: string) => void
}

/**
 * The name of this browser (R-WP-17): one field in the app header, written as it is typed — there is no
 * account to save and no button to press. It is not identity in the sense of a login: it labels what this
 * browser sends, and the server is the authority over what a line finally shows (R-WP-25, UC-WP-48).
 *
 * The field keeps the typed text, storing only the trimmed form: trimming what is displayed would make a
 * name that starts with a space impossible to type.
 */
export function AppUser({ user, onChange }: Props) {
  return (
    <input
      className="app-user"
      type="text"
      aria-label="Dein Name"
      data-testid="user-name"
      placeholder="Dein Name"
      value={user ?? ''}
      onChange={event => {
        writeUserName(event.target.value)
        onChange(event.target.value)
      }}
    />
  )
}
