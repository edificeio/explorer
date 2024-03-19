rm -rf build bin
pnpm i
pnpm run build
mv ./bin/index-macos ./bin/migrate-macos
mv ./bin/index-linux ./bin/migrate-linux
mv ./bin/index-win.exe ./bin/migrate-win.exe