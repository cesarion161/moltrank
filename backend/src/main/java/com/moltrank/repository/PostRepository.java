package com.moltrank.repository;

import com.moltrank.model.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Integer> {
    Optional<Post> findByMoltbookId(String moltbookId);
    List<Post> findByMarketId(Integer marketId);
    List<Post> findByMarketId(Integer marketId, Pageable pageable);
    List<Post> findByAgent(String agent);
    List<Post> findByMarketIdOrderByEloDesc(Integer marketId);
}
