"""Adversarial scenario configurations for simulation.

Implements 10 test scenarios with different combinations of agent types
to stress-test the Moltbook ranking system.

Each scenario runs 10,000+ rounds.

Reference: PRD Sections 6.2, 6.3
"""

from typing import List, Dict, Optional, Callable
from dataclasses import dataclass

from .models import Curator, Post, Vote
from .engine import SimulationEngine, SimulationConfig
from .agents import (
    HonestCurator,
    RandomCurator,
    LazyCurator,
    AIBotCurator,
    ColludingRing,
    WhaleCurator,
    SybilFarmer,
    FreeRiderReader,
    ChurningReader,
    AgentStrategy,
)


@dataclass
class Market:
    """Represents a content market."""

    market_id: str
    posts: List[Post]
    subscribers: int = 0


@dataclass
class ScenarioConfig:
    """Configuration for a simulation scenario."""

    name: str
    description: str
    num_rounds: int = 10000
    curators: List[tuple] = None  # (curator_id, strategy, stake)
    markets: List[Market] = None
    readers: List = None  # FreeRiderReader or ChurningReader instances
    simulation_config: Optional[SimulationConfig] = None

    # Dynamic events (round_number -> callable)
    events: Optional[Dict[int, Callable]] = None


def create_baseline_scenario() -> ScenarioConfig:
    """Scenario 1: Baseline.

    - 50 honest curators
    - 200 readers (free-riders)
    - 3 markets
    - 10,000 rounds
    """
    # Create curators
    curators = []
    for i in range(50):
        curator_id = f"honest_{i}"
        strategy = HonestCurator(accuracy=0.75)
        stake = 1000.0
        curators.append((curator_id, strategy, stake))

    # Create markets
    markets = []
    for m in range(3):
        market_id = f"market_{m}"
        posts = [Post(post_id=f"m{m}_post_{i}", content=f"Post {i} in market {m}")
                 for i in range(50)]
        market = Market(market_id=market_id, posts=posts, subscribers=200)
        markets.append(market)

    # Create readers
    readers = []
    for i in range(200):
        reader = FreeRiderReader(
            reader_id=f"reader_{i}",
            subscribed_markets=[f"market_{i % 3}"]
        )
        readers.append(reader)

    return ScenarioConfig(
        name="Baseline",
        description="50 honest curators, 200 readers, 3 markets",
        num_rounds=10000,
        curators=curators,
        markets=markets,
        readers=readers,
        simulation_config=SimulationConfig(random_seed=42)
    )


def create_bot_flood_scenario() -> ScenarioConfig:
    """Scenario 2: Bot flood.

    - Baseline + 200 AI bot curators
    - Tests system resilience to automated curation
    """
    # Start with baseline
    baseline = create_baseline_scenario()

    # Add 200 AI bots
    curators = list(baseline.curators)
    for i in range(200):
        curator_id = f"bot_{i}"
        strategy = AIBotCurator(accuracy=0.70)
        stake = 500.0  # Lower stake than honest curators
        curators.append((curator_id, strategy, stake))

    return ScenarioConfig(
        name="Bot Flood",
        description="Baseline + 200 AI bot curators",
        num_rounds=10000,
        curators=curators,
        markets=baseline.markets,
        readers=baseline.readers,
        simulation_config=SimulationConfig(random_seed=42)
    )


def create_collusion_scenario() -> ScenarioConfig:
    """Scenario 3: Collusion ring.

    - Baseline + ring of 10 colluding curators
    - Tests anti-collusion mechanisms
    """
    baseline = create_baseline_scenario()

    # Create collusion ring
    ring_ids = [f"colluder_{i}" for i in range(10)]
    ring = ColludingRing(curator_ids=ring_ids, accuracy=0.60)

    # Add ring members
    curators = list(baseline.curators)
    for ring_id in ring_ids:
        curators.append((ring_id, ring, 1000.0))

    return ScenarioConfig(
        name="Collusion Ring",
        description="Baseline + 10-member collusion ring",
        num_rounds=10000,
        curators=curators,
        markets=baseline.markets,
        readers=baseline.readers,
        simulation_config=SimulationConfig(random_seed=42)
    )


def create_whale_sybil_scenario() -> ScenarioConfig:
    """Scenario 4: Whale + Sybil attack.

    - Baseline + 1 whale (50x stake) + 20 Sybil wallets
    - Tests stake concentration and identity splitting
    """
    baseline = create_baseline_scenario()
    curators = list(baseline.curators)

    # Add whale
    whale_id = "whale_1"
    whale_strategy = WhaleCurator(accuracy=0.70)
    whale_stake = 50000.0  # 50x normal stake
    curators.append((whale_id, whale_strategy, whale_stake))

    # Add Sybil farmer with 20 wallets
    sybil_ids = [f"sybil_{i}" for i in range(20)]
    sybil_strategy = SybilFarmer(wallet_ids=sybil_ids, accuracy=0.65)
    for sybil_id in sybil_ids:
        # Split 20,000 across 20 wallets = 1,000 each
        curators.append((sybil_id, sybil_strategy, 1000.0))

    return ScenarioConfig(
        name="Whale + Sybil",
        description="Baseline + 1 whale (50x stake) + 20 Sybil wallets",
        num_rounds=10000,
        curators=curators,
        markets=baseline.markets,
        readers=baseline.readers,
        simulation_config=SimulationConfig(random_seed=42)
    )


def create_lazy_majority_scenario() -> ScenarioConfig:
    """Scenario 5: Lazy curator majority.

    - 60% lazy curators, 40% honest
    - Tests system degradation with low-effort curation
    """
    curators = []

    # 30 lazy curators (60%)
    for i in range(30):
        curator_id = f"lazy_{i}"
        strategy = LazyCurator()
        stake = 1000.0
        curators.append((curator_id, strategy, stake))

    # 20 honest curators (40%)
    for i in range(20):
        curator_id = f"honest_{i}"
        strategy = HonestCurator(accuracy=0.75)
        stake = 1000.0
        curators.append((curator_id, strategy, stake))

    # Same markets and readers as baseline
    baseline = create_baseline_scenario()

    return ScenarioConfig(
        name="Lazy Majority",
        description="60% lazy curators, 40% honest",
        num_rounds=10000,
        curators=curators,
        markets=baseline.markets,
        readers=baseline.readers,
        simulation_config=SimulationConfig(random_seed=42)
    )


def create_reader_churn_scenario() -> ScenarioConfig:
    """Scenario 6: Reader churn.

    - Baseline, but 50% of readers leave at round 5000
    - Tests impact of sudden subscriber drop
    """
    baseline = create_baseline_scenario()

    # Replace free-riders with churning readers
    readers = []
    for i in range(200):
        reader = ChurningReader(
            reader_id=f"churner_{i}",
            subscribed_markets=[f"market_{i % 3}"],
            quality_threshold=0.6
        )
        readers.append(reader)

    # Event: Force churn at round 5000
    def churn_event(engine: SimulationEngine, readers: List[ChurningReader]):
        """Force 50% of readers to churn."""
        for i, reader in enumerate(readers):
            if i < len(readers) // 2:  # First 50%
                reader.subscribed_markets = []
                reader.churn_round = 5000

    events = {
        5000: lambda engine: churn_event(engine, readers)
    }

    return ScenarioConfig(
        name="Reader Churn",
        description="50% readers leave at round 5000",
        num_rounds=10000,
        curators=baseline.curators,
        markets=baseline.markets,
        readers=readers,
        simulation_config=SimulationConfig(random_seed=42),
        events=events
    )


def create_new_market_scenario() -> ScenarioConfig:
    """Scenario 7: New market launch.

    - Baseline with 3 markets
    - 4th market added at round 3000 with 0 initial subscribers
    - Tests market bootstrap dynamics
    """
    baseline = create_baseline_scenario()
    markets = list(baseline.markets)

    # Event: Add new market at round 3000
    def add_market_event(engine: SimulationEngine):
        """Add 4th market with no subscribers."""
        new_market = Market(
            market_id="market_3",
            posts=[Post(post_id=f"m3_post_{i}", content=f"New market post {i}")
                   for i in range(50)],
            subscribers=0
        )
        # Add posts to engine
        for post in new_market.posts:
            engine.add_post(post)

    events = {
        3000: add_market_event
    }

    return ScenarioConfig(
        name="New Market Launch",
        description="4th market added at round 3000 with 0 subscribers",
        num_rounds=10000,
        curators=baseline.curators,
        markets=markets,
        readers=baseline.readers,
        simulation_config=SimulationConfig(random_seed=42),
        events=events
    )


def create_alpha_sweep_scenario() -> ScenarioConfig:
    """Scenario 8: Alpha parameter sweep.

    - Baseline scenario
    - Run with alpha values: 0.10, 0.20, 0.30, 0.40, 0.50
    - Tests reward distribution sensitivity

    Note: This returns a single config; caller should run multiple times
    with different alpha values.
    """
    baseline = create_baseline_scenario()

    # Default to alpha=0.30; caller modifies simulation_config.alpha
    config = SimulationConfig(random_seed=42)
    config.alpha = 0.30  # Will be modified by scenario runner

    return ScenarioConfig(
        name="Alpha Sweep (α=0.30)",
        description="Test alpha from 0.10 to 0.50",
        num_rounds=10000,
        curators=baseline.curators,
        markets=baseline.markets,
        readers=baseline.readers,
        simulation_config=config
    )


def create_minority_loss_sweep_scenario() -> ScenarioConfig:
    """Scenario 9: Minority loss percentage sweep.

    - Baseline scenario
    - Run with minority payout: 90%, 80%, 70%, 60%, 50%
    - (Minority loss = 100% - payout)
    - Tests incentive alignment

    Note: This returns a single config; caller should run multiple times
    with different minority_payout values.
    """
    baseline = create_baseline_scenario()

    # Default to 80% payout (20% loss)
    config = SimulationConfig(random_seed=42)
    config.minority_payout = 0.80  # Will be modified by scenario runner

    return ScenarioConfig(
        name="Minority Loss Sweep (20% loss)",
        description="Test minority payout from 50% to 90%",
        num_rounds=10000,
        curators=baseline.curators,
        markets=baseline.markets,
        readers=baseline.readers,
        simulation_config=config
    )


def create_base_farming_scenario() -> ScenarioConfig:
    """Scenario 10: Base farming attack.

    - Attacker creates 5 empty markets (0 real posts, 0 subscribers)
    - Tests if attacker can drain rewards via empty markets
    """
    baseline = create_baseline_scenario()
    markets = list(baseline.markets)

    # Add 5 empty markets controlled by attacker
    for i in range(5):
        empty_market = Market(
            market_id=f"farm_market_{i}",
            posts=[],  # No real posts
            subscribers=0
        )
        markets.append(empty_market)

    # Attacker curators vote on non-existent pairs to farm rewards
    curators = list(baseline.curators)
    for i in range(10):
        curator_id = f"farmer_{i}"
        # Farmers vote randomly since there's no content
        strategy = RandomCurator()
        stake = 500.0
        curators.append((curator_id, strategy, stake))

    return ScenarioConfig(
        name="Base Farming",
        description="Attacker creates 5 empty markets to farm rewards",
        num_rounds=10000,
        curators=curators,
        markets=markets,
        readers=baseline.readers,
        simulation_config=SimulationConfig(random_seed=42)
    )


# Scenario registry
SCENARIOS = {
    "baseline": create_baseline_scenario,
    "bot_flood": create_bot_flood_scenario,
    "collusion": create_collusion_scenario,
    "whale_sybil": create_whale_sybil_scenario,
    "lazy_majority": create_lazy_majority_scenario,
    "reader_churn": create_reader_churn_scenario,
    "new_market": create_new_market_scenario,
    "alpha_sweep": create_alpha_sweep_scenario,
    "minority_loss_sweep": create_minority_loss_sweep_scenario,
    "base_farming": create_base_farming_scenario,
}


def get_scenario(name: str) -> ScenarioConfig:
    """Get a scenario by name.

    Args:
        name: Scenario name (e.g., "baseline", "bot_flood")

    Returns:
        ScenarioConfig instance

    Raises:
        KeyError: If scenario name not found
    """
    if name not in SCENARIOS:
        raise KeyError(f"Unknown scenario: {name}. Available: {list(SCENARIOS.keys())}")
    return SCENARIOS[name]()


def list_scenarios() -> List[str]:
    """List all available scenario names.

    Returns:
        List of scenario names
    """
    return list(SCENARIOS.keys())
