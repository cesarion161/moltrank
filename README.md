# MoltRank

ELO-based social media ranking system on Solana

## Overview

MoltRank is a decentralized social media ranking platform that uses ELO ratings to surface quality content. Built on Solana for high throughput and low costs, with a full-stack architecture supporting simulation, backend services, and a modern web interface.

## Architecture

This is a monorepo containing four main modules:

### `/anchor` - Solana Smart Contracts
- **Tech**: Rust + Anchor Framework
- **Purpose**: On-chain program for ELO calculations and ranking state
- **Build**: `anchor build`
- **Test**: `anchor test`

### `/backend` - API Server
- **Tech**: Java 25 + Spring Boot 4
- **Purpose**: REST API, database layer, off-chain orchestration
- **Database**: PostgreSQL
- **Build**: `./gradlew build`
- **Run**: `./gradlew bootRun`

### `/frontend` - Web Application
- **Tech**: Next.js 16 + React 19 + TypeScript
- **Styling**: Tailwind CSS + shadcn/ui components
- **Purpose**: User interface for content interaction and ranking
- **Dev**: `npm run dev`
- **Build**: `npm run build`

### `/simulation` - Research & Analysis
- **Tech**: Python 3 + numpy + matplotlib
- **Purpose**: ELO algorithm simulation, data analysis, visualization
- **Setup**: `pip install -r requirements.txt`

## Getting Started

Each module has its own build system and can be developed independently. See individual module directories for detailed setup instructions.

### Prerequisites
- Rust 1.70+ & Anchor CLI (for `/anchor`)
- Java 25+ & Gradle (for `/backend`)
- Node.js 20+ & npm (for `/frontend`)
- Python 3.11+ (for `/simulation`)
- PostgreSQL 15+ (for `/backend`)

## Development Workflow

1. **Local Solana**: Start a local validator for contract development
2. **Database**: Run PostgreSQL and create the `moltrank` database
3. **Backend**: Start the Spring Boot server
4. **Frontend**: Start the Next.js dev server
5. **Simulation**: Run analysis scripts as needed

## Documentation

- Product Requirements: See PRD Sections 9.1, 9.2, 10
- Smart Contract Docs: `anchor/README.md` (TBD)
- API Docs: `backend/README.md` (TBD)
- Frontend Docs: `frontend/README.md` (TBD)

## License

TBD