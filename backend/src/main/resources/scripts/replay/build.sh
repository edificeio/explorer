rm -rf build bin
pnpm i
pnpm run build
mv ./bin/index-macos ./bin/replay-macos
mv ./bin/index-linux ./bin/replay-linux
mv ./bin/index-win.exe ./bin/replay-win.exe