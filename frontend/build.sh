#!/bin/bash

# Options
NO_DOCKER=""
for i in "$@"
do
case $i in
  --no-docker*)
  NO_DOCKER="true"
  shift
  ;;
  *)
  ;;
esac
done

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 <clean|init|localDep|build|install|watch>"
  echo "Example: $0 clean"
  echo "Example: $0 init"
  echo "Example: $0 localDep   Use this option to update the edifice-ts-client NPM dependency with a local version"
  echo "Example: $0 build"
  echo "Example: $0 install"
  echo "Example: $0 [--springboard=recette] watch"
  exit 1
fi

MVN_MOD_GROUPID=`grep 'modowner=' gradle.properties | sed 's/modowner=//'`
MVN_MOD_NAME=`grep 'modname=' gradle.properties | sed 's/modname=//'`
MVN_MOD_VERSION=`grep 'version=' gradle.properties | sed 's/version=//'`

if [ ! -e node_modules ]
then
  mkdir node_modules
fi

if [ -z ${USER_UID:+x} ]
then
  export USER_UID=1000
  export GROUP_GID=1000
fi

if [ -e "?/.gradle" ] && [ ! -e "?/.gradle/gradle.properties" ]
then
  echo "odeUsername=$NEXUS_ODE_USERNAME" > "?/.gradle/gradle.properties"
  echo "odePassword=$NEXUS_ODE_PASSWORD" >> "?/.gradle/gradle.properties"
  echo "sonatypeUsername=$NEXUS_SONATYPE_USERNAME" >> "?/.gradle/gradle.properties"
  echo "sonatypePassword=$NEXUS_SONATYPE_PASSWORD" >> "?/.gradle/gradle.properties"
fi

# options
SPRINGBOARD="recette"
for i in "$@"
do
case $i in
    -s=*|--springboard=*)
    SPRINGBOARD="${i#*=}"
    shift
    ;;
    *)
    ;;
esac
done

clean () {
  rm -rf node_modules dist build .gradle .pnpm-store
  rm -f package.json pnpm-lock.yaml
}

doInit () {
  echo "[init] Get branch name from jenkins env..."
  BRANCH_NAME=`echo $GIT_BRANCH | sed -e "s|origin/||g"`
  if [ "$BRANCH_NAME" = "" ]; then
    echo "[init] Get branch name from git..."
    BRANCH_NAME=`git branch | sed -n -e "s/^\* \(.*\)/\1/p"`
  fi

  # echo "[init] Generate deployment file from conf.deployment..."
  # mkdir -p deployment/$MVN_MOD_NAME
  # cp conf.deployment deployment/$MVN_MOD_NAME/conf.json.template
  # sed -i "s/%MODNAME%/${MVN_MOD_NAME}/" deployment/$MVN_MOD_NAME/conf.json.template
  # sed -i "s/%VERSION%/${MVN_MOD_VERSION}/" deployment/$MVN_MOD_NAME/conf.json.template

  echo "[init] Generate package.json from package.json.template..."
  NPM_VERSION_SUFFIX=`date +"%Y%m%d%H%M"`
  cp package.json.template package.json
  sed -i "s/%branch%/${BRANCH_NAME}/" package.json
  sed -i "s/%generateVersion%/${NPM_VERSION_SUFFIX}/" package.json

  if [ "$1" == "Dev" ]
  then
    sed -i "s/%packageVersion%/link:..\/edifice-ts-client\//" package.json
  else
    sed -i "s/%packageVersion%/${BRANCH_NAME}/" package.json
  fi

  if [ "$NO_DOCKER" = "true" ] ; then
    pnpm install
  else
    docker-compose run --rm -u "$USER_UID:$GROUP_GID" node sh -c "pnpm install"
  fi
}

init() {
  doInit
}

initDev() {
  doInit "Dev"
}

# Install local dependencies as tarball (installing as folder creates symlinks which won't resolve in the docker container)
localDep () {
  if [ -e $PWD/../edifice-ts-client ]; then
    rm -rf edifice-ts-client.tar edifice-ts-client.tar.gz
    mkdir edifice-ts-client.tar && mkdir edifice-ts-client.tar/dist \
      && cp -R $PWD/../edifice-ts-client/dist $PWD/../edifice-ts-client/package.json edifice-ts-client.tar
    tar cfzh edifice-ts-client.tar.gz edifice-ts-client.tar
    docker-compose run --rm -u "$USER_UID:$GROUP_GID" node sh -c "pnpm install --no-save edifice-ts-client.tar.gz"
    rm -rf edifice-ts-client.tar edifice-ts-client.tar.gz
  fi
}

build () {
  if [ "$NO_DOCKER" = "true" ] ; then
    pnpm build
  else
    docker-compose run --rm -u "$USER_UID:$GROUP_GID" node sh -c "pnpm build"
  fi
  status=$?
  if [ $status != 0 ];
  then
    exit $status
  fi

  VERSION=`grep "version="  gradle.properties| sed 's/version=//g'`
  echo "ode-explorer=$VERSION `date +'%d/%m/%Y %H:%M:%S'`" >> dist/version.txt
}

publishNPM () {
  LOCAL_BRANCH=`echo $GIT_BRANCH | sed -e "s|origin/||g"`
  if [ "$NO_DOCKER" = "true" ] ; then
    npm publish --tag $LOCAL_BRANCH
  else
    docker-compose run --rm -u "$USER_UID:$GROUP_GID" node sh -c "pnpm publish --tag $LOCAL_BRANCH"
  fi
}

publishMavenLocal (){
  mvn install:install-file \
    --batch-mode \
    -DgroupId=$MVN_MOD_GROUPID \
    -DartifactId=$MVN_MOD_NAME \
    -Dversion=$MVN_MOD_VERSION \
    -Dpackaging=tar.gz \
    -Dfile=${MVN_MOD_NAME}.tar.gz
}

for param in "$@"
do
  case $param in
    clean)
      clean
      ;;
    init)
      init
      ;;
    initDev)
      initDev
      ;;
    localDep)
      localDep
      ;;
    build)
      build
      ;;
    install)
      build && archive && publishMavenLocal && rm -rf build
      ;;
    watch)
      watch
      ;;
    archive)
      archive
      ;;
    publishNPM)
      publishNPM
      ;;
    *)
      echo "Invalid argument : $param"
  esac
  if [ ! $? -eq 0 ]; then
    exit 1
  fi
done