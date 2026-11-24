# PiPup – Fire TV fork

This is a fork of [rogro82/PiPup](https://github.com/rogro82/PiPup), adjusted to show a **live doorbell camera** in a pop-up on an Amazon Fire TV Stick. The pop-up is triggered from Home Assistant. All credit for PiPup goes to the original author. The upstream code was last updated in 2019 and is only published through Google Play, which Fire TV doesn't have, so this fork has to be built and sideloaded.

## Changes in this fork

| Change | Why |
|---|---|
| WebView: JavaScript, DOM storage and autoplay enabled | Camera web players such as go2rtc need them |
| WebView destroyed when the pop-up closes | Stops the camera stream instead of leaving it running in the background |
| `android:usesCleartextTraffic="true"` | Lets the pop-up load plain `http://` LAN pages (otherwise `net::ERR_CLEARTEXT_NOT_PERMITTED`) |
| `cornerRadius` (float, default `24`) | Rounded pop-up corners; the media is clipped to match |
| `borderColor` / `borderWidth` | Optional coloured border, e.g. `"#7DF9FF"` and `5` |
| `sound: "chime"` | Plays a bundled two-note doorbell chime (`res/raw/chime.wav`) when the pop-up opens |

Requests that don't use the new fields behave the same as upstream.

## Example request

```json
POST http://<fire-tv-ip>:7979/notify
{
  "duration": 20, "position": 0,
  "message": "Front Door", "messageSize": 16,
  "backgroundColor": "#CC000000",
  "cornerRadius": 24, "borderColor": "#7DF9FF", "borderWidth": 5,
  "sound": "chime",
  "media": { "web": { "uri": "http://<server>:<port>/door.html", "width": 480, "height": 480 } }
}
```

**Fire TV tip:** Fire TV Sticks have very little memory. Full-resolution camera video in the WebView froze a Fire TV Stick 4K. Serve a small feed instead, for example go2rtc transcoding to 480×480 MJPEG at 5 fps. Wrap it in a tiny HTML page with `<img src="/api/stream.mjpeg?src=...">`. A bare MJPEG URL in an Android WebView only shows the first frame.

## Build (Docker, no Android Studio needed)

```sh
# one-time: Android SDK into a docker volume
docker run --rm -v android-sdk:/sdk eclipse-temurin:17-jdk bash -c '
  apt-get -qq update && apt-get -qq install -y unzip curl &&
  curl -sSfLo /tmp/ct.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip &&
  mkdir -p /sdk/cmdline-tools && unzip -qo /tmp/ct.zip -d /sdk/cmdline-tools && mv /sdk/cmdline-tools/cmdline-tools /sdk/cmdline-tools/latest &&
  yes | /sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/sdk --licenses >/dev/null &&
  /sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/sdk "platforms;android-28" "build-tools;28.0.3"'

# build (keep the pipup-android volume: it holds the signing key, and a new key blocks in-place upgrades)
docker run --rm -v android-sdk:/sdk -v "$PWD":/src -v pipup-gradle:/root/.gradle -v pipup-android:/root/.android \
  -e ANDROID_HOME=/sdk -w /src eclipse-temurin:11-jdk ./gradlew --no-daemon assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

## Install on Fire TV

Turn on ADB debugging on the Fire TV (Settings → My Fire TV → About → click the device name 7 times, then Developer Options → ADB debugging), and then run:

```sh
adb connect <fire-tv-ip>:5555
adb push app-debug.apk /data/local/tmp/pipup.apk
adb shell pm install -r /data/local/tmp/pipup.apk
adb shell appops set nl.rogro82.pipup SYSTEM_ALERT_WINDOW allow
adb shell monkey -p nl.rogro82.pipup -c android.intent.category.LEANBACK_LAUNCHER 1   # start once
```

On Fire OS 8, `pm install` can't read `/sdcard`, so install from `/data/local/tmp`.

---

*Original README below.*

# PiPup

PiPup is an application that allows displaying user-defined custom notifications on Android TV.

The most common use-case for this application is for sending notifications, from a home-automation solution, to your Android TV.

![](https://github.com/rogro82/PiPup/raw/master/graphics/screenshot-1.png)

__Some example scenarios:__

- Show a snapshot of your camera on your TV (eg on a motion trigger)
- Display a notification with the video of your camera when someone is at your door
- Send a notification when your dryer/washingmachine is ready
- Anything else you might find useful


__The application is currently in a `public beta`__

To enter the `beta` and install the application on your device go to:  
https://play.google.com/apps/testing/nl.rogro82.pipup

_Important: after installation / updating it is currently adviced to restart your TV and open the application once to make sure the background-service is running_

#### Sideloading:

On Android TV (8.0+), when sideloading, you will need to set the permission for SYSTEM_ALERT_WINDOW manually (using adb) as there is no interface on Android TV to do this.

To give the application the required permission to draw overlays you will need to run:
```
adb shell appops set nl.rogro82.pipup SYSTEM_ALERT_WINDOW allow
```

## Integrating

PiPup uses an embedded webserver (NanoHTTPD) which runs on port 7979.

### Sending notifications

#### To send notifications with an external media resource (image, url or webview) use application/json


| Property      | Value            |
| ------------- | ---------------- |
| Path:         | /notify          |
| Method:       | POST             |
| Content-Type: | application/json |

Example json data:

```json
{
  "duration": 30,
  "position": 0,
  "title": "Your awesome title",
  "titleColor": "#0066cc",
  "titleSize": 20,
  "message": "What ever you want to say... do it here...",
  "messageColor": "#000000",
  "messageSize": 14,
  "backgroundColor": "#ffffff",
  "media": { "image": {
    "uri": "https://mir-s3-cdn-cf.behance.net/project_modules/max_1200/cfcc3137009463.5731d08bd66a1.png", "width": 480
  }}
}
```
All fields are optional and for `media` you can specify 3 types:

```json 
{ "image": { "uri": "address_to_your_image", "width": 480 }}
{ "video": { "uri": "address_to_your_video", "width": 480 }}
{ "web":   { "uri": "address_to_your_resource", "width": 640, "height": 480 }}
```

#### To send notifications with an image file use multipart/form-data

| Property      | Value               |
| ------------- | ------------------- |
| Path:         | /notify             |
| Method:       | POST                |
| Content-Type: | multipart/form-data |

Form-fields:

| Field           | Type                                         |
| --------------- | -------------------------------------------- |
| duration        | Integer (default=30)                         |
| position        | Integer (0..4, default=0)                    |
| title           | String                                       |
| titleSize       | Integer (default=16)                         |
| titleColor      | string (default=#FFFFFF, format=[AA]RRGGBB   |
| message         | String                                       |
| messageSize     | Integer (default=12)                         |
| messageColor    | String (default=#FFFFFF, format=[AA]RRGGBB   |
| backgroundColor | String (default=#CC000000, format=[AA]RRGGBB |
| image           | File                                         |
| imageWidth      | Integer (default=480)                        |

`position` is an enum ranging from 0 to 4

|  | Position    |
| -----: | ----------- |
| 0     | TopRight    |
| 1     | TopLeft     |
| 2     | BottomRight |
| 3     | BottomLeft  |
| 4     | Center      |

Color-properties are in `[AA]RRGGBB` where the alpha channel is optional e.g. #FFFFFF or #CCFFFFFF