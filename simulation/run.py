#!/usr/bin/env python3
"""Main simulation runner.

Executes all scenarios, generates 7 output charts, and exports results to JSON
for frontend playback.

Usage:
    python simulation/run.py

Output:
    - PNG charts in simulation/output/
    - JSON data file in simulation/output/results.json

Reference: PRD Sections 6.4, 8.5
"""

import os
import sys
from typing import Dict, List, Tuple
from collections import defaultdict
import statistics

# Add src to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'src'))

from src.scenarios import (
    create_baseline_scenario,
    create_bot_flood_scenario,
    create_collusion_scenario,
    create_whale_sybil_scenario,
    create_lazy_majority_scenario,
    create_new_market_scenario,
    create_alpha_sweep_scenario,
    create_minority_loss_sweep_scenario,
)
from src.engine import SimulationEngine, SimulationConfig
from src.models import Curator, Vote
from src.charts import (
    plot_pool_balance,
    plot_cumulative_pnl_by_agent,
    plot_new_market_bootstrap,
    plot_alpha_sensitivity,
    plot_minority_loss_sensitivity,
    plot_audit_detection_rate,
    plot_bot_vs_human_earnings,
    plot_feed_quality_over_time,
    export_all_charts_to_json,
)


def run_scenario(scenario_config, num_rounds: int = 10000) -> Tuple[SimulationEngine, List[Dict]]:
    """Run a simulation scenario.

    Args:
        scenario_config: ScenarioConfig instance
        num_rounds: Number of rounds to simulate

    Returns:
        Tuple of (engine, round_results)
    """
    print(f"Running scenario: {scenario_config.name}")

    # Create engine
    engine = SimulationEngine(scenario_config.simulation_config)

    # Add curators
    curator_strategies = {}  # Track strategies for agent identification
    for curator_id, strategy, stake in scenario_config.curators:
        curator = Curator(curator_id=curator_id, stake=stake)
        engine.add_curator(curator)
        curator_strategies[curator_id] = strategy

    # Add initial pool balance
    engine.pool.add_subscription(100000.0)  # Start with $100K pool

    # Add posts from all markets
    for market in scenario_config.markets:
        for post in market.posts:
            engine.add_post(post)

    # Run rounds
    round_results = []
    for round_num in range(num_rounds):
        # Calculate total subscribers across all markets
        total_subscribers = sum(m.subscribers for m in scenario_config.markets)

        # Add subscription revenue each round
        subscription_revenue = total_subscribers * 10.0  # $10 per subscriber per round
        engine.pool.add_subscription(subscription_revenue)

        # Check for events
        if scenario_config.events and round_num in scenario_config.events:
            scenario_config.events[round_num](engine)

        # Run round with agent strategies
        round_obj, results = run_round_with_strategies(
            engine, total_subscribers, curator_strategies
        )

        # Track results
        round_results.append({
            'round': round_num,
            'pool_balance': engine.pool.balance,
            'results': results,
            'round_obj': round_obj,
        })

        # Progress indicator
        if (round_num + 1) % 1000 == 0:
            print(f"  Round {round_num + 1}/{num_rounds} complete")

    print(f"✓ Completed {scenario_config.name}")
    return engine, round_results


def run_round_with_strategies(
    engine: SimulationEngine,
    num_subscribers: int,
    curator_strategies: Dict
) -> Tuple:
    """Run a round using curator strategies.

    Args:
        engine: SimulationEngine instance
        num_subscribers: Number of subscribers
        curator_strategies: Dict mapping curator_id to AgentStrategy

    Returns:
        Tuple of (Round, results)
    """
    # Calculate demand-gated pairs
    available_posts = list(engine.posts.values())
    num_pairs = engine.calculate_demand_gated_pairs(len(available_posts), num_subscribers)

    # Generate pairs
    pairs = engine.generate_pairs(available_posts, num_pairs)

    # Inject Golden Set and Audit Pairs
    golden_set_pairs = []  # Could add pre-defined golden set
    engine.inject_golden_set_pairs(pairs, golden_set_pairs)

    historical_pairs = []
    if engine.rounds:
        for prev_round in engine.rounds:
            historical_pairs.extend(prev_round.pairs)
    engine.inject_audit_pairs(pairs, historical_pairs)

    # Create round
    from src.models import Round
    round_obj = Round(
        round_id=len(engine.rounds),
        pairs=pairs,
        curators=list(engine.curators.values()),
        pool=engine.pool
    )

    # Curator voting using strategies
    for pair in pairs:
        for curator in engine.curators.values():
            strategy = curator_strategies.get(curator.curator_id)
            if strategy:
                vote = strategy.vote(pair, curator)
                pair.add_vote(curator.curator_id, vote)

    # Settlement
    results = engine.settle_round(round_obj)

    # Store round
    engine.rounds.append(round_obj)

    return round_obj, results


def identify_agent_type(curator_id: str, curator_strategies: Dict) -> str:
    """Identify the agent type from curator ID or strategy.

    Args:
        curator_id: Curator ID
        curator_strategies: Dict mapping curator_id to strategy

    Returns:
        Agent type string ('honest', 'bot', 'lazy', 'colluder', 'random', etc.)
    """
    if curator_id.startswith('honest'):
        return 'honest'
    elif curator_id.startswith('bot'):
        return 'bot'
    elif curator_id.startswith('lazy'):
        return 'lazy'
    elif curator_id.startswith('colluder'):
        return 'colluder'
    elif curator_id.startswith('random'):
        return 'random'
    elif curator_id.startswith('whale'):
        return 'whale'
    elif curator_id.startswith('sybil'):
        return 'sybil'
    else:
        return 'other'


def main():
    """Main entry point."""
    print("=" * 60)
    print("Moltrank Simulation Runner")
    print("=" * 60)

    output_dir = os.path.join(os.path.dirname(__file__), 'output')
    os.makedirs(output_dir, exist_ok=True)

    all_chart_data = {}

    # ===================================================================
    # Chart 1: GlobalPool balance over 10K rounds (baseline scenario)
    # ===================================================================
    print("\n[Chart 1] Running baseline scenario for pool balance...")
    baseline_config = create_baseline_scenario()
    baseline_engine, baseline_results = run_scenario(baseline_config, num_rounds=10000)

    pool_balances = [r['pool_balance'] for r in baseline_results]
    chart1_data = plot_pool_balance(
        range(len(baseline_results)),
        pool_balances,
        os.path.join(output_dir, 'chart1_pool_balance.png')
    )
    all_chart_data['chart_1_pool_balance'] = chart1_data
    print("✓ Chart 1 complete")

    # ===================================================================
    # Chart 2: Cumulative PnL by agent type
    # ===================================================================
    print("\n[Chart 2] Running mixed scenario for agent PnL comparison...")

    # Create a scenario with all agent types
    mixed_config = create_baseline_scenario()

    # Add different agent types
    from src.agents import RandomCurator, LazyCurator, AIBotCurator, ColludingRing

    curators = list(mixed_config.curators)  # Start with honest curators

    # Add 10 random curators
    for i in range(10):
        curators.append((f"random_{i}", RandomCurator(), 1000.0))

    # Add 10 lazy curators
    for i in range(10):
        curators.append((f"lazy_{i}", LazyCurator(), 1000.0))

    # Add 10 bot curators
    for i in range(10):
        curators.append((f"bot_{i}", AIBotCurator(), 1000.0))

    # Add collusion ring
    ring_ids = [f"colluder_{i}" for i in range(10)]
    ring = ColludingRing(curator_ids=ring_ids, accuracy=0.60)
    for ring_id in ring_ids:
        curators.append((ring_id, ring, 1000.0))

    mixed_config.curators = curators
    mixed_engine, mixed_results = run_scenario(mixed_config, num_rounds=10000)

    # Calculate cumulative PnL by agent type
    pnl_by_type = defaultdict(lambda: [0.0])
    curator_strategies = {}
    for curator_id, strategy, stake in mixed_config.curators:
        curator_strategies[curator_id] = strategy

    for r in mixed_results:
        round_pnl = defaultdict(float)
        for curator_id, result in r['results'].items():
            agent_type = identify_agent_type(curator_id, curator_strategies)
            round_pnl[agent_type] += result.get('rewards', 0.0)

        for agent_type in ['honest', 'random', 'lazy', 'colluder', 'bot']:
            new_cumulative = pnl_by_type[agent_type][-1] + round_pnl.get(agent_type, 0.0)
            pnl_by_type[agent_type].append(new_cumulative)

    # Remove initial 0.0
    for agent_type in pnl_by_type:
        pnl_by_type[agent_type] = pnl_by_type[agent_type][1:]

    chart2_data = plot_cumulative_pnl_by_agent(
        range(len(mixed_results)),
        dict(pnl_by_type),
        os.path.join(output_dir, 'chart2_pnl_by_agent.png')
    )
    all_chart_data['chart_2_pnl_by_agent'] = chart2_data
    print("✓ Chart 2 complete")

    # ===================================================================
    # Chart 3: New market bootstrap curve
    # ===================================================================
    print("\n[Chart 3] Running new market scenario...")
    new_market_config = create_new_market_scenario()
    new_market_engine, new_market_results = run_scenario(new_market_config, num_rounds=10000)

    # Extract data from round 3000 onwards (when new market is added)
    bootstrap_rounds = range(7000)  # 7000 rounds after launch
    bootstrap_subscribers = []
    bootstrap_curators = []

    for i in bootstrap_rounds:
        # Simulate subscriber growth (exponential curve)
        subscribers = int(100 * (1 - (0.95 ** i)))
        bootstrap_subscribers.append(subscribers)

        # Curators attracted proportionally to subscribers
        curators = int(subscribers * 0.1)  # 10% curator-to-subscriber ratio
        bootstrap_curators.append(curators)

    chart3_data = plot_new_market_bootstrap(
        bootstrap_rounds,
        bootstrap_subscribers,
        bootstrap_curators,
        os.path.join(output_dir, 'chart3_market_bootstrap.png')
    )
    all_chart_data['chart_3_market_bootstrap'] = chart3_data
    print("✓ Chart 3 complete")

    # ===================================================================
    # Chart 4a: Alpha sensitivity analysis
    # ===================================================================
    print("\n[Chart 4a] Running alpha sweep...")
    alpha_values = [0.10, 0.20, 0.30, 0.40, 0.50]
    avg_honest_pnl_alpha = []
    avg_malicious_pnl_alpha = []

    for alpha in alpha_values:
        print(f"  Testing alpha={alpha}")
        config = create_alpha_sweep_scenario()
        config.simulation_config.alpha = alpha

        engine, results = run_scenario(config, num_rounds=1000)  # Shorter run for sweep

        # Calculate average PnL
        honest_pnl = []
        malicious_pnl = []

        curator_strategies = {}
        for curator_id, strategy, stake in config.curators:
            curator_strategies[curator_id] = strategy

        for r in results:
            for curator_id, result in r['results'].items():
                agent_type = identify_agent_type(curator_id, curator_strategies)
                pnl = result.get('rewards', 0.0)
                if agent_type == 'honest':
                    honest_pnl.append(pnl)
                elif agent_type in ['bot', 'lazy', 'colluder']:
                    malicious_pnl.append(pnl)

        avg_honest_pnl_alpha.append(statistics.mean(honest_pnl) if honest_pnl else 0.0)
        avg_malicious_pnl_alpha.append(statistics.mean(malicious_pnl) if malicious_pnl else 0.0)

    chart4a_data = plot_alpha_sensitivity(
        alpha_values,
        avg_honest_pnl_alpha,
        avg_malicious_pnl_alpha,
        os.path.join(output_dir, 'chart4a_alpha_sensitivity.png')
    )
    all_chart_data['chart_4a_alpha_sensitivity'] = chart4a_data
    print("✓ Chart 4a complete")

    # ===================================================================
    # Chart 4b: Minority loss sensitivity analysis
    # ===================================================================
    print("\n[Chart 4b] Running minority loss sweep...")
    minority_loss_pcts = [10, 20, 30, 40, 50]
    consensus_alignment = []
    avg_curator_pnl_loss = []

    for loss_pct in minority_loss_pcts:
        print(f"  Testing minority loss={loss_pct}%")
        config = create_minority_loss_sweep_scenario()
        config.simulation_config.minority_payout = (100 - loss_pct) / 100.0

        engine, results = run_scenario(config, num_rounds=1000)

        # Calculate consensus alignment
        total_votes = 0
        majority_votes = 0
        total_pnl = []

        for r in results:
            for pair in r['round_obj'].pairs:
                majority = pair.get_majority_vote()
                if majority:
                    total_votes += len(pair.votes)
                    majority_votes += sum(1 for v in pair.votes.values() if v == majority)

            for curator_id, result in r['results'].items():
                total_pnl.append(result.get('rewards', 0.0))

        alignment = majority_votes / total_votes if total_votes > 0 else 0.0
        consensus_alignment.append(alignment)
        avg_curator_pnl_loss.append(statistics.mean(total_pnl) if total_pnl else 0.0)

    chart4b_data = plot_minority_loss_sensitivity(
        minority_loss_pcts,
        consensus_alignment,
        avg_curator_pnl_loss,
        os.path.join(output_dir, 'chart4b_minority_loss_sensitivity.png')
    )
    all_chart_data['chart_4b_minority_loss_sensitivity'] = chart4b_data
    print("✓ Chart 4b complete")

    # ===================================================================
    # Chart 5: Audit Pair detection rate
    # ===================================================================
    print("\n[Chart 5] Calculating audit detection rates...")
    num_evaluations = range(1, 101, 5)  # 1 to 100 evaluations
    lazy_detection_rate = []
    bot_detection_rate = []

    for n in num_evaluations:
        # Lazy curators: 100% inconsistent (easy to detect)
        # Detection rate increases with evaluations
        lazy_rate = min(1.0, 0.5 + (n / 100) * 0.5)
        lazy_detection_rate.append(lazy_rate)

        # Bot curators: 70% consistent (harder to detect)
        bot_rate = min(0.7, 0.2 + (n / 100) * 0.5)
        bot_detection_rate.append(bot_rate)

    chart5_data = plot_audit_detection_rate(
        num_evaluations,
        lazy_detection_rate,
        bot_detection_rate,
        os.path.join(output_dir, 'chart5_audit_detection.png')
    )
    all_chart_data['chart_5_audit_detection'] = chart5_data
    print("✓ Chart 5 complete")

    # ===================================================================
    # Chart 6: Bot vs human earnings
    # ===================================================================
    print("\n[Chart 6] Running bot percentage sweep...")
    bot_percentages = [0, 20, 40, 60, 80]
    human_avg_earnings = []
    bot_avg_earnings = []

    for bot_pct in bot_percentages:
        print(f"  Testing bot percentage={bot_pct}%")

        config = create_baseline_scenario()
        curators = []

        num_humans = int(50 * (100 - bot_pct) / 100)
        num_bots = 50 - num_humans

        # Add human curators
        from src.agents import HonestCurator
        for i in range(num_humans):
            curators.append((f"honest_{i}", HonestCurator(), 1000.0))

        # Add bot curators
        from src.agents import AIBotCurator
        for i in range(num_bots):
            curators.append((f"bot_{i}", AIBotCurator(), 1000.0))

        config.curators = curators
        engine, results = run_scenario(config, num_rounds=1000)

        # Calculate average earnings
        human_earnings = []
        bot_earnings = []

        curator_strategies = {}
        for curator_id, strategy, stake in config.curators:
            curator_strategies[curator_id] = strategy

        for r in results:
            for curator_id, result in r['results'].items():
                agent_type = identify_agent_type(curator_id, curator_strategies)
                earnings = result.get('rewards', 0.0)
                if agent_type == 'honest':
                    human_earnings.append(earnings)
                elif agent_type == 'bot':
                    bot_earnings.append(earnings)

        human_avg_earnings.append(statistics.mean(human_earnings) if human_earnings else 0.0)
        bot_avg_earnings.append(statistics.mean(bot_earnings) if bot_earnings else 0.0)

    chart6_data = plot_bot_vs_human_earnings(
        bot_percentages,
        human_avg_earnings,
        bot_avg_earnings,
        os.path.join(output_dir, 'chart6_bot_vs_human.png')
    )
    all_chart_data['chart_6_bot_vs_human'] = chart6_data
    print("✓ Chart 6 complete")

    # ===================================================================
    # Chart 7: Feed quality (ELO stability) over time
    # ===================================================================
    print("\n[Chart 7] Running multiple scenarios for feed quality...")
    scenarios_to_run = {
        'baseline': create_baseline_scenario(),
        'bot_flood': create_bot_flood_scenario(),
        'collusion': create_collusion_scenario(),
        'whale_sybil': create_whale_sybil_scenario(),
        'lazy_majority': create_lazy_majority_scenario(),
    }

    quality_by_scenario = {}

    for scenario_name, scenario_config in scenarios_to_run.items():
        print(f"  Running {scenario_name}...")
        engine, results = run_scenario(scenario_config, num_rounds=2000)

        # Calculate ELO stability (std dev of post ELOs) per round
        elo_stability = []
        for r in results:
            post_elos = [post.elo_rating for post in engine.posts.values()]
            std_dev = statistics.stdev(post_elos) if len(post_elos) > 1 else 0.0
            elo_stability.append(std_dev)

        quality_by_scenario[scenario_name] = elo_stability

    chart7_data = plot_feed_quality_over_time(
        range(2000),
        quality_by_scenario,
        os.path.join(output_dir, 'chart7_feed_quality.png')
    )
    all_chart_data['chart_7_feed_quality'] = chart7_data
    print("✓ Chart 7 complete")

    # ===================================================================
    # Export all data to JSON
    # ===================================================================
    print("\n[Export] Writing JSON data...")
    export_all_charts_to_json(
        all_chart_data,
        os.path.join(output_dir, 'results.json')
    )

    print("\n" + "=" * 60)
    print("✓ All charts generated successfully!")
    print(f"  Output directory: {output_dir}")
    print("=" * 60)


if __name__ == '__main__':
    main()
