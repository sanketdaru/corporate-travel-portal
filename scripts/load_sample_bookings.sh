#!/usr/bin/env bash

set -euo pipefail

############################################
# Defaults
############################################

TOTAL_RECORDS=100

############################################
# Usage
############################################

usage() {
  cat <<EOF
Usage:
  $0 --host HOST --port PORT --db DATABASE --user USER --password PASSWORD [--records N]

Example:
  $0 --host db.example.com --port 5432 --db travel_db --user postgres --password secret --records 200
EOF
  exit 1
}

############################################
# Parse Arguments
############################################

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      export PGHOST="$2"
      shift 2
      ;;
    --port)
      export PGPORT="$2"
      shift 2
      ;;
    --db)
      export PGDATABASE="$2"
      shift 2
      ;;
    --user)
      export PGUSER="$2"
      shift 2
      ;;
    --password)
      export PGPASSWORD="$2"
      shift 2
      ;;
    --records)
      TOTAL_RECORDS="$2"
      shift 2
      ;;
    *)
      echo "Unknown parameter: $1"
      usage
      ;;
  esac
done

############################################
# Validation
############################################

: "${PGHOST:?Missing --host}"
: "${PGPORT:?Missing --port}"
: "${PGDATABASE:?Missing --db}"
: "${PGUSER:?Missing --user}"
: "${PGPASSWORD:?Missing --password}"

############################################
# Booking Domain Config
############################################

BOOKING_TYPES=("FLIGHT" "HOTEL" "CAR" "TRAIN" "PACKAGE")

TENANT_A_USERS=("alice.employee" "bob.manager" "carol.executive" "dave.assistant")
TENANT_B_USERS=("eve.employee")

TENANTS=("tenant-a" "tenant-b")

############################################
# Helper Functions
############################################

rand_element() {
  local arr=("$@")
  echo "${arr[RANDOM % ${#arr[@]}]}"
}

rand_date() {
  local offset=$((RANDOM % 150 - 30))
  date -d "$offset day" +"%Y-%m-%d"
}

calc_end_date() {
  local start="$1"
  local duration=$((RANDOM % 10 + 1))
  date -d "$start + $duration day" +"%Y-%m-%d"
}

amount_for_type() {
  case "$1" in
    FLIGHT)  awk -v min=5000  -v max=40000 'BEGIN{srand(); printf "%.2f", min+rand()*(max-min)}' ;;
    HOTEL)   awk -v min=3000  -v max=25000 'BEGIN{srand(); printf "%.2f", min+rand()*(max-min)}' ;;
    CAR)     awk -v min=1000  -v max=8000  'BEGIN{srand(); printf "%.2f", min+rand()*(max-min)}' ;;
    TRAIN)   awk -v min=800   -v max=6000  'BEGIN{srand(); printf "%.2f", min+rand()*(max-min)}' ;;
    PACKAGE) awk -v min=15000 -v max=90000 'BEGIN{srand(); printf "%.2f", min+rand()*(max-min)}' ;;
  esac
}

destination_for_type() {
  case "$1" in
    FLIGHT|PACKAGE) rand_element "Singapore" "Dubai" "London" "New York" "Goa" ;;
    HOTEL)          rand_element "Mumbai" "Bangalore" "Pune" "Delhi" "Jaipur" ;;
    CAR|TRAIN)      rand_element "Pune" "Nashik" "Surat" "Ahmedabad" "Chennai" ;;
  esac
}

json_details() {
  local type="$1"

  case "$type" in
    FLIGHT)
      echo "jsonb_build_object(
        'airline', (ARRAY['Air India','IndiGo','Vistara','SpiceJet'])[floor(random()*4+1)],
        'seat_class', (ARRAY['ECONOMY','PREMIUM_ECONOMY','BUSINESS'])[floor(random()*3+1)],
        'meal_pref', (ARRAY['VEG','NON_VEG','JAIN'])[floor(random()*3+1)]
      )"
      ;;
    HOTEL)
      echo "jsonb_build_object(
        'hotel_name', (ARRAY['Taj','Marriott','Hyatt','Hilton'])[floor(random()*4+1)],
        'room_type', (ARRAY['STANDARD','DELUXE','SUITE'])[floor(random()*3+1)],
        'breakfast_included', (random() > 0.5)
      )"
      ;;
    CAR)
      echo "jsonb_build_object(
        'vendor', (ARRAY['Zoomcar','Avis','Hertz'])[floor(random()*3+1)],
        'car_type', (ARRAY['HATCHBACK','SEDAN','SUV'])[floor(random()*3+1)],
        'fuel_type', (ARRAY['PETROL','DIESEL','EV'])[floor(random()*3+1)]
      )"
      ;;
    TRAIN)
      echo "jsonb_build_object(
        'coach_class', (ARRAY['SL','3AC','2AC','1AC'])[floor(random()*4+1)],
        'meal_included', (random() > 0.5)
      )"
      ;;
    PACKAGE)
      echo "jsonb_build_object(
        'package_type', (ARRAY['LEISURE','BUSINESS','HONEYMOON','ADVENTURE'])[floor(random()*4+1)],
        'includes_flight', (random() > 0.3),
        'includes_hotel', true
      )"
      ;;
  esac
}

############################################
# Ensure Extension Exists
############################################

psql -v ON_ERROR_STOP=1 <<SQL
CREATE EXTENSION IF NOT EXISTS pgcrypto;
SQL

############################################
# Data Generation
############################################

for ((i=1; i<=TOTAL_RECORDS; i++))
do
  tenant=$(rand_element "${TENANTS[@]}")
  booking_type=$(rand_element "${BOOKING_TYPES[@]}")

  if [[ "$tenant" == "tenant-a" ]]; then
    user=$(rand_element "${TENANT_A_USERS[@]}")
  else
    user=$(rand_element "${TENANT_B_USERS[@]}")
  fi

  start_date=$(rand_date)
  end_date=$(calc_end_date "$start_date")
  amount=$(amount_for_type "$booking_type")
  destination=$(destination_for_type "$booking_type")
  details_sql=$(json_details "$booking_type")

  psql -v ON_ERROR_STOP=1 <<SQL
INSERT INTO travel.bookings (
  tenant_id,
  user_id,
  booking_type,
  destination,
  start_date,
  end_date,
  status,
  total_amount,
  details,
  created_by,
  updated_by
)
VALUES (
  '$tenant',
  '$user',
  '$booking_type',
  '$destination',
  '$start_date',
  '$end_date',
  'DRAFT',
  $amount,
  $details_sql,
  '$user',
  '$user'
);
SQL

done

echo "Successfully inserted $TOTAL_RECORDS bookings."
