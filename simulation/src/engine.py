"""Main simulation engine for round execution.

Round execution flow:
1. Pair generation (demand-gated: min(uniquePosts/2, subscribers × K))
2. Golden Set injection (10% of pairs)
3. Audit Pair injection (5% of pairs)
4. Curator voting phase
5. Settlement with asymmetric payouts:
   - Majority: 100%
   - Minority: 80%
   - Non-reveal: 0%
"""

import random
from itertools import islice
from typing import List, Tuple, Dict, Optional
from dataclasses import dataclass

from .models import (
    GlobalPool, Curator, Post, Pair, Round, Vote
)
from .elo import ELOSystem
from .slashing import SlashingSystem


@dataclass
class SimulationConfig:
    """Configuration parameters for the simulation."""

    # Pair generation
    demand_gate_k: float = 1.0  # Multiplier for subscriber-based demand
    golden_set_percentage: float = 0.10  # 10% of pairs
    audit_pair_percentage: float = 0.05  # 5% of pairs

    # Rewards
    majority_payout: float = 1.0  # 100%
    minority_payout: float = 0.8  # 80%
    no_reveal_payout: float = 0.0  # 0%

    # ELO
    elo_base_k: float = 32.0
    elo_default_rating: float = 1500.0

    # Random seed for reproducibility
    random_seed: Optional[int] = None


class SimulationEngine:
    """Main engine for running simulation rounds."""

    def __init__(self, config: Optional[SimulationConfig] = None):
        """Initialize the simulation engine.

        Args:
            config: Simulation configuration (uses defaults if None)
        """
        self.config = config or SimulationConfig()
        if self.config.random_seed is not None:
            random.seed(self.config.random_seed)

        self.pool = GlobalPool()
        self.elo_system = ELOSystem(base_k=self.config.elo_base_k)
        self.slashing_system = SlashingSystem()

        self.curators: Dict[str, Curator] = {}
        self.posts: Dict[str, Post] = {}
        self.rounds: List[Round] = []

    def add_curator(self, curator: Curator) -> None:
        """Add a curator to the simulation."""
        self.curators[curator.curator_id] = curator

    def add_post(self, post: Post) -> None:
        """Add a post to the simulation."""
        self.posts[post.post_id] = post

    def calculate_demand_gated_pairs(
        self,
        unique_posts: int,
        num_subscribers: int
    ) -> int:
        """Calculate number of pairs based on demand gating.

        Formula: min(uniquePosts/2, subscribers × K)

        Args:
            unique_posts: Number of unique posts available
            num_subscribers: Number of active subscribers

        Returns:
            Number of pairs to generate
        """
        max_pairs_from_posts = unique_posts // 2
        demand_based_pairs = int(num_subscribers * self.config.demand_gate_k)
        return min(max_pairs_from_posts, demand_based_pairs)

    def generate_pairs(
        self,
        available_posts: List[Post],
        num_pairs: int
    ) -> List[Pair]:
        """Generate random pairs from available posts.

        Args:
            available_posts: List of posts to pair
            num_pairs: Number of pairs to generate

        Returns:
            List of generated pairs
        """
        if len(available_posts) < 2:
            return []

        pairs = []
        posts_copy = available_posts.copy()
        random.shuffle(posts_copy)

        for i in range(min(num_pairs, len(posts_copy) // 2)):
            pair = Pair(
                pair_id=f"pair_{len(self.rounds)}_{i}",
                post_left=posts_copy[i * 2],
                post_right=posts_copy[i * 2 + 1]
            )
            pairs.append(pair)

        return pairs

    def inject_golden_set_pairs(
        self,
        pairs: List[Pair],
        golden_set_pairs: List[Tuple[Post, Post, Vote]]
    ) -> None:
        """Inject Golden Set pairs (10% of total pairs).

        Args:
            pairs: List of pairs to inject into
            golden_set_pairs: List of (post_left, post_right, correct_vote) tuples
        """
        num_golden = int(len(pairs) * self.config.golden_set_percentage)

        for i in range(min(num_golden, len(golden_set_pairs))):
            if i < len(pairs):
                post_left, post_right, correct_vote = golden_set_pairs[i]
                pairs[i] = Pair(
                    pair_id=f"golden_{len(self.rounds)}_{i}",
                    post_left=post_left,
                    post_right=post_right,
                    is_golden_set=True,
                    golden_correct_answer=correct_vote
                )

    def inject_audit_pairs(
        self,
        pairs: List[Pair],
        historical_pairs: List[Pair]
    ) -> None:
        """Inject Audit Pairs (5% of total pairs).

        Re-shows pairs that curators have seen before to check consistency.

        Args:
            pairs: List of pairs to inject into
            historical_pairs: List of previously shown pairs
        """
        num_audit = int(len(pairs) * self.config.audit_pair_percentage)
        golden_count = int(len(pairs) * self.config.golden_set_percentage)

        # Stop scanning as soon as enough candidates are found.
        candidate_pairs = (
            p for p in historical_pairs
            if not p.is_golden_set and not p.is_audit_pair
        )

        for i, historical_pair in enumerate(islice(candidate_pairs, num_audit)):
            insert_idx = golden_count + i
            if insert_idx < len(pairs):
                pairs[insert_idx] = Pair(
                    pair_id=f"audit_{len(self.rounds)}_{i}",
                    post_left=historical_pair.post_left,
                    post_right=historical_pair.post_right,
                    is_audit_pair=True
                )

    def simulate_curator_votes(
        self,
        pairs: List[Pair],
        curators: List[Curator]
    ) -> None:
        """Simulate curator voting on pairs.

        For now, uses random voting. In full simulation, this would use
        agent-based models with different curator strategies.

        Args:
            pairs: List of pairs to vote on
            curators: List of curators voting
        """
        for pair in pairs:
            for curator in curators:
                # Random vote for simulation
                # In real implementation, use curator strategy
                vote = random.choice([Vote.LEFT, Vote.RIGHT])

                # Handle Golden Set
                if pair.is_golden_set and pair.golden_correct_answer:
                    # Simulate varying accuracy
                    accuracy = curator.score.calibration_rate if curator.score else 0.8
                    if random.random() < accuracy:
                        vote = pair.golden_correct_answer

                pair.add_vote(curator.curator_id, vote)

    def settle_round(
        self,
        round: Round
    ) -> Dict[str, Dict]:
        """Settle a round: calculate rewards, update ratings, apply slashing.

        Args:
            round: Round to settle

        Returns:
            Settlement results per curator
        """
        results = {}
        total_stake = sum(c.stake for c in round.curators)

        # Process each pair
        for pair in round.pairs:
            majority_vote = pair.get_majority_vote()
            if majority_vote is None:
                continue  # Skip ties

            # Determine winner post
            if majority_vote == Vote.LEFT:
                winner, loser = pair.post_left, pair.post_right
            else:
                winner, loser = pair.post_right, pair.post_left

            # Update post ELO ratings
            new_winner_rating, new_loser_rating = self.elo_system.update_post_ratings(
                winner, loser, total_stake
            )
            winner.elo_rating = new_winner_rating
            loser.elo_rating = new_loser_rating

            # Process curator payouts
            minority_voters = pair.get_minority_voters()
            no_reveal_voters = pair.get_no_reveal_voters()

            for curator in round.curators:
                if curator.curator_id not in results:
                    results[curator.curator_id] = {
                        'rewards': 0.0,
                        'slashed': 0.0,
                        'minority_losses': 0.0,
                        'votes': 0,
                    }

                curator_result = results[curator.curator_id]
                vote = pair.votes.get(curator.curator_id)

                if vote is None:
                    continue

                # Calculate base reward
                base_reward = round.pool.calculate_base_reward(len(round.pairs))

                # Apply payout multiplier
                if curator.curator_id in no_reveal_voters:
                    payout = base_reward * self.config.no_reveal_payout
                elif curator.curator_id in minority_voters:
                    payout = base_reward * self.config.minority_payout
                    # Minority loss goes to pool
                    minority_loss = base_reward * (1.0 - self.config.minority_payout)
                    round.pool.add_minority_loss(minority_loss)
                    curator_result['minority_losses'] += minority_loss
                else:
                    payout = base_reward * self.config.majority_payout

                # Update curator ELO
                voted_correctly = curator.curator_id not in minority_voters
                new_curator_rating = self.elo_system.update_curator_rating(
                    curator, voted_correctly, total_stake
                )
                curator.elo_rating = new_curator_rating

                curator_result['rewards'] += payout
                curator_result['votes'] += 1

        # Process slashing metrics
        slashing_results = self.slashing_system.process_round_metrics(
            round.pairs, round.curators
        )

        for curator in round.curators:
            curator_result = results.get(curator.curator_id, {})
            score, should_suspend, slash_amount = slashing_results[curator.curator_id]

            # Apply reward multiplier based on score
            reward_multiplier = self.slashing_system.determine_reward_multiplier(
                curator.curator_id
            )
            curator_result['rewards'] *= reward_multiplier

            # Apply slashing
            if slash_amount > 0:
                curator.stake -= slash_amount
                round.pool.add_slashing(slash_amount)
                curator_result['slashed'] = slash_amount

            curator_result['score'] = score
            curator_result['suspended'] = should_suspend

        # Withdraw total rewards from pool
        total_rewards = sum(r.get('rewards', 0.0) for r in results.values())
        if total_rewards > 0 and total_rewards <= round.pool.balance:
            round.pool.withdraw(total_rewards)

        return results

    def run_round(
        self,
        num_subscribers: int,
        golden_set_pairs: List[Tuple[Post, Post, Vote]] = None
    ) -> Tuple[Round, Dict[str, Dict]]:
        """Run a complete simulation round.

        Args:
            num_subscribers: Number of active subscribers (for demand gating)
            golden_set_pairs: Optional Golden Set pairs with correct answers

        Returns:
            Tuple of (Round, settlement_results)
        """
        golden_set_pairs = golden_set_pairs or []

        # 1. Calculate demand-gated pairs
        available_posts = list(self.posts.values())
        num_pairs = self.calculate_demand_gated_pairs(
            len(available_posts), num_subscribers
        )

        # 2. Generate pairs
        pairs = self.generate_pairs(available_posts, num_pairs)

        # 3. Inject Golden Set (10%)
        self.inject_golden_set_pairs(pairs, golden_set_pairs)

        # 4. Inject Audit Pairs (5%)
        historical_pairs = []
        if self.rounds:
            for prev_round in self.rounds:
                historical_pairs.extend(prev_round.pairs)
        self.inject_audit_pairs(pairs, historical_pairs)

        # 5. Create round
        round = Round(
            round_id=len(self.rounds),
            pairs=pairs,
            curators=list(self.curators.values()),
            pool=self.pool
        )

        # 6. Curator voting phase
        self.simulate_curator_votes(pairs, list(self.curators.values()))

        # 7. Settlement
        results = self.settle_round(round)

        # Store round
        self.rounds.append(round)

        return round, results

    def get_statistics(self) -> Dict:
        """Get simulation statistics.

        Returns:
            Dictionary with overall stats
        """
        return {
            'total_rounds': len(self.rounds),
            'pool_balance': self.pool.balance,
            'total_curators': len(self.curators),
            'total_posts': len(self.posts),
            'pool_stats': {
                'subscriptions': self.pool.total_subscriptions,
                'slashing': self.pool.total_slashing,
                'minority_losses': self.pool.total_minority_losses,
            }
        }
