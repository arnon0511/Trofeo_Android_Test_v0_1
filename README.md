# TROFEO Android Test v0.1
Target: Thermalright TROFEO Vision 6.86, VID 0416 / PID 5302, PM=128, 1280x480.

## Test order
1. Do NOT run TRCC/InfoPanel at the same time.
2. Connect TROFEO to Galaxy via USB-C OTG / powered USB hub.
3. Install APK, open app, tap CONNECT TROFEO and approve USB permission.
4. Tap TEST IMAGE.
5. v0.1 intentionally sends only one frame per connection/session. If the panel stops responding, unplug/replug TROFEO before retrying.

## Build on GitHub
Push this project to a GitHub repository. Actions > Build APK > Run workflow. Download artifact TROFEO-Android-Test-v0.1.

Protocol implementation is a clean-room implementation from public wire-format documentation. It validates PM=128 before sending a frame.
