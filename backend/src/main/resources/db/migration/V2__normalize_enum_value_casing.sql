-- Align PostgreSQL enum labels with Java EnumType.STRING names
-- so reads/writes and query parameters resolve consistently.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_enum e
        JOIN pg_type t ON t.oid = e.enumtypid
        WHERE t.typname = 'round_status' AND e.enumlabel = 'open'
    ) THEN
        ALTER TYPE round_status RENAME VALUE 'open' TO 'OPEN';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_enum e
        JOIN pg_type t ON t.oid = e.enumtypid
        WHERE t.typname = 'round_status' AND e.enumlabel = 'commit'
    ) THEN
        ALTER TYPE round_status RENAME VALUE 'commit' TO 'COMMIT';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_enum e
        JOIN pg_type t ON t.oid = e.enumtypid
        WHERE t.typname = 'round_status' AND e.enumlabel = 'reveal'
    ) THEN
        ALTER TYPE round_status RENAME VALUE 'reveal' TO 'REVEAL';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_enum e
        JOIN pg_type t ON t.oid = e.enumtypid
        WHERE t.typname = 'round_status' AND e.enumlabel = 'settling'
    ) THEN
        ALTER TYPE round_status RENAME VALUE 'settling' TO 'SETTLING';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_enum e
        JOIN pg_type t ON t.oid = e.enumtypid
        WHERE t.typname = 'round_status' AND e.enumlabel = 'settled'
    ) THEN
        ALTER TYPE round_status RENAME VALUE 'settled' TO 'SETTLED';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_enum e
        JOIN pg_type t ON t.oid = e.enumtypid
        WHERE t.typname = 'subscription_type' AND e.enumlabel = 'realtime'
    ) THEN
        ALTER TYPE subscription_type RENAME VALUE 'realtime' TO 'REALTIME';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_enum e
        JOIN pg_type t ON t.oid = e.enumtypid
        WHERE t.typname = 'subscription_type' AND e.enumlabel = 'free_delay'
    ) THEN
        ALTER TYPE subscription_type RENAME VALUE 'free_delay' TO 'FREE_DELAY';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_enum e
        JOIN pg_type t ON t.oid = e.enumtypid
        WHERE t.typname = 'pair_winner' AND e.enumlabel = 'tie'
    ) THEN
        ALTER TYPE pair_winner RENAME VALUE 'tie' TO 'TIE';
    END IF;
END
$$;
