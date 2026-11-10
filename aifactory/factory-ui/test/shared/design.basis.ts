/**
 * A second design library, in the forms it actually arrives in.
 *
 * Bootstrap does not come back into an app by being written anew — it comes back as a stylesheet import in
 * the entry, as an `@import` inside a sheet, as a component import, as an icon-font class in markup, or as a
 * package that moved to `devDependencies` while its CSS stays in the bundle. A guard that reads `from '…'`
 * only, and `dependencies` only, proves none of those five. So the forms are listed here as they occur and
 * each is proven on a synthetic string in `theme.test.ts`: the guard is tested, not just trusted.
 */
const OFFENDING_FORMS: { readonly what: string; readonly pattern: RegExp }[] = [
  { what: 'a component or icon import', pattern: /from\s+['"](?:react-)?bootstrap(?:-icons)?[/'"]/ },
  { what: 'a stylesheet import', pattern: /import\s+['"]bootstrap/ },
  { what: 'an @import of its css', pattern: /@import[^;]*['"]bootstrap/ },
  { what: 'its icon font in markup', pattern: /bi bi-/ },
  { what: 'a bridge of its variables', pattern: /--bs-/ },
]

/** Which of the forms a single source carries, named. */
export function offendingForms(source: string): string[] {
  return OFFENDING_FORMS.filter((form) => form.pattern.test(source)).map((form) => form.what)
}

/** Every file that carries one of the forms, with the forms named so a failure says what to remove. */
export function secondDesignLibraryOffenders(sources: Record<string, string>): string[] {
  return Object.entries(sources)
    .filter(([, code]) => offendingForms(code).length > 0)
    .map(([path, code]) => `${path} → ${offendingForms(code).join(', ')}`)
}

/**
 * Every package the manifest names, from both sections. A design library in `devDependencies` is not out of
 * the app: its CSS can still be imported and is then bundled, and the bundle is what a screen is painted of.
 */
export function manifestPackageNames(manifestJson: string): string[] {
  const manifest = JSON.parse(manifestJson) as {
    dependencies?: Record<string, string>
    devDependencies?: Record<string, string>
  }
  return [
    ...Object.keys(manifest.dependencies ?? {}),
    ...Object.keys(manifest.devDependencies ?? {}),
  ]
}

/** The package names that belong to a design layer rather than to this app's own code. */
export function isDesignLibrary(name: string): boolean {
  return name === 'react-bootstrap' || name.startsWith('bootstrap')
}
