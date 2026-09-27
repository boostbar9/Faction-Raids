"""Original low-poly utility relics; native materials, no borrowed artwork."""
from pathlib import Path
import json
from preview_olympian_weapons import draw
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'src/main/resources/assets/siegeoverhaul/models/item'
MODELS={}

def box(elements, start, end, material):
    elements.append({'from':start,'to':end,'faces':{side:{'texture':'#'+material,'uv':[4,4,12,12]}
        for side in ('north','south','east','west','up','down')}})

for name in ('forge_ember','owl_seal','sun_laurel'):
    elements=[]
    if name=='forge_ember':
        # A glowing coal held in a small, open copper forge cage.
        box(elements,[5,3,6],[11,4,10],'trim')
        box(elements,[5,11,6],[11,12,10],'trim')
        for x in (4,11):box(elements,[x,4,6],[x+1,11,10],'metal')
        box(elements,[6,4,6.5],[10,9,9.5],'gem')
        box(elements,[7,9,7],[9,11,9],'gem')
        box(elements,[7,2,7],[9,3,9],'metal')
        colors=('netherite_block','orange_concrete','copper_block')
    elif name=='owl_seal':
        # Broad owl face, split ears, pale eye disks and a pointed gold beak.
        box(elements,[4,3,7],[12,11,9],'metal')
        box(elements,[3,5,7],[4,10,9],'trim');box(elements,[12,5,7],[13,10,9],'trim')
        box(elements,[4,11,7],[6,13,9],'trim');box(elements,[10,11,7],[12,13,9],'trim')
        for x in (5,9):
            box(elements,[x,7,9],[x+2,10,9.5],'edge')
            box(elements,[x+.5,8,9.5],[x+1.5,9,10],'gem')
        box(elements,[7.5,5.5,9],[8.5,7.5,10],'trim')
        box(elements,[6,2,7],[10,3,9],'trim')
        colors=('blue_concrete','cyan_concrete','gold_block')
    else:
        # Sun disk framed by two gold laurel branches, open at the crown.
        box(elements,[6,5,7],[10,9,9.6],'gem')
        box(elements,[5,6,7],[11,8,8.5],'trim')
        box(elements,[7,4,7],[9,10,8.5],'trim')
        box(elements,[7,6,9.6],[9,8,10],'trim')
        for x in (3,11):
            box(elements,[x,4,7],[x+2,10,9],'metal')
            box(elements,[x-1,7,7],[x+1,9,9],'trim')
            box(elements,[x,10,7],[x+2,12,9],'trim')
        box(elements,[5,2,7],[11,4,9],'trim')
        colors=('copper_block','orange_concrete','gold_block')
    display={
        'gui':{'rotation':[10,-20,0],'translation':[0,.5,0],'scale':[.95,.95,.95]},
        'firstperson_righthand':{'rotation':[0,-25,0],'translation':[1,1,0],'scale':[.65]*3},
        'firstperson_lefthand':{'rotation':[0,25,0],'translation':[1,1,0],'scale':[.65]*3},
        'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.55]*3},
        'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.55]*3},
        'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.4]*3},
        'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[.8]*3}}
    model={'gui_light':'front','textures':{'metal':'minecraft:block/'+colors[0],
        'gem':'minecraft:block/'+colors[1],'trim':'minecraft:block/'+colors[2],
        'edge':'minecraft:block/iron_block','particle':'minecraft:block/'+colors[1]},
        'display':display,'elements':elements}
    (OUT/(name+'.json')).write_text(json.dumps(model,separators=(',',':'))+'\n')
    MODELS[name]=model

svg=['<svg xmlns="http://www.w3.org/2000/svg" width="960" height="430" viewBox="0 0 960 430">',
     '<rect width="960" height="430" fill="#111924"/>',
     '<g font-family="sans-serif" fill="#eee3cb">',
     '<text x="32" y="42" font-size="24">OLYMPIAN UTILITY RELICS</text>',
     '<text x="32" y="69" font-size="14" fill="#a3afbd">Actual model geometry · approximate materials · not a Minecraft screenshot</text>']
for i,(name,title,purpose) in enumerate([
    ('forge_ember','FORGE EMBER','Repair gear in your other hand'),
    ('owl_seal','OWL SEAL','Reveal nearby siege enemies'),
    ('sun_laurel','SUN LAUREL','Cleanse five combat afflictions')]):
    x=160+i*320
    svg.append(f'<rect x="{x-140}" y="94" width="280" height="306" rx="10" fill="#1b2838"/>')
    svg.append(draw(MODELS[name],x,220,240))
    svg.append(f'<text x="{x}" y="350" text-anchor="middle" font-size="19">{title}</text>')
    svg.append(f'<text x="{x}" y="378" text-anchor="middle" font-size="13" fill="#a3afbd">{purpose}</text>')
svg.append('</g></svg>')
(ROOT/'docs/images/olympian-relic-models.svg').write_text(''.join(svg))
print('Generated three relic models and contact sheet')
