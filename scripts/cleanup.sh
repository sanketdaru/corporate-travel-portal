#!/bin/bash

# Cleanup script - stops and removes all containers and volumes
# Usage: ./scripts/cleanup.sh [--volumes]

echo "🧹 Cleaning up Corporate Travel Platform..."
echo ""

# Stop all services
echo "⏹️  Stopping all services..."
docker-compose down

# Remove volumes if --volumes flag is provided
if [ "$1" == "--volumes" ]; then
    echo "🗑️  Removing volumes (this will delete all data)..."
    docker-compose down -v
    echo "✅ Volumes removed"
else
    echo "ℹ️  Volumes preserved. Use './scripts/cleanup.sh --volumes' to remove data."
fi

echo ""
echo "✅ Cleanup complete!"
echo ""
echo "📋 To start fresh:"
echo "   ./scripts/setup-local.sh"
