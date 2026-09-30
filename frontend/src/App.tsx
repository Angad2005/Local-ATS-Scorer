import { useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { AlertCircle, Check, FileText, LoaderCircle, Search, ShieldCheck, Upload, X } from 'lucide-react'
import './App.css'

type Match = { requirement: string; canonicalName: string; classification: string; evidence: string | null; sourceSection: string | null; confidence: number }
type Analysis = {
  fileName: string; jobTitle: string; overallScore: number; scoreType: string
  breakdown: Record<string, { score: number; weight: number; contribution: number; explanation: string }>
  matchedRequirements: Match[]; partialMatches: Match[]; missingRequirements: Match[]
  atsReadability: { score: number; detected: string[]; warnings: string[] }
  keywordAnalysis: { matched: string[]; missing: string[]; partial: string[]; mentionCounts: Record<string, number>; stuffingWarnings: string[] }
  recommendations: string[]; semanticAnalysisAvailable: boolean; explanation: string | null
}

const apiBase = import.meta.env.VITE_API_BASE_URL ?? '/api'
const sampleDescription = 'We are looking for an AI engineer with experience in Generative AI, Large Language Models, and Retrieval-Augmented Generation. Python is required. Experience with digital marketing is a plus.'

function App() {
  const [jobTitle, setJobTitle] = useState('AI Engineer')
  const [description, setDescription] = useState(sampleDescription)
  const [file, setFile] = useState<File | null>(null)
  const [analysis, setAnalysis] = useState<Analysis | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  function chooseFile(event: ChangeEvent<HTMLInputElement>) { const selected = event.target.files?.[0]; if (selected) { setFile(selected); setError(null) } }
  function onDrop(event: React.DragEvent<HTMLButtonElement>) { event.preventDefault(); const dropped = event.dataTransfer.files[0]; if (dropped) { setFile(dropped); setError(null) } }
  async function analyze(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!file) { setError('Choose a resume file to analyze.'); return }
    setBusy(true); setError(null)
    const payload = new FormData(); payload.append('jobTitle', jobTitle); payload.append('jobDescription', description); payload.append('resume', file)
    try {
      const response = await fetch(`${apiBase}/ats/analyze`, { method: 'POST', body: payload })
      const result = await response.json() as Analysis & { message?: string }
      if (!response.ok) throw new Error(result.message ?? 'Could not analyze this resume.')
      setAnalysis(result)
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'The analysis service is unavailable.') }
    finally { setBusy(false) }
  }

  return <div className="app-shell">
    <header className="topbar"><a className="brand" href="#top"><span className="brand-mark">↗</span><span>LOCAL <b>ATS</b> SCORER</span></a><span className="privacy"><ShieldCheck size={15} /> Local-first analysis</span></header>
    <main id="top" className="content-wrap">
      <section className="hero"><div><p className="eyebrow">RESUME / JOB DESCRIPTION / 01</p><h1>Resume Match Analysis</h1><p>See how closely your resume matches a specific job description, with evidence for every major signal.</p></div><div className="honesty"><strong>ATS Compatibility Score</strong><span>Application-specific methodology. Not an official universal ATS score.</span></div></section>
      <div className="workspace-grid">
        <section className="panel input-panel"><div className="section-heading"><span>01</span><div><h2>Build an analysis</h2><p>Provide the job requirements and your resume.</p></div></div>
          <form onSubmit={analyze}><label htmlFor="job-title">JOB TITLE</label><input id="job-title" value={jobTitle} onChange={event => setJobTitle(event.target.value)} required />
            <div className="label-row"><label htmlFor="job-description">JOB DESCRIPTION</label><small>{description.length} characters</small></div><textarea id="job-description" value={description} onChange={event => setDescription(event.target.value)} rows={9} required />
            <div className="label-row"><label>RESUME</label><small>PDF, DOCX, DOC, or TXT · 10 MB max</small></div><input ref={fileInput} className="visually-hidden" type="file" accept=".pdf,.docx,.doc,.txt,application/pdf,text/plain" onChange={chooseFile} />
            <button className={`drop-zone ${file ? 'has-file' : ''}`} type="button" onClick={() => fileInput.current?.click()} onDragOver={event => event.preventDefault()} onDrop={onDrop}><span className="upload-icon">{file ? <FileText size={19} /> : <Upload size={19} />}</span><span><b>{file?.name ?? 'Drop a resume here'}</b><small>{file ? `${(file.size / 1024 / 1024).toFixed(2)} MB ready` : 'or browse files on your device'}</small></span>{file ? <i onClick={event => { event.stopPropagation(); setFile(null) }}><X size={16} /></i> : null}</button>
            {error && <div className="error"><AlertCircle size={15} /> {error}</div>}<p className="privacy-copy"><ShieldCheck size={15} /> Resume content is processed locally by default. Optional local model services can enrich semantic analysis.</p><button className="analyze-button" disabled={busy} type="submit">{busy ? <><LoaderCircle className="spin" size={17} /> Analyzing resume</> : 'Analyze resume ↗'}</button>
          </form>
        </section>
        <section className="panel results-panel">{analysis ? <AnalysisView analysis={analysis} /> : <div className="empty"><FileText size={32} /><h2>Your analysis appears here</h2><p>Upload a resume to see match strength, missing requirements, source evidence, and resume improvements.</p></div>}</section>
      </div>
      <footer><span>LOCAL ATS SCORER / EXPLAINABLE MATCHING</span><span>Measures resume-to-job alignment, not hiring outcomes.</span></footer>
    </main>
  </div>
}

function AnalysisView({ analysis }: { analysis: Analysis }) {
  const [query, setQuery] = useState('')
  const allMatches = [...analysis.matchedRequirements, ...analysis.partialMatches, ...analysis.missingRequirements].filter(item => item.requirement.toLowerCase().includes(query.toLowerCase()))
  return <div className="analysis"><div className="analysis-head"><div><p className="eyebrow">02 / ANALYSIS</p><h2>{analysis.jobTitle}</h2><p>{analysis.fileName}</p></div><div className="score"><strong>{analysis.overallScore}</strong><span>/ 100</span></div></div><p className="score-caption">ATS Compatibility Score · this application's explainable scoring methodology</p>
    <div className="breakdown">{Object.entries(analysis.breakdown).map(([key, value]) => <div className="breakdown-item" key={key}><div><span>{key.replace(/([A-Z])/g, ' $1')}</span><b>{value.score}%</b></div><div className="bar"><i style={{ width: `${value.score}%` }} /></div><small>{value.weight}% weight · {value.contribution.toFixed(1)} points</small></div>)}</div>
    <div className="result-section"><div className="section-title"><h3>Requirements</h3><label><Search size={14} /><input placeholder="Filter requirements" value={query} onChange={event => setQuery(event.target.value)} /></label></div><div className="requirement-list">{allMatches.map(match => <div className="requirement" key={`${match.canonicalName}-${match.classification}`}><span className={`status ${match.classification.toLowerCase()}`}>{match.classification === 'STRONG_MATCH' ? <Check size={13} /> : match.classification === 'PARTIAL_MATCH' ? '◐' : <X size={13} />}</span><div><strong>{match.requirement}</strong><span>{match.classification.replace('_', ' ')}</span>{match.evidence && <p>“{match.evidence}” <em>{match.sourceSection}</em></p>}</div></div>)}</div></div>
    <div className="two-col"><div className="result-section"><h3>ATS Readability <b>{analysis.atsReadability.score}/100</b></h3><div className="chips">{analysis.atsReadability.detected.map(item => <span key={item}>✓ {item}</span>)}</div>{analysis.atsReadability.warnings.map(item => <p className="warning" key={item}>! {item}</p>)}</div><div className="result-section"><h3>Keyword Analysis</h3><p className="muted">Matched: {analysis.keywordAnalysis.matched.join(', ') || 'None'}</p><p className="muted">Missing: {analysis.keywordAnalysis.missing.join(', ') || 'None'}</p><p className="muted">Partial: {analysis.keywordAnalysis.partial.join(', ') || 'None'}</p></div></div>
    {analysis.explanation && <div className="result-section"><h3>Local model explanation</h3><p className="muted">{analysis.explanation}</p></div>}
    <div className="result-section recommendations"><h3>Resume Improvements</h3>{analysis.recommendations.length ? analysis.recommendations.map((item, index) => <p key={item}><b>{index + 1}</b>{item}</p>) : <p className="muted">No specific gaps were detected.</p>}</div>
  </div>
}

export default App
