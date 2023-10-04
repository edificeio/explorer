# Script de migration

Ce script permet de réindexer les indexs et mappings opensearch

Une interface d'aide est dispo en lançant:

```shell
./migrate --help
```

Pour build l'executable lancer le script ./build.sh

## Création des indexs

> Prérequis: In faut désactiver la création des indexs par vertx dans la config de "explorer" (entcore.conf)

```
"config": {
    "main" : "com.opendigitaleducation.explorer.Explorer",
    "create-index":false
}
```

Pour créer les indexs il faut éxecuter la commande suivante pour initialiser les alias (init-alias=true)

```shell
./migrate --list-apps=all --git-branch=master
```

Cette commande va pour chaque app de la liste (argument --list-apps):
- créer les indexs "$PREFIX$APP"
- télécharger le mapping sur la branch $git-branch du répo explorer
- créer le mapping correspondant

A la fin de l'execution, le shell va proposer de lancer l'URL suivante pour synchroniser l'index avec les données de l'ent:
```
Veuillez réactiver le job d'indexation et lancer la réindexation via l'URL: https://$ENT_DOMAIN/explorer/reindex/all/all?include_old_folders=true&include_new_folders=true
```

> Avant d'appeler cette URL il faut bien s'assurer que le job d'indexation est actif dans entcore.conf (vm jobs):

```
"config": {
    "main" : "com.opendigitaleducation.explorer.Explorer",
    "enable-job":true
}
```