"""Original pixel skins using Minecraft's own handheld/bow/crossbow transforms.
No animation replacement and no geometry derived from building-block textures.
"""
from pathlib import Path
import json, math
from PIL import Image, ImageDraw
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/siegeoverhaul'
out=root/'models/item/olympian';tex=root/'textures/item/olympian'
tex.mkdir(parents=True,exist_ok=True)
palettes={
 'ares':('#748096','#e1524f','#d4aa58'),
 'athena':('#ccdbe5','#537bac','#e1bf65'),
 'artemis':('#c1dfed','#83c9d1','#aea0dc'),
 'hephaestus':('#7b8795','#ed8f44','#c39266'),
 'poseidon':('#82babc','#57cdda','#c9dbe2'),
 'zeus':('#d4e6ea','#84c8ff','#dbb656'),
 'apollo':('#eac773','#ed8b42','#fff0ba'),
 'demeter':('#b5be9b','#79b167','#d7ac6c')}
shapes=['blade','spear','staff','bow','bow_1','bow_2','bow_3','crossbow','crossbow_1','crossbow_2','crossbow_3','crossbow_loaded','crossbow_rocket','pick','hoe','hook']
for patron,(metal,gem,trim) in palettes.items():
 for shape in shapes:
  im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
  def bowpoint(p):
   x,y=p[0]-16,p[1]-16
   return (round(16+(-x-y)*.52),round(16+(x-y)*.52))
  def line(points,color,width=2):
   if family=='bow':points=[bowpoint(p) for p in points]
   d.line(points,fill=color,width=width)
  def bowbox(rect,color):
   x1,y1,x2,y2=rect
   d.polygon([bowpoint(p) for p in [(x1,y1),(x2,y1),(x2,y2),(x1,y2)]],fill=color)
  family=shape.split('_')[0]
  parent='handheld'
  # Diagonal silhouettes fit vanilla item/generated extrusion and hand anchors.
  if family in ('blade','spear','staff','pick','hoe','hook'):
   line([(4,28),(24,8)],'#202734',5);line([(5,27),(23,9)],'#694a3d',3)
   for x,y in [(7,25),(10,22)]:line([(x-1,y-1),(x+1,y+1)],trim,2)
   if family=='blade':
    d.polygon([(12,17),(23,6),(29,2),(27,9),(16,21)],fill='#243044')
    d.polygon([(14,17),(24,7),(27,5),(25,10),(16,19)],fill=metal)
    line([(15,17),(25,7)],'#f2f4e8',1);line([(16,18),(23,11)],gem,1)
    line([(10,15),(18,23)],'#29313c',4);line([(10,16),(17,23)],trim,2)
   elif family=='spear':
    d.polygon([(21,9),(27,2),(30,1),(29,8),(23,13)],fill=metal);line([(23,9),(28,3)],'#eef6f3',1)
    line([(19,8),(25,14)],trim,2)
   elif family=='staff':
    d.polygon([(19,3),(27,3),(29,10),(23,15),(17,10)],fill=trim)
    d.polygon([(22,4),(26,7),(23,12),(19,9)],fill=gem);d.point((22,6),fill='#ffffff')
   elif family in ('pick','hoe'):
    line([(10,7),(18,5),(25,10),(28,19 if family=='pick' else 12)],'#27333d',5)
    line([(10,7),(18,6),(25,11),(27,18 if family=='pick' else 12)],metal,3)
    line([(12,6),(18,5),(24,10)],trim,1)
   else:
    line([(24,8),(25,19),(22,25)],'#d5dfdf',1);line([(22,25),(19,25),(19,22)],metal,2)
   d.rectangle((3,27,5,29),fill=gem)
  elif family=='bow':
   parent='bow';stage=int(shape[-1]) if shape[-1].isdigit() else 0
   # Left-hand riser; string retracts with the three vanilla pull states.
   limb=[(20,2),(14,5),(10,11),(9,16),(10,21),(14,27),(20,30)]
   line(limb,'#253242',5);line(limb,metal,3)
   line([(20,2),(20+stage*3,16),(20,30)],'#e0d9c0',1)
   line([(10,13),(10,19)],'#654638',3)
   for y in (6,25): bowbox((13,y,15,y+1),trim)
   bowbox((8,15,10,17),gem)
   if stage:
    line([(5,16),(21+stage*3,16)],'#aa8355',1)
    d.polygon([bowpoint(p) for p in [(2,16),(6,14),(6,18)]],fill='#e4e7e8')
  else:
   parent='crossbow';stage=int(shape[-1]) if shape[-1].isdigit() else 3 if shape.endswith(('loaded','rocket')) else 0
   line([(7,27),(24,10)],'#253242',6);line([(7,26),(23,10)],'#875d43',4)
   line([(3,8),(9,7),(23,21),(24,28)],'#253242',5)
   line([(3,8),(9,8),(23,22),(24,27)],metal,3)
   line([(3,9),(15-stage,17+stage),(23,27)],'#dfd6bd',1)
   line([(10,11),(20,21)],trim,1);d.rectangle((17,13,19,15),fill=gem)
   if shape.endswith(('loaded','rocket')):
    line([(10,22),(27,5)],'#b09268',2)
    d.polygon([(26,3),(29,3),(29,6)],fill='#eb6450' if shape.endswith('rocket') else '#e9eef1')
  im.save(tex/(patron+'_'+shape+'.png'))
  model={'parent':'minecraft:item/'+parent,'textures':{'layer0':'siegeoverhaul:item/olympian/'+patron+'_'+shape}}
  # Parent bow/crossbow predicates must not redirect the skin back to vanilla.
  if parent in ('bow','crossbow'):model['overrides']=[]
  (out/(patron+'_'+shape+'.json')).write_text(json.dumps(model,indent=2)+'\n')
