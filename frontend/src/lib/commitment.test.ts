import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  computeCommitmentHashHex,
  decodeRevealPayloadBase64,
  encodeRevealPayloadBase64,
  nonceFromHex,
  nonceHex,
  normalizeCommitmentHash,
} from './commitment'

interface CommitmentVector {
  name: string
  wallet: string
  pairId: number
  stakeAmount: number
  choice: 'A' | 'B'
  nonceHex: string
  revealPayloadBase64: string
  commitmentHash: string
}

function loadVectors(): CommitmentVector[] {
  const vectorsPath = resolve(process.cwd(), '..', 'config', 'commitment-test-vectors.json')
  return JSON.parse(readFileSync(vectorsPath, 'utf8')) as CommitmentVector[]
}

describe('commitment codec', () => {
  const vectors = loadVectors()

  it.each(vectors)('computes canonical hash and payload for $name', (vector) => {
    const nonce = nonceFromHex(vector.nonceHex)

    const payload = encodeRevealPayloadBase64(vector.choice, nonce)
    expect(payload).toBe(vector.revealPayloadBase64)

    const decoded = decodeRevealPayloadBase64(payload)
    expect(decoded.choice).toBe(vector.choice)
    expect(nonceHex(decoded.nonce)).toBe(vector.nonceHex.toLowerCase())

    const hash = computeCommitmentHashHex({
      wallet: vector.wallet,
      pairId: vector.pairId,
      choice: vector.choice,
      stakeAmount: vector.stakeAmount,
      nonce,
    })
    expect(hash).toBe(vector.commitmentHash.toLowerCase())
  })

  it('normalizes hash inputs with or without 0x prefix', () => {
    const digest = 'ABCD'.padEnd(64, '0')
    expect(normalizeCommitmentHash(`0x${digest}`)).toBe(`0x${digest.toLowerCase()}`)
    expect(normalizeCommitmentHash(digest)).toBe(`0x${digest.toLowerCase()}`)
  })

  it('rejects non-safe stake integers in hash preimage encoding', () => {
    const vector = vectors[0]
    const nonce = nonceFromHex(vector.nonceHex)

    expect(() =>
      computeCommitmentHashHex({
        wallet: vector.wallet,
        pairId: vector.pairId,
        choice: vector.choice,
        stakeAmount: Number.MAX_SAFE_INTEGER + 1,
        nonce,
      }),
    ).toThrow('stakeAmount must be a non-negative safe integer')
  })
})
