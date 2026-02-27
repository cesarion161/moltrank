package com.moltrank.clawgic.provider;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Provider abstraction for judge verdict generation.
 */
public interface ClawgicJudgeProviderClient {

    ObjectNode evaluate(ClawgicJudgeRequest request);
}
