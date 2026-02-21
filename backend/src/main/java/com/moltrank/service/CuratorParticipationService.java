package com.moltrank.service;

import com.moltrank.model.Curator;
import com.moltrank.model.Identity;
import com.moltrank.repository.CuratorRepository;
import com.moltrank.repository.IdentityRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Enforces per-curator round participation caps and round-boundary resets.
 */
@Service
@RequiredArgsConstructor
public class CuratorParticipationService {

    private static final Logger log = LoggerFactory.getLogger(CuratorParticipationService.class);

    @Value("${moltrank.curation.max-pairs-per-curator-per-round:20}")
    private int maxPairsPerCuratorPerRound;

    private final CuratorRepository curatorRepository;
    private final IdentityRepository identityRepository;

    @Transactional(readOnly = true)
    public boolean hasRemainingCapacity(String wallet, Integer marketId) {
        int currentPairs = curatorRepository.findByWalletAndMarketId(wallet, marketId)
                .map(Curator::getPairsThisEpoch)
                .orElse(0);

        return currentPairs < effectiveCap();
    }

    @Transactional
    public boolean tryConsumePairEvaluationSlot(String wallet, Integer marketId) {
        int cap = effectiveCap();
        OffsetDateTime now = OffsetDateTime.now();

        int updatedRows = curatorRepository.incrementPairsThisEpochIfBelowCap(wallet, marketId, cap, now);
        if (updatedRows > 0) {
            return true;
        }

        Curator curator = curatorRepository.findByWalletAndMarketId(wallet, marketId).orElse(null);
        if (curator != null) {
            return false;
        }

        Integer identityId = identityRepository.findByWallet(wallet)
                .map(Identity::getId)
                .orElseThrow(() -> new IllegalArgumentException("Identity not found for wallet: " + wallet));

        Curator created = new Curator();
        created.setWallet(wallet);
        created.setMarketId(marketId);
        created.setIdentityId(identityId);
        created.setPairsThisEpoch(1);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);

        try {
            curatorRepository.save(created);
            return true;
        } catch (DataIntegrityViolationException ex) {
            // Another request created the curator concurrently; retry atomic increment.
            return curatorRepository.incrementPairsThisEpochIfBelowCap(wallet, marketId, cap, OffsetDateTime.now()) > 0;
        }
    }

    @Transactional
    public int resetPairsThisEpochForMarket(Integer marketId) {
        OffsetDateTime now = OffsetDateTime.now();
        int resets = 0;

        for (Curator curator : curatorRepository.findByMarketId(marketId)) {
            int current = curator.getPairsThisEpoch() != null ? curator.getPairsThisEpoch() : 0;
            if (current == 0) {
                continue;
            }

            curator.setPairsThisEpoch(0);
            curator.setUpdatedAt(now);
            curatorRepository.save(curator);
            resets++;
        }

        if (resets > 0) {
            log.info("Reset pairs_this_epoch for {} curator(s) in market {}", resets, marketId);
        } else {
            log.debug("No curator epoch counters required reset for market {}", marketId);
        }

        return resets;
    }

    private int effectiveCap() {
        return Math.max(1, maxPairsPerCuratorPerRound);
    }
}
