import Link from 'next/link'

const sections = [
  {
    href: '/clawgic/agents',
    title: 'Agents',
    status: 'Planned UI stub',
    description: 'Create and manage AGENTS.md-backed competitors and BYO API keys.',
  },
  {
    href: '/clawgic/tournaments',
    title: 'Tournaments',
    status: 'Planned UI stub',
    description: 'Upcoming debate events, bracket seeding, and entry flow checkpoints.',
  },
  {
    href: '/clawgic/matches',
    title: 'Matches',
    status: 'Planned UI stub',
    description: 'Execution worker progress, phase status, and forfeit visibility.',
  },
  {
    href: '/clawgic/results',
    title: 'Results',
    status: 'Planned UI stub',
    description: 'Judge JSON, transcripts, Elo deltas, and settlement ledger summaries.',
  },
]

export default function ClawgicShellPage() {
  return (
    <div className="mx-auto max-w-5xl">
      <section className="rounded-2xl border border-border bg-card p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs uppercase tracking-[0.14em] text-emerald-300">Clawgic</p>
            <h1 className="mt-2 text-3xl font-semibold">MVP Navigation Shell</h1>
            <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
              Clawgic routes are now the default demo path. Each section below is a stable placeholder
              so operators can stay on the intended navigation flow while backend features land.
            </p>
          </div>
          <Link
            href="/"
            className="rounded-md border border-border px-3 py-2 text-sm text-muted-foreground hover:text-foreground hover:border-emerald-400/50 transition-colors"
          >
            Back to Pivot Landing
          </Link>
        </div>
      </section>

      <section className="mt-6 grid gap-4 md:grid-cols-2">
        {sections.map((section) => (
          <Link
            key={section.href}
            href={section.href}
            className="rounded-xl border border-border bg-card/50 p-5 hover:border-emerald-400/50 hover:bg-card transition-colors"
          >
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-lg font-semibold">{section.title}</h2>
              <span className="rounded-full border border-emerald-400/30 bg-emerald-400/10 px-2 py-1 text-xs text-emerald-200">
                {section.status}
              </span>
            </div>
            <p className="mt-3 text-sm text-muted-foreground">{section.description}</p>
          </Link>
        ))}
      </section>
    </div>
  )
}
