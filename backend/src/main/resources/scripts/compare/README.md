# Script de réindexation

Ce script permet de comparer la base de données mongo/postgres versus la base opensearch pour identifier les ressources non indexés

Une interface d'aide est dispo en lançant:

```shell
./compare-linux --help
```

Pour build l'executable lancer le script ./build.sh

# Lancement du script

Pour executer lancer la commande suivante:

```shell
./compare-linux --application=scrapbook --db-name=ent --collection-name=scrapbook
```

Cette commande va pour l'application scrapbook:
- dump les id ent des ressources opensearch dans un fichier opensearch-ids.txt
- dump les id ent des ressource mongodb dans un fichier mongodb-ids.txt
- comparer les 2 fichiers et écrire la différence dans diff-ids.txt

Le fichier diff-ids.txt contient tout les ID mongodb manquant dans l'index opensearch
