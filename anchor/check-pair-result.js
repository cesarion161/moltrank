const anchor = require("@coral-xyz/anchor");
const { Keypair } = require("@solana/web3.js");

async function main() {
  const provider = anchor.AnchorProvider.env();
  anchor.setProvider(provider);

  const idl = require("./target/idl/moltrank.json");
  const program = new anchor.Program(idl, provider);

  // Market and pair IDs from test
  const marketId = Buffer.from("moltrank_test_market_001").toString("hex");
  const pairId = Buffer.from("moltrank_test_pair_001").toString("hex");

  const [marketPda] = anchor.web3.PublicKey.findProgramAddressSync(
    [Buffer.from("market"), Buffer.from(marketId, "hex")],
    program.programId
  );

  const [pairPda] = anchor.web3.PublicKey.findProgramAddressSync(
    [Buffer.from("pair"), Buffer.from(pairId, "hex")],
    program.programId
  );

  try {
    const pair = await program.account.pair.fetch(pairPda);
    console.log("\n📊 Pair Results:");
    console.log("   Post A votes:", pair.votesA.toString());
    console.log("   Post B votes:", pair.votesB.toString());
    console.log("   Total stake A:", pair.totalStakeA.toString(), "lamports");
    console.log("   Total stake B:", pair.totalStakeB.toString(), "lamports");
    console.log("   Status:", pair.isSettled ? "✅ Settled" : "⏳ Pending");
    console.log("   Winner:", pair.votesA > pair.votesB ? "Post A" : pair.votesB > pair.votesA ? "Post B" : "Tie");
  } catch (err) {
    console.error("Error fetching pair:", err.message);
  }
}

main();
