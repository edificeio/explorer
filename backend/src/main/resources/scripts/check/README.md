# Check Script

Ce script compare les bases de données mongo, postgres et ES pour savoir si les 3 BDD sont bien synchronisée (après une migration ou autre).

## Configuration

Par défaut le script récupère les informations de connexion aux BDD dans le fichier de configuration /srv/vertx/entcore/conf/entcore.conf
Il est possible de changer le chemin de fichier utilisé en spécifiant l'argument "--config="

```
./check-linux --config=entcore.conf
```

## Build

Lancer le script: backend/src/main/resources/scripts/check/index.sh

Celui ci va générer 3 executables (1 par OS) dans backend/src/main/resources/scripts/check/bin/

## Run

Pour lancer le script il suffit de lancer l'executable check-linux:

```
$ /tmp/check-linux 
Count blogs:  ES= 8475 PG= 8475 APP= 8475
Count blogs folder:  ES= 530 PG= 530 APP= 530
Count exercizer:  ES= 15399 PG= 15399 APP= 15399
Count exercizer folder:  ES= 1223 PG= 1229 APP= 1229
=====================================
Blog missing in ES 34 {"id":"34"}
=====================================
Blog Folder missing in ES 34 {"id":"34"}
=====================================
Exercizer missing in ES 34 {"id":"34"}
=====================================
Exercizer Folder missing in ES 34 {"id":"34"}
Exercizer Folder missing in ES 157 {"id":"157"}
Exercizer Folder missing in ES 310 {"id":"310"}
Exercizer Folder missing in ES 756 {"id":"756"}
Exercizer Folder missing in ES 725 {"id":"725"}
Exercizer Folder missing in ES 900 {"id":"900"}
=====================================
Blog Missing In ES count. ES= 0 PG= 0
Blog Folder Missing In ES count. ES= 0 PG= 0
Exercizer Missing In ES count. ES= 0 PG= 0
Exercizer Folder Missing In ES count. ES= 6 PG=  0
```

L'executable retourne:
- le nombre de ressource dans la BDD de l'appli, dans la BDD PG de l'explorateur et dans OpenSearch
- la liste des ID de ressource manquantes dans OpenSearch (id de l'ent)
- le nombre de ressource qui sont absentes de PG explorer et Opensearch