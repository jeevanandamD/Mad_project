# Firebase Configuration Guide for PowerFix

This document details the manual steps required in the Firebase Console to enable the backend for the PowerFix app.

---

## 1. Authentication
1. Go to **Build > Authentication**.
2. Click **Get Started**.
3. Under **Sign-in method**, click **Add new provider**.
4. Select **Email/Password**, enable it, and click **Save**.
5. *(Optional for Dev)*: Disable **"Confirm email"** to allow immediate login after registration.

---

## 2. Cloud Firestore
1. Go to **Build > Firestore Database**.
2. Click **Create database**.
3. Select a location and start in **Production mode**.

### 2.1. Seed TNEB ID Registry
Create a collection named `tneb_ids` to store valid IDs for registration verification.

| Document ID | Field: `role` (string) | Field: `is_registered` (boolean) |
| :--- | :--- | :--- |
| `22556469956` | `customer` | `false` |
| `CUST-1234-5678` | `customer` | `false` |
| `WK-000123` | `worker` | `false` |
| `WK-000456` | `worker` | `false` |

---

## 3. Security Rules
Go to the **Rules** tab in Firestore and deploy the following:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Profiles: User manage own, Staff read all
    match /profiles/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
      allow read: if request.auth != null && 
        get(/databases/$(database)/documents/profiles/$(request.auth.uid)).data.role in ['admin', 'worker'];
    }

    // Complaints: User manage own, Staff manage all
    match /complaints/{complaintId} {
      allow create: if request.auth != null;
      allow read: if request.auth != null && (
        resource.data.customer_id == request.auth.uid || 
        get(/databases/$(database)/documents/profiles/$(request.auth.uid)).data.role in ['admin', 'worker']
      );
      allow update: if request.auth != null && 
        get(/databases/$(database)/documents/profiles/$(request.auth.uid)).data.role in ['admin', 'worker'];
    }

    // TNEB IDs: Verification access
    match /tneb_ids/{tnebId} {
      allow read: if true;
      allow update: if request.auth != null;
    }
    
    // SOS Alerts: Staff only
    match /emergency_requests/{reqId} {
      allow create: if request.auth != null;
      allow read, write: if request.auth != null && 
        get(/databases/$(database)/documents/profiles/$(request.auth.uid)).data.role == 'admin';
    }
  }
}
```

---

## 4. App Connection
1. Go to **Project Settings (⚙️) > General**.
2. Download `google-services.json` and place it in the `app/` folder of your Android Studio project.
3. Sync and build.
