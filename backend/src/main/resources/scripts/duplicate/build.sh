rm -rf build bin
npm i
npm run build
mv ./bin/index-macos ./bin/duplicate-macos
mv ./bin/index-linux ./bin/duplicate-linux
mv ./bin/index-win.exe ./bin/duplicate-win.exe