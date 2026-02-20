package com.moltrank.service;

import com.moltrank.model.Pair;
import com.moltrank.model.RoundStatus;
import com.moltrank.repository.PairRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Selects curator-facing pairs based on round phase eligibility.
 */
@Service
public class PairSelectionService {

    private static final List<RoundStatus> ELIGIBLE_CURATION_PHASES = List.of(RoundStatus.COMMIT);

    private final PairRepository pairRepository;

    public PairSelectionService(PairRepository pairRepository) {
        this.pairRepository = pairRepository;
    }

    public Optional<Pair> findNextPairForCurator(String wallet, Integer marketId) {
        return pairRepository.findNextPairForCurator(wallet, marketId, ELIGIBLE_CURATION_PHASES);
    }

    boolean isEligibleCurationPhase(RoundStatus status) {
        return ELIGIBLE_CURATION_PHASES.contains(status);
    }
}
