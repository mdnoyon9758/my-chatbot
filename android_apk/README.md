# Android APK Builder

## Folder Structure

```
android_apk/
├── setup_android_cli.sh       # Run this ONCE to install Android SDK CLI tools
├── env_check.txt              # Environment check output (Java, Gradle)
├── os_check.txt               # OS check output
├── device info/               # Placeholder folder for device info files
└── README.md                  # This file
```

## Setup Instructions (One-Time)

Run the setup script to install the Android SDK command-line tools:

```bash
bash android_apk/setup_android_cli.sh
```

This will:
1. Download Android command-line tools to `~/android-sdk`
2. Accept SDK licenses
3. Install platform-tools (adb), build-tools, and Android platform 35
4. Add `ANDROID_HOME`, `ANDROID_SDK_ROOT` to your `~/.bashrc`

After running, apply the environment variables:
```bash
source ~/.bashrc
```

## Building APK

Once the CLI tools are set up, we will write the APK source code inside `device info/` and compile it using the Gradle build system.