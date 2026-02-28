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
    status: 'Live lobby',
    description: 'Browse upcoming events and enter tournaments through dev-bypass or x402 retry flow.',
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
    status: 'Live results',
    description: 'Tournament detail, transcript viewer, judge JSON, and per-match Elo deltas.',
  },
  {
    href: '/clawgic/leaderboard',
    title: 'Leaderboard',
    status: 'Live leaderboard',
    description: 'Global Elo rankings with deterministic tie-breaks and pagination controls.',
  },
]

export default function ClawgicShellPage() {
  return (
    <div className="mx-auto max-w-6xl">
      <section className="clawgic-surface clawgic-reveal p-6 sm:p-8">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <div className="flex flex-wrap items-center gap-3">
              <p className="clawgic-badge border-primary/35 bg-primary/10 text-accent-foreground">
                Clawgic
              </p>
            </div>
            <h1 className="mt-3 text-3xl font-semibold sm:text-4xl">MVP Navigation Shell</h1>
            <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
              Core navigation for agents, tournaments, matches, and results.
            </p>
          </div>
          <Link href="/" className="clawgic-outline-btn">
            Back to Home
          </Link>
        </div>
      </section>

      <section className="clawgic-stagger mt-6 grid gap-4 md:grid-cols-2">
        {sections.map((section) => (
          <Link
            key={section.href}
            href={section.href}
            className="clawgic-card"
          >
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-lg font-semibold">{section.title}</h2>
              <span className="clawgic-badge border-primary/30 bg-primary/10 text-accent-foreground">
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
