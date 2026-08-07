"""Render the Play listing graphics from the same geometry as the launcher icon.

Kept as a script rather than hand-drawn art so the store icon and the icon on the phone can
never drift apart: both are the four-cell grid, 34 wide with a 4 gap, on #1E88E5.
"""
from PIL import Image, ImageDraw, ImageFont

BG = (30, 136, 229)          # #1E88E5, ic_launcher_background
ON = (255, 255, 255)
OFF = (120, 184, 239)        # #66FFFFFF composited over BG
SS = 4                       # supersample factor, downsampled at the end

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def draw_mark(draw, x, y, size):
    """The four-cell grid, filling a `size` box whose top-left is (x, y)."""
    u = size / 72.0          # the mark is 72 units across in the vector
    cell = 34 * u
    gap = 4 * u
    r = 4 * u
    for col in range(2):
        for row in range(2):
            cx = x + col * (cell + gap)
            cy = y + row * (cell + gap)
            box = (cx, cy, cx + cell, cy + cell)
            if col == 1 and row == 1:
                # The "off" day: a ring, drawn as outer shape then inner knocked back to BG.
                draw.rounded_rectangle(box, radius=r, fill=OFF)
                inset = 6 * u
                draw.rectangle(
                    (cx + inset, cy + inset, cx + cell - inset, cy + cell - inset), fill=BG
                )
            else:
                draw.rounded_rectangle(box, radius=r, fill=ON)


def store_icon(path):
    """512x512. Full bleed; the mark keeps its 72/108 proportion from the adaptive icon."""
    n = 512 * SS
    img = Image.new("RGB", (n, n), BG)
    d = ImageDraw.Draw(img)
    inset = n * 18 / 108.0
    draw_mark(d, inset, inset, n - 2 * inset)
    img.resize((512, 512), Image.LANCZOS).save(path)
    return path


def feature_graphic(path, headline, sub, rtl):
    """1024x500. Mark on one side, wordmark and one line of copy on the other."""
    w, h = 1024 * SS, 500 * SS
    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)

    mark = 232 * SS
    margin = 84 * SS
    # Mark leads on the side the language reads from.
    mark_x = w - margin - mark if rtl else margin
    draw_mark(d, mark_x, (h - mark) // 2, mark)

    title_f = ImageFont.truetype(FONT_BOLD, 96 * SS)
    sub_f = ImageFont.truetype(FONT, 44 * SS)
    anchor = "ra" if rtl else "la"
    tx = mark_x - 64 * SS if rtl else mark_x + mark + 64 * SS

    d.text((tx, h // 2 - 96 * SS), headline, font=title_f, fill=ON, anchor=anchor,
           direction="rtl" if rtl else "ltr")
    d.text((tx, h // 2 + 24 * SS), sub, font=sub_f, fill=OFF, anchor=anchor,
           direction="rtl" if rtl else "ltr")

    img.resize((1024, 500), Image.LANCZOS).save(path)
    return path


if __name__ == "__main__":
    import sys
    out = sys.argv[1].rstrip("/")
    print(store_icon(f"{out}/play-icon-512.png"))
    print(feature_graphic(f"{out}/play-feature-he.png",
                          "Shiftly", "הסידור שלך, חודש קדימה", rtl=True))
    print(feature_graphic(f"{out}/play-feature-en.png",
                          "Shiftly", "Your rota, months ahead", rtl=False))
