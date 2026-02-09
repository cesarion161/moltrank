"""Agent types for simulation curators and readers.

Implements 9 agent types with distinct voting behaviors for adversarial
testing of the Moltbook ranking system.

Reference: PRD Sections 6.2, 6.3
"""

import random
from abc import ABC, abstractmethod
from typing import List, Optional
from dataclasses import dataclass

from .models import Vote, Post, Pair, Curator


class AgentStrategy(ABC):
    """Base class for agent voting strategies."""

    @abstractmethod
    def vote(self, pair: Pair, curator: Curator) -> Vote:
        """Determine how this agent votes on a pair.

        Args:
            pair: The pair to vote on
            curator: The curator agent

        Returns:
            Vote choice (LEFT, RIGHT, or NO_REVEAL)
        """
        pass


class HonestCurator(AgentStrategy):
    """Honest curator: reads both posts, votes for better one.

    Alignment: 70-80% with majority
    Simulates genuine human curation with some subjectivity.
    """

    def __init__(self, accuracy: float = 0.75):
        """Initialize honest curator.

        Args:
            accuracy: Probability of voting with eventual majority (0.70-0.80)
        """
        self.accuracy = max(0.70, min(0.80, accuracy))

    def vote(self, pair: Pair, curator: Curator) -> Vote:
        """Vote honestly, simulating 70-80% alignment.

        Uses ELO ratings as ground truth proxy - higher ELO is "better".
        """
        # Determine "correct" answer based on ELO
        if pair.post_left.elo_rating > pair.post_right.elo_rating:
            correct_vote = Vote.LEFT
        elif pair.post_right.elo_rating > pair.post_left.elo_rating:
            correct_vote = Vote.RIGHT
        else:
            # Equal ratings - random choice
            return random.choice([Vote.LEFT, Vote.RIGHT])

        # Vote correctly with accuracy probability
        if random.random() < self.accuracy:
            return correct_vote
        else:
            # Vote incorrectly
            return Vote.RIGHT if correct_vote == Vote.LEFT else Vote.LEFT


class RandomCurator(AgentStrategy):
    """Random curator: votes randomly without reading.

    Alignment: ~50% (chance)
    """

    def vote(self, pair: Pair, curator: Curator) -> Vote:
        """Vote randomly between LEFT and RIGHT."""
        return random.choice([Vote.LEFT, Vote.RIGHT])


class LazyCurator(AgentStrategy):
    """Lazy curator: always votes LEFT without reading.

    Alignment: ~50% (chance)
    Fails on Audit Pairs (inconsistent voting).
    """

    def vote(self, pair: Pair, curator: Curator) -> Vote:
        """Always vote LEFT."""
        return Vote.LEFT


class AIBotCurator(AgentStrategy):
    """AI Bot curator: LLM-style evaluation, fast, high accuracy.

    Alignment: 65-75%
    Simulates automated LLM-based curation.
    """

    def __init__(self, accuracy: float = 0.70):
        """Initialize AI bot curator.

        Args:
            accuracy: Probability of voting correctly (0.65-0.75)
        """
        self.accuracy = max(0.65, min(0.75, accuracy))

    def vote(self, pair: Pair, curator: Curator) -> Vote:
        """Vote using simulated LLM judgment."""
        # Similar to HonestCurator but slightly lower accuracy
        if pair.post_left.elo_rating > pair.post_right.elo_rating:
            correct_vote = Vote.LEFT
        elif pair.post_right.elo_rating > pair.post_left.elo_rating:
            correct_vote = Vote.RIGHT
        else:
            return random.choice([Vote.LEFT, Vote.RIGHT])

        if random.random() < self.accuracy:
            return correct_vote
        else:
            return Vote.RIGHT if correct_vote == Vote.LEFT else Vote.LEFT


@dataclass
class ColludingRing:
    """Colluding ring of curators: coordinate votes same side.

    Size: 5-10 curators
    All members vote the same way on each pair.
    """

    curator_ids: List[str]
    accuracy: float = 0.60  # 60% alignment when coordinated

    def __post_init__(self):
        """Validate ring size."""
        if len(self.curator_ids) < 5 or len(self.curator_ids) > 10:
            raise ValueError("Colluding ring must have 5-10 members")

        # Shared voting decisions per pair
        self._pair_decisions: dict = {}

    def vote(self, pair: Pair, curator: Curator) -> Vote:
        """Vote in coordination with ring members.

        All ring members vote the same way on each pair.
        """
        if curator.curator_id not in self.curator_ids:
            raise ValueError(f"Curator {curator.curator_id} not in ring")

        # Use pair_id to coordinate decisions
        if pair.pair_id not in self._pair_decisions:
            # Ring decides vote with some accuracy
            if pair.post_left.elo_rating > pair.post_right.elo_rating:
                correct_vote = Vote.LEFT
            elif pair.post_right.elo_rating > pair.post_left.elo_rating:
                correct_vote = Vote.RIGHT
            else:
                correct_vote = random.choice([Vote.LEFT, Vote.RIGHT])

            # Vote correctly with accuracy, otherwise random
            if random.random() < self.accuracy:
                ring_vote = correct_vote
            else:
                ring_vote = random.choice([Vote.LEFT, Vote.RIGHT])

            self._pair_decisions[pair.pair_id] = ring_vote

        return self._pair_decisions[pair.pair_id]


class WhaleCurator(AgentStrategy):
    """Whale curator: 50x normal stake, hits cap.

    Alignment: ~70%
    High stake but same voting behavior as honest curator.
    """

    def __init__(self, accuracy: float = 0.70):
        """Initialize whale curator.

        Args:
            accuracy: Probability of voting correctly (~0.70)
        """
        self.accuracy = accuracy
        self.stake_multiplier = 50

    def vote(self, pair: Pair, curator: Curator) -> Vote:
        """Vote like honest curator."""
        if pair.post_left.elo_rating > pair.post_right.elo_rating:
            correct_vote = Vote.LEFT
        elif pair.post_right.elo_rating > pair.post_left.elo_rating:
            correct_vote = Vote.RIGHT
        else:
            return random.choice([Vote.LEFT, Vote.RIGHT])

        if random.random() < self.accuracy:
            return correct_vote
        else:
            return Vote.RIGHT if correct_vote == Vote.LEFT else Vote.LEFT


class SybilFarmer(AgentStrategy):
    """Sybil farmer: splits capital across multiple wallets.

    Alignment: 60-70%
    Represents single actor with multiple identities.
    """

    def __init__(self, wallet_ids: List[str], accuracy: float = 0.65):
        """Initialize Sybil farmer.

        Args:
            wallet_ids: List of curator IDs controlled by this farmer
            accuracy: Probability of voting correctly (0.60-0.70)
        """
        self.wallet_ids = wallet_ids
        self.accuracy = max(0.60, min(0.70, accuracy))

    def vote(self, pair: Pair, curator: Curator) -> Vote:
        """Vote independently per wallet but with consistent strategy."""
        if curator.curator_id not in self.wallet_ids:
            raise ValueError(f"Curator {curator.curator_id} not a Sybil wallet")

        # Each wallet votes independently but with same accuracy
        if pair.post_left.elo_rating > pair.post_right.elo_rating:
            correct_vote = Vote.LEFT
        elif pair.post_right.elo_rating > pair.post_left.elo_rating:
            correct_vote = Vote.RIGHT
        else:
            return random.choice([Vote.LEFT, Vote.RIGHT])

        if random.random() < self.accuracy:
            return correct_vote
        else:
            return Vote.RIGHT if correct_vote == Vote.LEFT else Vote.LEFT


@dataclass
class FreeRiderReader:
    """Free-rider reader: subscribes to markets, never curates.

    Consumes feed but doesn't participate in ranking.
    """

    reader_id: str
    subscribed_markets: List[str]

    def is_active_subscriber(self) -> bool:
        """Check if reader is actively subscribed."""
        return len(self.subscribed_markets) > 0


@dataclass
class ChurningReader:
    """Churning reader: unsubscribes if feed quality drops.

    Monitors feed quality and leaves if threshold not met.
    """

    reader_id: str
    subscribed_markets: List[str]
    quality_threshold: float = 0.6  # Unsubscribe if below 60%
    churn_round: Optional[int] = None  # Round when churned

    def evaluate_quality(self, avg_post_elo: float, base_elo: float = 1500.0) -> float:
        """Evaluate perceived feed quality.

        Args:
            avg_post_elo: Average ELO of posts in feed
            base_elo: Base ELO rating (default 1500)

        Returns:
            Quality score 0.0-1.0
        """
        # Simple quality metric: deviation from base ELO
        # Higher ELO = better quality
        if avg_post_elo >= base_elo:
            return min(1.0, (avg_post_elo - base_elo) / 500 + 0.6)
        else:
            return max(0.0, 0.6 - (base_elo - avg_post_elo) / 500)

    def check_churn(
        self,
        avg_post_elo: float,
        current_round: int,
        base_elo: float = 1500.0
    ) -> bool:
        """Check if reader should churn based on quality.

        Args:
            avg_post_elo: Average ELO of posts in subscribed feeds
            current_round: Current simulation round
            base_elo: Base ELO rating

        Returns:
            True if reader churned (unsubscribed)
        """
        if self.churn_round is not None:
            # Already churned
            return True

        quality = self.evaluate_quality(avg_post_elo, base_elo)
        if quality < self.quality_threshold:
            self.subscribed_markets = []
            self.churn_round = current_round
            return True

        return False
