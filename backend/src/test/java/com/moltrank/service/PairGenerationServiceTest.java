package com.moltrank.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the demand-gating formula: maxPairs = min(uniquePosts/2, subscribers * K)
 * where K = pairsPerSubscriber (default 5).
 */
class PairGenerationServiceTest {

    @ParameterizedTest(name = "posts={0}, subscribers={1}, K={2} -> maxPairs={3}")
    @CsvSource({
        // Basic cases: subscriber-limited
        "20, 1, 5, 5",    // min(10, 5) = 5
        "20, 2, 5, 10",   // min(10, 10) = 10
        "100, 3, 5, 15",  // min(50, 15) = 15

        // Basic cases: post-limited
        "4, 10, 5, 2",    // min(2, 50) = 2
        "6, 10, 5, 3",    // min(3, 50) = 3
        "10, 100, 5, 5",  // min(5, 500) = 5

        // Edge cases
        "0, 5, 5, 0",     // no posts -> 0
        "10, 0, 5, 0",    // no subscribers -> 0
        "0, 0, 5, 0",     // nothing -> 0
        "1, 5, 5, 0",     // 1 post -> 0 (can't make a pair)

        // Exact boundary
        "2, 1, 5, 1",     // min(1, 5) = 1 (smallest valid pair count)

        // Different K values
        "20, 2, 1, 2",    // K=1: min(10, 2) = 2
        "20, 2, 10, 10",  // K=10: min(10, 20) = 10
    })
    void computeMaxPairs_formula(int uniquePosts, int subscribers, int pairsPerSub, int expected) throws Exception {
        // Use reflection to test the private formula method
        PairGenerationService service = new PairGenerationService(null, null, null);
        setField(service, "pairsPerSubscriber", pairsPerSub);

        Method method = PairGenerationService.class.getDeclaredMethod("computeMaxPairs", int.class, int.class);
        method.setAccessible(true);
        int result = (int) method.invoke(service, uniquePosts, subscribers);

        assertEquals(expected, result,
                String.format("computeMaxPairs(%d, %d) with K=%d should be %d", uniquePosts, subscribers, pairsPerSub, expected));
    }

    @Test
    void demandGating_defaultK5_subscribersControlPairCount() throws Exception {
        PairGenerationService service = new PairGenerationService(null, null, null);
        setField(service, "pairsPerSubscriber", 5);

        Method method = PairGenerationService.class.getDeclaredMethod("computeMaxPairs", int.class, int.class);
        method.setAccessible(true);

        // With 100 posts (50 possible pairs), subscribers gate the output
        assertEquals(5, method.invoke(service, 100, 1));   // 1 sub * 5 = 5
        assertEquals(10, method.invoke(service, 100, 2));   // 2 subs * 5 = 10
        assertEquals(25, method.invoke(service, 100, 5));   // 5 subs * 5 = 25
        assertEquals(50, method.invoke(service, 100, 10));  // 10 subs * 5 = 50 (now post-limited)
        assertEquals(50, method.invoke(service, 100, 100)); // 100 subs * 5 = 500, but only 50 post pairs
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
