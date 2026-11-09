import struct
import zlib
import os

def create_png(width, height, pixels):
    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0) # filter type 0 (None)
        for x in range(width):
            r, g, b, a = pixels[y][x]
            raw_data.extend([r, g, b, a])
    
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    header = b'\x89PNG\r\n\x1a\n'
    ihdr = chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
    idat = chunk(b'IDAT', zlib.compress(bytes(raw_data)))
    iend = chunk(b'IEND', b'')
    return header + ihdr + idat + iend

# 1. Generate 16x16 crumpled_letter.png
# Old folded corporate letter, folded parchment with creases and corporate seal
EMPTY = (0, 0, 0, 0)
OUTLINE = (68, 56, 42, 255)
PARCHMENT_LIGHT = (235, 222, 195, 255)
PARCHMENT_MID = (212, 196, 168, 255)
PARCHMENT_DARK = (180, 162, 134, 255)
CREASE = (150, 130, 105, 255)
SEAL_RED = (168, 38, 38, 255)
SEAL_DARK = (112, 22, 22, 255)
INK = (82, 70, 58, 255)

letter_grid = [
    [EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY],
    [EMPTY, EMPTY, EMPTY, OUTLINE, OUTLINE, OUTLINE, OUTLINE, OUTLINE, OUTLINE, OUTLINE, OUTLINE, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY],
    [EMPTY, EMPTY, OUTLINE, PARCHMENT_LIGHT, PARCHMENT_LIGHT, PARCHMENT_LIGHT, PARCHMENT_LIGHT, PARCHMENT_LIGHT, PARCHMENT_LIGHT, PARCHMENT_LIGHT, PARCHMENT_MID, OUTLINE, EMPTY, EMPTY, EMPTY, EMPTY],
    [EMPTY, OUTLINE, PARCHMENT_LIGHT, PARCHMENT_LIGHT, INK, INK, INK, PARCHMENT_LIGHT, PARCHMENT_LIGHT, PARCHMENT_LIGHT, PARCHMENT_MID, PARCHMENT_MID, OUTLINE, EMPTY, EMPTY, EMPTY],
    [EMPTY, OUTLINE, PARCHMENT_LIGHT, PARCHMENT_LIGHT, PARCHMENT_LIGHT, PARCHMENT_LIGHT, PARCHMENT_LIGHT, PARCHMENT_LIGHT, INK, INK, PARCHMENT_MID, PARCHMENT_MID, OUTLINE, EMPTY, EMPTY, EMPTY],
    [EMPTY, OUTLINE, PARCHMENT_MID, CREASE, CREASE, CREASE, PARCHMENT_LIGHT, PARCHMENT_LIGHT, PARCHMENT_LIGHT, PARCHMENT_MID, PARCHMENT_MID, PARCHMENT_DARK, OUTLINE, EMPTY, EMPTY, EMPTY],
    [EMPTY, OUTLINE, PARCHMENT_MID, PARCHMENT_LIGHT, PARCHMENT_LIGHT, CREASE, PARCHMENT_LIGHT, SEAL_DARK, SEAL_RED, PARCHMENT_LIGHT, PARCHMENT_MID, PARCHMENT_DARK, OUTLINE, EMPTY, EMPTY, EMPTY],
    [EMPTY, OUTLINE, PARCHMENT_MID, INK, INK, PARCHMENT_LIGHT, CREASE, SEAL_RED, SEAL_RED, SEAL_DARK, PARCHMENT_MID, PARCHMENT_DARK, OUTLINE, EMPTY, EMPTY, EMPTY],
    [EMPTY, OUTLINE, PARCHMENT_MID, PARCHMENT_LIGHT, PARCHMENT_LIGHT, PARCHMENT_LIGHT, CREASE, SEAL_DARK, SEAL_RED, PARCHMENT_LIGHT, PARCHMENT_MID, PARCHMENT_DARK, OUTLINE, EMPTY, EMPTY, EMPTY],
    [EMPTY, OUTLINE, PARCHMENT_DARK, PARCHMENT_MID, PARCHMENT_LIGHT, PARCHMENT_LIGHT, PARCHMENT_LIGHT, CREASE, CREASE, PARCHMENT_MID, PARCHMENT_DARK, PARCHMENT_DARK, OUTLINE, EMPTY, EMPTY, EMPTY],
    [EMPTY, OUTLINE, PARCHMENT_DARK, INK, INK, INK, PARCHMENT_LIGHT, PARCHMENT_LIGHT, PARCHMENT_MID, CREASE, PARCHMENT_DARK, PARCHMENT_DARK, OUTLINE, EMPTY, EMPTY, EMPTY],
    [EMPTY, OUTLINE, PARCHMENT_DARK, PARCHMENT_DARK, PARCHMENT_MID, PARCHMENT_MID, PARCHMENT_MID, PARCHMENT_MID, PARCHMENT_DARK, PARCHMENT_DARK, CREASE, PARCHMENT_DARK, OUTLINE, EMPTY, EMPTY, EMPTY],
    [EMPTY, EMPTY, OUTLINE, PARCHMENT_DARK, PARCHMENT_DARK, PARCHMENT_DARK, PARCHMENT_DARK, PARCHMENT_DARK, PARCHMENT_DARK, PARCHMENT_DARK, PARCHMENT_DARK, OUTLINE, EMPTY, EMPTY, EMPTY, EMPTY],
    [EMPTY, EMPTY, EMPTY, OUTLINE, OUTLINE, OUTLINE, OUTLINE, OUTLINE, OUTLINE, OUTLINE, OUTLINE, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY],
    [EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY],
    [EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY]
]

letter_bytes = create_png(16, 16, letter_grid)
target_path = r"F:\AppCaches\Credit\PLAYER-MARKET\src\main\resources\assets\playermarket\textures\item\crumpled_letter.png"
os.makedirs(os.path.dirname(target_path), exist_ok=True)
with open(target_path, "wb") as f:
    f.write(letter_bytes)
print(f"Generated {target_path} ({len(letter_bytes)} bytes)")

# 2. Generate 64x64 leg_brace.png for player model layer
brace_grid = [[EMPTY for _ in range(64)] for _ in range(64)]

TITANIUM_DARK = (45, 52, 60, 255)
TITANIUM_MID = (78, 88, 100, 255)
TITANIUM_LIGHT = (140, 155, 175, 255)
STRAP_BLACK = (22, 26, 30, 255)
HINGE_GOLD = (212, 160, 23, 255)
LED_RED = (235, 45, 45, 255)
LED_GLOW = (255, 120, 120, 255)

# Fill Right Leg region: x: 0..16, y: 16..32 and outer layer x: 0..16, y: 32..48
for y in range(16, 32):
    for x in range(0, 16):
        # Upper and lower straps
        if y in (17, 18, 28, 29):
            brace_grid[y][x] = STRAP_BLACK
        # Vertical structural titanium rods on sides (x=0..3 right, x=8..11 left)
        elif x in (1, 2, 9, 10):
            brace_grid[y][x] = TITANIUM_MID if (y % 2 == 0) else TITANIUM_LIGHT
        # Knee joint at y=23..24
        elif y in (23, 24):
            if x in (0, 3, 8, 11):
                brace_grid[y][x] = HINGE_GOLD
            else:
                brace_grid[y][x] = TITANIUM_DARK
        # Status LED on front side (x=5..6, y=20..21)
        elif (x in (5, 6)) and (y in (20, 21)):
            brace_grid[y][x] = LED_RED if (x == 5 and y == 20) else LED_GLOW
        # Support mesh
        elif y in (19, 27) and (x % 2 == 0):
            brace_grid[y][x] = TITANIUM_DARK

brace_bytes = create_png(64, 64, brace_grid)
brace_path = r"F:\AppCaches\Credit\PLAYER-MARKET\src\client\resources\assets\playermarket\textures\entity\leg_brace.png"
os.makedirs(os.path.dirname(brace_path), exist_ok=True)
with open(brace_path, "wb") as f:
    f.write(brace_bytes)
print(f"Generated {brace_path} ({len(brace_bytes)} bytes)")
