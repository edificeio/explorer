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

if [ ! -e node_modules ]
then
  mkdir node_modules
fi

case `uname -s` in
  MINGW* | Darwin*)
    USER_UID=1000
    GROUP_UID=1000
    ;;
  *)
    if [ -z ${USER_UID:+x} ]
    then
      USER_UID=`id -u`
      GROUP_GID=`id -g`
    fi
esac

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

# Read value of a key from gradle.properties.
function prop {
    grep "^\\s*${1}=" gradle.properties|cut -d'=' -f2
}

clean () {
  if [ "$NO_DOCKER" = "true" ] ; then
    gradle clean
  else
    docker-compose run --rm -u "$USER_UID:$GROUP_GID" gradle gradle clean
  fi
}

buildGradle () {
  if [ "$NO_DOCKER" = "true" ] ; then
    gradle shadowJar install publishToMavenLocal
  else
    docker-compose run --rm -u "$USER_UID:$GROUP_GID" gradle gradle shadowJar install publishToMavenLocal
  fi
}

publish () {
  if [ -e "?/.gradle" ] && [ ! -e "?/.gradle/gradle.properties" ]
  then
    echo "odeUsername=$NEXUS_ODE_USERNAME" > "?/.gradle/gradle.properties"
    echo "odePassword=$NEXUS_ODE_PASSWORD" >> "?/.gradle/gradle.properties"
    echo "sonatypeUsername=$NEXUS_SONATYPE_USERNAME" >> "?/.gradle/gradle.properties"
    echo "sonatypePassword=$NEXUS_SONATYPE_PASSWORD" >> "?/.gradle/gradle.properties"
  fi
  if [ "$NO_DOCKER" = "true" ] ; then
    gradle publish
  else
    docker-compose run --rm -u "$USER_UID:$GROUP_GID" gradle gradle publish
  fi
}

for param in "$@"
do
  case $param in
    clean)
      clean
      ;;
    buildGradle)
      buildGradle
      ;;
    install)
      buildGradle
      ;;
    publish)
      publish
      ;;
    *)
      echo "Invalid argument : $param"
  esac
  if [ ! $? -eq 0 ]; then
    exit 1
  fi
done

