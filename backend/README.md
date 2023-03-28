# Notes d'utilisation

## Installation

### Build app

```sh
gradle clean install
```

## Procédures de tests

- dev feat-explorer
- scp preprod-na
- redémarrer vertx
- vide elastic et postgres avec les requêtes suivantes :
```
https://preprod-na.opendigitaleducation.com/explorer/reindex/exercizer/subject?include_folders=true&drop=true
https://preprod-na.opendigitaleducation.com/explorer/reindex/blog/blog?include_folders=true&drop=true
```
- Happy debugging sucker !

## Hacks 
- Pour augmenter la taille de la file Mongo :
```
 "mongoConfig": {
           "maxWaitQueueSize": 10000,
           "username": "web-education",
           
```