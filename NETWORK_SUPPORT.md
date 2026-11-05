<div align="center">

# Network support

[Kepler Slicer](README.md) · Printer hosts and print services

</div>

Kepler Slicer sends sliced G-code to printers and print services over the network, using the same host types as
OrcaSlicer. Printers are added in **Preferences → Network**; the **Device** screen shows live status and controls.

<table>
<tr>
<td>
<strong>⚠️ Please note.</strong> Every protocol is tested against local simulators (<code>tools/test_*.ps1</code>),
not against every real printer or account. A plain upload never starts a print: starting, pausing, stopping and
moving axes are separate actions that you confirm, and each command is sent once and never retried.
</td>
</tr>
</table>

## Overview

| Symbol | Meaning |
|:---:|---|
| ✅ | Supported |
| ➖ | Not available for this host |

| Host | Upload | Start print | Live status | Pause / resume / stop | Speed, light, axes |
|---|:---:|:---:|:---:|:---:|:---:|
| **Bambu Lab — LAN** | ✅ | ✅ | ✅ (with AMS) | ✅ | ✅ |
| **Bambu Lab — cloud** | ✅ | ➖ | ✅ (with AMS) | ✅ | ✅ |
| OctoPrint | ✅ | ✅ | ✅ | ✅ | ✅ |
| Klipper / Moonraker | ✅ | ✅ | ✅ | ✅ | ✅ |
| PrusaLink | ✅ | ➖ | ✅ | ✅ | ✅ |
| Duet (RRF and DSF) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Repetier Server | ✅ | ➖ | ✅ | ✅ | ➖ |
| Creality OS (K1 / K2) | ✅ | ➖ | ✅ | ✅ | ✅ |
| Elegoo Neptune | ✅ | ➖ | ✅ | ✅ | ➖ |
| Elegoo Centauri Carbon (SDCP) | ✅ | ➖ | ✅ | ✅ | ✅ |
| Elegoo Centauri Carbon 2 | ✅ | ➖ | ✅ | ✅ | ✅ |
| UltiMaker | ✅ | ✅ | ➖ | ➖ | ➖ |
| PrusaConnect | ✅ | ➖ | ➖ | ➖ | ➖ |
| FlashAir, AstroBox, MKS, ESP3D, Flashforge, Creality Print | ✅ | ➖ | ➖ | ➖ | ➖ |
| Prusa SL1 / SL1S | `.sl1` / `.sl1s` only | ➖ | ➖ | ➖ | ➖ |
| SimplyPrint | ✅ | ➖ | ➖ | ➖ | ➖ |
| 3DPrinterOS | ✅ | ➖ | ➖ | ➖ | ➖ |
| Obico | ✅ | ➖ | ➖ | ➖ | ➖ |

## Bambu Lab

### LAN

| | |
|---|---|
| Connection | Printer IP address, **serial number** and **access code** (shown on the printer screen) |
| Discovery | Printers on the same Wi-Fi are found automatically (SSDP) |
| Status and commands | MQTT over TLS, port 8883, user `bblp` |
| Upload | Implicit FTPS, port 990 |
| Certificate | Pinned on first connection (trust on first use); a changed certificate is refused until you save the printer again |
| Network | Private (LAN) addresses only |

The printer must allow LAN access (LAN-only or developer mode, depending on the firmware). From the Device screen
you can start the uploaded file, pause, resume and stop, change the speed, switch the chamber light and auxiliary fan,
and move the axes while idle. Both the global and the China versions of the printers are supported.

### Cloud

Sign in with your Bambu Lab account (password or e-mail code; the region, including China, is set in
Preferences). The app lists your printers, shows status and AMS, and sends commands through the account MQTT broker
with certificate checks. Files are uploaded to your cloud storage; start the print from Bambu Handy or over LAN.

## Job control

| Host | How it works |
|---|---|
| OctoPrint | `api/job`, `api/printer/printhead`, `api/printer/command`; upload with `print=false`, then a separate start |
| Klipper / Moonraker | `printer/print/*`, `printer/gcode/script`; upload, then a separate start |
| PrusaLink | `api/v1/job/{id}`; API key or Digest authentication |
| Duet | RepRapFirmware (HTTP) and DSF; speed and axes by G-code, start of an uploaded file with `M32` |
| Repetier Server | Status and job control for the selected printer or group |
| Creality OS | WebSocket on port 9999 (K1 / K2), including the light |
| Elegoo | Neptune through the OctoPrint API; Centauri Carbon through SDCP; Centauri Carbon 2 through its MQTT broker (port 1883, LAN-only mode) |
| UltiMaker | Digest authentication with pairing code; its upload always starts the print, so it is a separate “upload and print” action |

Speed changes use `M220`. Axis moves are allowed only while the printer is idle.

## Print services

| Service | Sign-in | What it does |
|---|---|---|
| SimplyPrint | Browser sign-in (OAuth with PKCE) | Uploads the file in chunks and returns a link to finish the import in SimplyPrint |
| 3DPrinterOS | Browser sign-in | Uploads the file to a project, optionally sets its type |
| Obico | API token | Lists your printers and uploads the file without starting it |

## Testing

```bash
pwsh tools/run_host_tests.ps1 -JsonJar <path to org.json jar>
```

The suites (`test_bambu_lan.ps1`, `test_printer_control.ps1` and others) run every protocol against loopback
simulators, including wrong passwords, refused certificates and cancelled uploads.

## References

- [OrcaSlicer at the pinned commit](https://github.com/OrcaSlicer/OrcaSlicer/tree/6d2869c430284714f0a22570c38d3b359965a802) — host types and upload behaviour
- [Bambu Studio](https://github.com/bambulab/BambuStudio) — Bambu Lab printers
