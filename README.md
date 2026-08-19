# PowerFix – Smart Electricity Complaint & Worker Tracking System

PowerFix is a role-based electricity breakdown reporting, field technician dispatching, and live tracking platform.

---

## ⚡ What is PowerFix?

PowerFix provides dedicated workflows for three key user roles:
1. **Customer**: Report power outages, meter faults, voltage surges, and sparks; track assigned field technicians in real-time.
2. **Field Worker / Technician**: Toggle duty availability, receive dispatch tickets, navigate to breakdowns, and submit work completion updates.
3. **Administrator / Dispatcher**: Triage incoming grid complaints, assign available field technicians, write resolution updates, and manage emergency hazard alerts.

---

## 🏗️ Architecture & Tech Stack

- **Platform**: Android Native (Kotlin 2.1.21, minSdk 30, targetSdk 34)
- **UI Framework**: Android Views + Material Components (Material 3), ViewBinding
- **Backend & Database**: **Firebase** (Auth, Cloud Firestore, Storage)
- **Serialization**: `kotlinx.serialization` (for local prefs) + Firebase Firestore Mapper
- **Networking**: Firebase SDK + `ktor-client-android` (for auxiliary APIs)

---

## 📂 Project Structure

```
app/src/main/java/com/example/powerfix/
├── MainActivity.kt                      # Main router based on authenticated user role
├── PowerFixApplication.kt               # Application entry point & Firebase initialization
├── data/
│   ├── AuthRepository.kt                # Firebase Auth & Profile logic
│   ├── ComplaintRepository.kt           # Firestore Complaint management
│   ├── EmergencyRepository.kt           # Firestore SOS hazard tracking
│   ├── Complaint.kt                     # Complaint model with ETA computation (Firestore mapping)
│   ├── UserProfile.kt                   # User entity (Customer, Worker, Admin)
│   ├── EmergencyRequest.kt              # Emergency SOS hazard request model
│   └── PowerFixPrefs.kt                 # Centralized preferences with legacy SUCS migration
└── ui/
    ├── admin/
    │   ├── AdminDashboardFragment.kt    # Dispatcher console
    │   ├── ComplaintsListFragment.kt    # Complaint assignment & admin reply
    │   └── EmergencyRequestsFragment.kt # Hazard SOS management
    ├── auth/
    │   ├── LoginFragment.kt             # Unified role-aware authentication
    │   └── RegisterFragment.kt          # Customer & Worker self-registration
    ├── common/
    │   ├── AuthUtil.kt                  # Secure sign-out and session clearance
    │   ├── ComplaintAdapter.kt          # RecyclerView adapter for complaints
    │   └── EmergencyRequestAdapter.kt   # SOS request adapter
    ├── customer/
    │   ├── CustomerDashboardFragment.kt # Customer quick actions
    │   ├── RegisterComplaintFragment.kt # Pre-filled smart complaint lodging
    │   ├── ComplaintTrackingFragment.kt # Real-time tracking & detail inspection
    │   └── EmergencyContactFragment.kt  # SOS emergency broadcast
    └── worker/
        ├── WorkerDashboardFragment.kt   # Technician workbench
        ├── WorkerAvailabilityFragment.kt# Live availability status toggle (Active/Inactive)
        └── WorkerTasksFragment.kt       # Assigned work orders & task progress
```

---

## 🔄 Backward Compatibility (from "sucs" to "power-fix")

The project has been migrated from the legacy name `"sucs"` to `"power-fix"` with zero disruption to user sessions:

1. **Package & Namespace**: Renamed to `com.example.powerfix`.
2. **Preferences Migration (`PowerFixPrefs`)**: Automatically detects existing `sucs_prefs` and migrates cached credentials into `powerfix_prefs`.
3. **Database Migration**: All logic has been ported from Supabase to **Firebase Firestore**.

---

## ✨ Key Features

1. **Smart Electricity Complaint Presets**: Specialized electrical fault categories.
2. **Live ETA & Dispatch Estimation Engine**: Dynamic calculation of technician response times.
3. **Interactive Worker Dispatch Dialog**: Real-time worker availability visualization.
4. **Realtime Technician Task Progress**: Workers update task status with live listener feedback.
5. **Role-Based Navigation**: One-way navigation flow ensuring secure access to dashboards.

---

## 🛠️ Database Setup (Firebase)

See [FIREBASE_CONFIG_GUIDE.md](FIREBASE_CONFIG_GUIDE.md) for detailed setup instructions including:
- Firestore Collections (`profiles`, `complaints`, `emergency_requests`, `tneb_ids`)
- Security Rules (Role-based access control)
- Authentication settings

---

## 🚀 Build and Run

1. Place your `google-services.json` in the `app/` directory.
2. Clean and assemble debug APK:
   ```bash
   ./gradlew.bat assembleDebug
   ```
3. Install and run:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.example.powerfix/.MainActivity
   ```
