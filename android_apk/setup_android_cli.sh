#!/bin/bash
# ============================================================
# Android Studio CLI Setup Script
# Installs Android SDK command-line tools, platform-tools,
# build-tools, and sets environment variables.
# Reusable - safe to run multiple times.
# ============================================================

set -e

ANDROID_SDK_DIR="$HOME/android-sdk"
CMD_LINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

echo "========================================"
echo " Android Studio CLI Setup"
echo "========================================"

# Step 1: Create SDK directory
mkdir -p "$ANDROID_SDK_DIR"
cd "$ANDROID_SDK_DIR"

# Step 2: Download command-line tools if not already present
if [ ! -d "$ANDROID_SDK_DIR/cmdline-tools/latest" ]; then
    echo "[1/5] Downloading Android command-line tools..."
    curl -o cmdline-tools.zip "$CMD_LINE_TOOLS_URL" 2>&1 | tail -1
    echo "[2/5] Extracting..."
    unzip -q cmdline-tools.zip
    rm cmdline-tools.zip
    mkdir -p latest
    mv cmdline-tools/* latest/
    mv latest cmdline-tools/
    echo "       Done."
else
    echo "[1-2/5] Command-line tools already installed, skipping."
fi

# Step 3: Accept licenses
echo "[3/5] Accepting SDK licenses..."
yes | "$ANDROID_SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_SDK_DIR" --licenses 2>&1 | tail -1

# Step 4: Install required SDK packages
echo "[4/5] Installing SDK packages (platform-tools, build-tools, platform)..."
"$ANDROID_SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_SDK_DIR" \
    "platform-tools" \
    "build-tools;35.0.0" \
    "platforms;android-35" 2>&1 | tail -5
echo "       Done."

# Step 5: Set up environment variables in .bashrc (if not already there)
echo "[5/5] Setting up environment variables..."
if ! grep -q "ANDROID_HOME" ~/.bashrc; then
    {
        echo ""
        echo "# Android SDK (added by setup_android_cli.sh)"
        echo "export ANDROID_HOME=\"\$HOME/android-sdk\""
        echo "export ANDROID_SDK_ROOT=\"\$HOME/android-sdk\""
        echo "export PATH=\"\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/build-tools/35.0.0\""
    } >> ~/.bashrc
    echo "       Environment variables added to ~/.bashrc"
else
    echo "       Environment variables already set in ~/.bashrc"
fi

echo ""
echo "========================================"
echo " Setup Complete!"
echo "========================================"
echo ""
echo "Installed at: $ANDROID_SDK_DIR"
echo ""
echo "To apply environment variables now, run:"
echo "  source ~/.bashrc"
echo ""
echo "To verify installation, run:"
echo "  adb --version"
echo "  aapt version"