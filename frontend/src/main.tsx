import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.tsx'
import { SearchHistoryProvider } from './context/SearchHistoryContext'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <SearchHistoryProvider>
        <App />
      </SearchHistoryProvider>
    </BrowserRouter>
  </React.StrictMode>,
)
