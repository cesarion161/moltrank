package com.moltrank.service;

import com.moltrank.model.Pair;
import com.moltrank.model.PairSkip;
import com.moltrank.repository.PairSkipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PairSkipService {

    private final PairSkipRepository pairSkipRepository;

    public PairSkipService(PairSkipRepository pairSkipRepository) {
        this.pairSkipRepository = pairSkipRepository;
    }

    @Transactional
    public PairSkip skipPair(Pair pair, String curatorWallet) {
        return pairSkipRepository.findByPairIdAndCuratorWallet(pair.getId(), curatorWallet)
                .orElseGet(() -> {
                    PairSkip skip = new PairSkip();
                    skip.setPair(pair);
                    skip.setCuratorWallet(curatorWallet);
                    return pairSkipRepository.save(skip);
                });
    }
}
