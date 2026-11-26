import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from './api'
import { ErrorMessage, Filter, FragranceCard, NoteSelector, Section } from './shared'

function useSearch(endpoint, initialQuery = null, method = 'GET') {
  const [request, setRequest] = useState(initialQuery ? { query: initialQuery } : null)
  const [response, setResponse] = useState(null)

  useEffect(() => {
    if (!request) return
    const controller = new AbortController()
    const parameters = method === 'POST' ? { body: request.query } : { query: request.query }
    api(endpoint, { ...parameters, method, signal: controller.signal })
      .then(items => setResponse({ request, items }))
      .catch(error => {
        if (!controller.signal.aborted) setResponse({ request, error: error.message })
      })
    return () => controller.abort()
  }, [endpoint, method, request])

  return {
    result: response?.request === request ? response : null,
    busy: Boolean(request && response?.request !== request),
    run: query => setRequest({ query }),
    retry: () => setRequest(current => ({ query: current.query })),
  }
}

function OptionsState({ options }) {
  return options.error ? <ErrorMessage error={options.error} retry={options.reload} />
    : <p role="status">Loading fragrance choices…</p>
}

export function Finder({ options, shortlist }) {
  const [params] = useSearchParams()
  const [selections, setSelections] = useState(() => {
    const mood = params.get('mood')
    return mood ? [mood] : []
  })
  const [preferredNote, setPreferredNote] = useState('')
  const [family, setFamily] = useState('')
  const [time, setTime] = useState('')
  const search = useSearch('recommendations', null, 'POST')
  const [limitNotice, setLimitNotice] = useState('')
  const selectedMoods = options.data?.moods.filter(mood => selections.includes(mood.id)).map(mood => mood.id) ?? []

  function toggleMood(id) {
    if (selectedMoods.includes(id)) {
      setSelections(selectedMoods.filter(item => item !== id))
      setLimitNotice('')
    } else if (selectedMoods.length < 2) {
      setSelections([...selectedMoods, id])
      setLimitNotice('')
    } else {
      setLimitNotice('Two moods selected. Deselect one to choose another.')
    }
  }

  function submit(event) {
    event.preventDefault()
    if (selectedMoods.length) search.run({ moods: selectedMoods, preferredNote, family, timeOfDay: time })
  }

  function clearWear(key) {
    const query = { ...search.result.request.query, [key]: '' }
    setSelections(query.moods)
    setPreferredNote(query.preferredNote)
    setFamily(query.family)
    setTime(query.timeOfDay)
    search.run(query)
  }

  const result = search.result
  return (
    <Section className="product-section" title="Find your scent">
      {!options.data ? <OptionsState options={options} /> : (
        <form className="search-form" onSubmit={submit}>
          <fieldset className="mood-picker">
            <legend>What do you want your fragrance to feel like?</legend>
            <p aria-live="polite">{limitNotice || selectedMoods.length + ' of 2 moods selected'}</p>
            <div className="mood-picker__grid">
              {options.data.moods.map(mood => (
                <button type="button" key={mood.id} className="mood-choice" data-mood={mood.id}
                  aria-pressed={selections.includes(mood.id)} onClick={() => toggleMood(mood.id)}>
                  <span className="mood-choice__name">{mood.label}</span>
                  <span>{mood.description}</span>
                </button>
              ))}
            </div>
          </fieldset>
          <fieldset className="refinements">
            <legend>Optional refinements</legend>
            <div className="fields">
              <Filter label="Preferred note" placeholder="Any note" options={options.data.preferredNotes}
                value={preferredNote} onChange={setPreferredNote} />
              <Filter label="Fragrance family" placeholder="Any family" options={options.data.families} value={family} onChange={setFamily} />
              <Filter label="Time of day" placeholder="Any Time" options={options.data.times} value={time} onChange={setTime} />
            </div>
          </fieldset>
          <button className="button" disabled={!selectedMoods.length || search.busy}>Find fragrances</button>
        </form>
      )}
      {search.busy && <p className="result-status" role="status">Finding fragrances…</p>}
      <ErrorMessage error={result?.error} retry={search.retry} />
      {result?.items && (
        <section className="results" aria-labelledby="recommendations-title">
          <h2 id="recommendations-title">Your recommendations</h2>
          <p role="status">{result.items.length ? result.items.length + ' fragrance matches' : 'No fragrances match these choices.'}</p>
          {result.items.length === 0 && (result.request.query.family || result.request.query.timeOfDay) && (
            <div className="filter-recovery">
              {result.request.query.family && <button className="button button--secondary" onClick={() => clearWear('family')}>Clear family filter</button>}
              {result.request.query.timeOfDay && <button className="button button--secondary" onClick={() => clearWear('timeOfDay')}>Clear time filter</button>}
            </div>
          )}
          {result.items.length > 0 && <ul className="fragrance-grid">
            {result.items.map(recommendation => (
              <li key={recommendation.fragrance.id}>
                <FragranceCard fragrance={recommendation.fragrance} recommendation={recommendation}
                  preferredNote={result.request.query.preferredNote} moodCount={result.request.query.moods.length} shortlist={shortlist} />
              </li>
            ))}
          </ul>}
        </section>
      )}
    </Section>
  )
}

const emptyFilters = { q: '', notes: [], family: '', season: '', time: '' }

export function Catalog({ options, shortlist }) {
  const [filters, setFilters] = useState(emptyFilters)
  const search = useSearch('catalog', emptyFilters)
  const change = (key, value) => setFilters(current => ({ ...current, [key]: value }))
  function clear() {
    setFilters(emptyFilters)
    search.run(emptyFilters)
  }

  const result = search.result
  return (
    <Section className="product-section" title="Fragrance catalog">
      {options.data ? (
        <form className="search-form" onSubmit={event => { event.preventDefault(); search.run(filters) }}>
          <label className="field catalog-search">
            <span>Fragrance or brand</span>
            <input type="search" value={filters.q} onChange={event => change('q', event.target.value)} />
          </label>
          <div className="fields fields--catalog">
            <NoteSelector multiple label="Filter by notes" notes={options.data.notes} selected={filters.notes}
              onChange={value => change('notes', value)} />
            <Filter label="Fragrance family" placeholder="Any family" options={options.data.families}
              value={filters.family} onChange={value => change('family', value)} />
            <Filter label="Season" placeholder="Any season" options={options.data.seasons}
              value={filters.season} onChange={value => change('season', value)} />
            <Filter label="Time of day" placeholder="Any Time" options={options.data.times}
              value={filters.time} onChange={value => change('time', value)} />
          </div>
          <div className="form-actions">
            <button className="button" disabled={search.busy}>Search catalog</button>
            <button className="button button--secondary" onClick={clear} type="button">Clear filters</button>
          </div>
        </form>
      ) : <OptionsState options={options} />}
      {search.busy && <p className="result-status" role="status">Searching the catalog…</p>}
      <ErrorMessage error={result?.error} retry={search.retry} />
      {result?.items && <section className="results" aria-labelledby="catalog-title">
        <h2 id="catalog-title">Fragrances</h2>
        <p role="status">{result.items.length ? result.items.length + (result.items.length === 1 ? ' fragrance' : ' fragrances')
          : 'No fragrances found. Try fewer notes or a different search.'}</p>
        {result.items.length > 0 && <ul className="fragrance-grid">
          {result.items.map(fragrance => (
            <li key={fragrance.id}><FragranceCard fragrance={fragrance} shortlist={shortlist} /></li>
          ))}
        </ul>}
      </section>}
    </Section>
  )
}
