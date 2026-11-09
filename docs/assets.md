# PLAYER MARKET — ASSET SPECIFICATION & LICENSING

## 1. Design System & Palette
- **Obsidian Black**: `#0B0E14` (Background panels, window frames)
- **Dark Graphite**: `#121620`, `#1A2232` (Card backings, headers, sidebars)
- **Emerald Green**: `#10B981`, `#059669`, `#34D399` (Primary accents, liquid CR values, active tabs)
- **Muted White**: `#F1F5F9`, `#E2E8F0` (Headings, primary typography)
- **Subtle Platinum**: `#94A3B8`, `#64748B` (Secondary text, contract descriptions, metadata)
- **Corporate Crimson**: `#EF4444`, `#B91C1C` (Blocked cards, audit warnings, debt penalties)

## 2. Textures & Visual Assets

### Custom Items
1. `textures/item/market_access_card.png`:
   - 16x16 original pixel art of the corporate Access Card.
   - Status: Original creation for Player Market. License: CC0 / Public Domain.
2. `textures/item/crumpled_letter.png`:
   - 16x16 original pixel art of a folded, weathered corporate dispatch with wax seal and creased edges.
   - Status: Original creation for Player Market. License: CC0 / Public Domain.

### Player Model Visual Alteration
1. `textures/entity/leg_brace.png`:
   - 64x64 custom entity model layer texture.
   - Represents an external corporate titanium mobility stabilizer with carbon straps, reinforced hinge, and status LED.
   - Applied to the player model via `LivingEntityRenderLayerRegistrationCallback` on `AvatarRenderer`.
   - Status: Original creation for Player Market. License: CC0 / Public Domain.

### GUI & Nine-Slice Sprites
- Located in `assets/playermarket/textures/gui/sprites/`
- Designed for compact, scalable UI rendering:
  - `panel.png`: Nine-sliced dark obsidian terminal frame.
  - `header.png` / `footer.png`: High-density graphite bars.
  - `card.png`, `card_hover.png`, `card_selected.png`: Rounded container tiles.
  - `btn_green.png`, `btn_blue.png`, `btn_amber.png`, `btn_gray.png` (with hover states).
  - Status: Original procedural/pixel sprites created for Player Market. License: CC0 / Public Domain.
