@echo off
cd /d "%~dp0.."
if not exist bridge\out mkdir bridge\out
javac -encoding UTF-8 -cp "datomic\lib\*;datomic\peer-1.0.7622.jar" -d bridge\out bridge\src\Bridge.java
echo Compilado em bridge\out
