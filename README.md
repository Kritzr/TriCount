<div align="center">

<!-- Animated banner -->
<img src="https://capsule-render.vercel.app/api?type=waving&color=4F52B2&height=200&section=header&text=Tricount&fontSize=80&fontAlignY=35&animation=fadeIn&fontColor=ffffff&desc=Shared%20Expense%20Splitting%20App&descAlignY=60&descSize=22" width="100%"/>

<br/>

<!-- Badges -->
![Android](https://img.shields.io/badge/Android-Jetpack%20Compose-4F52B2?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-MVVM-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-Auth%20%2B%20Firestore-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)
![Room](https://img.shields.io/badge/Room-v15-4F52B2?style=for-the-badge&logo=sqlite&logoColor=white)

<br/>

> 🎉 **My first complete Android app**

</div>

---

## ✨ What is Tricount?

Tricount is a **shared expense splitting app** (think Splitwise) built entirely with **Jetpack Compose**. Split bills, track who owes whom, settle debts, and sync everything across devices in real-time — all wrapped in a clean, dark-mode-ready UI.

---

## 🚀 Features

<details open>
<summary><b>💸 Expense Management</b></summary>

- Add expenses with **equal / percentage / parts** splitting
- **11 predefined categories** for expense organisation
- Search, archive, and browse expense history
- Edit or delete any expense

</details>

<details open>
<summary><b>👥 Groups & Members</b></summary>

- Create shared expense groups (Tricounts)
- Invite members via a **6-character join code**
- Placeholder users for members who haven't registered yet
- Favourite and archive Tricounts

</details>

<details open>
<summary><b>📊 Balances & Settlements</b></summary>

- Real-time balance tracking — see exactly who owes whom
- **Greedy settlement algorithm** that minimises the number of transactions needed to settle all debts
- Track payment history and mark debts as settled

</details>

<details open>
<summary><b>💱 Currency Converter</b></summary>

- Enter expenses in **any currency**, stored internally in INR
- 4-level fallback rate fetching: cache → open.er-api.com → frankfurter.dev → hardcoded rates
- Live conversion banner while typing

</details>

<details open>
<summary><b>🔒 Authentication</b></summary>

- **Google Sign-In** and **email/password** with OTP email verification
- Secure session management with `SharedPreferences`
- Firebase UID persisted across all auth flows

</details>

<details open>
<summary><b>☁️ Cloud Sync</b></summary>

- **Offline-first** — all reads from Room, writes pushed to Firestore in the background
- Automatic sync on login; detects real internet vs captive portals
- Profile photos stored as Base64 in Firestore (no Firebase Storage needed)

</details>

<details open>
<summary><b>🔔 Notifications & Insights</b></summary>

- Push notifications for join requests and payment reminders (FCM)
- Expense analytics and charts in the Insights screen
- Dark mode support 🌙

</details>

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────┐
│                  View Layer                  │
│   Activities (Compose UI) + Composables      │
└──────────────────┬──────────────────────────┘
                   │ collectAsStateWithLifecycle()
┌──────────────────▼──────────────────────────┐
│              ViewModel Layer                 │
│   TricountViewModel  │  AuthViewModel        │
│   StateFlow<T> state management              │
└──────────────────┬──────────────────────────┘
                   │ suspend functions
┌──────────────────▼──────────────────────────┐
│             Repository Layer                 │
│  TricountRepository  │  PaymentRepository   │
│  FirebaseSyncRepository  │  SyncManager     │
└──────────┬───────────────────┬──────────────┘
           │                   │
┌──────────▼──────┐   ┌────────▼───────────────┐
│   Room DB v15   │   │   Cloud Firestore       │
│  7 entities     │   │  + Firebase Auth + FCM  │
└─────────────────┘   └────────────────────────┘
```

**Pattern:** MVVM + Repository  
**UI:** 100% Jetpack Compose (no XML layouts)  
**Navigation:** Explicit Intents between `ComponentActivity` screens  
**State:** `MutableStateFlow` in ViewModels, `collectAsStateWithLifecycle()` in composables

---

## 🗄️ Database

**Room v15** with 7 entities:

| Entity | Purpose |
|---|---|
| `UserEntity` | Registered users (local + Firebase) |
| `TricountEntity` | Expense groups with join codes |
| `TricountMemberCrossRef` | Many-to-many user ↔ group junction |
| `ExpenseEntity` | Individual expenses per group |
| `ExpenseSplitEntity` | Per-user shares (stored as integer ratios) |
| `PaymentEntity` | Recorded settlements between users |
| `TricountFavorite` | Per-user favourite groups |

All foreign keys use **`ON DELETE CASCADE`** so deleting a Tricount cleans up everything automatically.

---

## ☁️ Firebase Structure

```
users/{firebaseUid}
  ├── uid, name, email, nickname
  ├── photoBase64 (200×200px JPEG, 60% quality)
  └── fcmToken

tricounts/{tricountId}
  ├── name, description, joinCode, emoji, isArchived
  ├── creatorUid, members[]
  ├── expenses/{expenseId}
  │     └── splits[]
  ├── payments/{paymentId}
  ├── messages/{msgId}
  └── userMeta/{uid}  ← per-user favourite state

joinRequests/{tricountId}/pending/{uid}
notifications/{notifId}
```

---

## 💱 Currency Conversion

All expenses are stored in **INR**. Conversion uses USD as a pivot:

```
result = amount × rates[to] / rates[from]
```

| Priority | Source | Notes |
|---|---|---|
| 1 | In-memory cache | Only if fetched today |
| 2 | open.er-api.com | Free, no API key |
| 3 | frankfurter.dev | EUR-based, normalised to USD |
| 4 | Hardcoded `FALLBACK_RATES_USD_BASED` | Shows ⚠️ warning to user |

---

## 🎨 Design System

**Accent:** Microsoft Teams Blue palette  
**Font:** Lato (Regular + Bold, bundled)  
**Theme:** Jet-black dark mode / white light mode

| Token | Light | Dark |
|---|---|---|
| `primary` | `#4F52B2` | `#8B8CC8` |
| `surface` | `#FFFFFF` | `#000000` |
| `surfaceVariant` | `#F0EFF9` | `#1C1A2E` |
| `error` | `#BA1A1A` | `#FFB4AB` |

---

## 📱 Screens

| Screen | Purpose |
|---|---|
| `LoginActivity` | Email/password + Google Sign-In |

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/5e28ec5e-8179-41d9-9b35-c7259ff28c0a" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/2990f623-01b7-436b-a980-3809ca80378f" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/56166769-4dc4-4d3c-b832-fd7b1ccd699f" />

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/1f65ea89-563e-406b-ba3f-b178ee27533f" />

| `SignUpActivity` | New account with OTP email verification |
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/f72cbaec-531c-4c9b-982e-19fdf95125f6" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/fe3255bd-1ec3-44e5-8765-93063a1f763f" />

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/5f46413f-484e-4b0b-97b3-5887e4604240" />

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/4339e1f5-f465-48ad-9617-1f5809b856d8" />


<img width="440" height="264" alt="image" src="https://github.com/user-attachments/assets/f0879a45-1a5f-481a-8b6b-12abf4db99c2" />

| `HomeActivity` | Tricount list + Profile tabs |

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/76f0e3f8-229f-4f87-8048-bd892bf94a6c" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/cb6cd65c-579d-48f6-8034-cb7237a1d4df" />


<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/f966d784-ad5d-41d9-b9ab-ed7a2ea111c0" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/3fd80b0b-52b1-48c9-a8bd-808fe33cb404" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/85b8025b-0f4b-4997-b8b3-f2ca234fcf85" />

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/2c9605b5-0e5e-4fa7-b248-60e7211f2bb8" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/37ab5ed8-13bf-453e-87aa-a3c4e3e05ae8" />

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/9a4971ba-3912-4b64-bf15-052114fc40ed" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/0bbf7e8f-4442-4603-8edc-34d1485b29b9" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/2d84a2e7-f018-4393-9e28-b3aad028a3b5" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/e0bcef75-a0e0-4ca0-97ed-9c05edb340f6" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/0d9d6778-8a8c-4687-a80f-476cb8378813" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/31f2ee96-293c-4fc6-8d12-36897affdbb5" />


| `AddTricountActivity` |add new tricount, members, and description |
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/66b99e61-df6f-4f16-b533-21248672555a" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/8ea37710-e4db-4d1c-9504-4ebea6359f54" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/b077f704-4ff1-418a-8e43-1c234f39e9bb" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/cbf29179-ea33-4946-bbf8-eada953c105c" />

| `JoinTricountActivity` |join requests |
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/32816b13-aad2-4615-816c-cdc0e258c86f" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/bcfc9bf0-8089-468d-81c1-25ff9d2ce258" />

| `ArchivedTricountActivity` |Archived tricount to delete, unarchive |
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/ced9cead-8904-4eaf-85a1-2863b8d0cf82" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/afc13776-c850-4d08-b09e-6ef262b78b4d" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/87abef4d-4edd-4f96-9b28-2aec2661b38f" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/c2c1f58c-2190-40e0-a08d-df1487efdd98" />

| `TricountDetailActivity` | Expenses, Balances, Details tabs |
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/30c80186-ce65-429f-ba3a-2db00850a99a" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/2638fe34-58a8-4e31-8722-ce5c90a6a8c3" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/424ebcd7-62ce-4985-a41d-b4af8d32dae6" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/5f48db69-1cf5-44b6-afbe-b64eb7cf40f8" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/762a7c6a-bc5b-44d9-b0ff-8caf808729d6" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/8e6a2ffa-70fc-4633-9aa7-d49a2c0848ad" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/0259abc8-340b-472e-8708-c7e3fab64c84" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/5d944012-b0b5-4de3-8da8-ac919f736d64" />

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/86f6e478-7842-4a1a-8f41-85450a591501" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/80767199-92f7-47fa-87a9-a925a977d987" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/6ef76b8b-0a46-403e-b346-988bc4b221fa" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/855d04ed-fceb-4daf-acff-b534b4d345b3" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/92be88e9-143d-445b-adca-48e53bdbd53d" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/761ce2ca-c56a-42ab-b300-cfa2921512ac" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/baaa170a-d6ea-41f1-a132-390ed4e519b9" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/3313ae86-86b9-4afb-9459-546db428a998" />



| `SearchExpenseActivity` | Expenses search results |
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/1b9ed0e2-033f-41e4-bf97-d956cdaf6666" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/06d7c3f3-4aed-467f-87e0-e0d2e297d166" />

 | `ExpenseDetailActivity` | Expenses in detail |
 <img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/52c2389c-2043-4f60-b458-b2b092038d5a" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/9618fada-c5c8-44d0-a6e0-3e8ec0d4d228" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/20a98db5-2478-47a3-ae31-5d6fb585a078" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/41520046-abf4-40b3-b442-1b12e27119ba" />

| `AddExpenseActivity` | Add expense with split configuration |
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/31f91f86-0734-4100-bd4e-af5e8bc165bd" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/5119df0a-9d77-4a76-95e4-80f60ee39fc2" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/bb0e1097-41f4-4aeb-9642-e47505884e9d" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/62f9afb9-5983-4eca-9bc3-ca079a74dfe8" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/7a8a7607-f97b-4918-8667-511980bd7d6c" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/e3d88b3e-2dca-4840-9296-65ab9dc132df" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/71aadfaf-a3d4-441a-81df-fd302049ffb1" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/8f64a0cb-6ba6-409c-bea9-2659dca1c81f" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/151532c8-94a6-4936-9247-97af1b68d820" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/59207115-d30f-4993-8d66-bfb16f7e71d2" />


| `EditTricountActivity` | Edit existing tricount |
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/a938ff46-8426-41d0-bace-21a0e48300e6" />

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/eeb09e8c-bc11-466e-be55-a0e80ae46af3" />


| `EditExpenseActivity` | Edit existing expense |
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/a8b56fb9-3341-4875-b0bb-192277bbdc93" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/25ef758e-3a0f-4ea4-b869-4ba908f008ac" />

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/a5062fd9-df01-4af3-a99b-46d57b890eae" />

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/8f941777-b65a-4bfd-ae3a-ec8c66180052" />

| `InsightsActivity` | Analytics and charts |
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/0c566b75-6046-4b6c-b24f-56bf16fe57a9" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/3e75f860-e1cb-44cc-96fe-49d07e65e93c" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/fb23a6b5-076f-4ea3-b5b7-1420d76a38a4" />

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/93816a41-6b15-4859-bba9-f2f98ceb8d99" />

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/a2df9df4-66ec-4941-96d5-64d475dc3ca5" />

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/72a216b2-c6a9-403b-9626-8c085fedf817" />

| `ArchivedExpenseActivity` |Archived expense to delete, unarchive |
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/85c6bf3f-eb91-4c75-bcc4-56bc4078eb4e" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/619602b7-06b8-4ab5-84cc-30189b3cad38" />

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/b69072c8-5052-453c-8eb1-a7606e969909" />

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/9a59af2c-6b03-411e-8978-f10c0abfd2b3" />

| `NotificationsActivity` | Join requests + payment reminders |
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/61d47547-a973-404e-902c-97d26efd0434" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/e4e61473-2f05-4e22-a61f-ce1633d4040a" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/725476db-8f26-490c-ad50-e9bba204764c" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/12fa1036-0462-41a6-9f42-c79822226867" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/4df72eee-3ead-4246-b9ca-e35fd1b400bc" />
<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/8136e31b-6cce-450d-b988-b521eda90325" />

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/834907cf-900a-423e-b514-9bff8cce508c" />
| firestore` | database and rules |
<img width="797" height="454" alt="image" src="https://github.com/user-attachments/assets/b94ea4ce-4da5-4234-857f-e339d3f005ae" />
<img width="732" height="467" alt="image" src="https://github.com/user-attachments/assets/7fdfd4c6-83f9-451f-9589-ee8be1209cc3" />

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | MVVM + Repository |
| Local DB | Room v15 |
| Cloud DB | Cloud Firestore |
| Auth | Firebase Authentication |
| Push | Firebase Cloud Messaging |
| Networking | Retrofit 2 + OkHttp 3 |
| State | `StateFlow` + `collectAsStateWithLifecycle()` |
| Session | `SharedPreferences` (SessionManager) |

---

## 🏁 Getting Started

1. **Clone the repo**
   ```bash
   git clone https://github.com/your-username/tricount.git
   ```

2. **Add your `google-services.json`** to the `app/` directory (from your Firebase Console)

3. **Open in Android Studio** (Hedgehog or newer recommended)

4. **Build and run** on an emulator or physical device (API 26+)

---

## 🌱 What I Learned Building This

This was my **first complete Android app** — here's what the journey taught me:

- 📐 Designing a proper **MVVM architecture** from scratch
- 🔄 Managing **offline-first sync** between Room and Firestore
- 🔐 Implementing **Firebase Auth** with email verification flows
- 💡 Writing **complex SQL JOIN queries** with Room DAOs
- ⚡ Building reactive UIs entirely in **Jetpack Compose**
- 🧮 Implementing a **greedy debt-simplification algorithm**
- 🌍 Handling **multi-currency conversion** with graceful fallbacks

---

<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=4F52B2&height=120&section=footer&animation=fadeIn" width="100%"/>

**Made with 💙 as my first complete Android app**

*If you find this useful, drop a ⭐ — it means a lot!*

</div>
