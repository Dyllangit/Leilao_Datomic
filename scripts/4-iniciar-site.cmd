@echo off
cd /d "%~dp0..\web"
if not exist node_modules (
  echo Instalando dependencias do Node...
  call npm install
)
node server.js
