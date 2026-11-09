import math
import random
from PIL import Image, ImageDraw, ImageFilter

def generate_parchment_contract(width=400, height=280):
    random.seed(42)
    # Create base image RGBA
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Base parchment colors: warm cream / sepia aged paper
    base_r, base_g, base_b = 244, 234, 212

    # Draw rounded paper rectangle
    margin = 4
    for y in range(margin, height - margin):
        for x in range(margin, width - margin):
            # Distance from edge for vignette
            dist_x = min(x - margin, (width - margin) - x)
            dist_y = min(y - margin, (height - margin) - y)
            edge_dist = min(dist_x, dist_y)

            # Perlin-like noise simulation using sine waves & random
            noise = (
                math.sin(x * 0.08) * 4 +
                math.cos(y * 0.08) * 4 +
                math.sin((x + y) * 0.03) * 6 +
                random.uniform(-4, 4)
            )

            # Edge darkening / aging vignette
            edge_factor = min(1.0, edge_dist / 28.0)
            vignette = (1.0 - edge_factor) * -35

            r = int(max(0, min(255, base_r + noise + vignette)))
            g = int(max(0, min(255, base_g + noise * 0.9 + vignette * 1.1)))
            b = int(max(0, min(255, base_b + noise * 0.7 + vignette * 1.4)))

            img.putpixel((x, y), (r, g, b, 255))

    # Outer burnt deckled edge border
    border_draw = ImageDraw.Draw(img)
    # Outer dark rim
    border_draw.rectangle([margin, margin, width - margin - 1, height - margin - 1], outline=(90, 70, 48, 255), width=2)
    border_draw.rectangle([margin + 2, margin + 2, width - margin - 3, height - margin - 3], outline=(180, 150, 110, 180), width=1)
    # Inner ornamental border line
    inner_m = 12
    border_draw.rectangle([inner_m, inner_m, width - inner_m, height - inner_m], outline=(140, 115, 85, 200), width=1)
    border_draw.rectangle([inner_m + 2, inner_m + 2, width - inner_m - 2, height - inner_m - 2], outline=(195, 175, 145, 160), width=1)

    # Corner ornamental accents (L-shaped filigree)
    corner_len = 16
    for cx, cy in [(inner_m, inner_m), (width - inner_m, inner_m), (inner_m, height - inner_m), (width - inner_m, height - inner_m)]:
        dx = 1 if cx == inner_m else -1
        dy = 1 if cy == inner_m else -1
        border_draw.line([(cx, cy), (cx + dx * corner_len, cy)], fill=(110, 85, 55, 255), width=2)
        border_draw.line([(cx, cy), (cx, cy + dy * corner_len)], fill=(110, 85, 55, 255), width=2)

    # Header decorative separator bar
    head_y = 38
    border_draw.line([(inner_m + 8, head_y), (width - inner_m - 8, head_y)], fill=(140, 115, 85, 220), width=2)
    border_draw.line([(inner_m + 8, head_y + 2), (width - inner_m - 8, head_y + 2)], fill=(205, 185, 155, 180), width=1)

    # Footer separator bar
    foot_y = height - 52
    border_draw.line([(inner_m + 8, foot_y), (width - inner_m - 8, foot_y)], fill=(140, 115, 85, 220), width=2)
    border_draw.line([(inner_m + 8, foot_y + 2), (width - inner_m - 8, foot_y + 2)], fill=(205, 185, 155, 180), width=1)

    # Corporate Red Wax Seal in lower right (above footer)
    seal_cx = width - 48
    seal_cy = height - 76
    seal_r = 22

    # Melted outer wax rim (slightly irregular circle)
    for a in range(360):
        rad = math.radians(a)
        r_offset = math.sin(a * 7) * 2.5 + math.cos(a * 4) * 1.5
        for cur_r in range(seal_r + int(r_offset)):
            sx = int(seal_cx + cur_r * math.cos(rad))
            sy = int(seal_cy + cur_r * math.sin(rad))
            # 3D shading
            dist_from_center = cur_r / seal_r
            light = math.cos(rad - math.pi * 0.75) * 25 * (1 - dist_from_center)
            cr = int(max(0, min(255, 160 + light - dist_from_center * 40)))
            cg = int(max(0, min(255, 28 + light * 0.5 - dist_from_center * 15)))
            cb = int(max(0, min(255, 28 + light * 0.5 - dist_from_center * 15)))
            img.putpixel((sx, sy), (cr, cg, cb, 255))

    # Inner embossed seal circle with gold monogram / icon
    inner_seal_r = 14
    for a in range(360):
        rad = math.radians(a)
        sx = int(seal_cx + inner_seal_r * math.cos(rad))
        sy = int(seal_cy + inner_seal_r * math.sin(rad))
        border_draw.point((sx, sy), fill=(100, 15, 15, 255))

    # Embossed monogram 'M' (Market) in gold in the wax seal
    border_draw.text((seal_cx - 7, seal_cy - 9), "M", fill=(235, 195, 95, 255))

    # Save output texture in both main and client resources
    out_paths = [
        "PLAYER-MARKET/src/main/resources/assets/playermarket/textures/gui/parchment_contract.png",
        "PLAYER-MARKET/src/client/resources/assets/playermarket/textures/gui/parchment_contract.png"
    ]
    for p in out_paths:
        import os
        os.makedirs(os.path.dirname(p), exist_ok=True)
        img.save(p, "PNG")
        print(f"Generated texture: {p}")

if __name__ == "__main__":
    generate_parchment_contract()
