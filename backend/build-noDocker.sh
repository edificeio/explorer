#!/bin/bash

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

GIT_BRANCH="feat-explorer"

# Read value of a key from gradle.properties.
function prop {
    grep "^\\s*${1}=" gradle.properties|cut -d'=' -f2
}

clean () {
  gradle clean
}

# buildNode () {
#   #jenkins
#   echo "[buildNode] Get branch name from jenkins env..."
#   BRANCH_NAME=`echo $GIT_BRANCH | sed -e "s|origin/||g"`
#   if [ "$BRANCH_NAME" = "" ]; then
#     echo "[buildNode] Get branch name from git..."
#     BRANCH_NAME=`git branch | sed -n -e "s/^\* \(.*\)/\1/p"`
#   fi
#   if [ "$BRANCH_NAME" = "" ]; then
#     echo "[buildNode] Branch name should not be empty!"
#     exit -1
#   fi

#   if [ "$BRANCH_NAME" = 'master' ]; then
#       echo "[buildNode] Use entcore version from package.json ($BRANCH_NAME)"
#       case `uname -s` in
#         MINGW*)
#           yarn install --no-bin-links && npm update entcore && node_modules/gulp/bin/gulp.js build
#           ;;
#         *)
#           yarn install && npm update entcore && node_modules/gulp/bin/gulp.js build
#       esac
#   else
#       echo "[buildNode] Use entcore tag $BRANCH_NAME"
#       case `uname -s` in
#         MINGW*)
#           yarn install --no-bin-links && npm rm --no-save entcore && yarn install --no-save entcore@$BRANCH_NAME && node_modules/gulp/bin/gulp.js build
#           ;;
#         *)
#           yarn install && npm rm --no-save entcore && yarn install --no-save entcore@$BRANCH_NAME && node_modules/gulp/bin/gulp.js build
#       esac
#   fi
# }

buildGradle () {
  gradle shadowJar install publishToMavenLocal
}

publish () {
  if [ -e "?/.gradle" ] && [ ! -e "?/.gradle/gradle.properties" ]
  then
    echo "odeUsername=$NEXUS_ODE_USERNAME" > "?/.gradle/gradle.properties"
    echo "odePassword=$NEXUS_ODE_PASSWORD" >> "?/.gradle/gradle.properties"
    echo "sonatypeUsername=$NEXUS_SONATYPE_USERNAME" >> "?/.gradle/gradle.properties"
    echo "sonatypePassword=$NEXUS_SONATYPE_PASSWORD" >> "?/.gradle/gradle.properties"
  fi
  gradle publish
}

# buildStatic () {
#     yarn install --no-save ode-bootstrap-neo@feat-explorer && npm run dev:build
# }

# watch () {
#   BUILD_APP="$(prop 'modowner')~$(prop 'modname')~$(prop 'version')"
#   echo "Watching app $BUILD_APP"
#   npm run watch --springboard=/home/node/$SPRINGBOARD --app=\"$BUILD_APP\"
# }

for param in "$@"
do
  case $param in
    clean)
      clean
      ;;
    # buildNode)
    #   buildNode
    #   ;;
    buildGradle)
      buildGradle
      ;;
    install)
      buildGradle
      ;;
    # watch)
    #   watch
    #   ;;
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

