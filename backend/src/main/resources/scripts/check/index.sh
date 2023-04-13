#!/bin/bash

npm install
pkg index.js
mkdir bin
mv index-macos bin/check-macos 
mv index-linux bin/check-linux 
mv index-win.exe bin/check-win.exe 
