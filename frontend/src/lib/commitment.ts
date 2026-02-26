import { PublicKey } from '@solana/web3.js'
import { keccak_256 } from '@noble/hashes/sha3.js'
import { sha256 } from '@noble/hashes/sha2.js'

export type CommitmentChoice = 'A' | 'B'

export interface CommitmentHashInput {
  wallet: string
  pairId: number
  choice: CommitmentChoice
  stakeAmount: number
  nonce: Uint8Array
}

export const NONCE_SIZE_BYTES = 32
export const REVEAL_PAYLOAD_SIZE_BYTES = 1 + NONCE_SIZE_BYTES
export const COMMIT_REQUEST_NONCE_SIZE_BYTES = 16
export const COMMIT_REVEAL_IV_SIZE_BYTES = 12
const COMMIT_AUTH_MESSAGE_PREFIX = 'moltrank-commit-v1'

export interface CommitAuthMessageInput {
  wallet: string
  pairId: number
  commitmentHash: string
  stakeAmount: number
  signedAtEpochSeconds: number
  requestNonceHex: string
}

export interface EncryptRevealInput {
  choice: CommitmentChoice
  nonce: Uint8Array
  signature: Uint8Array
  authMessage: string
}

export function generateNonce(): Uint8Array {
  const nonce = new Uint8Array(NONCE_SIZE_BYTES)
  crypto.getRandomValues(nonce)
  return nonce
}

export function generateCommitRequestNonce(): Uint8Array {
  const nonce = new Uint8Array(COMMIT_REQUEST_NONCE_SIZE_BYTES)
  crypto.getRandomValues(nonce)
  return nonce
}

export function buildRevealPayload(
  choice: CommitmentChoice,
  nonce: Uint8Array,
): Uint8Array {
  validateNonce(nonce)

  const payload = new Uint8Array(REVEAL_PAYLOAD_SIZE_BYTES)
  payload[0] = choiceToByte(choice)
  payload.set(nonce, 1)
  return payload
}

export function encodeRevealPayloadBase64(
  choice: CommitmentChoice,
  nonce: Uint8Array,
): string {
  return bytesToBase64(buildRevealPayload(choice, nonce))
}

export async function encryptRevealPayloadWithSignature(input: EncryptRevealInput): Promise<{
  encryptedRevealBase64: string
  revealIvBase64: string
}> {
  const revealPayload = buildRevealPayload(input.choice, input.nonce)
  const keyBytes = toWebCryptoBytes(deriveClientRevealKey(input.signature))
  const iv = new Uint8Array(COMMIT_REVEAL_IV_SIZE_BYTES)
  crypto.getRandomValues(iv)

  const key = await crypto.subtle.importKey('raw', keyBytes, { name: 'AES-GCM' }, false, ['encrypt'])
  const ciphertext = await crypto.subtle.encrypt(
    {
      name: 'AES-GCM',
      iv,
      additionalData: toWebCryptoBytes(utf8Bytes(input.authMessage)),
    },
    key,
    toWebCryptoBytes(revealPayload),
  )

  return {
    encryptedRevealBase64: bytesToBase64(new Uint8Array(ciphertext)),
    revealIvBase64: bytesToBase64(iv),
  }
}

export function buildCommitAuthMessage(input: CommitAuthMessageInput): string {
  const normalizedHash = normalizeCommitmentHash(input.commitmentHash)
  const requestNonce = normalizeRequestNonceHex(input.requestNonceHex)

  return `${COMMIT_AUTH_MESSAGE_PREFIX}|wallet=${input.wallet}|pairId=${input.pairId}|hash=${normalizedHash}|stake=${input.stakeAmount}|signedAt=${input.signedAtEpochSeconds}|nonce=${requestNonce}`
}

export function decodeRevealPayloadBase64(encodedPayload: string): {
  choice: CommitmentChoice
  nonce: Uint8Array
} {
  const payload = base64ToBytes(encodedPayload)
  if (payload.length !== REVEAL_PAYLOAD_SIZE_BYTES) {
    throw new Error(
      `Invalid reveal payload length: expected ${REVEAL_PAYLOAD_SIZE_BYTES}, got ${payload.length}`,
    )
  }

  const voteByte = payload[0]
  if (voteByte !== 0 && voteByte !== 1) {
    throw new Error(`Invalid reveal payload choice byte: ${voteByte}`)
  }

  return {
    choice: voteByte === 0 ? 'A' : 'B',
    nonce: payload.slice(1),
  }
}

export function computeCommitmentHashHex(input: CommitmentHashInput): string {
  const walletBytes = decodeWalletBytes(input.wallet)
  const pairId = encodeU32LE(input.pairId)
  const choiceByte = new Uint8Array([choiceToByte(input.choice)])
  const stakeAmount = encodeU64LE(input.stakeAmount)

  validateNonce(input.nonce)

  const preimage = new Uint8Array(
    walletBytes.length +
      pairId.length +
      choiceByte.length +
      stakeAmount.length +
      input.nonce.length,
  )

  let offset = 0
  preimage.set(walletBytes, offset)
  offset += walletBytes.length

  preimage.set(pairId, offset)
  offset += pairId.length

  preimage.set(choiceByte, offset)
  offset += choiceByte.length

  preimage.set(stakeAmount, offset)
  offset += stakeAmount.length

  preimage.set(input.nonce, offset)

  return `0x${bytesToHex(keccak_256(preimage))}`
}

export function normalizeCommitmentHash(hash: string): string {
  const trimmed = hash.trim().toLowerCase()
  const withoutPrefix = trimmed.startsWith('0x') ? trimmed.slice(2) : trimmed
  if (!/^[0-9a-f]{64}$/.test(withoutPrefix)) {
    throw new Error('Invalid commitment hash format')
  }
  return `0x${withoutPrefix}`
}

export function nonceHex(nonce: Uint8Array): string {
  validateNonce(nonce)
  return bytesToHex(nonce)
}

export function commitRequestNonceHex(nonce: Uint8Array): string {
  if (nonce.length !== COMMIT_REQUEST_NONCE_SIZE_BYTES) {
    throw new Error(`Commit request nonce must be ${COMMIT_REQUEST_NONCE_SIZE_BYTES} bytes`)
  }
  return bytesToHex(nonce)
}

export function nonceFromHex(hex: string): Uint8Array {
  if (!/^[0-9a-fA-F]{64}$/.test(hex)) {
    throw new Error('Nonce hex must be 64 hex chars')
  }

  const bytes = new Uint8Array(NONCE_SIZE_BYTES)
  for (let i = 0; i < NONCE_SIZE_BYTES; i += 1) {
    const start = i * 2
    bytes[i] = parseInt(hex.slice(start, start + 2), 16)
  }
  return bytes
}

function decodeWalletBytes(wallet: string): Uint8Array {
  const key = new PublicKey(wallet)
  return key.toBytes()
}

function choiceToByte(choice: CommitmentChoice): number {
  return choice === 'A' ? 0 : 1
}

function validateNonce(nonce: Uint8Array): void {
  if (nonce.length !== NONCE_SIZE_BYTES) {
    throw new Error(`Nonce must be ${NONCE_SIZE_BYTES} bytes`)
  }
}

function normalizeRequestNonceHex(value: string): string {
  const normalized = value.trim().toLowerCase()
  if (!/^[0-9a-f]{32}$/.test(normalized)) {
    throw new Error('requestNonce must be 32 lowercase hex chars')
  }
  return normalized
}

function encodeU32LE(value: number): Uint8Array {
  if (!Number.isInteger(value) || value < 0 || value > 0xffffffff) {
    throw new Error('pairId must be an unsigned 32-bit integer')
  }

  const bytes = new Uint8Array(4)
  const view = new DataView(bytes.buffer)
  view.setUint32(0, value, true)
  return bytes
}

function encodeU64LE(value: number): Uint8Array {
  if (!Number.isSafeInteger(value) || value < 0) {
    throw new Error('stakeAmount must be a non-negative safe integer')
  }

  const bigint = BigInt(value)
  const max = (1n << 64n) - 1n
  if (bigint > max) {
    throw new Error('stakeAmount exceeds uint64 range')
  }

  const bytes = new Uint8Array(8)
  let remaining = bigint
  for (let i = 0; i < 8; i += 1) {
    bytes[i] = Number(remaining & 0xffn)
    remaining >>= 8n
  }
  return bytes
}

function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes)
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('')
}

export function bytesToBase64(bytes: Uint8Array): string {
  const maybeBuffer = (globalThis as { Buffer?: { from: (input: Uint8Array) => { toString: (encoding: string) => string } } }).Buffer
  if (maybeBuffer) {
    return maybeBuffer.from(bytes).toString('base64')
  }

  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })

  if (typeof btoa !== 'function') {
    throw new Error('Base64 encoding unavailable in this runtime')
  }
  return btoa(binary)
}

function base64ToBytes(value: string): Uint8Array {
  const maybeBuffer = (globalThis as {
    Buffer?: { from: (input: string, encoding: string) => { values: () => IterableIterator<number> } }
  }).Buffer
  if (maybeBuffer) {
    return Uint8Array.from(maybeBuffer.from(value, 'base64').values())
  }

  if (typeof atob !== 'function') {
    throw new Error('Base64 decoding unavailable in this runtime')
  }

  const decoded = atob(value)
  return Uint8Array.from(decoded, (char) => char.charCodeAt(0))
}

function deriveClientRevealKey(signature: Uint8Array): Uint8Array {
  return sha256(signature)
}

function toWebCryptoBytes(bytes: Uint8Array): Uint8Array<ArrayBuffer> {
  return new Uint8Array(bytes)
}

function utf8Bytes(value: string): Uint8Array {
  return new TextEncoder().encode(value)
}
