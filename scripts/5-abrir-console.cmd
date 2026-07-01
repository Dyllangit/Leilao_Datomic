@echo off
cd /d "%~dp0..\datomic"
bin\console.cmd -p 8080 dev datomic:dev://localhost:4334/
