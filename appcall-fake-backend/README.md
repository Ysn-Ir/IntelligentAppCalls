## Connecting a Physical Android Device

When using an Android emulator, the app connects to the mock backend through `10.0.2.2:8000`. However, a physical Android device cannot access your computer using this address.

To test on a real device, follow these steps.

### 1. Find Your Computer's Local IP Address

Open **PowerShell** or **Command Prompt** and run:

```cmd
ipconfig
```

Locate your active network adapter (usually **Wi-Fi**) and note its **IPv4 Address**, for example:

```
192.168.1.50
```

---

### 2. Update the Android App Configuration

#### REST API

Open:

```
app/src/main/java/com/example/appcall/di/NetworkModule.kt
```

Replace the emulator address:

```kotlin
private const val BASE_URL = "http://10.0.2.2:8000/api/v1/"
```

with your computer's local IP:

```kotlin
private const val BASE_URL = "http://192.168.1.50:8000/api/v1/"
```

> Replace `192.168.1.50` with your own IPv4 address.

---

#### Live Transcript WebSocket

If you are using the live transcript feature, also update:

```
app/src/main/java/com/example/appcall/data/calling/LiveTranscriptManager.kt
```

Replace:

```kotlin
val wsUrl = "ws://10.0.2.2:8000/api/v1/ws/calls/$callId/live-transcript"
```

with:

```kotlin
val wsUrl = "ws://192.168.1.50:8000/api/v1/ws/calls/$callId/live-transcript"
```

---

### 3. Network Requirements

#### Same Network

Ensure that:

- Your computer and Android phone are connected to the **same Wi-Fi network**.
- The mock backend is running on your computer.

---

#### Windows Firewall

Windows Firewall may block incoming connections on port **8000**.

To allow access, open **PowerShell as Administrator** and run:

```powershell
New-NetFirewallRule -DisplayName "FastAPI Mock Server" `
    -Direction Inbound `
    -LocalPort 8000 `
    -Protocol TCP `
    -Action Allow
```

---

#### Cleartext HTTP

The mock backend uses **HTTP** instead of **HTTPS**.

The Android application is already configured with:

```xml
android:usesCleartextTraffic="true"
```

inside the `<application>` tag of `AndroidManifest.xml`, so no additional changes are required.

---

### Emulator vs Physical Device

| Environment | Base URL |
|-------------|----------|
| Android Emulator | `http://10.0.2.2:8000/api/v1/` |
| Physical Android Device | `http://<YOUR_LOCAL_IP>:8000/api/v1/` |

Likewise, use:

- Emulator: `ws://10.0.2.2:8000/...`
- Physical device: `ws://<YOUR_LOCAL_IP>:8000/...`
