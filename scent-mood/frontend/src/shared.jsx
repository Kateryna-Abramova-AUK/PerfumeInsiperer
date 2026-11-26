import { useId, useState } from 'react'
import { Link, NavLink } from 'react-router-dom'

export function Header() {
  return (
    <header className="site-header">
      <nav className="site-nav section__inner" aria-label="Main navigation">
        <Link className="wordmark site-nav__brand" to="/" aria-label="Scent Mood home">Scent Mood</Link>
        <NavLink className="nav-label site-nav__discover" to="/finder"><span>Discover</span></NavLink>
        <NavLink className="nav-label site-nav__catalog" to="/catalog"><span>Catalog</span></NavLink>
        <NavLink className="nav-label site-nav__shortlist" to="/shortlist"><span>Shortlist</span></NavLink>
      </nav>
    </header>
  )
}

export function LaceTrim() {
  return <div className="lace-trim" aria-hidden="true" />
}

export function Footer() {
  return (
    <footer className="site-footer">
      <LaceTrim />
      <Link className="wordmark site-footer__brand" to="/" aria-label="Scent Mood home">Scent Mood</Link>
    </footer>
  )
}

export function Section({ title, children, className = '' }) {
  const id = useId()
  return (
    <section className={'section ' + className} aria-labelledby={id}>
      <div className="section__inner">
        <h1 className="section__title" id={id}>{title}</h1>
        {children}
      </div>
    </section>
  )
}

export function Filter({ label, placeholder, options, value, onChange }) {
  return (
    <label className="field">
      <span>{label}</span>
      <select value={value} onChange={event => onChange(event.target.value)}>
        <option value="">{placeholder}</option>
        {options.map(item => <option key={item}>{item}</option>)}
      </select>
    </label>
  )
}

export function NoteSelector({ notes, selected, onChange, multiple = false, label }) {
  const [search, setSearch] = useState('')
  const group = useId()
  const visible = notes.filter(note => note.toLowerCase().includes(search.trim().toLowerCase()))

  function toggle(note) {
    if (!multiple) {
      onChange([note])
      return
    }
    onChange(selected.includes(note) ? selected.filter(item => item !== note) : [...selected, note])
  }

  return (
    <div className="note-selector">
      <details>
        <summary>{label}{selected.length ? ' (' + selected.length + ')' : ''}</summary>
        <div className="note-panel">
          <label className="field">
            <span>Search notes</span>
            <input type="search" value={search} onChange={event => setSearch(event.target.value)} />
          </label>
          <fieldset className="note-options">
            <legend className="visually-hidden">{label}</legend>
            {visible.map(note => (
              <label className="note-option" key={note}>
                <input type={multiple ? 'checkbox' : 'radio'} name={group} checked={selected.includes(note)}
                  onChange={() => toggle(note)} />
                {note}
              </label>
            ))}
            {!visible.length && <p>No notes match that search.</p>}
          </fieldset>
        </div>
      </details>
      {selected.length > 0 && (
        <ul className="note-selections" aria-label="Selected notes">
          {selected.map(note => (
            <li key={note}><button type="button" onClick={() => onChange(selected.filter(item => item !== note))}
              aria-label={'Remove ' + note}>{note} <span aria-hidden="true">×</span></button></li>
          ))}
        </ul>
      )}
    </div>
  )
}

export function ErrorMessage({ error, retry }) {
  return error && (
    <div className="message message--error" role="alert">
      <p>{error}</p>
      {retry && <button className="button button--secondary" onClick={retry}>Try again</button>}
    </div>
  )
}

export function FragranceCard({ fragrance, recommendation, preferredNote, moodCount, shortlist, heading: Heading = 'h3' }) {
  const saved = shortlist.saved.some(item => item.id === fragrance.id)
  return (
    <article className="fragrance-card">
      <p className="fragrance-brand">{fragrance.brand}</p>
      <Heading className="fragrance-name">{fragrance.name}</Heading>
      <p className="fragrance-notes">{[...fragrance.notes, ...fragrance.accords].join(' · ')}</p>
      <p className="fragrance-wear">{[fragrance.family, ...fragrance.seasons,
        fragrance.times.length === 2 ? 'Day & evening' : fragrance.times[0]].join(' · ')}</p>
      {recommendation && (
        <div className="match-explanation">
          {moodCount === 2 && recommendation.matches.length === 1 && <p>Matches one of your selected moods.</p>}
          <ul>
            {recommendation.matches.map(match => (
              <li key={match.mood}>
                <strong>{match.mood}</strong>{match.curatedTag ? ' · Curated mood tag' : ''}
                {match.signals.length > 0 ? ' · ' + match.signals.join(', ') : ''}
              </li>
            ))}
          </ul>
          {preferredNote && <p>{recommendation.preferredNotePresent
            ? 'Contains ' + (recommendation.matchedPreferredNote || preferredNote)
            : 'Does not contain ' + preferredNote}.</p>}
        </div>
      )}
      <div className="fragrance-actions">
        <button className={'button' + (saved ? ' button--secondary' : '')} disabled={shortlist.busy}
          onClick={() => shortlist.toggle(fragrance)} aria-label={(saved ? 'Remove ' : 'Save ') + fragrance.name}>
          {saved ? 'Remove from shortlist' : 'Save to shortlist'}
        </button>
        <a className="text-link" href={fragrance.sourceUrl} target="_blank" rel="noreferrer">Brand notes</a>
      </div>
    </article>
  )
}
