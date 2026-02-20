use anchor_lang::prelude::*;
use anchor_lang::solana_program::keccak;

use crate::error::MoltRankError;
use crate::state::Commitment;

pub const NONCE_BYTES: usize = 32;
pub const REVEAL_PAYLOAD_BYTES: usize = 1 + NONCE_BYTES;

pub struct ParsedRevealPayload {
    pub vote: u8,
    pub nonce: [u8; NONCE_BYTES],
}

pub fn parse_reveal_payload(decrypted_payload: &[u8]) -> Result<ParsedRevealPayload> {
    require!(
        decrypted_payload.len() == REVEAL_PAYLOAD_BYTES,
        MoltRankError::InvalidRevealPayload
    );

    let vote = decrypted_payload[0];
    require!(vote == 0 || vote == 1, MoltRankError::InvalidVoteValue);

    let mut nonce = [0u8; NONCE_BYTES];
    nonce.copy_from_slice(&decrypted_payload[1..]);

    Ok(ParsedRevealPayload { vote, nonce })
}

pub fn compute_commitment_hash(commitment: &Commitment, payload: &ParsedRevealPayload) -> [u8; 32] {
    let pair_id_bytes = commitment.pair_id.to_le_bytes();
    let stake_amount_bytes = commitment.stake_amount.to_le_bytes();
    let vote_bytes = [payload.vote];

    keccak::hashv(&[
        commitment.curator_wallet.as_ref(),
        pair_id_bytes.as_ref(),
        vote_bytes.as_ref(),
        stake_amount_bytes.as_ref(),
        payload.nonce.as_ref(),
    ])
    .to_bytes()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::state::Commitment;
    use anchor_lang::prelude::Pubkey;

    #[test]
    fn computes_known_vector_hash() {
        let mut commitment = Commitment {
            commitment_hash: [0u8; 32],
            encrypted_reveal: vec![],
            curator_wallet: Pubkey::default(),
            pair_id: 42,
            round_id: 1,
            stake_amount: 5_000_000_000,
            timestamp: 0,
            revealed: false,
            vote: None,
            bump: 0,
        };

        let payload_bytes =
            hex_to_bytes("00000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        let payload = parse_reveal_payload(&payload_bytes).unwrap();
        commitment.commitment_hash = compute_commitment_hash(&commitment, &payload);

        assert_eq!(
            to_hex(&commitment.commitment_hash),
            "4aed971d471472c3b59e1e629a67a2ad7e54bc99d0fe0747fcac5c6c4edd71ca"
        );
    }

    fn hex_to_bytes(hex: &str) -> Vec<u8> {
        (0..hex.len())
            .step_by(2)
            .map(|i| u8::from_str_radix(&hex[i..i + 2], 16).unwrap())
            .collect()
    }

    fn to_hex(bytes: &[u8]) -> String {
        bytes.iter().map(|byte| format!("{:02x}", byte)).collect()
    }
}
