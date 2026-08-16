#!/bin/bash
# TV Tenderr - Setup Script
set -e

echo "🎬 TV Tenderr Setup"
echo "==================="

# Check Python
if ! command -v python3 &> /dev/null; then
    echo "❌ Python3 not found. Install it first."
    exit 1
fi

# Install Python dependencies
echo "📦 Installing Python dependencies..."
pip3 install --user fastapi uvicorn httpx python-dotenv 2>/dev/null || \
pip3 install --break-system-packages fastapi uvicorn httpx python-dotenv

# Create .env if it doesn't exist
if [ ! -f .env ]; then
    echo "📝 Creating .env from template..."
    cp .env.example .env
    echo ""
    echo "⚠️  Edit .env with your actual API keys and URLs:"
    echo "   nano .env"
    echo ""
else
    echo "✅ .env already exists"
fi

# Create data directory
mkdir -p data

# Install systemd service (optional)
if [ "$1" = "--service" ]; then
    echo "🔧 Installing systemd service..."
    sudo cp movie-swipe.service /etc/systemd/system/
    sudo systemctl daemon-reload
    sudo systemctl enable movie-swipe
    sudo systemctl start movie-swipe
    echo "✅ Service installed and started"
else
    echo ""
    echo "To run manually:"
    echo "  python3 backend.py"
    echo ""
    echo "To install as a service:"
    echo "  ./setup.sh --service"
fi

echo ""
echo "✅ Setup complete!"
echo ""
echo "Next steps:"
echo "  1. Edit .env with your API keys"
echo "  2. Run: python3 backend.py"
echo "  3. Install the APK on your phone"
echo "  4. Enter the server URL in the app"
