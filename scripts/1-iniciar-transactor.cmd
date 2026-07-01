@echo off
cd /d "%~dp0..\datomic"
bin\transactor.cmd ..\config\transactor.properties
