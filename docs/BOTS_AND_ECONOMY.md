# Simulated Players and Economy

## Goal

Create persistent server-side players rather than fake decorative NPCs.

Each simulated player is a real account-like entity with:

- stats
- bank
- inventory
- equipment
- quests
- wealth
- goals
- preferences
- play schedule
- persistent history

## Architecture

```text
SimulatedPlayer
      │
      ▼
   BotBrain
      │
      ├── GoalSelector
      ├── Navigation
      ├── EquipmentPlanner
      ├── BankPlanner
      ├── EconomyPlanner
      └── Activities
           ├── Mining
           ├── Fishing
           ├── Woodcutting
           ├── Combat
           ├── Slayer
           ├── Trading
           └── PKing
```

## Example profile

```yaml
name: LemonCow

personality:
  efficiency: 0.35
  social: 0.60
  risk_tolerance: 0.20

goals:
  fishing: 70
  cooking: 70
  strength: 60

preferred_activities:
  - fishing
  - cooking

average_session_hours: 3.2
```

## Persistence

A bot should begin like a real player:

```text
Day 1

Attack       1
Strength     1
Defence      1
Fishing      1
Woodcutting  1

Bank:
- bronze axe
- tinderbox
```

Months later:

```text
Combat       82
Fishing      91
Woodcutting  74
Quest Points 88

Bank:
- rune armour
- dragon scimitar
- 18,402 lobsters
- clue rewards
- random junk
```

## Economy

Items should actually flow through inventories.

```text
Bot mines coal
     ↓
Bot sells coal
     ↓
Market receives coal
     ↓
Player buys coal
     ↓
Coins go to bot
     ↓
Bot buys equipment
```

For a 2006-style world, trading can happen through:

- NPC-like market agents
- player-to-player bot trading
- Varrock/Falador trade hubs
- optional simplified exchange system

## Wilderness

Persistent bots can populate:

- Edgeville
- low-level Wilderness
- rune rocks
- Mage Bank
- major travel routes

Bot PKers should use:

- real gear
- real food
- real runes/ammo
- actual risk
- actual drops

## Initial population

Start small:

- 30–50 persistent accounts
- around 10–20 concurrently active
- increase later after profiling

The objective is believable activity, not maximum player count.
