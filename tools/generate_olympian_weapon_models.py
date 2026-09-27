# Original Siege Overhaul geometry; vanilla Minecraft texture references only.
from pathlib import Path
import json, math
out=Path(__file__).resolve().parents[1]/'src/main/resources/assets/siegeoverhaul/models/item/olympian';out.mkdir(parents=True,exist_ok=True)
palettes={'ares':('netherite_block','redstone_block','gold_block'),'athena':('iron_block','lapis_block','gold_block'),'artemis':('iron_block','dark_prismarine','amethyst_block'),'hephaestus':('netherite_block','magma','copper_block'),'poseidon':('prismarine_bricks','dark_prismarine','gold_block'),'zeus':('gold_block','lapis_block','iron_block'),'apollo':('gold_block','orange_concrete','iron_block'),'demeter':('copper_block','moss_block','gold_block')}
shapes=['blade','spear','staff','bow','bow_1','bow_2','bow_3','crossbow','crossbow_1','crossbow_2','crossbow_3','crossbow_loaded','crossbow_rocket','pick','hoe','hook']
for patron,palette in palettes.items():
 for shape in shapes:
  elems=[]
  def box(a,b,t):elems.append({'from':a,'to':b,'faces':{f:{'texture':'#'+t} for f in ['north','south','east','west','up','down']}})
  box([7,1,7],[9,7,9],'grip')
  box([6.5,1,6.5],[9.5,2.5,9.5],'trim')
  if shape=='blade':
   box([6,6,7],[10,15,9],'metal');box([7,15,7],[9,17,9],'trim');box([4,6,6.5],[12,7.5,9.5],'trim');box([7.25,8,6.8],[8.75,14,9.2],'gem')
  elif shape in ['staff','spear']:
   box([7.5,6,7.5],[8.5,19,8.5],'grip');box([6,15,6],[10,16,10],'trim');box([7,17,7],[9,23,9],'metal')
   if shape=='spear':
    box([6,18,7],[10,21,9],'metal');box([7.5,21,7.5],[8.5,25,8.5],'trim')
   elif patron=='poseidon':
    box([4,18,7],[12,19,9],'trim');box([4,18,7],[5,22,9],'metal');box([11,18,7],[12,22,9],'metal')
   elif patron=='hephaestus':
    box([4,19,6],[12,22,10],'metal');box([6,19.5,5.8],[10,21.5,10.2],'gem')
   elif patron=='artemis':
    box([4,18,7],[6,22,9],'metal');box([5,21,7],[10,23,9],'metal');box([9,20,7],[11,22,9],'trim')
   else:
    box([5,18,7],[11,19,9],'trim');box([6,19,6],[10,22,10],'gem');box([7,22,7],[9,24,9],'metal')
  elif shape.startswith('bow'):
   stage=int(shape[-1]) if shape[-1].isdigit() else 0
   box([7,6,7],[9,10,9],'grip');box([9,10,7],[11,14,9],'metal');box([9,2,7],[11,6,9],'metal');box([10,13,7],[13,16,9],'trim');box([10,0,7],[13,3,9],'trim')
   angle=0 if stage<2 else 22.5 if stage==2 else 45
   x=12-6*math.tan(math.radians(angle))
   length=6/math.cos(math.radians(angle))
   for y,rotation in [(5,angle),(11,-angle)]:
    center=(12+x)/2
    box([center-.12,y-length/2,7.8],[center+.12,y+length/2,8.2],'string')
    elems[-1]['rotation']={'origin':[center,y,8],'axis':'z','angle':rotation}
   if stage:box([x,7.8,7.8],[19,8.2,8.2],'grip');box([18,7.4,7.4],[20,8.6,8.6],'metal')
   box([8.8,7,6.5],[10,9,9.5],'gem')
  elif shape.startswith('crossbow'):
   loaded=shape in ['crossbow_loaded','crossbow_rocket']
   stage=3 if loaded else int(shape[-1]) if shape[-1].isdigit() else 0
   box([6.5,5,2],[9.5,7,16],'grip');box([1,6,11],[15,8,13],'metal');box([1,5,9],[3,7,12],'trim');box([13,5,9],[15,7,12],'trim');box([7,7,8],[9,8,11],'gem')
   # Two rotating string halves keep their outer ends attached to the limb tips.
   angle=0 if stage<2 else 22.5 if stage==2 else 45
   pulled_z=9-6*math.tan(math.radians(angle))
   length=6/math.cos(math.radians(angle))
   for x,rotation in [(5,angle),(11,-angle)]:
    center_z=(9+pulled_z)/2
    box([x-length/2,6.5,center_z-.125],[x+length/2,6.75,center_z+.125],'string')
    elems[-1]['rotation']={'origin':[x,6.625,center_z],'axis':'y','angle':rotation}
   if shape=='crossbow_loaded':
    box([7.7,8,3],[8.3,8.6,18],'grip');box([7,7.5,17],[9,9,19],'metal')
   elif shape=='crossbow_rocket':
    box([7.7,8,3],[8.3,8.6,16],'grip');box([6.8,7.3,12],[9.2,9.7,18],'string')
    box([6.6,7.1,15],[9.4,9.9,16],'gem');box([7.2,7.7,18],[8.8,9.3,20],'gem')
  elif shape=='pick':
   box([7.5,6,7.5],[8.5,14,8.5],'grip');box([2,13,7],[14,15,9],'metal');box([1,11,7],[3,14,9],'trim');box([13,11,7],[15,14,9],'trim');box([7,13,6.5],[9,15,9.5],'gem')
  elif shape=='hoe':
   box([7.5,6,7.5],[8.5,15,8.5],'grip');box([2,14,7],[10,16,9],'metal');box([2,11,7],[4,15,9],'trim');box([7,14,6.5],[9,16,9.5],'gem')
  else:
   box([7.5,6,7.5],[8.5,20,8.5],'grip');box([7,17,7],[9,19,9],'trim');box([8,19,7.8],[12,19.2,8.2],'string');box([11.8,8,7.8],[12,19,8.2],'string');box([10,7,7],[12,9,9],'metal')
  display={'thirdperson_righthand':{'rotation':[0,-90,55],'translation':[0,4,.5],'scale':[.65,.65,.65]},'thirdperson_lefthand':{'rotation':[0,90,-55],'translation':[0,4,.5],'scale':[.65,.65,.65]},'firstperson_righthand':{'rotation':[0,-80,15],'translation':[1,1,0],'scale':[.65,.65,.65]},'firstperson_lefthand':{'rotation':[0,80,-15],'translation':[1,1,0],'scale':[.65,.65,.65]},'gui':{'rotation':[20,-35,-30],'translation':[0,-1,0],'scale':[.65,.65,.65]},'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.4,.4,.4]},'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[.65,.65,.65]}}
  if shape.startswith('crossbow'):
   # Barrel runs along Z; align it with the hand instead of using the blade tilt.
   for hand in ['righthand','lefthand']:
    display['thirdperson_'+hand]={'rotation':[90,0,0],'translation':[0,2,0],'scale':[.65,.65,.65]}
    display['firstperson_'+hand]={'rotation':[0,180,0],'translation':[0,1,0],'scale':[.65,.65,.65]}
  model={'gui_light':'front','textures':{'metal':'minecraft:block/'+palette[0],'gem':'minecraft:block/'+palette[1],'trim':'minecraft:block/'+palette[2],'grip':'minecraft:block/dark_oak_planks','string':'minecraft:block/white_wool','particle':'minecraft:block/'+palette[0]},'display':display,'elements':elems}
  (out/(patron+'_'+shape+'.json')).write_text(json.dumps(model,separators=(',',':'))+'\n')
print('Original models:',len(list(out.glob('*.json'))))
