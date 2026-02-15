const anchor = require("@coral-xyz/anchor");
const fs = require("fs");

async function main() {
  const connection = new anchor.web3.Connection("http://localhost:8899", "confirmed");
  const wallet = anchor.Wallet.local();
  const provider = new anchor.AnchorProvider(connection, wallet, { commitment: "confirmed" });
  anchor.setProvider(provider);

  const idl = JSON.parse(fs.readFileSync("target/idl/moltrank.json", "utf8"));
  const program = new anchor.Program(idl, provider);

  console.log("🔍 Searching for pair accounts...\n");

  try {
    // Fetch all pair accounts
    const pairs = await program.account.pair.all();
    
    if (pairs.length === 0) {
      console.log("❌ No pair accounts found");
      return;
    }

    console.log(`✅ Found ${pairs.length} pair account(s):\n`);

    pairs.forEach((pair, index) => {
      const p = pair.account;
      console.log(`📊 Pair ${index + 1}:`);
      console.log(`   Address: ${pair.publicKey.toBase58()}`);
      console.log(`   Post A: ${p.postA.toBase58()}`);
      console.log(`   Post B: ${p.postB.toBase58()}`);
      console.log(`   Commit deadline: ${new Date(p.commitDeadline.toNumber() * 1000).toISOString()}`);
      console.log(`   Reveal deadline: ${new Date(p.revealDeadline.toNumber() * 1000).toISOString()}`);
      console.log(`   Total votes: ${p.totalVotes}`);
      console.log(`   Votes A: ${p.votesA}`);
      console.log(`   Votes B: ${p.votesB}`);
      console.log(`   Settled: ${p.settled ? "✅ Yes" : "⏳ No"}`);
      console.log(`   Golden: ${p.isGolden ? "⭐ Yes" : "No"}`);
      
      if (p.winner !== null) {
        const winnerName = p.winner === 0 ? "Post A" : p.winner === 1 ? "Post B" : "Tie";
        console.log(`   Winner: ${winnerName}`);
      } else {
        const winnerName = p.votesA > p.votesB ? "Post A (pending settlement)" :
                           p.votesB > p.votesA ? "Post B (pending settlement)" : "Tie (pending settlement)";
        console.log(`   Winner: ${winnerName}`);
      }
      console.log("");
    });
  } catch (err) {
    console.error("Error:", err.message);
    console.error(err.stack);
  }
}

main();
