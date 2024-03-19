rm -rf build bin
pnpm i
pnpm run build
mv ./bin/index-macos ./bin/duplicate-macos
mv ./bin/index-linux ./bin/duplicate-linux
mv ./bin/index-win.exe ./bin/duplicate-win.exe