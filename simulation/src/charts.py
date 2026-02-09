"""Chart generation for simulation results.

Generates 7 charts and exports data to JSON for frontend playback:
1. GlobalPool balance over 10K rounds (solvency proof)
2. Cumulative PnL by agent type (honest > random > lazy > colluder)
3. New market bootstrap curve (curators attracted over time)
4. Alpha sensitivity + minority loss sensitivity analysis
5. Audit Pair detection rate (lazy/bot curators caught within N evaluations)
6. Bot vs human earnings under varying bot percentages
7. Feed quality (ELO stability) over time across scenarios

Reference: PRD Sections 6.4, 8.5
"""

import json
import os
from typing import List, Dict, Any, Tuple
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker


def plot_pool_balance(
    rounds: range,
    balances: List[float],
    output_path: str
) -> List[Dict[str, Any]]:
    """Chart 1: GlobalPool balance over 10K rounds.

    Args:
        rounds: Range of round numbers
        balances: Pool balance at each round
        output_path: Path to save PNG file

    Returns:
        JSON data points for export
    """
    plt.figure(figsize=(12, 6))
    plt.plot(list(rounds), balances, linewidth=2, color='#2563eb')
    plt.xlabel('Round', fontsize=12)
    plt.ylabel('Pool Balance ($)', fontsize=12)
    plt.title('GlobalPool Solvency: Balance Over 10K Rounds', fontsize=14, fontweight='bold')
    plt.grid(True, alpha=0.3)
    plt.gca().yaxis.set_major_formatter(ticker.FuncFormatter(lambda x, p: f'${x:,.0f}'))
    plt.tight_layout()
    plt.savefig(output_path, dpi=300)
    plt.close()

    # Return JSON data
    return [
        {"round": r, "balance": float(b)}
        for r, b in zip(rounds, balances)
    ]


def plot_cumulative_pnl_by_agent(
    rounds: range,
    pnl_by_type: Dict[str, List[float]],
    output_path: str
) -> List[Dict[str, Any]]:
    """Chart 2: Cumulative PnL by agent type.

    Args:
        rounds: Range of round numbers
        pnl_by_type: Dict mapping agent type to cumulative PnL over time
        output_path: Path to save PNG file

    Returns:
        JSON data points for export
    """
    plt.figure(figsize=(12, 6))

    colors = {
        'honest': '#10b981',
        'random': '#f59e0b',
        'lazy': '#ef4444',
        'colluder': '#8b5cf6',
        'bot': '#06b6d4',
    }

    for agent_type, pnl_values in pnl_by_type.items():
        color = colors.get(agent_type, '#6b7280')
        plt.plot(list(rounds), pnl_values, label=agent_type.capitalize(),
                linewidth=2, color=color)

    plt.xlabel('Round', fontsize=12)
    plt.ylabel('Cumulative PnL ($)', fontsize=12)
    plt.title('Cumulative PnL by Agent Type (Expected: Honest > Random > Lazy > Colluder)',
              fontsize=14, fontweight='bold')
    plt.legend(fontsize=10)
    plt.grid(True, alpha=0.3)
    plt.gca().yaxis.set_major_formatter(ticker.FuncFormatter(lambda x, p: f'${x:,.0f}'))
    plt.tight_layout()
    plt.savefig(output_path, dpi=300)
    plt.close()

    # Return JSON data
    json_data = []
    for agent_type, pnl_values in pnl_by_type.items():
        for r, pnl in zip(rounds, pnl_values):
            json_data.append({
                "round": r,
                "agent_type": agent_type,
                "cumulative_pnl": float(pnl)
            })
    return json_data


def plot_new_market_bootstrap(
    rounds: range,
    subscribers: List[int],
    curators_participating: List[int],
    output_path: str
) -> List[Dict[str, Any]]:
    """Chart 3: New market bootstrap curve.

    Args:
        rounds: Range of round numbers (starting from market creation)
        subscribers: Number of subscribers over time
        curators_participating: Number of curators participating over time
        output_path: Path to save PNG file

    Returns:
        JSON data points for export
    """
    fig, ax1 = plt.subplots(figsize=(12, 6))

    color1 = '#2563eb'
    ax1.set_xlabel('Rounds Since Market Launch', fontsize=12)
    ax1.set_ylabel('Subscribers', fontsize=12, color=color1)
    ax1.plot(list(rounds), subscribers, linewidth=2, color=color1, label='Subscribers')
    ax1.tick_params(axis='y', labelcolor=color1)
    ax1.grid(True, alpha=0.3)

    ax2 = ax1.twinx()
    color2 = '#10b981'
    ax2.set_ylabel('Curators Participating', fontsize=12, color=color2)
    ax2.plot(list(rounds), curators_participating, linewidth=2,
             color=color2, linestyle='--', label='Curators')
    ax2.tick_params(axis='y', labelcolor=color2)

    plt.title('New Market Bootstrap: Subscriber and Curator Growth', fontsize=14, fontweight='bold')
    fig.tight_layout()
    plt.savefig(output_path, dpi=300)
    plt.close()

    # Return JSON data
    return [
        {
            "round": r,
            "subscribers": int(s),
            "curators_participating": int(c)
        }
        for r, s, c in zip(rounds, subscribers, curators_participating)
    ]


def plot_alpha_sensitivity(
    alpha_values: List[float],
    avg_honest_pnl: List[float],
    avg_malicious_pnl: List[float],
    output_path: str
) -> List[Dict[str, Any]]:
    """Chart 4a: Alpha sensitivity analysis.

    Args:
        alpha_values: List of alpha parameter values tested
        avg_honest_pnl: Average PnL for honest curators at each alpha
        avg_malicious_pnl: Average PnL for malicious curators at each alpha
        output_path: Path to save PNG file

    Returns:
        JSON data points for export
    """
    plt.figure(figsize=(12, 6))
    plt.plot(alpha_values, avg_honest_pnl, marker='o', linewidth=2,
             color='#10b981', label='Honest Curators')
    plt.plot(alpha_values, avg_malicious_pnl, marker='s', linewidth=2,
             color='#ef4444', label='Malicious Curators')
    plt.xlabel('Alpha Parameter', fontsize=12)
    plt.ylabel('Average PnL ($)', fontsize=12)
    plt.title('Alpha Sensitivity: Impact on Curator Earnings', fontsize=14, fontweight='bold')
    plt.legend(fontsize=10)
    plt.grid(True, alpha=0.3)
    plt.gca().yaxis.set_major_formatter(ticker.FuncFormatter(lambda x, p: f'${x:,.0f}'))
    plt.tight_layout()
    plt.savefig(output_path, dpi=300)
    plt.close()

    # Return JSON data
    json_data = []
    for alpha, honest, malicious in zip(alpha_values, avg_honest_pnl, avg_malicious_pnl):
        json_data.append({
            "alpha": float(alpha),
            "avg_honest_pnl": float(honest),
            "avg_malicious_pnl": float(malicious)
        })
    return json_data


def plot_minority_loss_sensitivity(
    minority_loss_pcts: List[float],
    consensus_alignment: List[float],
    avg_curator_pnl: List[float],
    output_path: str
) -> List[Dict[str, Any]]:
    """Chart 4b: Minority loss sensitivity analysis.

    Args:
        minority_loss_pcts: List of minority loss percentages (e.g., [10, 20, 30, 40, 50])
        consensus_alignment: Average consensus alignment rate at each loss percentage
        avg_curator_pnl: Average curator PnL at each loss percentage
        output_path: Path to save PNG file

    Returns:
        JSON data points for export
    """
    fig, ax1 = plt.subplots(figsize=(12, 6))

    color1 = '#2563eb'
    ax1.set_xlabel('Minority Loss (%)', fontsize=12)
    ax1.set_ylabel('Consensus Alignment Rate', fontsize=12, color=color1)
    ax1.plot(minority_loss_pcts, consensus_alignment, marker='o',
             linewidth=2, color=color1, label='Consensus Alignment')
    ax1.tick_params(axis='y', labelcolor=color1)
    ax1.grid(True, alpha=0.3)
    ax1.set_ylim(0, 1.0)

    ax2 = ax1.twinx()
    color2 = '#10b981'
    ax2.set_ylabel('Average Curator PnL ($)', fontsize=12, color=color2)
    ax2.plot(minority_loss_pcts, avg_curator_pnl, marker='s',
             linewidth=2, color=color2, linestyle='--', label='Avg Curator PnL')
    ax2.tick_params(axis='y', labelcolor=color2)
    ax2.yaxis.set_major_formatter(ticker.FuncFormatter(lambda x, p: f'${x:,.0f}'))

    plt.title('Minority Loss Sensitivity: Impact on Consensus and Earnings',
              fontsize=14, fontweight='bold')
    fig.tight_layout()
    plt.savefig(output_path, dpi=300)
    plt.close()

    # Return JSON data
    return [
        {
            "minority_loss_pct": float(loss_pct),
            "consensus_alignment": float(consensus),
            "avg_curator_pnl": float(pnl)
        }
        for loss_pct, consensus, pnl in zip(minority_loss_pcts, consensus_alignment, avg_curator_pnl)
    ]


def plot_audit_detection_rate(
    num_evaluations: range,
    lazy_detection_rate: List[float],
    bot_detection_rate: List[float],
    output_path: str
) -> List[Dict[str, Any]]:
    """Chart 5: Audit Pair detection rate.

    Args:
        num_evaluations: Range of number of evaluations (N audit pairs)
        lazy_detection_rate: Detection rate for lazy curators
        bot_detection_rate: Detection rate for bot curators
        output_path: Path to save PNG file

    Returns:
        JSON data points for export
    """
    plt.figure(figsize=(12, 6))
    plt.plot(list(num_evaluations), lazy_detection_rate, marker='o',
             linewidth=2, color='#ef4444', label='Lazy Curators')
    plt.plot(list(num_evaluations), bot_detection_rate, marker='s',
             linewidth=2, color='#06b6d4', label='Bot Curators')
    plt.xlabel('Number of Audit Pair Evaluations', fontsize=12)
    plt.ylabel('Detection Rate', fontsize=12)
    plt.title('Audit Pair Detection Rate: Catching Lazy/Bot Curators', fontsize=14, fontweight='bold')
    plt.legend(fontsize=10)
    plt.grid(True, alpha=0.3)
    plt.ylim(0, 1.0)
    plt.tight_layout()
    plt.savefig(output_path, dpi=300)
    plt.close()

    # Return JSON data
    json_data = []
    for n, lazy_rate, bot_rate in zip(num_evaluations, lazy_detection_rate, bot_detection_rate):
        json_data.append({
            "num_evaluations": int(n),
            "lazy_detection_rate": float(lazy_rate),
            "bot_detection_rate": float(bot_rate)
        })
    return json_data


def plot_bot_vs_human_earnings(
    bot_percentages: List[float],
    human_avg_earnings: List[float],
    bot_avg_earnings: List[float],
    output_path: str
) -> List[Dict[str, Any]]:
    """Chart 6: Bot vs human earnings under varying bot percentages.

    Args:
        bot_percentages: List of bot percentage values (e.g., [0, 20, 40, 60, 80])
        human_avg_earnings: Average earnings for human curators at each percentage
        bot_avg_earnings: Average earnings for bot curators at each percentage
        output_path: Path to save PNG file

    Returns:
        JSON data points for export
    """
    plt.figure(figsize=(12, 6))
    plt.plot(bot_percentages, human_avg_earnings, marker='o',
             linewidth=2, color='#10b981', label='Human Curators')
    plt.plot(bot_percentages, bot_avg_earnings, marker='s',
             linewidth=2, color='#06b6d4', label='Bot Curators')
    plt.xlabel('Bot Percentage (%)', fontsize=12)
    plt.ylabel('Average Earnings ($)', fontsize=12)
    plt.title('Bot vs Human Earnings: Impact of Bot Infiltration', fontsize=14, fontweight='bold')
    plt.legend(fontsize=10)
    plt.grid(True, alpha=0.3)
    plt.gca().yaxis.set_major_formatter(ticker.FuncFormatter(lambda x, p: f'${x:,.0f}'))
    plt.tight_layout()
    plt.savefig(output_path, dpi=300)
    plt.close()

    # Return JSON data
    return [
        {
            "bot_percentage": float(bot_pct),
            "human_avg_earnings": float(human),
            "bot_avg_earnings": float(bot)
        }
        for bot_pct, human, bot in zip(bot_percentages, human_avg_earnings, bot_avg_earnings)
    ]


def plot_feed_quality_over_time(
    rounds: range,
    quality_by_scenario: Dict[str, List[float]],
    output_path: str
) -> List[Dict[str, Any]]:
    """Chart 7: Feed quality (ELO stability) over time across scenarios.

    Args:
        rounds: Range of round numbers
        quality_by_scenario: Dict mapping scenario name to ELO stability values
        output_path: Path to save PNG file

    Returns:
        JSON data points for export
    """
    plt.figure(figsize=(12, 6))

    colors = {
        'baseline': '#10b981',
        'bot_flood': '#06b6d4',
        'collusion': '#8b5cf6',
        'whale_sybil': '#f59e0b',
        'lazy_majority': '#ef4444',
    }

    for scenario, quality_values in quality_by_scenario.items():
        color = colors.get(scenario, '#6b7280')
        plt.plot(list(rounds), quality_values, label=scenario.replace('_', ' ').title(),
                linewidth=2, color=color)

    plt.xlabel('Round', fontsize=12)
    plt.ylabel('ELO Stability (Std Dev)', fontsize=12)
    plt.title('Feed Quality: ELO Stability Over Time Across Scenarios',
              fontsize=14, fontweight='bold')
    plt.legend(fontsize=10)
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.savefig(output_path, dpi=300)
    plt.close()

    # Return JSON data
    json_data = []
    for scenario, quality_values in quality_by_scenario.items():
        for r, quality in zip(rounds, quality_values):
            json_data.append({
                "round": r,
                "scenario": scenario,
                "elo_stability": float(quality)
            })
    return json_data


def export_all_charts_to_json(
    chart_data: Dict[str, List[Dict[str, Any]]],
    output_path: str
) -> None:
    """Export all chart data to JSON file for frontend playback.

    Args:
        chart_data: Dictionary mapping chart names to their data points
        output_path: Path to save JSON file

    Format:
        {
            "chart_1_pool_balance": [
                {"round": 0, "balance": 10000.0},
                ...
            ],
            ...
        }
    """
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    with open(output_path, 'w') as f:
        json.dump(chart_data, f, indent=2)

    print(f"✓ Exported chart data to {output_path}")
