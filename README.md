# Ad CoverMuteSkip ![Android App Icon](https://github.com/echoesofcreation/adCoverMuteSkip/blob/d7b0d84a709583ac0ec5a96c970f9cfd84dbf0ba/assets/android_icon.png)



An adblocker for Native Android apps. Cover ads, mute audio during videos and skip when possible.

Currently, it only handles the Youtube app, but the goal is to provide a system similar to web browser adblockers to block ads from any app.

## What's the point of this project compared to other Android Adblockers

- **uBlock Origin** Downside: it only works within the web browsers, not the native apps

- **Third party clones (NewPipe for Youtube)** Downside: it requires extreme development efforts. It usually doesn't implement the features of logged in users.

- **APK patching (Revanced)** Downside: it requires manual updating and repatching all the time. High user effort.

- ***Ad CoverMuteSkip***. Downside: it doesn't actually remove ads, but just covers the ads with an overlay and auto-mute/auto-skip video ads.

The goal of this project is to provide a system that decreases the toxicity of ads on all native Android apps with minimum user effort.

## What it looks like

### [Check this video demo]([https://github.com/echoesofcreation/adCoverMuteSkip/blob/f4d7c24be25f73d15c12324a0773e2335f75823d/assets/Ad%20CoverMuteSkipVideoDemo.mp4](https://github.com/echoesofcreation/adCoverMuteSkip/blob/d7b0d84a709583ac0ec5a96c970f9cfd84dbf0ba/assets/Ad%20CoverMuteSkip_VideoDemo.mp4)) That demo shows Youtube sound auto-muting and moving the floating player above a covered ad.

![3 screenshots](https://github.com/echoesofcreation/adCoverMuteSkip/blob/d7b0d84a709583ac0ec5a96c970f9cfd84dbf0ba/assets/AppYoutubeScreenshot.png)

## How to install

Download the latest APK from the **release-apk** folder (or recompile it yourself).

### [Ad CoverMuteSkip v1.1.2.2.apk](https://github.com/echoesofcreation/adCoverMuteSkip/blob/d7b0d84a709583ac0ec5a96c970f9cfd84dbf0ba/release-apk/adcovermuteskip%201.1.2.2.apk)

The app uses the Accessibility Service feature from Android to receive screen change events and generate virtual clicks. You'll need to enable/disable the service manually, once activated it'll run all the time independantly of the graphical app.

The app's frontpage explains in more details how to do it.
