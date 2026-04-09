# ShadowCheck-Web Integration Guide

This document details the modifications made to the WiGLE Wireless Wardriving application to enable it as a primary data source for the `shadowcheck-web` ingest pipeline.

## Overview
The application has been enhanced to support direct, authenticated uploads of its SQLite database to a ShadowCheck-compatible S3 ingest API. Additionally, it now captures critical device and mission metadata.

## Configuration
Integration settings are centrally managed in `wiglewifiwardriving/src/main/java/net/wigle/wigleandroid/util/UrlConfig.java`.

- **`SHADOWCHECK_POST_URL`**: Must point at `shadowcheck-web` ingest base `/api/v1/ingest` or its `/request-upload` endpoint.
- **`SHADOWCHECK_API_KEY`**: The shared secret or token used to authenticate the upload request.

The uploader uses the same 3-step presigned upload contract as `shadowcheck-web`:
- `POST /api/v1/ingest/request-upload` with JSON including `fileName`, `filesize`, and optional `case_id`.
- `PUT` the SQLite database bytes to the returned `uploadUrl` without an Authorization header.
- `POST /api/v1/ingest/complete` with `s3Key`, `uploadId`, and device metadata so the server can verify the object and queue ETL.

## SC Collector Mode
For professional field operations, users can enable **"SC Collector Mode"** in Settings. This provides:
- **Visual Alignment**: A modern "Slate & Amber" UI matching the ShadowCheck-Web dashboard.
- **High Performance**: Disables the live network list and maps to maximize CPU for write speeds and tracker detection.
- **Mission Labeling**: Allows setting a `Case ID` that is persisted in every record of the database.

## Data Schema (Database Version 7)
The local SQLite database (`wiglewifi.sqlite`) has been upgraded to **Version 7** to include mission-critical metadata.

### New Columns in `route` Table:
| Column | Type | Description |
| :--- | :--- | :--- |
| `case_id` | TEXT | The mission or project name (e.g., "OP_ALPHA_01"). |
| `device_model` | TEXT | The hardware model name (e.g., "Pixel 7 Pro"). |
| `device_brand` | TEXT | The manufacturer brand (e.g., "Google"). |
| `os_release` | TEXT | The Android OS version (e.g., "14"). |
| `device_id` | TEXT | The unique `ANDROID_ID` for the device instance. |
| `barometer` | DOUBLE | Atmospheric pressure in hPa (millibars), if sensor is available. |

## Manual Data Extraction
On modern Android devices (API 30+), the database is stored in a location accessible to standard file managers and ADB without root access:

**Path**: `/sdcard/Android/data/net.wigle.wigleandroid/files/wiglewifi/wiglewifi.sqlite`
