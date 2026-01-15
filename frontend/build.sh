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

if [ ! -e node_modules ]
then
  mkdir node_modules
fi

if [ -z ${USER_UID:+x} ]
then
  export USER_UID=1000
  export GROUP_GID=1000
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
  if [ ! -z "$FRONT_TAG" ]; then
    echo "[init] Get tag name from jenkins param... $FRONT_TAG"
    BRANCH_NAME="$FRONT_TAG"
  else
    BRANCH_NAME=`echo $GIT_BRANCH | sed -e "s|origin/||g"`
    if [ "$BRANCH_NAME" = "" ]; then
      echo "[init] Get branch name from git..."
      BRANCH_NAME=`git branch | sed -n -e "s/^\* \(.*\)/\1/p"`
    fi
  fi

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
}

linkDependencies () {
  # Check if the edifice-frontend-framework directory exists
  if [ ! -d "$PWD/../../edifice-frontend-framework/packages" ]; then
    echo "Directory edifice-frontend-framework/packages does not exist."
    exit 1
  else
    echo "Directory edifice-frontend-framework/packages exists."
  fi


  # # Extract dependencies from package.json using sed
  DEPENDENCIES=$(sed -n '/"dependencies": {/,/}/p' package.json | sed -n 's/ *"@edifice\.io\/\([^"]*\)":.*/\1/p')

  # # Link each dependency if it exists in the edifice-frontend-framework
  for dep in $DEPENDENCIES; do
    # Handle special case for ts-client
    package_path="$PWD/../../edifice-frontend-framework/packages/$dep"

    if [ -d "$package_path" ]; then
      echo "Linking package: $dep"
      (cd "$package_path" && pnpm link --global)
    else
      echo "Package $dep not found in edifice-frontend-framework."
    fi
  done

  # # Link the packages in the current application
  echo "Linking packages in the current application..."
  Link each dependency from package.json
  for dep in $DEPENDENCIES; do
    pnpm link --global "@edifice.io/$dep"
  done

  echo "All specified packages have been linked successfully."
}

cleanDependencies() {
  rm -rf node_modules && rm -f pnpm-lock.yaml && pnpm install
}

publishNPM () {
  echo "[publish] Publish package..."
  LOCAL_BRANCH=$(echo $GIT_BRANCH | sed -e "s|origin/||g")
  TAG_BRANCH=$([ "$LOCAL_BRANCH" = "main" ] && echo "latest" || echo "$LOCAL_BRANCH")
  
  docker compose run -e NPM_TOKEN=$NPM_TOKEN -e GIT_BRANCH=$GIT_BRANCH --rm -u "$USER_UID:$GROUP_GID" node sh -c "pnpm version:update"
  docker compose run -e NPM_TOKEN=$NPM_TOKEN --rm -u "$USER_UID:$GROUP_GID" node sh -c "pnpm publish --no-git-checks --access public --tag $TAG_BRANCH"
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
    linkDependencies)
      linkDependencies
      ;;
    cleanDependencies)
      cleanDependencies
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