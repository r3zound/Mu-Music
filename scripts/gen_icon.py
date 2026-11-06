"""
生成 Mu 音乐的启动图标。

产出：
  res/drawable/ic_launcher_background.xml   自适应图标背景（深色竖向渐变）
  res/drawable/ic_launcher_foreground.xml   自适应图标前景（5 根律动条）
  res/mipmap-*/ic_launcher.png              Android 8 以下的方形位图
  res/mipmap-*/ic_launcher_round.png        Android 8 以下的圆形位图

设计：深色底 + 5 根高低不一的圆角竖条（均衡器），
中间最高的一根用品牌红 #E5484D，其余用近白 #F2F4F8。
"""
import os
from PIL import Image, ImageDraw

import os
RES = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "res")

# ── 颜色 ──
BG_TOP = (26, 29, 40)
BG_BOTTOM = (13, 15, 22)
INK = (242, 244, 248)
BRAND = (229, 72, 77)

# ── 前景几何（108x108 画布，安全区中心 66x66）──
BARS = [
    # (x, 高, 颜色)  中心那根最高且是品牌红
    (23.5, 26, INK),
    (36.5, 38, INK),
    (49.5, 50, BRAND),
    (62.5, 34, INK),
    (75.5, 28, INK),
]
BAR_W = 9.0
BAR_R = 4.5
CENTER_Y = 54.0


def rounded_rect_path(x, y, w, h, r):
    """生成 SVG/VectorDrawable 用的圆角矩形 path（相对指令）。"""
    return (
        f"M{x + r},{y}"
        f"h{w - 2 * r}"
        f"a{r},{r} 0 0 1 {r},{r}"
        f"v{h - 2 * r}"
        f"a{r},{r} 0 0 1 {-r},{r}"
        f"h{-(w - 2 * r)}"
        f"a{r},{r} 0 0 1 {-r},{-r}"
        f"v{-(h - 2 * r)}"
        f"a{r},{r} 0 0 1 {r},{-r}"
        "z"
    )


def hexc(rgb):
    return "#%02X%02X%02X" % rgb


def write_foreground():
    paths = []
    for x, h, color in BARS:
        y = CENTER_Y - h / 2
        paths.append(
            f'    <path\n'
            f'        android:fillColor="{hexc(color)}"\n'
            f'        android:pathData="{rounded_rect_path(x, y, BAR_W, h, BAR_R)}" />'
        )
    xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="108"\n'
        '    android:viewportHeight="108">\n'
        + "\n".join(paths)
        + "\n</vector>\n"
    )
    with open(os.path.join(RES, "drawable/ic_launcher_foreground.xml"), "w", encoding="utf-8") as f:
        f.write(xml)
    print("写出 drawable/ic_launcher_foreground.xml")


def write_background():
    xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="108"\n'
        '    android:viewportHeight="108">\n'
        '    <path android:pathData="M0,0h108v108h-108z">\n'
        '        <aapt:attr name="android:fillColor">\n'
        '            <gradient\n'
        '                android:type="linear"\n'
        '                android:startX="0" android:startY="0"\n'
        '                android:endX="0" android:endY="108">\n'
        f'                <item android:offset="0" android:color="{hexc(BG_TOP)}" />\n'
        f'                <item android:offset="1" android:color="{hexc(BG_BOTTOM)}" />\n'
        '            </gradient>\n'
        '        </aapt:attr>\n'
        '    </path>\n'
        '</vector>\n'
    )
    with open(os.path.join(RES, "drawable/ic_launcher_background.xml"), "w", encoding="utf-8") as f:
        f.write(xml)
    print("写出 drawable/ic_launcher_background.xml")


def draw_bars(draw, unit, k=1.0):
    """
    画律动条。

    unit: 108 画布单位 → 像素 的换算系数
    k:    设计缩放（相对 108 画布），围绕中心 (54,54) 缩放
    """
    for x, h, color in BARS:
        px = ((x - 54) * k + 54) * unit
        pw = BAR_W * k * unit
        ph = h * k * unit
        py = (CENTER_Y - h / 2) * k * unit
        r = BAR_R * k * unit
        draw.rounded_rectangle([px, py, px + pw, py + ph], radius=r, fill=color)


def write_legacy_pngs():
    """Android 8 以下的 PNG：图形放大 1.3 倍，四周留白更少。"""
    densities = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }
    for density, size in densities.items():
        folder = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        unit = size / 108.0

        # 方形图标：圆角背景
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        grad = Image.new("RGBA", (1, size))
        gd = ImageDraw.Draw(grad)
        for y in range(size):
            t = y / max(1, size - 1)
            gd.point(
                (0, y),
                fill=tuple(
                    int(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t) for i in range(3)
                ) + (255,),
            )
        img.paste(grad.resize((size, size)), (0, 0))
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=255)
        img.putalpha(mask)

        draw = ImageDraw.Draw(img)
        # 旧版图标没有自适应图标的安全区，图形按 108 画布放大 1.42 倍更饱满
        draw_bars(draw, unit, 1.42)
        img.save(os.path.join(folder, "ic_launcher.png"))

        # 圆形图标
        img2 = img.copy()
        mask2 = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask2).ellipse([0, 0, size - 1, size - 1], fill=255)
        img2.putalpha(mask2)
        img2.save(os.path.join(folder, "ic_launcher_round.png"))
        print(f"写出 mipmap-{density}/ic_launcher.png + round.png ({size}x{size})")


if __name__ == "__main__":
    write_foreground()
    write_background()
    write_legacy_pngs()
    print("完成")
