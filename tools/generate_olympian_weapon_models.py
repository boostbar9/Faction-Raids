# Original Siege Overhaul geometry; vanilla Minecraft texture references only.
from pathlib import Path
import json, math
from itertools import product
out=Path(__file__).resolve().parents[1]/'src/main/resources/assets/siegeoverhaul/models/item/olympian';out.mkdir(parents=True,exist_ok=True)
# Quiet material patches, not complete tiled building-block faces.
palettes={
 'ares':('netherite_block','red_concrete','gold_block'),
 'athena':('iron_block','blue_concrete','gold_block'),
 'artemis':('iron_block','cyan_concrete','amethyst_block'),
 'hephaestus':('netherite_block','orange_concrete','copper_block'),
 'poseidon':('dark_prismarine','light_blue_concrete','iron_block'),
 'zeus':('iron_block','light_blue_concrete','gold_block'),
 'apollo':('gold_block','orange_concrete','iron_block'),
 'demeter':('copper_block','green_concrete','gold_block')}


def rotate(point, rotation):
 """Minecraft ItemTransform uses rotationXYZ: Z, then Y, then X on a vector."""
 x,y,z=point
 rx,ry,rz=map(math.radians,rotation)
 x,y=x*math.cos(rz)-y*math.sin(rz),x*math.sin(rz)+y*math.cos(rz)
 x,z=x*math.cos(ry)+z*math.sin(ry),-x*math.sin(ry)+z*math.cos(ry)
 y,z=y*math.cos(rx)-z*math.sin(rx),y*math.sin(rx)+z*math.cos(rx)
 return x,y,z


def corners(element):
 for point in product(*zip(element['from'],element['to'])):
  if 'rotation' in element:
   r=element['rotation'];origin=r['origin'];angles=[0,0,0]
   angles['xyz'.index(r['axis'])]=r['angle']
   point=tuple(v+o for v,o in zip(rotate([v-o for v,o in zip(point,origin)],angles),origin))
  yield point


def fit_icon(models):
 # One transform for every draw state prevents inventory-size/position popping.
 rotation=[20,-30,-35]
 points=[rotate([v-8 for v in point],rotation) for model in models for e in model['elements'] for point in corners(e)]
 low=[min(p[a] for p in points) for a in range(3)]
 high=[max(p[a] for p in points) for a in range(3)]
 scale=min(0.9,14/max(high[a]-low[a] for a in (0,1)))
 return {'rotation':rotation,'translation':[round(-(lo+hi)*scale/2,5) for lo,hi in zip(low,high)],'scale':[round(scale,5)]*3}

def held_profile(shape, left=False):
 # Anchor the actual grip, not the center of the icon, at a consistent palm point.
 family=shape.split('_')[0]
 grip=(8,8,8) if family=='bow' else (8,4.25,8)
 scale=.65 if family in ('staff','spear','hook') else .75
 rotation=[0,-90,0] if family=='bow' else [90,0,0]
 offset=rotate([(v-8)*scale for v in grip],rotation)
 translation=[round(t-v,5) for t,v in zip((0,2,0),offset)]
 if left:
  rotation=[rotation[0],-rotation[1],-rotation[2]]
  translation[0]=-translation[0]
 return {'rotation':rotation,'translation':translation,'scale':[scale]*3}

models={}

shapes=['blade','spear','staff','bow','bow_1','bow_2','bow_3','crossbow','crossbow_1','crossbow_2','crossbow_3','crossbow_loaded','crossbow_rocket','pick','hoe','hook']
for patron,palette in palettes.items():
 for shape in shapes:
  elems=[]
  def box(a,b,t):elems.append({'from':a,'to':b,'faces':{f:{'texture':'#'+t,'uv':[4,4,12,12]} for f in ['north','south','east','west','up','down']}})
  box([7.25,1.5,7.25],[8.75,7,8.75],'grip')
  box([7,1,7],[9,2,9],'trim')
  # Broad grip bands remain legible at inventory size; no subpixel engraving.
  for y in (3,5):box([7.15,y,7.15],[8.85,y+.4,8.85],'metal')
  if shape=='blade':
   # Stepped point, bright cutting edges and a recessed colored fuller.
   box([6.25,7,7.35],[9.75,15,8.65],'edge')
   box([6.8,7,7.1],[9.2,14.5,8.9],'metal')
   box([6.8,15,7.35],[9.2,16.5,8.65],'edge')
   box([7.4,16.5,7.5],[8.6,18,8.5],'edge')
   box([7.65,8,6.95],[8.35,14,9.05],'gem')
   box([4.5,6,6.75],[11.5,7.25,9.25],'trim')
   if patron=='ares':
    for x in (4.5,10.5):box([x,7,7],[x+1,9,9],'metal')
   elif patron=='athena':
    for x in (4,10):box([x,5.5,7],[x+2,6.5,9],'edge')
   elif patron=='artemis':
    box([3.5,7,7],[5,9,9],'trim');box([11,7,7],[12.5,9,9],'trim')
   elif patron=='hephaestus':
    box([4,5.5,6.5],[6,8,9.5],'metal');box([10,5.5,6.5],[12,8,9.5],'metal')
   elif patron=='poseidon':
    for x in (4.5,7.5,10.5):box([x,7,7],[x+1,9.5,9],'trim')
   elif patron=='zeus':
    box([4,7,7],[6,8,9],'trim');box([10,5,7],[12,6.5,9],'trim')
   elif patron=='apollo':
    box([5,7,7],[6,9,9],'trim');box([10,7,7],[11,9,9],'trim')
    box([7.5,5,6.5],[8.5,8,9.5],'trim')
   else:
    box([4,7,7],[6.5,8,9],'gem');box([9.5,7,7],[12,8,9],'gem')
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
   elif patron=='zeus':
    box([8,18,7],[11,20,9],'trim');box([6,20,7],[10,22,9],'gem');box([5,22,7],[8,24,9],'trim')
   elif patron=='apollo':
    box([5,20,7],[11,22,9],'trim');box([7,18,7],[9,24,9],'trim');box([6.5,20,6.5],[9.5,22,9.5],'gem')
   elif patron=='athena':
    box([5,19,7],[11,22,9],'metal');box([5,22,7],[6.5,24,9],'trim');box([9.5,22,7],[11,24,9],'trim')
    box([6,20,6.7],[7.5,21,9.3],'gem');box([8.5,20,6.7],[10,21,9.3],'gem')
   elif patron=='demeter':
    box([5,19,7],[8,21,9],'gem');box([8,21,7],[11,23,9],'gem');box([7.5,19,6.8],[8.5,24,9.2],'trim')
   else:
    box([5,19,7],[11,22,9],'metal');box([5,22,7],[6.5,24,9],'trim');box([9.5,22,7],[11,24,9],'trim')
    box([7,19,6.8],[9,22,9.2],'gem')
  elif shape.startswith('bow'):
   stage=int(shape[-1]) if shape[-1].isdigit() else 0
   box([7,6,7],[9,10,9],'grip');box([9,10,7.4],[10.5,14,8.6],'metal');box([9,2,7.4],[10.5,6,8.6],'metal');box([10,13,7.4],[12.5,15,8.6],'trim');box([10,1,7.4],[12.5,3,8.6],'trim')
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
  display['thirdperson_righthand']=held_profile(shape)
  display['thirdperson_lefthand']=held_profile(shape,True)
  model={'gui_light':'front','textures':{'metal':'minecraft:block/'+palette[0],'gem':'minecraft:block/'+palette[1],'trim':'minecraft:block/'+palette[2],'edge':'minecraft:block/iron_block','grip':'minecraft:block/black_concrete','string':'minecraft:block/white_concrete','particle':'minecraft:block/'+palette[0]},'display':display,'elements':elems}
  models[patron+'_'+shape]=model
# Share GUI framing per weapon family, including all animation variants.
for patron in palettes:
 for family in ('blade','spear','staff','bow','crossbow','pick','hoe','hook'):
  group=[model for key,model in models.items() if key==patron+'_'+family or key.startswith(patron+'_'+family+'_')]
  display=fit_icon(group)
  for model in group:model['display']['gui']=display
for name,model in models.items():
 (out/(name+'.json')).write_text(json.dumps(model,separators=(',',':'))+'\n')
print('Original models:',len(models))
