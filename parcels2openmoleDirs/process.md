Pour créer les répertoires de parcelles qui vont servir à alimenter les jobs lancés sur openmole, on part d'une base de données postgres qui contient une table (**parcelles_rulez**) associant à chaque parcelle ses règles et son idblock (correspondant à un répertoire).

De cette table, on génère une arborescence de répertoires regroupant des parcelles dans un fichier `shapefile`, correspondant à un ilôt urbain.

Chaque répertoire a pour nom son numéro de block (**idblock**) qui identifie l'ilôt.

On décrit ici le processus pour créer cette table et générer les répertoires de parcelles.

***

* [Créer la table parcelles_rulez en base de données](#parcelles_rulez)
* [Générer les répertoires de parcelles regroupées par idblock](#shapeWriter)

***



# Créer **parcelles_rulez** à partir du shapefile des parcelles

<a id="parcelles_rulez"/>

## Import des parcelles dans une table (à refaire si on change le partitionnement en idblocks)

```sh
shp2pgsql parcels public.parcels | psql -h localhost -d imrandb -U imrandb
```

Cette table a la structure suivante :
| gid | idpar | idblock | geom |

## Import des règles dans la base (dump disponible **regles_fixed**, plus à refaire normalement)
Idem que précédemment, à coup de shp2pgsql, puis on fixe les problèmes topologiques avec une requête sql, et on ajoute un index sur la géométrie.

## Import des tables mos et amenagement (dumps disponibles **mos_fixed** et **amenagement_fixed**, plus à refaire normalement)
Idem que précédemment, à coup de shp2pgsql, puis on fixe les problèmes topologiques avec une requête sql, et on ajoute un index sur la géométrie.

## Création de la table **parcelles_rulez** liant les parcelles aux règles (à partir de **parcels** et **regles_fixed**): 

```sql
create table parcelles_rulez as (
	SELECT distinct on (p.gid) p.gid pid, r.gid rid,
	idpar,
	idblock,
	libelle_zo,
	date_dul_1 date_dul,
	libelle_de,
	libelle__1,
	cast(b1_fonct as integer) FONCTIONS,
	cast(b1_top_zac as integer) TOP_ZAC,
	b2_top_zac TOP_ZAC2,
	cast(b1_bande as integer) BANDE1,
	cast(b2_t_bande as integer) TYP_BANDE2,
	b2_bande BANDE2,
	cast(b1_t_bande as integer) TYP_BANDE1,
	b1_art_5 ART_51,
	b1_art_6 ART_61,
	cast(b1_art_71 as integer) ART_711,
	b1_art_72 ART_721,
	b1_art_73 ART_731,
	cast(b1_art_74 as integer) ART_741,
	b1_art_8 ART_81,
	b1_art_9 ART_91,
	cast(b1_art_10t as integer) ART_10_TOP,
	b1_art_10 ART_101,
	b1_art_12 ART_121,
	b1_art_13 ART_131,
	b1_art_14 ART_141,
	b2_art_5 ART_52,
	b2_art_6 ART_62,
	cast(b2_art_71 as integer) ART_712,
	b2_art_72 ART_722,
	b2_art_73 ART_732,
	cast(b2_art_74 as integer) ART_742,
	b2_art_8 ART_82,
	b2_art_9 ART_92,
	cast(b2_art_10t as integer) ART_10_T_1,
	b2_art_10 ART_102,
	b2_art_12 ART_122,
	b2_art_13 ART_132,
	b2_art_14 ART_142,
	cast (insee as integer) INSEE,
	object_id,
	dep,
	annee,
	statut_dul,
	b1_haut_m,
	b2_haut_m,
	b1_haut_mt,
	b1_art_9_t,
	cast(b1_zon_cor as integer) CORRECTION,
	cast(b2_fonct as integer) FONCTIONS2,
	b2_zon_cor,
	1 AS ZONAGE_COH,
	1 as simul,    
	st_area(ST_INTERSECTION(r.geom, p.geom)) as aire_intersectee,
	p.geom
	FROM regles_fixed r, parcels p
	WHERE ST_Intersects(r.geom, p.geom)
	ORDER BY pid, aire_intersectee DESC
)
```

## Ajout d'un champs mos

```sql
ALTER TABLE public.parcelles_rulez
   ADD COLUMN mos2012 integer;

update parcelles_rulez
set mos2012 = 0;
```

## Mettre à jour le champs mos avec la table **mos_fixed**

```sql
update parcelles_rulez p
set mos2012 = (
		SELECT mos2012 from mos_fixed m
		where st_intersects(p.geom, m.geom) and st_area(st_intersection(p.geom, m.geom)) > 1
		ORDER BY st_area(st_intersection(p.geom, m.geom)) DESC
		LIMIT 1
		)
```

## Mettre à 0 le champs simul si dans amenagement etat_lib est à "en cours"

```sql
update parcelles_rulez
set simul = 0
where pid in (
	select p.pid
	from parcelles_rulez p, amenagement_fixed a
	where st_intersects(p.geom, a.geom) and a.etat_lib like 'en cours'
)
```

## Filtrer les aires trop petites/grandes en mettant simul à 0

```sql
UPDATE parcelles_rulez SET simul = 0
WHERE ST_AREA(geom) >= 5000 OR ST_AREA(geom) <= 50
```


# Générer les répertoires regroupant les parcelles par `idblock`
<a id="shapeWriter"/>

Pour se faire on a un script *nodejs* qui attend en entrée entre autres paramètres (paramètres de connexion à la base, nom de la table, etc..), un fichier listant les différents idblocks pour lesquels on va regouper les parcelles.

## Générer un fichier d'idblocks

Par exemple, si dans notre cas, **parcelles94** correspond à un extrait de **parcelles_rulez** limité au département 94, on peut récupérer les idblocks dans un fichier de la manière suivante, via pgsql :

```sql
COPY (
select distinct idblock
from parcelles94
ORDER BY idblock
) to '/home/imran/idblocks94.csv'
```

## Lancer le script `shapeWriter.js`

On modifie d'abord les paramètres adéquats au début du script :

```sh
const outputdir = '/home/imran/testoss/dep94/';
const blockfile = 'idblocks94.csv';
const host = 'localhost';
const user = 'imrandb';
const password = 'pass';
const db = 'iauidf_testoss';
const table = 'parcelles94';
```

et on exécute via :
```sh
$ nodejs shapeWriter.js
```


