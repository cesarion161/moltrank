"""
MoltRank ELO Simulation Engine

This module provides simulation and analysis tools for the MoltRank
ELO-based ranking system.
"""

from .models import (
    GlobalPool,
    CuratorScore,
    Curator,
    Post,
    Pair,
    Round,
    Vote,
)
from .elo import ELOSystem
from .slashing import SlashingSystem
from .engine import SimulationEngine, SimulationConfig
from .agents import (
    AgentStrategy,
    HonestCurator,
    RandomCurator,
    LazyCurator,
    AIBotCurator,
    ColludingRing,
    WhaleCurator,
    SybilFarmer,
    FreeRiderReader,
    ChurningReader,
)
from .scenarios import (
    ScenarioConfig,
    Market,
    get_scenario,
    list_scenarios,
    SCENARIOS,
)

__all__ = [
    'GlobalPool',
    'CuratorScore',
    'Curator',
    'Post',
    'Pair',
    'Round',
    'Vote',
    'ELOSystem',
    'SlashingSystem',
    'SimulationEngine',
    'SimulationConfig',
    'AgentStrategy',
    'HonestCurator',
    'RandomCurator',
    'LazyCurator',
    'AIBotCurator',
    'ColludingRing',
    'WhaleCurator',
    'SybilFarmer',
    'FreeRiderReader',
    'ChurningReader',
    'ScenarioConfig',
    'Market',
    'get_scenario',
    'list_scenarios',
    'SCENARIOS',
]

__version__ = "0.1.0"
