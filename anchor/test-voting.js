/**
 * Test voting instructions on devnet
 * Tests: create_pair, commit_vote, reveal_vote, settle_pair
 */

const anchor = require("@coral-xyz/anchor");
const { PublicKey, Keypair, SystemProgram } = require("@solana/web3.js");
const fs = require("fs");

// Program ID on localnet
const PROGRAM_ID = new PublicKey("41rM5dUSCMNSF3GyrXUH7Ykrr7d2suTxs8G2XP9ajD71");

async function main() {
  const { keccak_256 } = await import("@noble/hashes/sha3.js");

  // Connect to localhost
  const connection = new anchor.web3.Connection(
    "http://localhost:8899",
    "confirmed"
  );

  // Load wallet
  const wallet = anchor.Wallet.local();
  const provider = new anchor.AnchorProvider(connection, wallet, {
    commitment: "confirmed",
  });
  anchor.setProvider(provider);

  console.log("🔗 Connected to localhost");
  console.log("👛 Wallet:", wallet.publicKey.toBase58());
  console.log("📝 Program:", PROGRAM_ID.toBase58());

  // Load the program
  const idl = JSON.parse(fs.readFileSync("target/idl/moltrank.json", "utf8"));
  const program = new anchor.Program(idl, provider);

  console.log("\n=== Testing Voting Instructions ===\n");

  // Generate test IDs (must be exactly 32 bytes)
  const marketIdStr = "test_market_" + Date.now();
  const marketId = Buffer.alloc(32);
  Buffer.from(marketIdStr).copy(marketId);

  const pairIdStr = "test_pair_" + Date.now();
  const pairId = Buffer.alloc(32);
  Buffer.from(pairIdStr).copy(pairId);

  // Create fake post public keys (just for testing)
  const postA = Keypair.generate().publicKey;
  const postB = Keypair.generate().publicKey;

  // Set deadlines (30 seconds from now for commit, 60 seconds for reveal)
  const now = Math.floor(Date.now() / 1000);
  const commitDeadline = new anchor.BN(now + 30);
  const revealDeadline = new anchor.BN(now + 60);

  // Derive PDAs
  const [globalPool] = PublicKey.findProgramAddressSync(
    [Buffer.from("global_pool")],
    program.programId
  );

  const [market] = PublicKey.findProgramAddressSync(
    [Buffer.from("market"), marketId],
    program.programId
  );

  const [pair] = PublicKey.findProgramAddressSync(
    [Buffer.from("pair"), pairId],
    program.programId
  );

  const [vote] = PublicKey.findProgramAddressSync(
    [Buffer.from("vote"), pairId, wallet.publicKey.toBuffer()],
    program.programId
  );

  try {
    // 1. Check if global pool exists, if not initialize it
    console.log("1️⃣  Checking global pool...");
    try {
      const poolData = await connection.getAccountInfo(globalPool);
      if (poolData) {
        console.log("   ✅ Global pool exists");
      } else {
        throw new Error("Not found");
      }
    } catch (e) {
      console.log("   ⚠️  Global pool not found, initializing...");
      const tx = await program.methods
        .initGlobalPool()
        .accounts({
          globalPool,
          authority: wallet.publicKey,
          systemProgram: SystemProgram.programId,
        })
        .rpc();
      console.log("   ✅ Global pool initialized:", tx);
      await new Promise(resolve => setTimeout(resolve, 2000)); // Wait for confirmation
    }

    // 2. Create market
    console.log("\n2️⃣  Creating test market...");
    try {
      const tx = await program.methods
        .createMarket(
          Array.from(marketId),
          "Test Market",
          1,
          new anchor.BN(1000000)
        )
        .accounts({
          market,
          globalPool,
          authority: wallet.publicKey,
          systemProgram: SystemProgram.programId,
        })
        .rpc();
      console.log("   ✅ Market created:", tx);
      await new Promise(resolve => setTimeout(resolve, 2000));
    } catch (e) {
      if (e.message?.includes("already in use") || e.message?.includes("0x0")) {
        console.log("   ℹ️  Market already exists, continuing...");
      } else {
        throw e;
      }
    }

    // 3. Create voting pair
    console.log("\n3️⃣  Creating voting pair...");
    console.log("   Post A:", postA.toBase58());
    console.log("   Post B:", postB.toBase58());
    console.log("   Commit deadline:", new Date(commitDeadline.toNumber() * 1000).toISOString());
    console.log("   Reveal deadline:", new Date(revealDeadline.toNumber() * 1000).toISOString());

    const createPairTx = await program.methods
      .createPair(
        Array.from(pairId),
        Array.from(marketId),
        postA,
        postB,
        commitDeadline,
        revealDeadline,
        false, // not golden
        null
      )
      .accounts({
        pair,
        market,
        globalPool,
        authority: wallet.publicKey,
        systemProgram: SystemProgram.programId,
      })
      .rpc();
    console.log("   ✅ Pair created:", createPairTx);
    await new Promise(resolve => setTimeout(resolve, 2000));

    // 4. Commit a vote
    console.log("\n4️⃣  Committing vote...");

    // Generate commitment
    const choice = 0; // Vote for Post A
    const salt = Keypair.generate().publicKey.toBuffer(); // Random 32 bytes
    const commitmentData = Buffer.concat([Buffer.from([choice]), salt]);
    const commitmentHash = Buffer.from(keccak_256(commitmentData));

    console.log("   Choice:", choice, "(Post A)");
    console.log("   Salt:", salt.toString('hex').slice(0, 16) + "...");
    console.log("   Commitment hash:", commitmentHash.toString('hex').slice(0, 16) + "...");

    const stake = new anchor.BN(100000); // 0.0001 SOL

    const commitTx = await program.methods
      .commitVote(
        Array.from(pairId),
        Array.from(commitmentHash),
        stake
      )
      .accounts({
        vote,
        pair,
        globalPool,
        curator: wallet.publicKey,
        systemProgram: SystemProgram.programId,
      })
      .rpc();
    console.log("   ✅ Vote committed:", commitTx);
    console.log("   Stake:", stake.toString(), "lamports");

    // 5. Wait for commit deadline to pass
    console.log("\n⏰ Waiting for commit deadline to pass (31 seconds)...");
    await new Promise(resolve => setTimeout(resolve, 31000));

    // 6. Reveal the vote
    console.log("\n5️⃣  Revealing vote...");

    const revealTx = await program.methods
      .revealVote(
        Array.from(pairId),
        choice,
        Array.from(salt)
      )
      .accounts({
        vote,
        pair,
        curator: wallet.publicKey,
      })
      .rpc();
    console.log("   ✅ Vote revealed:", revealTx);
    await new Promise(resolve => setTimeout(resolve, 2000));

    // 7. Wait for reveal deadline to pass
    console.log("\n⏰ Waiting for reveal deadline to pass (31 more seconds)...");
    await new Promise(resolve => setTimeout(resolve, 31000));

    // 8. Settle the pair
    console.log("\n6️⃣  Settling pair...");

    const settleTx = await program.methods
      .settlePair(Array.from(pairId))
      .accounts({
        pair,
        globalPool,
        authority: wallet.publicKey,
      })
      .rpc();
    console.log("   ✅ Pair settled:", settleTx);

    console.log("\n✅ ALL TESTS PASSED! 🎉");
    console.log("\n📊 Test Summary:");
    console.log("   • Global pool: ✅ Initialized/verified");
    console.log("   • Market: ✅ Created");
    console.log("   • Pair: ✅ Created with deadlines");
    console.log("   • Vote: ✅ Committed with hash");
    console.log("   • Reveal: ✅ Verified and recorded");
    console.log("   • Settlement: ✅ Pair settled");

  } catch (error) {
    console.error("\n❌ Test failed:");
    console.error(error);
    if (error.logs) {
      console.error("\nProgram logs:");
      error.logs.forEach((log) => console.error(log));
    }
    process.exit(1);
  }
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
