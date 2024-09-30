# Script de suppresion des ressources indéxées qui n'existent plus

Ce script permet de détecter les ressources indéxées dans opensearch mais qui n'existent plus dans la base de données primaire (mongo). 
Ces documents sont ensuite supprimés de l'index OpenSearch et de la base de données Postgres (table explorer.*).

Une interface d'aide est dispo en lançant:

```shell
./clean --help
```

Pour build l'executable lancer le script ./build.sh

## Utilisation

Commande de base
Pour exécuter clean-linux, utilisez la commande suivante :

./clean-linux

Vous serez invité à fournir les informations suivantes :

```shell
Chemin vers le fichier de configuration (par défaut : /srv/vertx/entcore/conf/entcore.conf)
Application à migrer
Nom de la base de données (par défaut : ent)
Nom de la collection ou table
Confirmation pour supprimer les données
```

Vous pouvez également utiliser les options suivantes :

```shell
--version : Affiche le numéro de version
--conf-path, --cp : Chemin vers le fichier de configuration (par défaut : /srv/vertx/entcore/conf/entcore.conf)
--db-name, --db : Nom de la base de données (par défaut : ent)
--collection-name, --cn : Nom de la collection ou table
--delete, --del : Supprimer les données (par défaut : false)
-h, --help : Affiche l’aide
```

Exemple

```shell
./clean-linux --conf-path /srv/vertx/entcore/conf/entcore.conf --db-name ent --collection-name scrapbook --delete
Mongo document is missing: collection=scrapbook, _id=91bd8639-54b6-4d15-8c00-616c44a435c7
Deleted from PostgreSQL: rows=0, _id=4098737 
Deleted from OpenSearch:  _id=4098737
Number of documents iterated:  641
Finished
```