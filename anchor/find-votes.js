const anchor = require("@coral-xyz/anchor");
const fs = require("fs");

async function main() {
  const connection = new anchor.web3.Connection("http://localhost:8899", "confirmed");
  const wallet = anchor.Wallet.local();
  const provider = new anchor.AnchorProvider(connection, wallet, { commitment: "confirmed" });
  anchor.setProvider(provider);

  const idl = JSON.parse(fs.readFileSync("target/idl/moltrank.json", "utf8"));
  const program = new anchor.Program(idl, provider);

  console.log("🔍 Searching for vote accounts...\n");

  try {
    const votes = await program.account.vote.all();
    
    if (votes.length === 0) {
      console.log("❌ No vote accounts found");
      return;
    }

    console.log(`✅ Found ${votes.length} vote account(s):\n`);

    votes.forEach((vote, index) => {
      const v = vote.account;
      console.log(`🗳️  Vote ${index + 1}:`);
      console.log(`   Address: ${vote.publicKey.toBase58()}`);
      console.log(`   Curator: ${v.curator.toBase58()}`);
      console.log(`   Stake: ${v.stake.toString()} lamports`);
      console.log(`   Committed at: ${new Date(v.committedAt.toNumber() * 1000).toISOString()}`);
      
      if (v.revealedChoice !== null) {
        const choiceName = v.revealedChoice === 0 ? "Post A" : v.revealedChoice === 1 ? "Post B" : "Unknown";
        console.log(`   Revealed: ✅ Yes`);
        console.log(`   Choice: ${choiceName}`);
        if (v.revealedAt) {
          console.log(`   Revealed at: ${new Date(v.revealedAt.toNumber() * 1000).toISOString()}`);
        }
      } else {
        console.log(`   Revealed: ⏳ No (still in commit phase)`);
      }
      
      if (v.isWinner !== null) {
        console.log(`   Winner side: ${v.isWinner ? "✅ Yes" : "❌ No"}`);
      }
      
      if (v.reward && v.reward.toNumber() > 0) {
        console.log(`   Reward earned: ${v.reward.toString()} lamports`);
      }
      
      console.log("");
    });
  } catch (err) {
    console.error("Error:", err.message);
    console.error(err.stack);
  }
}

main();
