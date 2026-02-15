# Device Owner App — Documentation

This folder contains **technical documentation** for the Android Device Owner application. Documents are in **semantic order**: start at 01 and follow the sequence to understand the project from overview → features → setup → APIs → behavior → reference.

---

## Reading order (recommended)

| # | Document | Description |
|---|----------|-------------|
| **01** | [Device Owner — Overview](01-device-owner-overview.md) | Purpose, architecture, security modes |
| **02** | [Features Implemented](02-features-implemented.md) | What the app does (registration, heartbeat, lock, tamper, etc.) |
| **03** | [Compatibility](03-compatibility.md) | Device requirements (Android 13+, stock/supported) |
| **04** | [Device Installation](04-device-installation.md) | Provisioning and in-app setup flow |
| **05** | [Device Registration](05-device-registration.md) | Loan number, data collection, device_id |
| **06** | [APIs](06-apis.md) | Backend endpoints reference (registration, heartbeat, tamper, logs, bugs) |
| **07** | [Device Heartbeat](07-device-heartbeat.md) | 30s heartbeat, request/response, where it runs |
| **08** | [Hard Lock and Soft Lock](08-hard-lock-and-soft-lock.md) | Hard lock (kiosk) vs soft lock (reminder overlay) |
| **09** | [Device Tamper](09-device-tamper.md) | Tamper detection, response, server report |
| **10** | [Local Databases](10-local-databases.md) | DeviceOwnerDatabase, entities, offline queue |
| **11** | [Device Logs and Bugs](11-device-logs-and-bugs.md) | LogManager, ServerBugAndLogReporter, tech API |
| **12** | [Agent Update](12-agent-update.md) | Auto-update from GitHub Releases |
| **13** | [Services](13-services.md) | All configured Android services |
| **14** | [Folder Structure](14-folder-structure.md) | Project source layout |

---

## Quick links by topic

- **Getting started**: 01 Overview → 02 Features → 03 Compatibility  
- **Setup**: 04 Installation → 05 Registration  
- **Backend**: 06 APIs  
- **Ongoing behavior**: 07 Heartbeat → 08 Hard/Soft Lock → 09 Tamper  
- **Data & observability**: 10 Local DBs → 11 Logs & Bugs  
- **Maintenance**: 12 Agent Update  
- **Reference**: 13 Services → 14 Folder Structure  

---

*Start with [01-device-owner-overview.md](01-device-owner-overview.md) for a high-level picture, then follow the list above to go through the docs in order (akiweza kupitia docs kwa mpangilio).*
