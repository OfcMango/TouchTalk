# TouchTalk — Android app + ESP32 companion firmware

This is a clean rebuild of TouchTalk using native Android Views (Kotlin), not Jetpack Compose. That deliberately removes the Compose Compiler/Kotlin compatibility failure from the earlier project.

## Included
- `app/`: Android app source, manifest and Gradle configuration.
- `.github/workflows/build-apk.yml`: GitHub Actions workflow that builds and uploads a debug APK.
- `esp32_firmware/TouchTalk_ESP32.ino`: Bluetooth Classic SPP firmware for the original ESP32 / ESP32-WROOM.

## Build the APK on GitHub
1. Download and extract this ZIP.
2. In a new GitHub repository, upload the **contents** of the extracted `TouchTalk_Rebuilt` folder. Ensure `.github/workflows/build-apk.yml` is included. If uploading with the GitHub website and `.github` is hidden, enable **View → Show → Hidden items** in Windows File Explorer.
3. Commit to the `main` branch (or `master`).
4. Open **Actions → Build TouchTalk APK** and wait for the workflow to finish.
5. Open the successful workflow run, scroll to **Artifacts**, and download `TouchTalk-apk`. Extract it to find `app-debug.apk`.
6. Transfer `app-debug.apk` to your Android phone and install it. Android may ask you to permit installation from that source.

The project uses Android Gradle Plugin 8.6.1, Gradle 8.7, Kotlin 1.9.24 and Java 17. No Compose Compiler plugin/version is configured because this project does not use Compose.

## Pair and use
1. Flash `esp32_firmware/TouchTalk_ESP32.ino` to an original ESP32 / ESP32-WROOM using Arduino IDE and the ESP32 Arduino board package.
2. In Android Bluetooth settings, pair the device named `TouchTalk`.
3. Open the app, allow Bluetooth permission and tap **Connect to TouchTalk ESP32**.
4. **Receive & Speak** displays incoming keyboard words and reads them aloud. The app automatically speaks received `TEXT:` messages; tap **Read aloud** to repeat.
5. **Speak → Braille** uses the phone's speech-recognition service. Review/edit the recognized text, preview the Braille masks, then send them to the ESP32 output pins.

## Pins and safety
- Braille input keys: dots 1–6 = GPIO 16, 17, 18, 19, 21, 22.
- Space = GPIO 23; Backspace = GPIO 25.
- LED test outputs: GPIO 4, 5, 26, 27, 32, 33. Use one resistor per LED (typically 220–1,000 ohms).
- Do not connect electromagnets or solenoids directly to ESP32 GPIO. They require a properly rated MOSFET/transistor driver, suitable separate power supply and flyback protection.

## Bluetooth protocol
- ESP32 → phone: `DRAFT:<text>` previews the keypad word; `TEXT:<text>` commits a word; `STATUS:<message>` shows status.
- Phone → ESP32: `CELL:<mask>` sets the six output bits; `DONE` marks end of message; `CLEAR` turns outputs off.
- Bit 0 maps to dot/output 1, bit 1 to dot/output 2, ... bit 5 to dot/output 6. The phone sends cells at approximately 650 ms intervals so each LED cell can be observed.

## Important limitations
- This is a functional prototype source project, not a production-certified or physically tested device. A successful GitHub build still needs to be confirmed by running the workflow.
- Speech recognition is provided by Android's installed recognition service and may require internet or downloaded language packs, depending on the phone.
- The firmware decodes basic uncontracted Braille letters from the physical keypad; unsupported input patterns show `?`.
- The phone-side preview supports basic English letter/digit cell patterns and common punctuation. This first prototype does not implement full literary Braille rules such as number/capital indicators or contracted Braille. Unsupported characters are skipped when sending.
- Output pins are LED test outputs only. Do not connect actuators directly.
