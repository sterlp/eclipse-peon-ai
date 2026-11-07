import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { AppRoot } from './app/app.root.tsx'
import { applyMode, startMode } from './shared/theme.mode'

// the mode is named on the document **before** anything is painted: both colour tables hang on that
// attribute, so a page that started dark shows dark on its first frame instead of flashing light (UC-WP-71)
applyMode(startMode())

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppRoot />
  </StrictMode>,
)
