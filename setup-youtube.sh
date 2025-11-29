#!/bin/bash
# Setup script for YouTube cookie configuration

echo "=== Lexicon YouTube Download Setup ==="
echo ""
echo "This script will help you configure YouTube cookies for yt-dlp downloads."
echo ""

# Check if yt-dlp is installed
if ! command -v yt-dlp &> /dev/null; then
    echo "❌ yt-dlp is not installed!"
    echo ""
    echo "Please install yt-dlp first:"
    echo "  pip install yt-dlp"
    echo "  or"
    echo "  brew install yt-dlp  (on macOS)"
    echo "  sudo apt install yt-dlp  (on Ubuntu/Debian)"
    exit 1
fi

echo "✅ yt-dlp is installed (version: $(yt-dlp --version))"
echo ""

# Ask for cookies file location
echo "Where is your youtube_cookies.txt file?"
echo "Tip: Use an absolute path like /home/user/youtube_cookies.txt"
read -p "Path to cookies file: " COOKIES_PATH

# Expand tilde to home directory
COOKIES_PATH="${COOKIES_PATH/#\~/$HOME}"

# Check if file exists
if [ ! -f "$COOKIES_PATH" ]; then
    echo ""
    echo "❌ File not found: $COOKIES_PATH"
    echo ""
    echo "Please export your YouTube cookies:"
    echo "1. Install browser extension:"
    echo "   Chrome: https://chromewebstore.google.com/detail/get-cookiestxt-locally/cclelndahbckbenkjhflpdbgdldlbecc"
    echo "   Firefox: https://addons.mozilla.org/en-US/firefox/addon/cookies-txt/"
    echo "2. Go to youtube.com (logged in)"
    echo "3. Export cookies as youtube_cookies.txt"
    exit 1
fi

echo "✅ Found cookies file: $COOKIES_PATH"
echo ""

# Detect shell
SHELL_NAME=$(basename "$SHELL")
SHELL_RC=""

case "$SHELL_NAME" in
    bash)
        SHELL_RC="$HOME/.bashrc"
        ;;
    zsh)
        SHELL_RC="$HOME/.zshrc"
        ;;
    *)
        echo "⚠️  Unknown shell: $SHELL_NAME"
        echo "Please manually add this to your shell configuration:"
        echo ""
        echo "export YTDLP_COOKIES_PATH=\"$COOKIES_PATH\""
        exit 0
        ;;
esac

# Ask to add to shell config
echo "Add YTDLP_COOKIES_PATH to $SHELL_RC?"
read -p "This will make it permanent (y/n): " -n 1 -r
echo ""

if [[ $REPLY =~ ^[Yy]$ ]]; then
    # Check if already exists
    if grep -q "YTDLP_COOKIES_PATH" "$SHELL_RC"; then
        echo "⚠️  YTDLP_COOKIES_PATH already exists in $SHELL_RC"
        echo "Please edit manually if you want to change it."
    else
        echo "" >> "$SHELL_RC"
        echo "# YouTube cookies for Lexicon yt-dlp downloads" >> "$SHELL_RC"
        echo "export YTDLP_COOKIES_PATH=\"$COOKIES_PATH\"" >> "$SHELL_RC"
        echo "✅ Added to $SHELL_RC"
    fi
    
    # Export for current session
    export YTDLP_COOKIES_PATH="$COOKIES_PATH"
    
    echo ""
    echo "✅ Setup complete!"
    echo ""
    echo "For current terminal session, run:"
    echo "  source $SHELL_RC"
    echo ""
    echo "Or restart your terminal."
else
    echo ""
    echo "To use for this session only, run:"
    echo "  export YTDLP_COOKIES_PATH=\"$COOKIES_PATH\""
fi

echo ""
echo "Now you can start the Lexicon server:"
echo "  ./gradlew bootRun"
