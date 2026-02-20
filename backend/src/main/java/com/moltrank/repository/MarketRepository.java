package com.moltrank.repository;

import com.moltrank.model.Market;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MarketRepository extends JpaRepository<Market, Integer> {
    Optional<Market> findByName(String name);
    Optional<Market> findBySubmoltId(String submoltId);
    boolean existsByNameIgnoreCase(String name);
    boolean existsBySubmoltIdIgnoreCase(String submoltId);
}
