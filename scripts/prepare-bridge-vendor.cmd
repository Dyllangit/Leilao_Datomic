@echo off
REM Copia as bibliotecas do Datomic Peer para dentro de bridge/vendor,
REM tornando a pasta bridge/ um contexto de build Docker autossuficiente
REM (necessario porque o deploy usa bridge/ como Root Directory no Railway).
cd /d "%~dp0.."
if exist bridge\vendor rmdir /s /q bridge\vendor
mkdir bridge\vendor\lib
xcopy /y datomic\lib\*.jar bridge\vendor\lib\ >nul
copy /y datomic\peer-1.0.7622.jar bridge\vendor\ >nul
echo bridge\vendor atualizado.
