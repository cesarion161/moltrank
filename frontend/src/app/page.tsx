export default function Home() {
  return (
    <div className="flex flex-col items-center justify-center min-h-[calc(100vh-12rem)]">
      <div className="text-center space-y-4">
        <h1 className="text-5xl font-bold mb-2">MoltRank</h1>
        <p className="text-xl text-muted-foreground max-w-2xl">
          ELO-based social media ranking on Solana
        </p>
        <p className="text-sm text-muted-foreground">
          A decentralized ranking platform that uses ELO ratings to surface quality content
        </p>
      </div>
    </div>
  )
}
