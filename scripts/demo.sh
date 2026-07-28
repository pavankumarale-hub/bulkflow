#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR/.."
API_BASE="${API_BASE:-http://localhost:8080}"
MINIO_URL="${MINIO_URL:-http://localhost:9000}"
MINIO_USER="${MINIO_USER:-minioadmin}"
MINIO_PASS="${MINIO_PASS:-minioadmin}"
INBOX_BUCKET="bulkflow-inbox"

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║             BULKFLOW — DEMO SCRIPT                       ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

# Step 1: Generate the bulk sample data (10,000 accounts with 12 bad rows)
echo "▶ Generating sample data..."
python3 "$ROOT/scripts/generate_sample_data.py"
echo ""

# Step 2: Wait for BulkFlow to be ready
echo "▶ Waiting for BulkFlow API to be ready..."
for i in $(seq 1 40); do
  if curl -sf "$API_BASE/actuator/health" >/dev/null 2>&1; then
    echo "  ✓ BulkFlow is up"
    break
  fi
  if [ "$i" -eq 40 ]; then
    echo "  ✗ BulkFlow did not start in time. Run 'make up' first, then retry."
    exit 1
  fi
  printf "  Waiting... (%d/40)\r" "$i"
  sleep 3
done
echo ""

# Step 3: Upload via API (no mc dependency required)
echo "▶ Uploading accounts_bulk.csv via REST API..."
UPLOAD_RESPONSE=$(curl -sf -X POST "$API_BASE/api/batch/upload" \
  -F "file=@$ROOT/sample-data/accounts_bulk.csv" \
  -F "feedType=ACCOUNTS" 2>/dev/null || echo '{"error":"Upload failed"}')

echo "$UPLOAD_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$UPLOAD_RESPONSE"
BATCH_ID=$(echo "$UPLOAD_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('batchId',''))" 2>/dev/null || true)

if [ -z "$BATCH_ID" ]; then
  echo "  Could not extract batchId — job may still be running. Check: $API_BASE/api/metrics/batches"
  exit 1
fi

echo "  Batch ID: $BATCH_ID"
echo ""

# Step 4: Poll for completion
echo "▶ Waiting for batch to complete..."
STATUS="RUNNING"
for i in $(seq 1 60); do
  sleep 4
  BATCH_RESPONSE=$(curl -sf "$API_BASE/api/metrics/batches/$BATCH_ID" 2>/dev/null || echo '{}')
  STATUS=$(echo "$BATCH_RESPONSE" | python3 -c \
    "import sys,json; print(json.load(sys.stdin).get('status','UNKNOWN'))" 2>/dev/null || echo "UNKNOWN")

  if [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "FAILED" ] || [ "$STATUS" = "STOPPED" ]; then
    echo "  ✓ Batch status: $STATUS"
    break
  fi
  printf "  Status: %s (poll %d/60)...\r" "$STATUS" "$i"
done
echo ""

# Step 5: Print the overall metrics summary
echo "▶ Overall Metrics Summary:"
curl -sf "$API_BASE/api/metrics/summary" | python3 -m json.tool || true
echo ""

# Step 6: Print the failure breakdown for this specific batch
echo "▶ Failure Breakdown (batch: $BATCH_ID):"
BREAKDOWN=$(curl -sf "$API_BASE/api/dead-letter/$BATCH_ID/breakdown" 2>/dev/null || echo '{}')
echo "$BREAKDOWN" | python3 -m json.tool || echo "$BREAKDOWN"

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║  DEMO COMPLETE                                           ║"
echo "║                                                          ║"
echo "║  Check the app logs for the formatted batch summary box  ║"
echo "║                                                          ║"
echo "║  Useful commands:                                        ║"
echo "║    make metrics     — overall pipeline success rate      ║"
echo "║    make deadletter  — browse all dead-lettered records   ║"
echo "║    make logs        — tail live application logs         ║"
echo "╚══════════════════════════════════════════════════════════╝"
