# Caravan Leveler

Android app for caravan levelling using the phone's built-in accelerometer.

## Included

- Live pitch and roll in degrees.
- Large visual spirit-level display.
- Calibration button for the phone's installed position.
- Caravan dimensions for correction-distance estimates.
- Front/rear and left/right correction guidance.
- Host mode for a second Android phone.
- Remote mode for a second Android phone.
- Local TCP connection on port 45678. No cloud account or internet service is required.
- Screen stays awake while the app is open.

## Second-phone setup

1. Connect both phones to the same Wi-Fi network, or start a hotspot on one phone and connect the other phone to it.
2. Open Caravan Leveler on the phone placed inside the caravan.
3. Open REMOTE and choose START HOST.
4. Note the displayed host IP address.
5. Open the app on the second phone.
6. Open REMOTE, enter the host IP, and choose CONNECT REMOTE.
7. The second phone now shows the live pitch and roll from the sensor phone.

The sensor phone should stay in the same position after calibration. Use a solid, repeatable mounting point on the caravan floor.

## Build

Open the project in Android Studio and run the app. Android Studio will download the Android Gradle Plugin and required Gradle distribution.

Target SDK: 35.
Minimum SDK: 26.

## Roadmap for a fuller Camping Assistant style app

- Caravan and motorhome profiles.
- Saved dimensions and multiple vehicles.
- Voice instructions.
- Wear OS display.
- Hitch height memory.
- Payload and nose-weight calculator.
- Departure and arrival checklists.
- Sun and satellite finder.
- Restaurant finder.
- Route and towing-rule information.

## Automatic APK builds

This project includes a GitHub Actions workflow at `.github/workflows/build.yml`.

After uploading the project to GitHub:

1. Push the project to a repository.
2. Open the repository's Actions tab.
3. Select `Build Caravan Leveler APK`.
4. Choose `Run workflow`.
5. Open the completed workflow run.
6. Download `caravan-leveler-debug-apk` from Artifacts.

The workflow also runs on pushes and pull requests targeting `main`.

To create a GitHub Release containing the APK, run the workflow manually and set `release` to `true`.
