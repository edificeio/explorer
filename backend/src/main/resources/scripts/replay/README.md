# Script de réindexation

Ce script permet de rejouer l'indexation d'une liste d'ids contenu dans un fichier d'entrée

Une interface d'aide est dispo en lançant:

```shell
./replay-linux --help
```

Pour build l'executable lancer le script ./build.sh

# Lancement du script

Pour executer lancer la commande suivante:

```shell
./replay-linux --url=https://oneconnect.opendigitaleducation.com/ --application=scrapbook --input=input.txt --cookie=COOKIE_SESSION_ID
```

Cette commande va rejouer l'ensemble des ids contenu dans le fichier d'entrée (1 ID = 1 ligne)