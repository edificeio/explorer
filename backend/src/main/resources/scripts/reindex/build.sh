rm -rf build bin
npm i
npm run build
mv ./bin/index-macos ./bin/reindex-macos
mv ./bin/index-linux ./bin/reindex-linux
mv ./bin/index-win.exe ./bin/reindex-win.exe