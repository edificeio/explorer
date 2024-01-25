# Notes d'utilisation

## Installation

### Containers

Add this container to your springboard.

```yaml
  redis:
    image: redis:5.0.3-alpine
```

### ENT conf

Add this piece of configuration for module infra :

```json
{
  "name": "org.entcore~infra~4.12-develop-pedago-SNAPSHOT",
  ...
  "config": {
    ...
    "explorerConfig": {
      "enabled": true,
      "postgres": false
    }
  }
}
```


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

## Hacks 
- Pour augmenter la taille de la file Mongo :
```
 "mongoConfig": {
           "maxWaitQueueSize": 10000,
           "username": "web-education",
           
```