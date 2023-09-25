rm -rf build bin
npm i
npm run build
mv ./bin/index-macos ./bin/alias-macos
mv ./bin/index-linux ./bin/alias-linux
mv ./bin/index-win.exe ./bin/alias-win.exe