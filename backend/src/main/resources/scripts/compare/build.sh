rm -rf build bin
pnpm i
pnpm run build
mv ./bin/index-macos ./bin/compare-macos
mv ./bin/index-linux ./bin/compare-linux
mv ./bin/index-win.exe ./bin/compare-win.exe