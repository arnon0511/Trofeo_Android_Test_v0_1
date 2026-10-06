@echo off
cd /d "%~dp0"
py -m pip install -r requirements.txt
py bridge_v17.py
pause
