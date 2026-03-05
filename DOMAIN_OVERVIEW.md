# ⚔️ Business Domain Overview

## 🏰 What is Heroes of Might & Magic III?

Heroes of Might & Magic III (1999) is a turn-based strategy game where players control a hero leading armies across a fantasy map. Each player manages a **town** — building structures, gathering resources (gold, wood, ore, gems, etc.), recruiting creatures, and sending heroes on quests or into battle. A typical turn involves economic decisions (what to build, where to invest), logistics (moving heroes, managing multiple armies), and combat (tactical grid-based battles between creature stacks).

The game operates on a **weekly cycle**: each week, creature dwellings produce a fixed growth of recruitable units, resource mines generate income, and players must balance short-term military needs against long-term economic development. There are multiple town types (Castle, Rampart, Necropolis, ...), each with unique creature rosters, buildings, and strategies — making it a deeply systemic domain with many interacting rules.

It has a massive community to this day, with wikis, guides, and tournaments documenting every rule in detail — essentially a domain with thousands of unpaid "domain experts."

## 🎯 Why This Domain?

Don't think of this as "just a game." Could we demonstrate DDD using a cinema or a shopping cart? Sure — but those examples have been repeated countless times in books and at conferences. Here, the reality is already defined by the rules of the game and we must discover and properly model it — just like in a real project. The difference is that the "domain experts" are already out there: decades of community knowledge in rulebooks, wikis, strategy guides, and fan-made specifications make this an ideal sandbox for practicing Domain-Driven Design.

The complexity rivals many enterprise systems — multiple bounded contexts, cross-domain processes, resource contention, and temporal business rules — all packaged in a domain that's intuitive and fun to reason about.

> The more cases you see, the better you model. Intuition favors those who have had the chance to be exposed to various cases.

This project is a training ground for building that intuition, so that when you encounter an analogous case in a real project, your brain immediately knows how to react.

## 💼 Real Business Processes in Disguise

Almost every game mechanic maps directly to real-world business patterns. Recruiting a creature is not just a "game logic" — it's an e-commerce purchase with limited stock, budget constraints, and an availability window. Building a dwelling is provisioning infrastructure with prerequisites and costs. The weekly creature growth cycle is scheduled inventory replenishment, no different from warehouse restocking.

| Game Mechanic | Business Equivalent |
|---|---|
| **Recruiting creatures** | E-commerce purchase with limited stock, budget constraints, and availability windows |
| **Resource management** | Treasury / budget allocation across competing priorities |
| **Building dwellings** | Provisioning infrastructure with prerequisites and costs |
| **Weekly creature growth** | Scheduled inventory replenishment (warehouse restocking) |
| **Army movement** | Logistics and fleet/delivery management |
| **Hero progression** | Employee skill development and certification |
| **Trading resources** | Financial exchange / currency conversion with rates |
| **Turn-based calendar** | Business cycle management (daily settlements, weekly reports, monthly reviews) |
| **Astrologers' proclamations** | External market events that disrupt normal business cycles |
| **Creature upgrades** | Product tier upselling with prerequisite infrastructure |

Consider creature recruitment alone: *"What must happen before recruiting?"* — the dwelling must be built, which triggers initial availability. *"Is that the only way availability changes?"* — no, every new week increases it, unless astrologers proclaim a plague week, which resets everything to zero. *"What happens after recruiting?"* — the creature joins an army, but that's also possible through map encounters or battle outcomes. Each question reveals a new integration point, a new bounded context, a new module boundary — exactly what happens in real enterprise systems when you dig past the surface requirements.

## 🧩 Autonomous Modules, Not a Monolithic Game Engine

The primary modeling goal is **autonomous modules** — not a faithful HOMM3 replica. How you implement them and which technologies you use is secondary.

Just as Uber separated route planning from passenger transport (enabling package delivery without changing the ride system), and just as the Heroes III board game offers two independent battle systems (simple cards in the base game, tactical miniatures as an expansion) — each module in this project can evolve, scale, or be replaced independently.

The Creature Recruitment module doesn't know about town building, calendars, or astrologers. It exposes an `IncreaseAvailableCreatures` command, and any module — whether it's a calendar automation, an astrologers' proclamation handler, or a future spell system — can trigger it. Adding a new reason for availability changes requires zero modifications to the recruitment module itself. This is the **Open-Closed Principle applied at the architecture level**.

This matters because:
- A programmer implementing recruitment doesn't need to understand the calendar or astrologers
- Teams can work on independent modules without merge conflicts or cognitive overload
- New capabilities (think: hero artifacts affecting recruitment) plug in without touching existing code
- Each module can serve as the foundation for an entirely new product, just like Uber's routing module powers both rides and deliveries

## 🚀 Beyond the Original Game

We take Heroes III as a **starting point**, not a constraint. The modular, slice-based architecture deliberately enables scenarios the original game never supported:

- **Real-time multiplayer** — think Tribal Wars or Travian rather than hot-seat turns. The event-sourced, tag-based consistency boundaries allow concurrent operations on independent parts of the game state without global locks.
- **Asynchronous gameplay** — players act on their own schedule; automations and projections keep the world consistent.
- **Custom rules and scenarios** — because each slice is an independent module toggled via feature flags, you can mix and match mechanics, add new ones, or rewrite existing ones without touching the rest of the system.
- **Scaling individual features** — a recruitment spike doesn't need to block calendar processing. Vertical slices can scale independently.
- **New "products" from existing modules** — the battle module could power a standalone arena mode, the recruitment module could become an independent marketplace — just like the Heroes III board game extracted battle mechanics into a separate purchasable expansion.

The goal is not to replicate Heroes III 1:1, but to use its domain as a proving ground for patterns that work equally well in fintech, logistics, e-commerce, or any event-driven system.
