package com.moltrank.repository;

import com.moltrank.model.Identity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdentityRepository extends JpaRepository<Identity, Integer> {
    Optional<Identity> findByWallet(String wallet);

    @Query("SELECT i FROM Identity i WHERE i.xAccount = :xAccount")
    Optional<Identity> findByXAccount(@Param("xAccount") String xAccount);
}
