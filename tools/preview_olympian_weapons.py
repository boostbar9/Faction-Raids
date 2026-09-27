"""Render actual JSON geometry as an SVG contact sheet (not a Minecraft screenshot).

Uses model GUI transforms, approximate material colors and painter-sorted faces.
It does not simulate Minecraft texture sampling, enchantment glint or lighting.
No raster assets or third-party artwork are generated or bundled.
"""
from pathlib import Path
from itertools import product
import json
import math

ROOT = Path(__file__).resolve().parents[1]
COLORS = {
    'netherite_block': '#504951', 'iron_block': '#cdd7dd', 'gold_block': '#d9ad4a',
    'red_concrete': '#af343c', 'blue_concrete': '#394dac', 'cyan_concrete': '#308a91',
    'amethyst_block': '#ac8bd3', 'orange_concrete': '#e88326', 'copper_block': '#b96e4e',
    'dark_prismarine': '#356e65', 'light_blue_concrete': '#51bfdf', 'green_concrete': '#628642',
    'black_concrete': '#29272c', 'white_concrete': '#e4e3db',
}
FACES = {'west': [0, 1, 3, 2], 'east': [4, 6, 7, 5], 'down': [0, 4, 5, 1],
         'up': [2, 3, 7, 6], 'north': [0, 2, 6, 4], 'south': [1, 5, 7, 3]}


def rotate(p, angles):
    x, y, z = p
    rx, ry, rz = map(math.radians, angles)
    x, y = x*math.cos(rz)-y*math.sin(rz), x*math.sin(rz)+y*math.cos(rz)
    x, z = x*math.cos(ry)+z*math.sin(ry), -x*math.sin(ry)+z*math.cos(ry)
    y, z = y*math.cos(rx)-z*math.sin(rx), y*math.sin(rx)+z*math.cos(rx)
    return x, y, z


def project(model, element, context):
    display = model['display'][context]
    points = []
    for p in product(*zip(element['from'], element['to'])):
        if 'rotation' in element:
            r = element['rotation']; angles = [0, 0, 0]
            angles['xyz'.index(r['axis'])] = r['angle']
            p = [v+o for v, o in zip(rotate([v-o for v, o in zip(p, r['origin'])], angles), r['origin'])]
        p = rotate([(v-8)*s for v, s in zip(p, display['scale'])], display['rotation'])
        points.append([v+t for v, t in zip(p, display['translation'])])
    return points


def draw(model, x, y, size, context='gui'):
    faces = []
    for element in model['elements']:
        points = project(model, element, context)
        for face, indices in FACES.items():
            vertices = [points[i] for i in indices]
            a, b, c = vertices[:3]
            u = [v-w for v, w in zip(b, a)]; v = [v-w for v, w in zip(c, a)]
            normal = [u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0]]
            if normal[2] <= 0:
                continue
            color = COLORS[model['textures'][element['faces'][face]['texture'][1:]].split('/')[-1]]
            brightness = .62 + .38*normal[2]/math.sqrt(sum(n*n for n in normal))
            color = '#' + ''.join(f'{int(int(color[i:i+2],16)*brightness):02x}' for i in (1,3,5))
            coords = ' '.join(f'{x+p[0]*size/16:.2f},{y-p[1]*size/16:.2f}' for p in vertices)
            faces.append((sum(p[2] for p in vertices)/4, f'<polygon points="{coords}" fill="{color}"/>'))
    return ''.join(face for _, face in sorted(faces, key=lambda f: f[0]))


def main():
    patrons = ['ares', 'athena', 'artemis', 'hephaestus', 'poseidon', 'zeus', 'apollo', 'demeter']
    shapes = ['blade', 'staff', 'spear', 'bow_3', 'crossbow_loaded', 'pick', 'hoe', 'hook']
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="1500" viewBox="0 0 1280 1500">',
           '<rect width="1280" height="1500" fill="#111924"/>',
           '<g fill="#eee3cb" font-family="sans-serif">',
           '<text x="40" y="48" font-size="28">OLYMPIAN ARMORY · MODEL STUDY</text>',
           '<text x="40" y="77" font-size="15" fill="#a3afbd">Actual model geometry and inventory transforms · approximate materials · not an in-game screenshot</text>']
    for col, patron in enumerate(patrons):
        svg.append(f'<text x="{col*150+115}" y="120" font-size="16" text-anchor="middle">{patron.title()}</text>')
    for row, shape in enumerate(shapes):
        for col, patron in enumerate(patrons):
            x, y = col*150+115, row*155+198
            model = json.loads((ROOT/f'src/main/resources/assets/siegeoverhaul/models/item/olympian/{patron}_{shape}.json').read_text())
            svg.append(f'<rect x="{x-69}" y="{y-64}" width="138" height="140" rx="8" fill="#1b2838"/>')
            svg.append(draw(model, x-10, y-3, 116))
            svg.append(draw(model, x+48, y+53, 24))
            if col == 0:
                svg.append(f'<text x="{x-65}" y="{y+70}" font-size="10" fill="#a3afbd">{shape.replace("_", " ").upper()}</text>')
    svg += ['<text x="40" y="1423" font-size="17">Clean metal edges · dark grips · restrained deity colors · shared icons and held models</text>',
            '<text x="40" y="1452" font-size="14" fill="#a3afbd">Small inset: 24 px inventory sample. Draw states share framing. Native held-item animation is preserved.</text>', '</g></svg>']
    output = ROOT/'docs/images/olympian-weapon-models.svg'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(''.join(svg))
    print(output)


if __name__ == '__main__':
    main()
