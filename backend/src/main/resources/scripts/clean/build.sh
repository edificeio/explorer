rm -rf build bin
pnpm i
pnpm run build
mv ./bin/index-macos ./bin/clean-macos
mv ./bin/index-linux ./bin/clean-linux
mv ./bin/index-win.exe ./bin/clean-win.exe