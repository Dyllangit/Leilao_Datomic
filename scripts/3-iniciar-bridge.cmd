@echo off
cd /d "%~dp0.."
java -Xmx1g -cp "bridge\resources;datomic\lib\*;datomic\peer-1.0.7622.jar;bridge\out" Bridge
