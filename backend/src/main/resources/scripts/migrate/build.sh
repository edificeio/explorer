rm -rf build bin
npm i
npm run build
mv ./bin/index-macos ./bin/migrate-macos
mv ./bin/index-linux ./bin/migrate-linux
mv ./bin/index-win.exe ./bin/migrate-win.exe