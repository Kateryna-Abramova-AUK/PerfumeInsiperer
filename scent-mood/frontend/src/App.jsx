import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, Route, Routes, useLocation } from 'react-router-dom'
import { api } from './api'
import { ErrorMessage, Footer, FragranceCard, Header, Section } from './shared'
import { Catalog, Finder } from './discovery-pages'

function useResource(path) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const reload = useCallback(() => {
    setError('')
    setAttempt(value => value + 1)
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    api(path, { signal: controller.signal }).then(setData).catch(reason => {
      if (!controller.signal.aborted) setError(reason.message)
    })
    return () => controller.abort()
  }, [path, attempt])

  return { data, setData, error, reload }
}

function Home() {
  return (
    <>
      <section className="hero" aria-labelledby="hero-title">
        <div className="hero__landscape" aria-hidden="true">
          <img src="/assets/misty-pond.webp" alt="" fetchPriority="high" />
        </div>
        <div className="hero__oval">
          <h1 id="hero-title">Find your<br />scent</h1>
          <Link className="text-link hero__link" to="/finder">Explore scents</Link>
        </div>
        <img className="hero__iris" src="/assets/iris-sprig.webp" width="700" height="352" alt="" />
      </section>
      <section className="section mood-section" aria-labelledby="moods-title">
        <div className="section__inner">
          <h2 className="section__title" id="moods-title">Explore by mood</h2>
          <div className="mood-grid">
            {[['Joyful', 'fresh'], ['Angelic', 'soft']].map(([mood, image]) => (
              <Link className="mood-tile mood-tile__summary" key={mood} to={'/finder?mood=' + mood.toLowerCase()}>
                <img className="mood-tile__image" src={'/assets/' + image + '.webp'}
                  width="920" height="503" alt="" loading="lazy" />
                <span className="mood-tile__label">{mood}</span>
              </Link>
            ))}
          </div>
        </div>
      </section>
    </>
  )
}

function Shortlist({ shortlist }) {
  return (
    <Section title="Shortlist" className="product-section">
      {shortlist.ready && (shortlist.saved.length > 0 ? (
        <ul className="fragrance-grid">
          {shortlist.saved.map(fragrance => (
            <li key={fragrance.id}><FragranceCard fragrance={fragrance} shortlist={shortlist} heading="h2" /></li>
          ))}
        </ul>
      ) : (
        <div className="message">
          <p>Save a fragrance from your recommendations or the catalog to keep it here.</p>
          <Link className="button button--secondary" to="/catalog">Browse catalog</Link>
        </div>
      ))}
      {!shortlist.ready && !shortlist.loadError && <p role="status">Loading shortlist…</p>}
    </Section>
  )
}

export default function App() {
  const location = useLocation()
  const main = useRef(null)
  const initialPath = useRef(location.pathname)
  const options = useResource('options')
  const saved = useResource('shortlist')
  const mutation = useRef(false)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    if (initialPath.current !== location.pathname) {
      initialPath.current = location.pathname
      window.scrollTo(0, 0)
      main.current.focus({ preventScroll: true })
    }
    const title = { '/finder': 'Find your scent', '/catalog': 'Catalog', '/shortlist': 'Shortlist' }[location.pathname]
    document.title = title ? title + ' — Scent Mood' : 'Scent Mood — Find your scent'
  }, [location.pathname])

  const shortlist = {
    saved: saved.data ?? [],
    busy: saving || !saved.data,
    ready: Boolean(saved.data),
    loadError: saved.error,
    async toggle(fragrance) {
      if (!saved.data || mutation.current) return
      const removing = saved.data.some(item => item.id === fragrance.id)
      mutation.current = true
      setSaving(true)
      setSaveError('')
      setNotice('')
      try {
        await api('shortlist/' + encodeURIComponent(fragrance.id), { method: removing ? 'DELETE' : 'PUT' })
        saved.setData(previous => removing ? previous.filter(item => item.id !== fragrance.id) : [fragrance, ...previous])
        setNotice(fragrance.name + (removing ? ' removed from shortlist.' : ' saved to shortlist.'))
      } catch (error) {
        setSaveError(error.message)
        saved.reload()
      } finally {
        mutation.current = false
        setSaving(false)
      }
    },
  }

  const landing = location.pathname === '/'
  return (
    <div className={landing ? 'page page--landing' : 'page'}>
      <a className="skip-link button" href="#main">Skip to content</a>
      <Header />
      <main id="main" ref={main} tabIndex={-1}>
        <div className="visually-hidden" role="status">{notice}</div>
        {!landing && <ErrorMessage error={saved.error} retry={saved.reload} />}
        {!landing && <ErrorMessage error={saveError} />}
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/finder" element={<Finder key={location.search} options={options} shortlist={shortlist} />} />
          <Route path="/catalog" element={<Catalog options={options} shortlist={shortlist} />} />
          <Route path="/shortlist" element={<Shortlist shortlist={shortlist} />} />
          <Route path="*" element={<Section title="Page not found"><Link className="button" to="/">Back to Scent Mood</Link></Section>} />
        </Routes>
      </main>
      <Footer />
    </div>
  )
}
