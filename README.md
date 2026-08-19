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
├── PowerFixApplication.kt               # Application entry point & Supabase client container
├── data/
│   ├── Complaint.kt                     # Complaint work-order model with ETA computation
│   ├── UserProfile.kt                   # User entity (Customer, Worker, Admin)
│   ├── EmergencyRequest.kt              # Emergency SOS hazard request model
│   └── PowerFixPrefs.kt                 # Centralized preferences with automatic SUCS legacy migration
└── ui/
    ├── admin/
    │   ├── AdminDashboardFragment.kt    # Dispatcher console
    │   ├── ComplaintsListFragment.kt    # Complaint assignment & admin reply dialog
    │   └── EmergencyRequestsFragment.kt # Hazard SOS management dialog
    ├── auth/
    │   ├── LoginFragment.kt             # Unified role-aware authentication
    │   └── RegisterFragment.kt          # Customer self-registration (server-side role guard)
    ├── common/
    │   ├── AuthUtil.kt                  # Secure sign-out and session clearance
    │   ├── ComplaintAdapter.kt          # RecyclerView adapter with priority/status badges & replies
    │   └── EmergencyRequestAdapter.kt   # SOS request adapter
    ├── customer/
    │   ├── CustomerDashboardFragment.kt # Customer quick actions
    │   ├── RegisterComplaintFragment.kt # Pre-filled smart complaint lodging
    │   ├── ComplaintTrackingFragment.kt # Real-time tracking & detail inspection
    │   └── EmergencyContactFragment.kt  # SOS emergency broadcast
    └── worker/
        ├── WorkerDashboardFragment.kt   # Technician workbench
        ├── WorkerAvailabilityFragment.kt# Live availability status toggle (Active/Inactive)
        └── WorkerTasksFragment.kt       # Assigned work orders & task progress dialog
```

---

## 🔄 Project Rename & Backward Compatibility (from "sucs" to "power-fix")

The project has been migrated from the legacy name `"sucs"` to `"power-fix"` with zero disruption to existing databases or user sessions:

1. **Package & Namespace**: Renamed to `com.example.powerfix`.
2. **Preferences Migration (`PowerFixPrefs`)**: Automatically detects existing `sucs_prefs` and migrates cached credentials and worker availability into `powerfix_prefs` while maintaining dual-write support.
3. **Application Alias**: `typealias SucsApplication = PowerFixApplication` preserved for any legacy reflective/manifest references.
4. **Theme Aliases**: `Theme.SUCS` is preserved as a style alias pointing to `Theme.PowerFix`.
5. **Database Interoperability**: Preserves all existing table structures (`profiles`, `complaints`, `emergency_requests`) with enhanced column definitions and triggers.

---

## ✨ Newly Added Features (Not in Legacy SUCS)

1. **Smart Electricity Complaint Presets**: Specialized electrical fault categories (Blackouts, Transformer Explosions, Voltage Surges, Sparks/Hazards, Meter Faults, Wire Snapping).
2. **Live ETA & Dispatch Estimation Engine**: Dynamic calculation of technician response times based on priority (`Urgent`, `High`, `Medium`, `Low`) and resolution progress.
3. **Interactive Worker Dispatch Dialog**: Administrators can view real-time worker availability (`Available` vs `Busy`) directly when assigning tickets.
4. **Realtime Technician Task Progress**: Workers can update task progress (`Assigned` → `In Progress` → `Resolved`) with status badges.
5. **Automatic Profile Pre-population**: Customer registration and complaint forms automatically pre-fill user name, mobile number, and address from their account.

---

## 🛠️ Database Setup (Firebase)

1. **Firestore Collections**:
   - `profiles`: Stores user data. Document ID is the Firebase Auth UID.
   - `complaints`: Stores electricity complaints.
   - `emergency_requests`: Stores SOS hazard alerts.
   - `tneb_ids`: Stores valid TNEB IDs for verification.

2. **TNEB Verification Setup**:
   Create a document in `tneb_ids` for every valid ID:
   - **Document ID**: `22556469956`
   - **Fields**:
     - `role`: "customer"
     - `is_registered`: false

3. **Security Rules**:
   Ensure you set up Firestore Security Rules to protect your data based on user roles.

---

## 🚀 Build and Run

```bash
# Clean and assemble debug APK
./gradlew.bat assembleDebug

# Install on connected Android device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.powerfix/.MainActivity
```
