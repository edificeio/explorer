# Script de réindexation

Ce script permet de réindexer les données de manière transparente en utilisant les alias.

Une interface d'aide est dispo en lançant:

```shell
./reindex --help
```

Pour build l'executable lancer le script ./build.sh

## Initialisation des alias

Lors du premier lancement il faut éxecuter la commande suivante pour initialiser les alias (init-alias=true)

```shell
./reindex --list-apps=all --git-branch=master --delete-old=true --init-alias=true
```

Cette commande va :
- remplacer les indexs qui portent les noms "$PREFIX$APP" par "$PREFIX$APP${yyyyMMddhhmmss}"
- créer des alias qui porteront les noms "$PREFIX$APP" et pointeront sur les indexs correspondant

Pour celà le programme va effectuer les opérations dans cet ordre:
- création d'un index "$PREFIX$APP${yyyyMMddhhmmss}" en récupérant le mapping via l'option git-branch
- réindexation des données depuis "$PREFIX$APP" vers "$PREFIX$APP${yyyyMMddhhmmss}"
- suppression de l'index "$PREFIX$APP"
- création de l'alias "$PREFIX$APP" qui pointe sur "$PREFIX$APP${yyyyMMddhhmmss}"

## Mis à jour d'index

Lors des lancements suivants il faut lancer la commande (init-alias=false) pour mettre à jour l'index ainsi que l'alias

```shell
./reindex --list-apps=all --git-branch=master --delete-old=true --init-alias=false
```

Cette commande va créer de nouveau indexs "$PREFIX$APP${yyyyMMddhhmmss}" en se basant sur la dernière version du mapping et mettre à jour l'alias pour pointer sur le denrier index

Pour celà le programme va effectuer les opérations dans cet ordre:
- création d'un index "$PREFIX$APP${yyyyMMddhhmmss}" en récupérant le mapping via l'option git-branch
- réindexation des données depuis le dernier index vers "$PREFIX$APP${yyyyMMddhhmmss}"
- mise à jour de l'alias "$PREFIX$APP" qui pointe sur "$PREFIX$APP${yyyyMMddhhmmss}"
- suppression de l'index précèdent
