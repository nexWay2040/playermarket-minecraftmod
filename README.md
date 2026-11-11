# PLAYER MARKET

> **EVERYTHING HAS A PRICE.** Your body, your stats, our market.

A dystopian RPG-economy and co-op narrative Fabric mod for **Minecraft Java Edition `26.3`** where corporate creditors let you liquidate your own biological capabilities for credits (**CR**), invest in diamond augmentations, survive volatile market cycles, complete field contracts, and face the hidden fine print of **Section 17.4**.

[![Minecraft 26.3](https://img.shields.io/badge/Minecraft-26.3-10B981?style=flat-square)](#requirements)
[![Fabric Loader](https://img.shields.io/badge/Fabric%20Loader-%E2%89%A50.20.1-141923?style=flat-square)](#requirements)
[![License CC0-1.0](https://img.shields.io/badge/License-CC0--1.0-94A3B8?style=flat-square)](LICENSE)
[![Release v1.0.0-pre](https://img.shields.io/badge/Release-v1.0.0%20(Pre--Release)-F59E0B?style=flat-square)](#releases)

---

## Table of Contents

- [Premise & Gameplay Loop](#premise--gameplay-loop)
- [Requirements](#requirements)
- [Core Features](#core-features)
  - [1. Onboarding Letter & Clickable Section 17.4 Contract](#1-onboarding-letter--clickable-section-174-contract)
  - [2. Financial Terminal HUD & Hybrid 3D/2D Bio-Monitor](#2-financial-terminal-hud--hybrid-3d2d-bio-monitor)
  - [3. Biological Asset Liquidation, Leasing & Buyback](#3-biological-asset-liquidation-leasing--buyback)
  - [4. Resource Exchange & Recipe Valuation Engine](#4-resource-exchange--recipe-valuation-engine)
  - [5. Diamond Augmentations, VIP Status & Ore Scanner Sonar](#5-diamond-augmentations-vip-status--ore-scanner-sonar)
  - [6. Black Market Protocols](#6-black-market-protocols)
  - [7. Macroeconomic Market Events & Archetype Builds](#7-macroeconomic-market-events--archetype-builds)
  - [8. Co-op Multiplayer Campaign, Quests & Final Ending](#8-co-op-multiplayer-campaign-quests--final-ending)
- [Controls & Commands](#controls--commands)
- [Configuration](#configuration)
- [Building from Source](#building-from-source)
- [Asset Attributions & License](#asset-attributions--license)

---

## Premise & Gameplay Loop

When you enter the world, you are enrolled in **The Asset Program** with a starter corporate dispatch (**Crumpled Letter**) and a **Market Access Card**:

1. **Read the Crumpled Letter & Sign the Contract**: Open the corporate parchment contract to begin. Buried under harmless bureaucratic filler sits clickable fine print for **Section 17.4** if you look closely enough.
2. **Monetize Yourself**: Sell `10%`, `25%`, or `50%` chunks of your character attributes (up to the `80%` corporate cap, unlocked progressively by stage) in exchange for liquid **CR**, passive CR/min dividends, or cross-stat bonuses — or lock stats into fixed **1 / 3 / 5-day Leases**.
3. **Mine, Trade & Upgrade**: Turn mined resources and vanilla crafting chains into **CR** via a recursive recipe pricing engine, convert daily capped **Diamonds** into cybernetic augmentations, and scan nearby ores through stone with the **Ore Scanner**.
4. **Progress Co-op Stages 1–6**: Complete 20 active gameplay tasks, 12 satirical corporate side quests, and rotating contracts with friends sharing synchronized world campaign progress.
5. **Sign Stage 6 Final Settlement**: Collect the **1,000,000 CR** payout — then experience the full consequences of **Section 17.4**, complete with a blackout analog-horror ending, permanent account revocation, server disconnect, and a **titanium orthopedic right-leg brace** rendered on your player model whenever you reconnect.

---

## Requirements

- **Minecraft**: `26.3` (Java Edition)
- **Mod Loader**: Fabric Loader `>= 0.20.1`
- **Dependencies**: Fabric API (`0.165.2+26.3` or compatible)
- **Java**: Java `25`
- **Environment**: Required on both **Client** and **Server** (works in Singleplayer, LAN Co-op, and Dedicated Servers)
- **Localization**: Full **English (`en_us`)** and **Russian (`ru_ru`)** translations included

---

## Core Features

### 1. Onboarding Letter & Clickable Section 17.4 Contract
- New players receive the **Crumpled Letter** (`playermarket:crumpled_letter`) and **Market Access Card** (`playermarket:market_access_card`).
- The Market Access Card vanishes permanently upon death to prevent card duplication (reclaimable via `/market card` before settlement).
- An interactive textured parchment contract includes subtle clickable **Section 17.4** fine print hidden beneath legal filler text, letting attentive players preview what corporate settlement actually entails.

### 2. Financial Terminal HUD & Hybrid 3D/2D Bio-Monitor
- Dedicated obsidian-graphite-emerald 9-slice terminal UI with 12 tabs:
  `Overview`, `Program`, `Mail`, `Sell Assets`, `Buyback`, `Resource Exchange`, `Upgrades Shop`, `Black Market`, `Contracts`, `Portfolio`, `History`, and `Market News`.
- **Hybrid 3D/2D Bio-Monitor**: Switch between an interactive **2D anatomical silhouette** (`Head`, `Torso`, `Arms`, `Legs`, `Heart`, and post-ending `Brace`) and a **live 3D rendered player model** displaying dynamic zone health tinting, floating armor/telemetry callouts, and the rendered Section 17.4 exoskeleton brace directly on your skin.
- **Compact World HUD**: Displays current CR balance, inventory diamonds, active market event banner, tracked contracts, and an active Ore Scanner summary; toggleable and uncluttered.

### 3. Biological Asset Liquidation, Leasing & Buyback
- **30 Monetizable Player Attributes (`AssetType`)** across 4 progressive corporate categories:
  - **Body (8)** — *Stage 1*: Max Health, Natural Regeneration, Saturation Efficiency, Hunger Efficiency, Oxygen Capacity, Fire Resistance, Fall Resistance, Max Absorption
  - **Movement (5)** — *Stage 2*: Movement Speed, Jump Strength, Sprint Efficiency, Swim Speed, Step Height
  - **Combat (10)** — *Stage 3*: Attack Damage, Attack Speed, Armor, Armor Toughness, Knockback Resistance, Entity Reach, Projectile Resistance, Explosion Resistance, Critical Damage, Knockback Power
  - **Professional (7)** — *Stage 4*: Mining Speed, Underwater Mining, Luck, XP Gain, Block Reach, Potion Effectiveness, Projectile Accuracy
- **Flexible Deal Modes**:
  - Permanent Liquidation (`10%`, `25%`, `50%` within stage limits up to `80%` max sold)
  - Choose between two **Dividend Perks** per asset (passive CR/min payouts or secondary stat bonuses)
  - **1 / 3 / 5-Day Leases** for temporary income + 100% auto-restoration (or early buyout with penalty)
  - **Buyback Market**: Reclaim sold traits later at `2.35x` base cost (`+135%` corporate commission by default), modified by live macroeconomic events and **Broker License** discounts.

### 4. Resource Exchange & Recipe Valuation Engine
- [`ResourceValuationEngine`](src/main/java/com/playermarket/market/ResourceValuationEngine.java) dynamically evaluates vanilla blocks and crafted items recursively from configurable base commodity prices (`dirt`, `coal`, `iron_ingot`, `gold_ingot`, `diamond`, `emerald`, `netherite_scrap`, `nether_star`, food, drops, etc.).
- Supports both selling inventory resources for CR and buying back items from the corporate catalog (`1.40x` default markup).
- **Daily Diamond Exchange**: Sell/convert capped diamonds (`10/day` base limit) into CR or expand your daily diamond quota through **VIP Client** tiers.

### 5. Diamond Augmentations, VIP Status & Ore Scanner Sonar
- **28 Multi-Tier Upgrades (`UpgradeType`)** across 4 trees (`Level 1` – `Level 3`), displaying exact maxed-out numerical stat effects on completed cards:
  - **Body (8)**: Reinforced Heart, Rapid Recovery, Adrenaline System, Enhanced Lungs, Strong Legs, Metabolism Upgrade, Reinforced Bones, Thermal Adaptation
  - **Combat (7)**: Muscle Overclock, Fast Reflexes, Reinforced Skin, Impact Dampener, Precision Targeting, Projectile Calibration, Combat Focus
  - **Professional (7)**: Mining Protocol, Lucky Chip, XP Accelerator, Ore Scanner, Efficient Metabolism, Aquatic Propulsion, Precision Hands
  - **VIP & Corporate Privileges (6)**: Premium Account, Broker License, Market Insurance, VIP Client (expands daily diamond trade cap to `15 -> 22 -> 35/day`, grants diamond discounts, boosts dividends, and opens high-tier perks), Price Lock, Diamond Cashback
- **3D Ore Scanner Sonar**: When upgraded (`5m -> 8m -> 12m`), [`OreScannerRenderer`](src/client/java/com/playermarket/client/render/OreScannerRenderer.java) highlights nearby high-value ores (`Diamond`, `Gold`, `Emerald`, `Ancient Debris`, and `Iron`/`Redstone` at Tier 2+) with color-coded X-ray outline pulses and HUD ore counts.

### 6. Black Market Protocols
Unlocks automatically once deal volume/turnover thresholds are met (or via **VIP Client** Tier 3). Contains **8 high-impact temporary/triggered protocols** (`BlackMarketItem`) priced in **CR + Diamonds**:
- `Second Chance` (saves from a lethal blow, restoring `2 HP` + Regeneration & Absorption)
- `Berserker Protocol` (`+40%` damage, `+20%` attack speed, `-30%` armor for 60s)
- `Phantom Step` (`+45%` speed, `+0.5m` step height, fall protection for 20s)
- `Synthetic Heart` (heals `4 HP` + `2` absorption per kill for 3m)
- `Overclocked Mind` (`+100%` XP, `+35%` mining speed for 2m)
- `Emergency Battery` (suppresses sold-stat penalties & adds `+15%` to all stats for 60s)
- `Golden Contract` (`2x` CR payout on your next deal or contract)
- `Asset Insurance` (`-50%` sold-stat & lease penalties for 5m)

### 7. Macroeconomic Market Events & Archetype Builds
- **15 Dynamic Market Events (`MarketEvent`)** rotate every half Minecraft day (`12,000 ticks`, disabled once the campaign enters final settlement):
  `Stable Market`, `Health Demand`, `Speed Shortage`, `Diamond Demand`, `Resource Oversupply`, `Market Panic`, `Dividend Day`, `Corporate Audit`, `Premium Week`, `Buyback Discount`, `Mining Boom`, `Market Crash`, `Bull Market`, `Tech Breakthrough`, and `Debt Crisis`.
- **6 Corporate Archetype Builds (`CharacterBuild`)** dynamically evaluated from your portfolio & upgrades:
  `The Glass Cannon`, `Chief Industrial Extractor`, `Kinetic Arbitrageur`, `Boardroom Sovereign`, `Apex Juggernaut`, and `Hollow Billionaire`.

### 8. Co-op Multiplayer Campaign, Quests & Final Ending
- **Shared Co-op State**: Campaign stage, shared tasks, market event timers, impaired player visual states, and the final ending are synchronized across all connected players via Fabric networking and persisted per world in NBT (`MarketSavedData`).
- **20 Active Story Quests** (4 per stage across Stages 1–5) built around interactive world mechanics (team-building near mobs, gravity survival tests, stealth crouch breaks, paranoia camera checks, rooftop `Y >= 170` deals, lightning/fire survival, Nether lava tests, bare-knuckle brawls, and more) plus **12 Corporate Satire Side Quests** and a refreshable **Contract Board**.
- **Stage 6 Final Settlement & Section 17.4**:
  - Approving the final resolution closes terminal UIs across the world, grants **+1,000,000 CR**, transforms active Market Access Cards into **Blocked Market Cards** (`playermarket:blocked_market_card`), freezes player movement/input cleanly during the 5-step liquidation sequence, cuts to darkness, fires a `1.95s` analog-horror jumpscare screamer, and disconnects players with a corporate revocation notice (cleanly releasing all client input locks on disconnect).
  - Reconnecting later into the settled world preserves permanent revoked market access, attaches a persistent biomechanics right-leg brace rendered on all clients (`-12%` movement speed, `-22%` mining speed — jump height untouched), and prevents further market event spam.

---

## Controls & Commands

### Keybindings
- **`M`** — Open the Player Market terminal (can be rebound in **Options -> Controls -> Key Binds -> Misc**; requires an active Market Access Card in inventory).
- **Right-Click** holding **Market Access Card** or **Crumpled Letter** — Open the terminal or intro letter directly.

### Player Commands
| Command | Description |
| :--- | :--- |
| `/market card` | Reissue a replacement Market Access Card if you lost yours before settlement |
| `/market balance` | Check your CR balance and vanilla diamond count |
| `/market stats` | Print portfolio summary, black market access, and active archetype builds |

### Operator / Testing Commands *(Permission Level 2)*
| Command | Description |
| :--- | :--- |
| `/market give <amount>` | Grant CR to your account |
| `/market asset <id> [fraction]` | Set sold fraction (`0.0`–`0.80`) on an asset (`max_health`, `movement_speed`, etc.) |
| `/market upgrade <id> [level]` | Set an augmentation tier (`0`–`3`) |
| `/market contract` | Complete all active contracts on your board |
| `/market event <id>` | Trigger a specific macroeconomic event (e.g. `bull_market`, `market_crash`) |
| `/market story setstage <1-6>` | Jump the shared co-op campaign to a specific stage |
| `/market story reset` | Reset campaign quests back to Stage 1 |
| `/market ending main \| secret \| reset` | Test or reset the synchronized Final Settlement ending & Section 17.4 lock |
| `/market reset` | Reset your market account **and** clear campaign settlement locks |
| `/market reload` | Reload `config/playermarket.json` and recompute item valuations |
| `/market debug` | Unlock Black Market and dump live player attribute telemetry |

---

## Configuration

A config file is generated automatically at **`config/playermarket.json`** on startup (`MarketConfig`):

- Adjust `startingCr`, `assetPriceMultiplier`, `resourceSellRatio`, `resourceBuyMarkup`, `diamondBuyPriceCr`, `diamondSellPriceCr`, `buybackCommission` (`1.35` default = `2.35x` total cost), `upgradePriceMultiplier`, `maxAttributeSaleFraction` (`0.80`), `contractRefreshTicks` (`24000`), `marketEventDurationTicks` (`12000`), `blackMarketMinDeals`, `blackMarketMinNetTurnover`, toast toggles, and custom per-item commodity / asset / upgrade prices.
- Changes can be hot-reloaded in-game with `/market reload`.

---

## Building from Source

```bash
git clone https://github.com/nexWay2040/playermarket-minecraftmod.git
cd playermarket-minecraftmod
./gradlew build
```

Compiled jars are output to `build/libs/` (`playermarket-1.0.0.jar` and `playermarket-1.0.0-sources.jar`).

---

## Asset Attributions & License

- **Code & Original Assets**: Released under **CC0-1.0** (see [`LICENSE`](LICENSE)).
- **UI Icons, Sprites & Audio Credits**: Detailed in [`docs/assets.md`](docs/assets.md), incorporating open-source MIT and CC0-1.0 assets from `fanquanpp/pixel-vault`, `GUI 77`, `Kenney RPG Extension`, `Gold Treasure Icons 16x16` / `Basic RPG Icons`, `Mekanism` dark UI inspiration, and CC0 `Jumpscare 1` (`#712416` by `zombyKlr` on Freesound).
