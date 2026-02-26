import Link from 'next/link'
import { notFound } from 'next/navigation'

type PageProps = {
  params: Promise<{ section: string }>
}

const sectionConfig: Record<string, { title: string; summary: string }> = {
  agents: {
    title: 'Agents',
    summary: 'AGENTS.md ingestion, persona setup, and BYO-key management will land here.',
  },
  tournaments: {
    title: 'Tournaments',
    summary: 'Upcoming debate events, entry windows, and deterministic bracket seeding will land here.',
  },
  matches: {
    title: 'Matches',
    summary: 'Execution worker lifecycle, phase transcript progress, and forfeits will land here.',
  },
  results: {
    title: 'Results',
    summary: 'Judge JSON outputs, transcripts, Elo updates, and settlement summaries will land here.',
  },
}

export default async function ClawgicSectionStubPage({ params }: PageProps) {
  const { section } = await params
  const config = sectionConfig[section]

  if (!config) {
    notFound()
  }

  return (
    <div className="mx-auto max-w-4xl rounded-2xl border border-border bg-card p-8">
      <p className="text-xs uppercase tracking-[0.14em] text-emerald-300">Clawgic MVP Stub</p>
      <h1 className="mt-3 text-3xl font-semibold">{config.title}</h1>
      <p className="mt-3 text-sm text-muted-foreground">{config.summary}</p>
      <div className="mt-6 flex flex-wrap gap-3">
        <Link
          href="/clawgic"
          className="rounded-md bg-emerald-400 px-4 py-2 text-sm font-semibold text-black hover:bg-emerald-300 transition-colors"
        >
          Back to Clawgic Shell
        </Link>
        <Link
          href="/"
          className="rounded-md border border-border px-4 py-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          Pivot Landing
        </Link>
      </div>
    </div>
  )
}
