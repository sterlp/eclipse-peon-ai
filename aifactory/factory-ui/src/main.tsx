import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import 'bootstrap/dist/css/bootstrap.min.css'
import 'bootstrap-icons/font/bootstrap-icons.css'
import './index.css'
import { AppBrowser } from './app/app.routes.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppBrowser />
  </StrictMode>,
)
